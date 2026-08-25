package com.routeplan.itinerary.application;

import com.routeplan.itinerary.domain.Itinerary;
import com.routeplan.itinerary.domain.ItineraryItem;
import com.routeplan.optimization.domain.OptimizationAlgorithm;
import java.time.Instant;
import java.util.List;

public record ItineraryView(
        Long itineraryId,
        Long tripId,
        int version,
        OptimizationAlgorithm algorithm,
        long totalDistanceMeters,
        int estimatedTravelMinutes,
        boolean closedTour,
        String routeDataType,
        Instant createdAt,
        List<Item> items
) {

    static ItineraryView from(Itinerary itinerary) {
        return new ItineraryView(
                itinerary.getId(),
                itinerary.getTrip().getId(),
                itinerary.getVersion(),
                itinerary.getAlgorithm(),
                itinerary.getTotalDistanceMeters(),
                itinerary.getEstimatedTravelMinutes(),
                false,
                "STRAIGHT_LINE_ESTIMATE",
                itinerary.getCreatedAt(),
                itinerary.getItems().stream().map(Item::from).toList()
        );
    }

    public record Item(
            int sequence,
            Long placeId,
            String placeName,
            long travelDistanceMeters,
            int estimatedTravelMinutes
    ) {

        static Item from(ItineraryItem item) {
            return new Item(
                    item.getSequence(),
                    item.getPlace().getId(),
                    item.getPlace().getName(),
                    item.getTravelDistanceMeters(),
                    item.getEstimatedTravelMinutes()
            );
        }
    }
}
