package com.routeplan.community.application;

public record RouteLikeView(
        Long routeId,
        long likeCount,
        boolean liked
) {
}
