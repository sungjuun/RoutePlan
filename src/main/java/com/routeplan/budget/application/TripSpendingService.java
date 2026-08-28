package com.routeplan.budget.application;

import com.routeplan.budget.domain.BudgetSettings;
import com.routeplan.common.error.*;
import com.routeplan.trip.domain.Trip;
import com.routeplan.trip.persistence.TripRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;

@Service
public class TripSpendingService {
    public enum Category { ACCOMMODATION, FOOD, TRANSPORT, ACTIVITY, SHOPPING, OTHER }
    private final JdbcTemplate jdbc;
    private final TripRepository trips;
    public TripSpendingService(JdbcTemplate jdbc, TripRepository trips) { this.jdbc = jdbc; this.trips = trips; }

    @Transactional(readOnly = true)
    public Spending get(long tripId) {
        Trip trip = require(tripId, false);
        var expenses = jdbc.query("SELECT * FROM trip_expenses WHERE trip_id=? ORDER BY spend_date, id",
                (rs, row) -> new Expense(rs.getLong("id"), rs.getObject("request_id", UUID.class),
                        rs.getObject("spend_date", LocalDate.class), Category.valueOf(rs.getString("category")),
                        rs.getString("description"), rs.getLong("amount_minor")), tripId);
        var allocations = jdbc.query("SELECT * FROM trip_budget_allocations WHERE trip_id=? ORDER BY spend_date NULLS FIRST, category NULLS FIRST",
                (rs, row) -> new Allocation(rs.getObject("spend_date", LocalDate.class),
                        rs.getString("category") == null ? null : Category.valueOf(rs.getString("category")), rs.getLong("limit_minor")), tripId);
        var scopes = allocations.stream().map(a -> {
            long spent = expenses.stream().filter(e -> (a.date() == null || a.date().equals(e.date()))
                    && (a.category() == null || a.category() == e.category())).mapToLong(Expense::amountMinor).sum();
            return new Scope(a.date(), a.category(), a.limitMinor(), spent, a.limitMinor() - spent);
        }).toList();
        return new Spending(trip.getBudgetSettings().currency().name(), trip.getBudgetSettings().limitMinor(),
                expenses.stream().mapToLong(Expense::amountMinor).sum(), scopes, expenses);
    }

    @Transactional
    public Spending allocations(long tripId, List<Allocation> values, com.routeplan.budget.domain.BudgetCurrency currency) {
        Trip trip = require(tripId, true);
        if (trip.getBudgetSettings().currency() != currency) throw new RoutePlanException(ErrorCode.CONFLICT, "통화가 변경되었습니다. 지출 장부를 새로고침해 주세요.");
        if (values == null || values.size() > 100) throw new IllegalArgumentException("예산 구간은 최대 100개입니다.");
        Set<String> keys = new HashSet<>();
        for (var value : values) {
            if (value == null || (value.date() == null && value.category() == null)) throw new IllegalArgumentException("날짜 또는 항목을 선택해 주세요.");
            if (value.date() != null) validateDate(trip, value.date());
            BudgetSettings.requireAmount(value.limitMinor());
            if (!keys.add(value.date() + ":" + value.category())) throw new IllegalArgumentException("같은 예산 구간은 중복될 수 없습니다.");
        }
        jdbc.update("DELETE FROM trip_budget_allocations WHERE trip_id=?", tripId);
        values.forEach(v -> jdbc.update("INSERT INTO trip_budget_allocations(trip_id,spend_date,category,limit_minor) VALUES(?,?,?,?)",
                tripId, v.date(), v.category() == null ? null : v.category().name(), v.limitMinor()));
        return get(tripId);
    }

    @Transactional
    public Spending save(long tripId, Long expenseId, UUID requestId, LocalDate date, Category category, String description, long amount, com.routeplan.budget.domain.BudgetCurrency currency) {
        Trip trip = require(tripId, true); validateDate(trip, date); BudgetSettings.requireAmount(amount);
        if (trip.getBudgetSettings().currency() != currency) throw new RoutePlanException(ErrorCode.CONFLICT, "통화가 변경되었습니다. 지출 장부를 새로고침해 주세요.");
        if (description == null || description.isBlank() || description.length() > 200 || category == null || requestId == null) throw new IllegalArgumentException("지출 입력을 확인해 주세요.");
        if (expenseId == null) {
            int inserted = jdbc.update("""
                    INSERT INTO trip_expenses(trip_id,request_id,spend_date,category,description,amount_minor)
                    VALUES(?,?,?,?,?,?) ON CONFLICT(trip_id,request_id) DO NOTHING
                    """, tripId, requestId, date, category.name(), description.strip(), amount);
            if (inserted == 0) {
                boolean same = get(tripId).expenses().stream().anyMatch(e -> e.requestId().equals(requestId)
                        && e.date().equals(date) && e.category() == category && e.description().equals(description.strip()) && e.amountMinor() == amount);
                if (!same) throw new RoutePlanException(ErrorCode.CONFLICT, "이미 사용한 지출 요청 ID입니다.");
            }
        } else if (jdbc.update("UPDATE trip_expenses SET spend_date=?,category=?,description=?,amount_minor=? WHERE id=? AND trip_id=?",
                date, category.name(), description.strip(), amount, expenseId, tripId) != 1) throw new RoutePlanException(ErrorCode.ACCESS_DENIED);
        return get(tripId);
    }

    @Transactional
    public Spending delete(long tripId, long expenseId) {
        require(tripId, true);
        if (jdbc.update("DELETE FROM trip_expenses WHERE id=? AND trip_id=?", expenseId, tripId) != 1) throw new RoutePlanException(ErrorCode.ACCESS_DENIED);
        return get(tripId);
    }
    private Trip require(long id, boolean lock) { return (lock ? trips.findByIdForUpdate(id) : trips.findById(id)).orElseThrow(() -> new RoutePlanException(ErrorCode.TRIP_NOT_FOUND)); }
    private void validateDate(Trip trip, LocalDate date) {
        if (date == null || date.isBefore(trip.getStartDate()) || date.isAfter(trip.getEndDate())) throw new IllegalArgumentException("지출 날짜는 여행 기간 안이어야 합니다.");
    }
    public record Allocation(LocalDate date, Category category, long limitMinor) {}
    public record Expense(long id, UUID requestId, LocalDate date, Category category, String description, long amountMinor) {}
    public record Scope(LocalDate date, Category category, long limitMinor, long spentMinor, long remainingMinor) {}
    public record Spending(String currency, Long totalLimitMinor, long spentMinor, List<Scope> scopes, List<Expense> expenses) {}
}
