package com.routeplan.community.application;

import com.routeplan.community.domain.SharedRoute;
import com.routeplan.community.domain.SharedRouteVisibility;
import com.routeplan.trip.domain.TransportMode;
import com.routeplan.trip.domain.TripPace;
import java.time.Instant;
import java.time.LocalDate;

public record SharedRouteSummaryView(
        Long routeId,
        Long ownerId,
        String ownerNickname,
        String title,
        String description,
        String region,
        int travelDays,
        SharedRouteVisibility visibility,
        String sourceTripName,
        LocalDate sourceStartDate,
        TransportMode transportMode,
        TripPace pace,
        int placeCount,
        String placePreview,
        long totalDistanceMeters,
        int estimatedTravelMinutes,
        int optimizationScore,
        long viewCount,
        long copyCount,
        long likeCount,
        Instant publishedAt
) {

    static SharedRouteSummaryView from(SharedRoute route) {
        return new SharedRouteSummaryView(
                route.getId(),
                route.getOwner().getId(),
                route.getOwner().getNickname(),
                route.getTitle(),
                route.getDescription(),
                route.getRegion(),
                route.getTravelDays(),
                route.getVisibility(),
                route.getSourceTripName(),
                route.getSourceStartDate(),
                route.getTransportMode(),
                route.getPace(),
                route.getPlaceCount(),
                route.getPlacePreview(),
                route.getTotalDistanceMeters(),
                route.getEstimatedTravelMinutes(),
                route.getOptimizationScore(),
                route.getViewCount(),
                route.getCopyCount(),
                route.getLikeCount(),
                route.getPublishedAt()
        );
    }
}
