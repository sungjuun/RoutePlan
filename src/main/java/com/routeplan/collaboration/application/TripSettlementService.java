package com.routeplan.collaboration.application;

import com.routeplan.auth.ResourceAccessService;
import com.routeplan.budget.application.TripSpendingService.Category;
import com.routeplan.budget.domain.BudgetCurrency;
import com.routeplan.collaboration.domain.TripMemberRole;
import com.routeplan.common.error.ErrorCode;
import com.routeplan.common.error.RoutePlanException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripSettlementService {

    private static final long MAX_AMOUNT = 1_000_000_000_000L;
    private static final int EXACT_SETTLEMENT_MEMBER_LIMIT = 12;

    private final JdbcTemplate jdbc;
    private final ResourceAccessService access;

    public TripSettlementService(JdbcTemplate jdbc, ResourceAccessService access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public SettlementView get(long tripId, long userId) {
        access.requireTripViewer(tripId, userId);
        TripInfo trip = tripInfo(tripId);
        Map<Long, MemberIdentity> identities = memberIdentities(tripId);
        List<SharedExpenseView> expenses = jdbc.query("""
                SELECT expense.id, expense.request_id, expense.spend_date, expense.category,
                       expense.description, expense.amount_minor, expense.place_id, place.name AS place_name,
                       expense.payer_user_id, expense.payer_nickname_snapshot,
                       expense.created_by_user_id
                FROM trip_expenses expense
                LEFT JOIN places place ON place.id=expense.place_id
                WHERE expense.trip_id=?
                  AND EXISTS(SELECT 1 FROM trip_expense_participants participant WHERE participant.expense_id=expense.id)
                ORDER BY expense.spend_date, expense.id
                """, (rs, row) -> {
            long expenseId = rs.getLong("id");
            List<ShareView> participants = jdbc.query("""
                    SELECT participant.user_id, participant.nickname_snapshot, participant.share_minor
                    FROM trip_expense_participants participant
                    WHERE participant.expense_id=?
                    ORDER BY participant.user_id
                    """, (shareRs, shareRow) -> new ShareView(
                    shareRs.getLong("user_id"),
                    shareRs.getString("nickname_snapshot"),
                    shareRs.getLong("share_minor")
            ), expenseId);
            return new SharedExpenseView(
                    expenseId,
                    rs.getObject("request_id", UUID.class),
                    rs.getObject("spend_date", LocalDate.class),
                    Category.valueOf(rs.getString("category")),
                    rs.getString("description"),
                    rs.getLong("amount_minor"),
                    rs.getObject("place_id", Long.class),
                    rs.getString("place_name"),
                    rs.getObject("payer_user_id", Long.class),
                    rs.getString("payer_nickname_snapshot"),
                    rs.getObject("created_by_user_id", Long.class),
                    participants
            );
        }, tripId);

        Map<Long, MutableBalance> balanceByUser = new LinkedHashMap<>();
        identities.values().forEach(identity -> balanceByUser.put(
                identity.userId(), new MutableBalance(identity.userId(), identity.nickname())));
        for (SharedExpenseView expense : expenses) {
            if (expense.payerUserId() != null) {
                balanceByUser.computeIfAbsent(
                        expense.payerUserId(),
                        ignored -> new MutableBalance(expense.payerUserId(), expense.payerNickname())
                ).paid = Math.addExact(
                        balanceByUser.get(expense.payerUserId()).paid,
                        expense.amountMinor()
                );
            }
            for (ShareView share : expense.participants()) {
                MutableBalance balance = balanceByUser.computeIfAbsent(
                        share.userId(),
                        ignored -> new MutableBalance(share.userId(), share.nickname())
                );
                balance.owed = Math.addExact(balance.owed, share.shareMinor());
            }
        }
        List<BalanceView> balances = balanceByUser.values().stream()
                .map(MutableBalance::view)
                .sorted(Comparator.comparing(BalanceView::nickname).thenComparingLong(BalanceView::userId))
                .toList();
        List<TransferView> transfers = minimumTransfers(balances);
        return new SettlementView(
                tripId,
                trip.currency(),
                expenses,
                balances,
                transfers,
                balances.stream().filter(balance -> balance.netMinor() != 0).count() <= EXACT_SETTLEMENT_MEMBER_LIMIT
        );
    }

    @Transactional
    public SettlementView create(long tripId, long actorId, CreateSharedExpense command) {
        access.requireTripEditor(tripId, actorId);
        TripInfo trip = tripInfo(tripId);
        validate(command, trip);
        Map<Long, MemberIdentity> members = memberIdentities(tripId);
        MemberIdentity payer = members.get(command.payerUserId());
        if (payer == null) {
            throw new RoutePlanException(ErrorCode.SETTLEMENT_PARTICIPANT_INVALID);
        }
        List<Long> participantIds = command.participantUserIds().stream().distinct().sorted().toList();
        if (participantIds.size() != command.participantUserIds().size()
                || participantIds.stream().anyMatch(id -> !members.containsKey(id))) {
            throw new RoutePlanException(ErrorCode.SETTLEMENT_PARTICIPANT_INVALID);
        }
        if (command.placeId() != null && !Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM trip_places WHERE trip_id=? AND place_id=?)",
                Boolean.class, tripId, command.placeId()))) {
            throw new RoutePlanException(ErrorCode.TRIP_PLACE_NOT_FOUND);
        }

        long expenseId;
        try {
            expenseId = Objects.requireNonNull(jdbc.queryForObject("""
                    INSERT INTO trip_expenses(
                        trip_id,request_id,spend_date,category,description,amount_minor,place_id,
                        payer_user_id,created_by_user_id,payer_nickname_snapshot
                    ) VALUES(?,?,?,?,?,?,?,?,?,?)
                    RETURNING id
                    """, Long.class, tripId, command.requestId(), command.date(), command.category().name(),
                    command.description().strip(), command.amountMinor(), command.placeId(),
                    command.payerUserId(), actorId, payer.nickname()));
        } catch (DataIntegrityViolationException exception) {
            throw new RoutePlanException(ErrorCode.CONFLICT, "이미 사용한 정산 요청 ID입니다.");
        }

        long base = command.amountMinor() / participantIds.size();
        long remainder = command.amountMinor() % participantIds.size();
        for (int index = 0; index < participantIds.size(); index++) {
            long participantId = participantIds.get(index);
            long share = base + (index < remainder ? 1 : 0);
            jdbc.update("""
                    INSERT INTO trip_expense_participants(expense_id,user_id,nickname_snapshot,share_minor)
                    VALUES(?,?,?,?)
                    """, expenseId, participantId, members.get(participantId).nickname(), share);
        }
        return get(tripId, actorId);
    }

    @Transactional
    public SettlementView delete(long tripId, long actorId, long expenseId) {
        TripMemberRole role = access.requireTripEditor(tripId, actorId);
        List<Long> creators = jdbc.query(
                "SELECT created_by_user_id FROM trip_expenses WHERE id=? AND trip_id=?",
                (rs, row) -> rs.getObject(1, Long.class), expenseId, tripId);
        if (creators.isEmpty()) {
            throw new RoutePlanException(ErrorCode.SETTLEMENT_EXPENSE_NOT_FOUND);
        }
        if (role != TripMemberRole.OWNER && !Objects.equals(creators.getFirst(), actorId)) {
            throw new RoutePlanException(ErrorCode.ACCESS_DENIED);
        }
        jdbc.update("DELETE FROM trip_expenses WHERE id=? AND trip_id=?", expenseId, tripId);
        return get(tripId, actorId);
    }

    private void validate(CreateSharedExpense command, TripInfo trip) {
        if (command == null || command.requestId() == null || command.date() == null
                || command.category() == null || command.payerUserId() == null
                || command.participantUserIds() == null || command.participantUserIds().isEmpty()) {
            throw new IllegalArgumentException("정산 지출의 필수값을 확인해 주세요.");
        }
        if (command.description() == null || command.description().isBlank()
                || command.description().strip().length() > 200) {
            throw new IllegalArgumentException("정산 내용은 1자 이상 200자 이하여야 합니다.");
        }
        if (command.amountMinor() <= 0 || command.amountMinor() > MAX_AMOUNT) {
            throw new IllegalArgumentException("정산 금액이 올바르지 않습니다.");
        }
        if (command.date().isBefore(trip.startDate()) || command.date().isAfter(trip.endDate())) {
            throw new IllegalArgumentException("정산 날짜는 여행 기간 안이어야 합니다.");
        }
        if (command.currency() != trip.currency()) {
            throw new RoutePlanException(ErrorCode.CONFLICT, "여행 예산 통화와 정산 통화가 다릅니다.");
        }
        if (command.participantUserIds().size() > TripCollaborationService.MAX_MEMBERS) {
            throw new RoutePlanException(ErrorCode.SETTLEMENT_PARTICIPANT_INVALID);
        }
    }

    private TripInfo tripInfo(long tripId) {
        return jdbc.query("""
                SELECT start_date,end_date,budget_currency FROM trips WHERE id=?
                """, (rs, row) -> new TripInfo(
                rs.getObject("start_date", LocalDate.class),
                rs.getObject("end_date", LocalDate.class),
                BudgetCurrency.valueOf(rs.getString("budget_currency"))
        ), tripId).stream().findFirst()
                .orElseThrow(() -> new RoutePlanException(ErrorCode.TRIP_NOT_FOUND));
    }

    private Map<Long, MemberIdentity> memberIdentities(long tripId) {
        Map<Long, MemberIdentity> members = new LinkedHashMap<>();
        jdbc.query("""
                SELECT member.user_id, app_user.nickname
                FROM trip_members member
                JOIN users app_user ON app_user.id=member.user_id
                WHERE member.trip_id=? ORDER BY member.id
                """, rs -> {
            while (rs.next()) {
                long userId = rs.getLong("user_id");
                members.put(userId, new MemberIdentity(userId, rs.getString("nickname")));
            }
            return null;
        }, tripId);
        return members;
    }

    private List<TransferView> minimumTransfers(List<BalanceView> balances) {
        List<BalanceView> unsettled = balances.stream()
                .filter(balance -> balance.netMinor() != 0)
                .toList();
        if (unsettled.isEmpty()) {
            return List.of();
        }
        if (unsettled.size() > EXACT_SETTLEMENT_MEMBER_LIMIT) {
            return greedyTransfers(unsettled);
        }
        long[] net = unsettled.stream().mapToLong(BalanceView::netMinor).toArray();
        int allAccounts = (1 << unsettled.size()) - 1;
        List<Integer> groups = maximumZeroSumPartition(allAccounts, net, new java.util.HashMap<>());
        List<TransferView> result = new ArrayList<>();
        for (int group : groups) {
            List<BalanceView> accounts = new ArrayList<>();
            for (int index = 0; index < unsettled.size(); index++) {
                if ((group & (1 << index)) != 0) {
                    accounts.add(unsettled.get(index));
                }
            }
            result.addAll(greedyTransfers(accounts));
        }
        return List.copyOf(result);
    }

    private List<Integer> maximumZeroSumPartition(
            int remaining,
            long[] net,
            Map<Integer, List<Integer>> memo
    ) {
        if (remaining == 0) {
            return List.of();
        }
        List<Integer> cached = memo.get(remaining);
        if (cached != null) {
            return cached;
        }
        int first = Integer.lowestOneBit(remaining);
        int optional = remaining ^ first;
        List<Integer> best = List.of(remaining);
        for (int subset = optional; ; subset = (subset - 1) & optional) {
            int group = subset | first;
            if (group != remaining && zeroSum(group, net)) {
                List<Integer> tail = maximumZeroSumPartition(remaining ^ group, net, memo);
                if (tail.size() + 1 > best.size()) {
                    List<Integer> candidate = new ArrayList<>(tail.size() + 1);
                    candidate.add(group);
                    candidate.addAll(tail);
                    best = List.copyOf(candidate);
                }
            }
            if (subset == 0) {
                break;
            }
        }
        memo.put(remaining, best);
        return best;
    }

    private boolean zeroSum(int group, long[] net) {
        long sum = 0;
        for (int index = 0; index < net.length; index++) {
            if ((group & (1 << index)) != 0) {
                sum = Math.addExact(sum, net[index]);
            }
        }
        return sum == 0;
    }

    private List<TransferView> greedyTransfers(List<BalanceView> balances) {
        List<MutableNet> debtors = balances.stream()
                .filter(balance -> balance.netMinor() < 0)
                .map(balance -> new MutableNet(balance, -balance.netMinor()))
                .sorted(Comparator.comparingLong(MutableNet::amount).reversed()).toList();
        List<MutableNet> creditors = balances.stream()
                .filter(balance -> balance.netMinor() > 0)
                .map(balance -> new MutableNet(balance, balance.netMinor()))
                .sorted(Comparator.comparingLong(MutableNet::amount).reversed()).toList();
        List<TransferView> result = new ArrayList<>();
        int debtorIndex = 0;
        int creditorIndex = 0;
        while (debtorIndex < debtors.size() && creditorIndex < creditors.size()) {
            MutableNet debtor = debtors.get(debtorIndex);
            MutableNet creditor = creditors.get(creditorIndex);
            long amount = Math.min(debtor.amount, creditor.amount);
            result.add(transfer(debtor.balance, creditor.balance, amount));
            debtor.amount -= amount;
            creditor.amount -= amount;
            if (debtor.amount == 0) debtorIndex++;
            if (creditor.amount == 0) creditorIndex++;
        }
        return result;
    }

    private static TransferView transfer(BalanceView from, BalanceView to, long amount) {
        return new TransferView(
                from.userId(), from.nickname(), to.userId(), to.nickname(), amount);
    }

    private record TripInfo(LocalDate startDate, LocalDate endDate, BudgetCurrency currency) {}

    private record MemberIdentity(long userId, String nickname) {}

    private static final class MutableBalance {
        private final long userId;
        private final String nickname;
        private long paid;
        private long owed;

        private MutableBalance(long userId, String nickname) {
            this.userId = userId;
            this.nickname = nickname;
        }

        private BalanceView view() {
            return new BalanceView(userId, nickname, paid, owed, paid - owed);
        }
    }

    private static final class MutableNet {
        private final BalanceView balance;
        private long amount;

        private MutableNet(BalanceView balance, long amount) {
            this.balance = balance;
            this.amount = amount;
        }

        private long amount() {
            return amount;
        }
    }

    public record CreateSharedExpense(
            UUID requestId,
            LocalDate date,
            Category category,
            String description,
            long amountMinor,
            Long placeId,
            BudgetCurrency currency,
            Long payerUserId,
            List<Long> participantUserIds
    ) {}

    public record SettlementView(
            long tripId,
            BudgetCurrency currency,
            List<SharedExpenseView> expenses,
            List<BalanceView> balances,
            List<TransferView> transfers,
            boolean exactMinimum
    ) {}

    public record SharedExpenseView(
            long expenseId,
            UUID requestId,
            LocalDate date,
            Category category,
            String description,
            long amountMinor,
            Long placeId,
            String placeName,
            Long payerUserId,
            String payerNickname,
            Long createdByUserId,
            List<ShareView> participants
    ) {}

    public record ShareView(long userId, String nickname, long shareMinor) {}

    public record BalanceView(long userId, String nickname, long paidMinor, long owedMinor, long netMinor) {}

    public record TransferView(
            long fromUserId,
            String fromNickname,
            long toUserId,
            String toNickname,
            long amountMinor
    ) {}
}
