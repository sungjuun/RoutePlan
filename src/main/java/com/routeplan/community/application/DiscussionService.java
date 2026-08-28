package com.routeplan.community.application;

import com.routeplan.auth.RoutePlanPrincipal;
import com.routeplan.common.error.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service
public class DiscussionService {
    public enum Target { ROUTE, COMMENT, REVIEW }
    public enum Reason { SPAM, ABUSE, MISLEADING, PRIVACY, OTHER }
    public enum Resolution { DISMISS, HIDE }
    private final JdbcTemplate jdbc;
    private final Set<String> moderators;
    public DiscussionService(JdbcTemplate jdbc, @Value("${routeplan.community.moderator-emails:}") String emails) {
        this.jdbc = jdbc; moderators = new HashSet<>(Arrays.stream(emails.split(",")).map(s -> s.strip().toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty()).toList());
    }
    public boolean canModerate(RoutePlanPrincipal user) { return user != null && moderators.contains(user.email().toLowerCase(Locale.ROOT)); }

    @Transactional(readOnly = true)
    public Discussion get(long routeId, int page) {
        requireRoute(routeId);
        if (page < 0 || page > 10000) throw new IllegalArgumentException("페이지 범위가 올바르지 않습니다.");
        List<Entry> comments = entries("route_comments", routeId, page);
        List<Entry> reviews = entries("route_reviews", routeId, page);
        Double average = jdbc.queryForObject("SELECT COALESCE(AVG(rating),0) FROM route_reviews WHERE route_id=? AND hidden=false", Double.class, routeId);
        Long count = jdbc.queryForObject("SELECT count(*) FROM route_reviews WHERE route_id=? AND hidden=false", Long.class, routeId);
        Long commentCount = jdbc.queryForObject("SELECT count(*) FROM route_comments WHERE route_id=? AND hidden=false", Long.class, routeId);
        return new Discussion(comments, reviews, average, count, commentCount, page);
    }
    private List<Entry> entries(String table, long routeId, int page) {
        String rating = table.equals("route_reviews") ? "e.rating" : "NULL AS rating";
        return jdbc.query("SELECT e.id,e.user_id,u.nickname,e.body,e.created_at," + rating
                        + " FROM " + table + " e JOIN users u ON u.id=e.user_id WHERE e.route_id=? AND e.hidden=false ORDER BY e.id DESC LIMIT 20 OFFSET ?",
                (rs,n) -> new Entry(rs.getLong("id"),rs.getLong("user_id"),rs.getString("nickname"),
                        rs.getString("body"),(Integer)rs.getObject("rating"),rs.getTimestamp("created_at").toInstant()), routeId, page*20);
    }
    @Transactional
    public Discussion comment(long routeId, long userId, Long id, String body) {
        requireRoute(routeId); validateBody(body); lockAndLimit(userId);
        if (id == null) jdbc.update("INSERT INTO route_comments(route_id,user_id,body) VALUES(?,?,?)",routeId,userId,body.strip());
        else if (jdbc.update("UPDATE route_comments SET body=?,updated_at=now() WHERE id=? AND route_id=? AND user_id=? AND hidden=false",body.strip(),id,routeId,userId)!=1) throw denied();
        return get(routeId,0);
    }
    @Transactional
    public Discussion review(long routeId, long userId, int rating, String body) {
        long owner = requireRoute(routeId); validateBody(body); lockAndLimit(userId);
        if (owner == userId) throw new IllegalArgumentException("본인이 공개한 루트에는 후기를 작성할 수 없습니다.");
        if (rating < 1 || rating > 5) throw new IllegalArgumentException("별점은 1~5점입니다.");
        int changed = jdbc.update("""
                INSERT INTO route_reviews(route_id,user_id,rating,body) VALUES(?,?,?,?)
                ON CONFLICT(route_id,user_id) DO UPDATE SET rating=EXCLUDED.rating,body=EXCLUDED.body,updated_at=now()
                WHERE route_reviews.hidden=false
                """,routeId,userId,rating,body.strip());
        if (changed == 0) throw denied();
        return get(routeId,0);
    }
    @Transactional
    public Discussion delete(long routeId, long userId, long id, Target target) {
        requireRoute(routeId);
        String table = table(target);
        if (jdbc.update("DELETE FROM " + table + " WHERE id=? AND route_id=? AND user_id=?",id,routeId,userId)!=1) throw denied();
        return get(routeId,0);
    }
    @Transactional
    public Report report(long routeId, long userId, Target target, long targetId, Reason reason, String detail) {
        requireRoute(routeId); lockAndLimit(userId);
        if (target == null || reason == null || detail == null || detail.length()>1000) throw new IllegalArgumentException("신고 내용을 확인해 주세요.");
        if (target == Target.ROUTE) {
            if (targetId != routeId) throw new IllegalArgumentException("신고 대상 루트가 일치하지 않습니다.");
        } else if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM " + table(target) + " WHERE id=? AND route_id=?)",Boolean.class,targetId,routeId))) throw denied();
        jdbc.update("""
                INSERT INTO community_reports(route_id,reporter_id,target_type,target_id,reason,detail) VALUES(?,?,?,?,?,?)
                ON CONFLICT(reporter_id,target_type,target_id) DO NOTHING
                """,routeId,userId,target.name(),targetId,reason.name(),detail.strip());
        return jdbc.queryForObject("SELECT id,status FROM community_reports WHERE reporter_id=? AND target_type=? AND target_id=?",
                (rs,n)->new Report(rs.getLong(1),rs.getString(2)),userId,target.name(),targetId);
    }
    public List<ModerationReport> queue(RoutePlanPrincipal user) {
        if (!canModerate(user)) throw denied();
        return jdbc.query("""
                SELECT r.*, CASE r.target_type
                  WHEN 'ROUTE' THEN (SELECT title || E'\\n' || COALESCE(description,'') FROM shared_routes WHERE id=r.target_id)
                  WHEN 'COMMENT' THEN (SELECT body FROM route_comments WHERE id=r.target_id)
                  WHEN 'REVIEW' THEN (SELECT rating || ' / 5: ' || body FROM route_reviews WHERE id=r.target_id)
                END AS target_content
                FROM community_reports r WHERE r.status='OPEN' ORDER BY r.id LIMIT 100
                """,
                (rs,n)->new ModerationReport(rs.getLong("id"),rs.getLong("route_id"),rs.getString("target_type"),
                        rs.getLong("target_id"),rs.getString("reason"),rs.getString("detail"),rs.getString("target_content")));
    }
    @Transactional
    public void resolve(RoutePlanPrincipal user,long reportId,Resolution resolution) {
        if (!canModerate(user)) throw denied();
        if (resolution == null) throw new IllegalArgumentException("처리 방법을 선택해 주세요.");
        var rows = jdbc.queryForList("SELECT * FROM community_reports WHERE id=? AND status='OPEN' FOR UPDATE",reportId);
        if (rows.isEmpty()) throw new RoutePlanException(ErrorCode.CONFLICT,"이미 처리되었거나 없는 신고입니다.");
        var row=rows.getFirst();
        if (resolution==Resolution.HIDE) {
            Target target=Target.valueOf((String)row.get("target_type"));
            String table=target==Target.ROUTE?"shared_routes":table(target);
            String flag=target==Target.ROUTE?"moderated_hidden":"hidden";
            jdbc.update("UPDATE "+table+" SET "+flag+"=true WHERE id=?",row.get("target_id"));
        }
        jdbc.update("UPDATE community_reports SET status=?,resolved_at=now(),resolved_by=? WHERE id=?",resolution==Resolution.HIDE?"ACTIONED":"DISMISSED",user.userId(),reportId);
    }
    private long requireRoute(long routeId) {
        return jdbc.query("SELECT user_id FROM shared_routes WHERE id=? AND moderated_hidden=false",(rs,n)->rs.getLong(1),routeId)
                .stream().findFirst().orElseThrow(()->new RoutePlanException(ErrorCode.SHARED_ROUTE_NOT_FOUND));
    }
    private void lockAndLimit(long userId) {
        jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE",Long.class,userId);
        int reserved = jdbc.update("""
                INSERT INTO community_write_usage(user_id,bucket_start,units) VALUES(?,date_trunc('hour',now()),1)
                ON CONFLICT(user_id,bucket_start) DO UPDATE SET units=community_write_usage.units+1
                WHERE community_write_usage.units<30
                """,userId);
        if (reserved==0) throw new RoutePlanException(ErrorCode.CONFLICT,"시간당 댓글·후기·신고 작성/수정은 최대 30회입니다. 다음 시간대에 다시 시도해 주세요.");
    }
    private void validateBody(String body) { if(body==null||body.isBlank()||body.length()>2000) throw new IllegalArgumentException("내용은 1~2000자입니다."); }
    private String table(Target target) { return switch(target) { case COMMENT -> "route_comments"; case REVIEW -> "route_reviews"; default -> throw new IllegalArgumentException("댓글 또는 후기를 선택하세요."); }; }
    private RoutePlanException denied() { return new RoutePlanException(ErrorCode.ACCESS_DENIED); }
    public record Entry(long id,long userId,String nickname,String body,Integer rating,Instant createdAt) {}
    public record Discussion(List<Entry> comments,List<Entry> reviews,double averageRating,long reviewCount,long commentCount,int page) {}
    public record Report(long id,String status) {}
    public record ModerationReport(long id,long routeId,String targetType,long targetId,String reason,String detail,String targetContent) {}
}
