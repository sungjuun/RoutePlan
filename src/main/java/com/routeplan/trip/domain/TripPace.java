package com.routeplan.trip.domain;

public enum TripPace {
    RELAXED,
    STANDARD,
    ACTIVE;

    public int stayMinutes(int averageStayMinutes, Integer minimumStayMinutes, Integer maximumStayMinutes) {
        if (averageStayMinutes <= 0) {
            throw new IllegalArgumentException("평균 체류시간은 0보다 커야 합니다.");
        }

        int minimum = minimumStayMinutes == null
                ? Math.max(15, (int) Math.ceil(averageStayMinutes * 0.75))
                : minimumStayMinutes;
        int maximum = maximumStayMinutes == null
                ? Math.max(minimum, (int) Math.ceil(averageStayMinutes * 1.25))
                : maximumStayMinutes;
        if (minimum <= 0 || maximum < minimum) {
            throw new IllegalArgumentException("최소·최대 체류시간이 올바르지 않습니다.");
        }

        return switch (this) {
            case ACTIVE -> minimum;
            case STANDARD -> Math.clamp(averageStayMinutes, minimum, maximum);
            case RELAXED -> maximum;
        };
    }
}
