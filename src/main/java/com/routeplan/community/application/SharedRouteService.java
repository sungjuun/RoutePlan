package com.routeplan.community.application;

import com.routeplan.common.error.ErrorCode;
import com.routeplan.common.error.RoutePlanException;
import com.routeplan.community.domain.RouteLike;
import com.routeplan.community.domain.SharedRoute;
import com.routeplan.community.domain.SharedRouteSort;
import com.routeplan.community.domain.SharedRouteVisibility;
import com.routeplan.community.persistence.RouteLikeRepository;
import com.routeplan.community.persistence.SharedRouteRepository;
import com.routeplan.itinerary.domain.Itinerary;
import com.routeplan.itinerary.persistence.ItineraryRepository;
import com.routeplan.trip.application.TripService;
import com.routeplan.trip.application.TripService.AddTripPlaceCommand;
import com.routeplan.trip.application.TripService.CreateTripCommand;
import com.routeplan.trip.application.TripService.TripResult;
import com.routeplan.trip.domain.TransportMode;
import com.routeplan.trip.domain.TripPace;
import com.routeplan.user.domain.User;
import com.routeplan.user.persistence.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SharedRouteService {

    private static final int MAX_PAGE_SIZE = 50;

    private final SharedRouteRepository sharedRouteRepository;
    private final RouteLikeRepository routeLikeRepository;
    private final ItineraryRepository itineraryRepository;
    private final UserRepository userRepository;
    private final TripService tripService;

    public SharedRouteService(
            SharedRouteRepository sharedRouteRepository,
            RouteLikeRepository routeLikeRepository,
            ItineraryRepository itineraryRepository,
            UserRepository userRepository,
            TripService tripService
    ) {
        this.sharedRouteRepository = sharedRouteRepository;
        this.routeLikeRepository = routeLikeRepository;
        this.itineraryRepository = itineraryRepository;
        this.userRepository = userRepository;
        this.tripService = tripService;
    }

    @Transactional
    public SharedRouteDetailView publish(Long itineraryId, PublishCommand command) {
        User owner = userRepository.findById(command.userId())
                .orElseThrow(() -> new RoutePlanException(ErrorCode.USER_NOT_FOUND));
        Itinerary itinerary = itineraryRepository.findDetailedById(itineraryId)
                .orElseThrow(() -> new RoutePlanException(ErrorCode.ITINERARY_NOT_FOUND));
        if (!itinerary.getTrip().getUser().getId().equals(owner.getId())) {
            throw new RoutePlanException(ErrorCode.ITINERARY_OWNER_MISMATCH);
        }
        if (sharedRouteRepository.existsBySourceItineraryId(itineraryId)) {
            throw new RoutePlanException(ErrorCode.ITINERARY_ALREADY_SHARED);
        }

        SharedRoute route;
        try {
            route = SharedRoute.publish(
                    owner,
                    itinerary,
                    command.title(),
                    command.description(),
                    command.region(),
                    command.visibility()
            );
        } catch (IllegalArgumentException exception) {
            throw new RoutePlanException(ErrorCode.ITINERARY_NOT_SHAREABLE, exception.getMessage());
        }
        return SharedRouteDetailView.from(sharedRouteRepository.saveAndFlush(route), false);
    }

    @Transactional(readOnly = true)
    public SharedRoutePageView discover(
            String region,
            Integer travelDays,
            SharedRouteSort routeSort,
            int page,
            int size
    ) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE
                || (travelDays != null && travelDays < 1)) {
            throw new IllegalArgumentException("페이지 또는 여행 기간 조건이 올바르지 않습니다.");
        }
        Sort sort = switch (routeSort == null ? SharedRouteSort.LATEST : routeSort) {
            case LATEST -> Sort.by(
                    Sort.Order.desc("publishedAt"),
                    Sort.Order.desc("id")
            );
            case POPULAR -> Sort.by(
                    Sort.Order.desc("copyCount"),
                    Sort.Order.desc("likeCount"),
                    Sort.Order.desc("viewCount"),
                    Sort.Order.desc("publishedAt"),
                    Sort.Order.desc("id")
            );
        };
        String regionFilter = region == null || region.isBlank() ? "" : region.strip();
        Page<SharedRouteSummaryView> routes = sharedRouteRepository.findDiscoverable(
                        SharedRouteVisibility.PUBLIC,
                        regionFilter,
                        travelDays,
                        PageRequest.of(page, size, sort)
                )
                .map(SharedRouteSummaryView::from);
        return SharedRoutePageView.from(routes);
    }

    @Transactional
    public SharedRouteDetailView get(Long routeId, Long viewerUserId) {
        SharedRoute route = getRouteForUpdate(routeId);
        route.increaseViewCount();
        boolean liked = viewerUserId != null
                && routeLikeRepository.existsBySharedRouteIdAndUserId(routeId, viewerUserId);
        return SharedRouteDetailView.from(route, liked);
    }

    @Transactional
    public RouteLikeView like(Long routeId, Long userId) {
        SharedRoute route = getRouteForUpdate(routeId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RoutePlanException(ErrorCode.USER_NOT_FOUND));
        if (routeLikeRepository.existsBySharedRouteIdAndUserId(routeId, userId)) {
            throw new RoutePlanException(ErrorCode.DUPLICATE_ROUTE_LIKE);
        }
        routeLikeRepository.save(RouteLike.create(route, user));
        route.increaseLikeCount();
        return new RouteLikeView(routeId, route.getLikeCount(), true);
    }

    @Transactional
    public RouteLikeView unlike(Long routeId, Long userId) {
        SharedRoute route = getRouteForUpdate(routeId);
        if (!userRepository.existsById(userId)) {
            throw new RoutePlanException(ErrorCode.USER_NOT_FOUND);
        }
        if (routeLikeRepository.deleteBySharedRouteIdAndUserId(routeId, userId) == 0) {
            throw new RoutePlanException(ErrorCode.ROUTE_LIKE_NOT_FOUND);
        }
        route.decreaseLikeCount();
        return new RouteLikeView(routeId, route.getLikeCount(), false);
    }

    @Transactional
    public TripResult copy(Long routeId, CopyCommand command) {
        SharedRoute route = getRouteForUpdate(routeId);
        List<AddTripPlaceCommand> places = route.getItems().stream()
                .map(item -> new AddTripPlaceCommand(
                        item.getPlace().getId(),
                        item.getPriority(),
                        item.isMustVisit(),
                        null,
                        null,
                        null,
                        null
                ))
                .toList();
        TripResult copied = tripService.createFromSnapshot(
                new CreateTripCommand(
                        command.userId(),
                        command.name(),
                        command.startDate(),
                        command.startDate(),
                        command.accommodationName(),
                        command.accommodationLatitude(),
                        command.accommodationLongitude(),
                        command.transportMode(),
                        command.dailyStartTime(),
                        command.dailyEndTime(),
                        command.pace()
                ),
                places
        );
        route.increaseCopyCount();
        return copied;
    }

    private SharedRoute getRouteForUpdate(Long routeId) {
        return sharedRouteRepository.findByIdForUpdate(routeId)
                .orElseThrow(() -> new RoutePlanException(ErrorCode.SHARED_ROUTE_NOT_FOUND));
    }

    public record PublishCommand(
            Long userId,
            String title,
            String description,
            String region,
            SharedRouteVisibility visibility
    ) {
    }

    public record CopyCommand(
            Long userId,
            String name,
            LocalDate startDate,
            LocalTime dailyStartTime,
            LocalTime dailyEndTime,
            String accommodationName,
            BigDecimal accommodationLatitude,
            BigDecimal accommodationLongitude,
            TransportMode transportMode,
            TripPace pace
    ) {
    }
}
