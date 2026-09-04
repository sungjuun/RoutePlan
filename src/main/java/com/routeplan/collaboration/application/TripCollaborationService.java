package com.routeplan.collaboration.application;

import com.routeplan.auth.ResourceAccessService;
import com.routeplan.collaboration.domain.TripMemberRole;
import com.routeplan.collaboration.domain.TripVoteValue;
import com.routeplan.common.error.ErrorCode;
import com.routeplan.common.error.RoutePlanException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripCollaborationService {

    public static final int MAX_MEMBERS = 20;

    private final JdbcTemplate jdbc;
    private final ResourceAccessService access;

    public TripCollaborationService(JdbcTemplate jdbc, ResourceAccessService access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public CollaborationView get(long tripId, long userId) {
        TripMemberRole currentRole = access.requireTripViewer(tripId, userId);
        List<MemberView> members = members(tripId);
        int memberCount = members.size();
        List<PlaceVoteView> places = jdbc.query("""
                SELECT trip_place.place_id,
                       place.name AS place_name,
                       trip_place.must_visit,
                       trip_place.priority AS configured_priority,
                       COUNT(vote.user_id) FILTER (WHERE vote.value='YES') AS yes_count,
                       COUNT(vote.user_id) FILTER (WHERE vote.value='NO') AS no_count,
                       MAX(CASE WHEN vote.user_id=? THEN vote.value END) AS my_vote
                FROM trip_places trip_place
                JOIN places place ON place.id=trip_place.place_id
                LEFT JOIN trip_place_votes vote
                  ON vote.trip_id=trip_place.trip_id AND vote.place_id=trip_place.place_id
                WHERE trip_place.trip_id=?
                GROUP BY trip_place.place_id, place.name, trip_place.must_visit, trip_place.priority, trip_place.id
                ORDER BY trip_place.id
                """, (rs, row) -> {
            int yes = rs.getInt("yes_count");
            int no = rs.getInt("no_count");
            boolean mustVisit = rs.getBoolean("must_visit");
            int configured = rs.getInt("configured_priority");
            String ownVote = rs.getString("my_vote");
            return new PlaceVoteView(
                    rs.getLong("place_id"),
                    rs.getString("place_name"),
                    mustVisit,
                    configured,
                    effectivePriority(configured, mustVisit, yes, no, memberCount),
                    priorityBand(mustVisit, yes, no, memberCount),
                    yes,
                    no,
                    Math.max(0, memberCount - yes - no),
                    ownVote == null ? null : TripVoteValue.valueOf(ownVote)
            );
        }, userId, tripId);
        return new CollaborationView(tripId, userId, currentRole, members, places);
    }

    @Transactional
    public CollaborationView addMember(
            long tripId,
            long actorId,
            String email,
            TripMemberRole role
    ) {
        access.requireTripOwner(tripId, actorId);
        TripMemberRole normalizedRole = editableRole(role);
        String normalizedEmail = normalizeEmail(email);
        Long invitedUserId = jdbc.query(
                "SELECT id FROM users WHERE lower(email)=?",
                (rs, row) -> rs.getLong(1), normalizedEmail
        ).stream().findFirst().orElseThrow(() -> new RoutePlanException(
                ErrorCode.USER_NOT_FOUND,
                "해당 이메일로 가입한 사용자를 찾을 수 없습니다. 먼저 RoutePlan 가입을 요청해 주세요."
        ));
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM trip_members WHERE trip_id=?", Integer.class, tripId);
        if (count != null && count >= MAX_MEMBERS) {
            throw new RoutePlanException(ErrorCode.TRIP_MEMBER_LIMIT_EXCEEDED);
        }
        try {
            jdbc.update("""
                    INSERT INTO trip_members(trip_id,user_id,role,invited_by)
                    VALUES(?,?,?,?)
                    """, tripId, invitedUserId, normalizedRole.name(), actorId);
        } catch (DataIntegrityViolationException exception) {
            throw new RoutePlanException(ErrorCode.TRIP_MEMBER_ALREADY_EXISTS);
        }
        markTripDraft(tripId);
        return get(tripId, actorId);
    }

    @Transactional
    public CollaborationView updateMember(
            long tripId,
            long actorId,
            long memberId,
            TripMemberRole role
    ) {
        access.requireTripOwner(tripId, actorId);
        TripMemberRole normalizedRole = editableRole(role);
        int changed = jdbc.update("""
                UPDATE trip_members member
                SET role=?
                WHERE member.id=? AND member.trip_id=? AND member.role<>'OWNER'
                """, normalizedRole.name(), memberId, tripId);
        if (changed == 0) {
            throw memberMutationError(tripId, memberId);
        }
        markTripDraft(tripId);
        return get(tripId, actorId);
    }

    @Transactional
    public CollaborationView removeMember(long tripId, long actorId, long memberId) {
        access.requireTripOwner(tripId, actorId);
        MemberView member = memberById(tripId, memberId);
        if (member.role() == TripMemberRole.OWNER) {
            throw new RoutePlanException(ErrorCode.TRIP_OWNER_ROLE_IMMUTABLE);
        }
        Boolean referenced = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM trip_expenses expense
                    WHERE expense.trip_id=? AND expense.payer_user_id=?
                ) OR EXISTS (
                    SELECT 1 FROM trip_expense_participants participant
                    JOIN trip_expenses expense ON expense.id=participant.expense_id
                    WHERE expense.trip_id=? AND participant.user_id=?
                )
                """, Boolean.class, tripId, member.userId(), tripId, member.userId());
        if (Boolean.TRUE.equals(referenced)) {
            throw new RoutePlanException(
                    ErrorCode.CONFLICT,
                    "이 동행자가 포함된 정산 기록이 있습니다. 해당 지출을 먼저 정리해 주세요."
            );
        }
        jdbc.update("DELETE FROM trip_members WHERE id=? AND trip_id=?", memberId, tripId);
        markTripDraft(tripId);
        return get(tripId, actorId);
    }

    @Transactional
    public CollaborationView vote(
            long tripId,
            long userId,
            long placeId,
            TripVoteValue value
    ) {
        access.requireTripViewer(tripId, userId);
        requireTripPlace(tripId, placeId);
        if (value == null) {
            throw new IllegalArgumentException("투표 값은 필수입니다.");
        }
        jdbc.update("""
                INSERT INTO trip_place_votes(trip_id,place_id,user_id,value)
                VALUES(?,?,?,?)
                ON CONFLICT(trip_id,place_id,user_id)
                DO UPDATE SET value=EXCLUDED.value, updated_at=now()
                """, tripId, placeId, userId, value.name());
        markTripDraft(tripId);
        return get(tripId, userId);
    }

    @Transactional
    public CollaborationView removeVote(long tripId, long userId, long placeId) {
        access.requireTripViewer(tripId, userId);
        requireTripPlace(tripId, placeId);
        if (jdbc.update(
                "DELETE FROM trip_place_votes WHERE trip_id=? AND place_id=? AND user_id=?",
                tripId, placeId, userId) == 0) {
            throw new RoutePlanException(ErrorCode.TRIP_VOTE_NOT_FOUND);
        }
        markTripDraft(tripId);
        return get(tripId, userId);
    }

    @Transactional(readOnly = true)
    public Map<Long, Integer> effectivePriorities(long tripId) {
        Integer memberCount = jdbc.queryForObject(
                "SELECT count(*) FROM trip_members WHERE trip_id=?", Integer.class, tripId);
        int total = memberCount == null ? 1 : Math.max(1, memberCount);
        Map<Long, Integer> priorities = new LinkedHashMap<>();
        jdbc.query("""
                SELECT trip_place.place_id, trip_place.priority, trip_place.must_visit,
                       COUNT(vote.user_id) FILTER (WHERE vote.value='YES') AS yes_count,
                       COUNT(vote.user_id) FILTER (WHERE vote.value='NO') AS no_count
                FROM trip_places trip_place
                LEFT JOIN trip_place_votes vote
                  ON vote.trip_id=trip_place.trip_id AND vote.place_id=trip_place.place_id
                WHERE trip_place.trip_id=?
                GROUP BY trip_place.place_id, trip_place.priority, trip_place.must_visit, trip_place.id
                ORDER BY trip_place.id
                """, rs -> {
            while (rs.next()) {
                priorities.put(
                        rs.getLong("place_id"),
                        effectivePriority(
                                rs.getInt("priority"),
                                rs.getBoolean("must_visit"),
                                rs.getInt("yes_count"),
                                rs.getInt("no_count"),
                                total
                        )
                );
            }
            return null;
        }, tripId);
        return Map.copyOf(priorities);
    }

    private List<MemberView> members(long tripId) {
        return jdbc.query("""
                SELECT member.id, member.user_id, app_user.nickname, app_user.email,
                       member.role, member.joined_at
                FROM trip_members member
                JOIN users app_user ON app_user.id=member.user_id
                WHERE member.trip_id=?
                ORDER BY CASE member.role WHEN 'OWNER' THEN 0 WHEN 'EDITOR' THEN 1 ELSE 2 END,
                         member.joined_at, member.id
                """, (rs, row) -> new MemberView(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getString("nickname"),
                rs.getString("email"),
                TripMemberRole.valueOf(rs.getString("role")),
                timestamp(rs.getTimestamp("joined_at"))
        ), tripId);
    }

    private MemberView memberById(long tripId, long memberId) {
        return jdbc.query("""
                SELECT member.id, member.user_id, app_user.nickname, app_user.email,
                       member.role, member.joined_at
                FROM trip_members member
                JOIN users app_user ON app_user.id=member.user_id
                WHERE member.trip_id=? AND member.id=?
                """, (rs, row) -> new MemberView(
                rs.getLong("id"), rs.getLong("user_id"), rs.getString("nickname"),
                rs.getString("email"), TripMemberRole.valueOf(rs.getString("role")),
                timestamp(rs.getTimestamp("joined_at"))
        ), tripId, memberId).stream().findFirst()
                .orElseThrow(() -> new RoutePlanException(ErrorCode.TRIP_MEMBER_NOT_FOUND));
    }

    private RoutePlanException memberMutationError(long tripId, long memberId) {
        MemberView member = memberById(tripId, memberId);
        return member.role() == TripMemberRole.OWNER
                ? new RoutePlanException(ErrorCode.TRIP_OWNER_ROLE_IMMUTABLE)
                : new RoutePlanException(ErrorCode.TRIP_MEMBER_NOT_FOUND);
    }

    private void requireTripPlace(long tripId, long placeId) {
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM trip_places WHERE trip_id=? AND place_id=?)",
                Boolean.class, tripId, placeId);
        if (!Boolean.TRUE.equals(exists)) {
            throw new RoutePlanException(ErrorCode.TRIP_PLACE_NOT_FOUND);
        }
    }

    private void markTripDraft(long tripId) {
        jdbc.update("UPDATE trips SET status='DRAFT', updated_at=now() WHERE id=?", tripId);
    }

    private static TripMemberRole editableRole(TripMemberRole role) {
        if (role == null) {
            throw new IllegalArgumentException("동행자 권한은 필수입니다.");
        }
        if (role == TripMemberRole.OWNER) {
            throw new RoutePlanException(ErrorCode.TRIP_OWNER_ROLE_IMMUTABLE);
        }
        return role;
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("초대할 사용자의 이메일은 필수입니다.");
        }
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        if (normalized.length() > 254) {
            throw new IllegalArgumentException("이메일은 254자를 초과할 수 없습니다.");
        }
        return normalized;
    }

    private static int effectivePriority(
            int configured,
            boolean mustVisit,
            int yes,
            int no,
            int memberCount
    ) {
        if (mustVisit) {
            return 100;
        }
        if (yes + no == 0) {
            return configured;
        }
        if (yes >= memberCount) {
            return 90;
        }
        if ((long) yes * 3 >= (long) memberCount * 2) {
            return 60;
        }
        if (yes > 0) {
            return 30;
        }
        return 10;
    }

    private static VotePriorityBand priorityBand(
            boolean mustVisit,
            int yes,
            int no,
            int memberCount
    ) {
        if (mustVisit) {
            return VotePriorityBand.MUST;
        }
        if (yes + no == 0) {
            return VotePriorityBand.UNVOTED;
        }
        if (yes >= memberCount) {
            return VotePriorityBand.HIGH;
        }
        if ((long) yes * 3 >= (long) memberCount * 2) {
            return VotePriorityBand.NORMAL;
        }
        return VotePriorityBand.LOW;
    }

    private static Instant timestamp(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    public enum VotePriorityBand {
        MUST,
        HIGH,
        NORMAL,
        LOW,
        UNVOTED
    }

    public record CollaborationView(
            long tripId,
            long currentUserId,
            TripMemberRole currentRole,
            List<MemberView> members,
            List<PlaceVoteView> places
    ) {}

    public record MemberView(
            long memberId,
            long userId,
            String nickname,
            String email,
            TripMemberRole role,
            Instant joinedAt
    ) {}

    public record PlaceVoteView(
            long placeId,
            String placeName,
            boolean mustVisit,
            int configuredPriority,
            int effectivePriority,
            VotePriorityBand priorityBand,
            int yesCount,
            int noCount,
            int pendingCount,
            TripVoteValue myVote
    ) {}
}
