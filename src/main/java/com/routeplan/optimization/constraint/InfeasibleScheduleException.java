package com.routeplan.optimization.constraint;

import java.util.List;

public class InfeasibleScheduleException extends RuntimeException {

    private final List<ConstraintViolation> violations;

    public InfeasibleScheduleException(List<ConstraintViolation> violations) {
        super(buildMessage(violations));
        this.violations = List.copyOf(violations);
    }

    public List<ConstraintViolation> violations() {
        return violations;
    }

    private static String buildMessage(List<ConstraintViolation> violations) {
        if (violations == null || violations.isEmpty()) {
            return "현재 조건으로는 모든 MUST_VISIT 장소를 방문할 수 없습니다.";
        }
        String places = violations.stream()
                .map(ConstraintViolation::placeName)
                .distinct()
                .sorted()
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
        return "현재 조건으로는 모든 MUST_VISIT 장소를 방문할 수 없습니다: " + places;
    }
}
