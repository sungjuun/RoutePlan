package com.routeplan.optimization.constraint;

public class BudgetConstraintException extends RuntimeException {

    public enum Reason { MISSING_COST, EXCEEDED }

    private final Reason reason;

    public BudgetConstraintException(Reason reason) {
        super(reason == Reason.MISSING_COST
                ? "예산을 적용하려면 모든 후보 장소와 완료 구간의 비용이 필요합니다. 비용을 입력하거나 예산 제한을 해제해 주세요."
                : "고정비·완료 구간·꼭 가기 장소의 비용이 예산을 초과합니다. 예산 또는 방문 조건을 조정해 주세요.");
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
