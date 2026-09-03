package com.routeplan.wishlist.application;

import com.routeplan.common.error.ErrorCode;
import com.routeplan.common.error.RoutePlanException;
import com.routeplan.contentimport.domain.ContentSourceType;
import com.routeplan.place.domain.Place;
import com.routeplan.place.persistence.PlaceRepository;
import com.routeplan.trip.application.TripService;
import com.routeplan.trip.application.TripService.AddTripPlaceCommand;
import com.routeplan.trip.application.TripService.CreateTripCommand;
import com.routeplan.trip.application.TripService.TripResult;
import com.routeplan.trip.domain.TransportMode;
import com.routeplan.trip.domain.TripPace;
import com.routeplan.user.domain.User;
import com.routeplan.user.persistence.UserRepository;
import com.routeplan.wishlist.domain.Wishlist;
import com.routeplan.wishlist.domain.WishlistPlace;
import com.routeplan.wishlist.domain.WishlistPriority;
import com.routeplan.wishlist.persistence.WishlistPlaceRepository;
import com.routeplan.wishlist.persistence.WishlistRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WishlistService {
    private static final int MAX_PLACES = 100;

    private final UserRepository userRepository;
    private final PlaceRepository placeRepository;
    private final WishlistRepository wishlistRepository;
    private final WishlistPlaceRepository wishlistPlaceRepository;
    private final TripService tripService;

    public WishlistService(
            UserRepository userRepository,
            PlaceRepository placeRepository,
            WishlistRepository wishlistRepository,
            WishlistPlaceRepository wishlistPlaceRepository,
            TripService tripService
    ) {
        this.userRepository = userRepository;
        this.placeRepository = placeRepository;
        this.wishlistRepository = wishlistRepository;
        this.wishlistPlaceRepository = wishlistPlaceRepository;
        this.tripService = tripService;
    }

    @Transactional
    public WishlistResult create(Long userId, String name, String country, String city) {
        if (wishlistRepository.existsByOwnerIdAndNameIgnoreCase(userId, name.trim())) {
            throw new RoutePlanException(ErrorCode.DUPLICATE_WISHLIST);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RoutePlanException(ErrorCode.USER_NOT_FOUND));
        Wishlist wishlist = wishlistRepository.save(Wishlist.create(user, name, country, city));
        return result(wishlist, List.of());
    }

    @Transactional(readOnly = true)
    public List<WishlistSummaryResult> list(Long userId) {
        return wishlistRepository.findAllByOwnerIdOrderByUpdatedAtDesc(userId).stream()
                .map(wishlist -> new WishlistSummaryResult(
                        wishlist.getId(), wishlist.getName(), wishlist.getCountry(), wishlist.getCity(),
                        wishlistPlaceRepository.countByWishlistId(wishlist.getId()),
                        wishlist.getCreatedAt(), wishlist.getUpdatedAt()
                )).toList();
    }

    @Transactional(readOnly = true)
    public WishlistResult get(Long userId, Long wishlistId) {
        Wishlist wishlist = owned(userId, wishlistId);
        return result(wishlist, wishlistPlaceRepository.findAllByWishlistIdOrderByCreatedAtAsc(wishlistId));
    }

    @Transactional
    public WishlistResult update(Long userId, Long wishlistId, String name, String country, String city) {
        Wishlist wishlist = owned(userId, wishlistId);
        if (!wishlist.getName().equalsIgnoreCase(name.trim())
                && wishlistRepository.existsByOwnerIdAndNameIgnoreCase(userId, name.trim())) {
            throw new RoutePlanException(ErrorCode.DUPLICATE_WISHLIST);
        }
        wishlist.update(name, country, city);
        return result(wishlist, wishlistPlaceRepository.findAllByWishlistIdOrderByCreatedAtAsc(wishlistId));
    }

    @Transactional
    public void delete(Long userId, Long wishlistId) {
        wishlistRepository.delete(owned(userId, wishlistId));
    }

    @Transactional
    public WishlistResult addPlace(
            Long userId,
            Long wishlistId,
            Long placeId,
            WishlistPriority priority,
            ContentSourceType sourceType,
            String sourceUrl,
            String memo,
            Long estimatedCostMinor
    ) {
        Wishlist wishlist = owned(userId, wishlistId);
        if (wishlistPlaceRepository.existsByWishlistIdAndPlaceId(wishlistId, placeId)) {
            throw new RoutePlanException(ErrorCode.DUPLICATE_WISHLIST_PLACE);
        }
        if (wishlistPlaceRepository.countByWishlistId(wishlistId) >= MAX_PLACES) {
            throw new RoutePlanException(ErrorCode.WISHLIST_PLACE_LIMIT_EXCEEDED);
        }
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new RoutePlanException(ErrorCode.PLACE_NOT_FOUND));
        wishlistPlaceRepository.save(new WishlistPlace(
                wishlist, place, priority, sourceType, sourceUrl, memo, estimatedCostMinor
        ));
        return result(wishlist, wishlistPlaceRepository.findAllByWishlistIdOrderByCreatedAtAsc(wishlistId));
    }

    @Transactional
    public WishlistResult updatePlace(
            Long userId,
            Long wishlistId,
            Long wishlistPlaceId,
            WishlistPriority priority,
            ContentSourceType sourceType,
            String sourceUrl,
            String memo,
            Long estimatedCostMinor
    ) {
        Wishlist wishlist = owned(userId, wishlistId);
        WishlistPlace saved = wishlistPlaceRepository.findByIdAndWishlistId(wishlistPlaceId, wishlistId)
                .orElseThrow(() -> new RoutePlanException(ErrorCode.WISHLIST_PLACE_NOT_FOUND));
        saved.update(priority, sourceType, sourceUrl, memo, estimatedCostMinor);
        return result(wishlist, wishlistPlaceRepository.findAllByWishlistIdOrderByCreatedAtAsc(wishlistId));
    }

    @Transactional
    public void removePlace(Long userId, Long wishlistId, Long wishlistPlaceId) {
        owned(userId, wishlistId);
        WishlistPlace saved = wishlistPlaceRepository.findByIdAndWishlistId(wishlistPlaceId, wishlistId)
                .orElseThrow(() -> new RoutePlanException(ErrorCode.WISHLIST_PLACE_NOT_FOUND));
        wishlistPlaceRepository.delete(saved);
    }

    @Transactional
    public TripResult createTrip(Long userId, Long wishlistId, CreateTripFromWishlistCommand command) {
        owned(userId, wishlistId);
        HashSet<Long> requested = new HashSet<>(command.wishlistPlaceIds());
        if (requested.size() != command.wishlistPlaceIds().size()) {
            throw new IllegalArgumentException("같은 위시리스트 장소를 중복 선택할 수 없습니다.");
        }
        List<WishlistPlace> selected = wishlistPlaceRepository
                .findAllByWishlistIdAndIdInOrderByCreatedAtAsc(wishlistId, requested);
        if (selected.size() != requested.size()) {
            throw new RoutePlanException(ErrorCode.WISHLIST_PLACE_NOT_FOUND);
        }
        List<AddTripPlaceCommand> places = selected.stream().map(item -> new AddTripPlaceCommand(
                item.getPlace().getId(), item.getPriority().tripPriority(), item.getPriority().mustVisit(),
                null, null, null, null
        )).toList();
        return tripService.createFromSnapshot(new CreateTripCommand(
                userId, command.name(), command.startDate(), command.endDate(), command.accommodationName(),
                command.accommodationLatitude(), command.accommodationLongitude(), command.transportMode(),
                command.dailyStartTime(), command.dailyEndTime(), command.pace()
        ), places);
    }

    private Wishlist owned(Long userId, Long wishlistId) {
        return wishlistRepository.findByIdAndOwnerId(wishlistId, userId)
                .orElseThrow(() -> new RoutePlanException(ErrorCode.WISHLIST_NOT_FOUND));
    }

    private WishlistResult result(Wishlist wishlist, List<WishlistPlace> places) {
        return new WishlistResult(
                wishlist.getId(), wishlist.getName(), wishlist.getCountry(), wishlist.getCity(),
                wishlist.getCreatedAt(), wishlist.getUpdatedAt(), places.stream().map(WishlistPlaceResult::from).toList()
        );
    }

    public record CreateTripFromWishlistCommand(
            String name,
            LocalDate startDate,
            LocalDate endDate,
            String accommodationName,
            BigDecimal accommodationLatitude,
            BigDecimal accommodationLongitude,
            TransportMode transportMode,
            LocalTime dailyStartTime,
            LocalTime dailyEndTime,
            TripPace pace,
            List<Long> wishlistPlaceIds
    ) {}

    public record WishlistSummaryResult(
            Long id, String name, String country, String city, long placeCount,
            Instant createdAt, Instant updatedAt
    ) {}

    public record WishlistResult(
            Long id, String name, String country, String city,
            Instant createdAt, Instant updatedAt, List<WishlistPlaceResult> places
    ) {}

    public record WishlistPlaceResult(
            Long id, Long placeId, String externalPlaceId, String name,
            BigDecimal latitude, BigDecimal longitude, String category,
            WishlistPriority priority, ContentSourceType sourceType, String sourceUrl,
            String memo, Long estimatedCostMinor, Instant createdAt, Instant updatedAt
    ) {
        static WishlistPlaceResult from(WishlistPlace item) {
            Place place = item.getPlace();
            return new WishlistPlaceResult(
                    item.getId(), place.getId(), place.getExternalPlaceId(), place.getName(),
                    place.getLatitude(), place.getLongitude(), place.getCategory(), item.getPriority(),
                    item.getSourceType(), item.getSourceUrl(), item.getMemo(), item.getEstimatedCostMinor(),
                    item.getCreatedAt(), item.getUpdatedAt()
            );
        }
    }
}
