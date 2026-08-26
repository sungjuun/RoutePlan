package com.routeplan.community.application;

import com.routeplan.community.domain.SharedRoute;
import com.routeplan.community.domain.SharedRouteItem;
import com.routeplan.community.domain.SharedRouteVisibility;
import com.routeplan.optimization.domain.OptimizationAlgorithm;
import com.routeplan.trip.domain.TransportMode;
import com.routeplan.trip.domain.TripPace;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record SharedRouteDetailView(
        Long routeId,
        Long ownerId,
        String ownerNickname,
        Long sourceTripId,
        Long sourceItineraryId,
        int sourceItineraryVersion,
        String sourceTripName,
        LocalDate sourceStartDate,
        LocalTime dailyStartTime,
        LocalTime dailyEndTime,
        String accommodationName,
        BigDecimal accommodationLatitude,
        BigDecimal accommodationLongitude,
        TransportMode transportMode,
        TripPace pace,
        OptimizationAlgorithm algorithm,
        String title,
        String description,
        String region,
        int travelDays,
        SharedRouteVisibility visibility,
        int placeCount,
        long totalDistanceMeters,
        int estimatedTravelMinutes,
        int optimizationScore,
        long viewCount,
        long copyCount,
        long likeCount,
        boolean likedByViewer,
        Instant publishedAt,
        List<Item> items
) {

    static SharedRouteDetailView from(SharedRoute route, boolean likedByViewer) {
        return new SharedRouteDetailView(
                route.getId(),
                route.getOwner().getId(),
                route.getOwner().getNickname(),
                route.getSourceTrip() == null ? null : route.getSourceTrip().getId(),
                route.getSourceItinerary() == null ? null : route.getSourceItinerary().getId(),
                route.getSourceItineraryVersion(),
                route.getSourceTripName(),
                route.getSourceStartDate(),
                route.getDailyStartTime(),
                route.getDailyEndTime(),
                route.getAccommodationName(),
                route.getAccommodationLatitude(),
                route.getAccommodationLongitude(),
                route.getTransportMode(),
                route.getPace(),
                route.getAlgorithm(),
                route.getTitle(),
                route.getDescription(),
                route.getRegion(),
                route.getTravelDays(),
                route.getVisibility(),
                route.getPlaceCount(),
                route.getTotalDistanceMeters(),
                route.getEstimatedTravelMinutes(),
                route.getOptimizationScore(),
                route.getViewCount(),
                route.getCopyCount(),
                route.getLikeCount(),
                likedByViewer,
                route.getPublishedAt(),
                route.getItems().stream().map(Item::from).toList()
        );
    }

    public record Item(
            Long itemId,
            Long placeId,
            int dayNumber,
            int sequence,
            LocalDate visitDate,
            String placeName,
            BigDecimal latitude,
            BigDecimal longitude,
            String category,
            LocalTime arrivalTime,
            LocalTime startTime,
            LocalTime endTime,
            long travelDistanceMeters,
            int estimatedTravelMinutes,
            int waitingMinutes,
            int stayMinutes,
            int priority,
            boolean mustVisit
    ) {

        static Item from(SharedRouteItem item) {
            return new Item(
                    item.getId(),
                    item.getPlace().getId(),
                    item.getDayNumber(),
                    item.getSequence(),
                    item.getVisitDate(),
                    item.getPlaceName(),
                    item.getLatitude(),
                    item.getLongitude(),
                    item.getCategory(),
                    item.getArrivalTime(),
                    item.getStartTime(),
                    item.getEndTime(),
                    item.getTravelDistanceMeters(),
                    item.getEstimatedTravelMinutes(),
                    item.getWaitingMinutes(),
                    item.getStayMinutes(),
                    item.getPriority(),
                    item.isMustVisit()
            );
        }
    }
}
