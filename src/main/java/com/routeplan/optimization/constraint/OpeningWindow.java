package com.routeplan.optimization.constraint;

/** Half-open local-day interval; 1440 represents the next midnight. */
public record OpeningWindow(int startMinute, int endMinute) {
    public OpeningWindow {
        if (startMinute < 0 || endMinute > 1440 || startMinute >= endMinute) {
            throw new IllegalArgumentException("영업 구간은 하루 안의 시작·종료 순서여야 합니다.");
        }
    }
}
