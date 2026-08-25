package com.routeplan.optimization.domain;

import java.math.BigDecimal;

public record Location(double latitude, double longitude) {

    public Location {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("위도 값이 올바르지 않습니다.");
        }
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("경도 값이 올바르지 않습니다.");
        }
    }

    public static Location of(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null) {
            throw new IllegalArgumentException("위도와 경도는 필수입니다.");
        }
        return new Location(latitude.doubleValue(), longitude.doubleValue());
    }
}
