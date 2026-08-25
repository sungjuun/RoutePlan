package com.routeplan.place.search;

import com.routeplan.optimization.domain.Location;

public record PlaceSearchQuery(
        String textQuery,
        Location locationBias,
        int radiusMeters,
        int limit,
        String languageCode
) {

    public PlaceSearchQuery {
        if (textQuery == null || textQuery.isBlank()) {
            throw new IllegalArgumentException("장소 검색어는 필수입니다.");
        }
        textQuery = textQuery.trim();
        if (textQuery.length() > 200) {
            throw new IllegalArgumentException("장소 검색어는 200자를 초과할 수 없습니다.");
        }
        if (locationBias == null && radiusMeters != 0) {
            throw new IllegalArgumentException("검색 반경을 사용하려면 중심 좌표가 필요합니다.");
        }
        if (locationBias != null && (radiusMeters < 1 || radiusMeters > 50_000)) {
            throw new IllegalArgumentException("검색 반경은 1m 이상 50,000m 이하여야 합니다.");
        }
        if (limit < 1 || limit > 20) {
            throw new IllegalArgumentException("검색 결과 수는 1개 이상 20개 이하여야 합니다.");
        }
        if (languageCode == null || languageCode.isBlank() || languageCode.length() > 20) {
            throw new IllegalArgumentException("언어 코드는 20자 이하의 값이어야 합니다.");
        }
    }
}
