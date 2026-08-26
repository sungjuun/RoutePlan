package com.routeplan.community.application;

import java.util.List;
import org.springframework.data.domain.Page;

public record SharedRoutePageView(
        List<SharedRouteSummaryView> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    static SharedRoutePageView from(Page<SharedRouteSummaryView> routes) {
        return new SharedRoutePageView(
                routes.getContent(),
                routes.getNumber(),
                routes.getSize(),
                routes.getTotalElements(),
                routes.getTotalPages(),
                routes.isFirst(),
                routes.isLast()
        );
    }
}
