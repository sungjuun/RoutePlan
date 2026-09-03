package com.routeplan.wishlist.api;

import com.routeplan.auth.RoutePlanPrincipal;
import com.routeplan.contentimport.domain.ContentSourceType;
import com.routeplan.trip.application.TripService.TripResult;
import com.routeplan.trip.domain.TransportMode;
import com.routeplan.trip.domain.TripPace;
import com.routeplan.wishlist.application.WishlistService;
import com.routeplan.wishlist.application.WishlistService.CreateTripFromWishlistCommand;
import com.routeplan.wishlist.application.WishlistService.WishlistResult;
import com.routeplan.wishlist.application.WishlistService.WishlistSummaryResult;
import com.routeplan.wishlist.domain.WishlistPriority;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wishlists")
public class WishlistController {
    private final WishlistService service;

    public WishlistController(WishlistService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<WishlistResult> create(
            @AuthenticationPrincipal RoutePlanPrincipal principal,
            @Valid @RequestBody SaveWishlistRequest request
    ) {
        WishlistResult result = service.create(principal.userId(), request.name(), request.country(), request.city());
        return ResponseEntity.created(URI.create("/api/v1/wishlists/" + result.id())).body(result);
    }

    @GetMapping
    public List<WishlistSummaryResult> list(@AuthenticationPrincipal RoutePlanPrincipal principal) {
        return service.list(principal.userId());
    }

    @GetMapping("/{wishlistId}")
    public WishlistResult get(@PathVariable Long wishlistId, @AuthenticationPrincipal RoutePlanPrincipal principal) {
        return service.get(principal.userId(), wishlistId);
    }

    @PatchMapping("/{wishlistId}")
    public WishlistResult update(
            @PathVariable Long wishlistId,
            @AuthenticationPrincipal RoutePlanPrincipal principal,
            @Valid @RequestBody SaveWishlistRequest request
    ) {
        return service.update(principal.userId(), wishlistId, request.name(), request.country(), request.city());
    }

    @DeleteMapping("/{wishlistId}")
    public ResponseEntity<Void> delete(@PathVariable Long wishlistId, @AuthenticationPrincipal RoutePlanPrincipal principal) {
        service.delete(principal.userId(), wishlistId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{wishlistId}/places")
    public ResponseEntity<WishlistResult> addPlace(
            @PathVariable Long wishlistId,
            @AuthenticationPrincipal RoutePlanPrincipal principal,
            @Valid @RequestBody SaveWishlistPlaceRequest request
    ) {
        WishlistResult result = service.addPlace(
                principal.userId(), wishlistId, request.placeId(), request.priority(), request.sourceType(),
                request.sourceUrl(), request.memo(), request.estimatedCostMinor()
        );
        return ResponseEntity.created(URI.create("/api/v1/wishlists/" + wishlistId + "/places")).body(result);
    }

    @PatchMapping("/{wishlistId}/places/{wishlistPlaceId}")
    public WishlistResult updatePlace(
            @PathVariable Long wishlistId,
            @PathVariable Long wishlistPlaceId,
            @AuthenticationPrincipal RoutePlanPrincipal principal,
            @Valid @RequestBody UpdateWishlistPlaceRequest request
    ) {
        return service.updatePlace(
                principal.userId(), wishlistId, wishlistPlaceId, request.priority(), request.sourceType(),
                request.sourceUrl(), request.memo(), request.estimatedCostMinor()
        );
    }

    @DeleteMapping("/{wishlistId}/places/{wishlistPlaceId}")
    public ResponseEntity<Void> removePlace(
            @PathVariable Long wishlistId,
            @PathVariable Long wishlistPlaceId,
            @AuthenticationPrincipal RoutePlanPrincipal principal
    ) {
        service.removePlace(principal.userId(), wishlistId, wishlistPlaceId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{wishlistId}/trips")
    public ResponseEntity<TripResult> createTrip(
            @PathVariable Long wishlistId,
            @AuthenticationPrincipal RoutePlanPrincipal principal,
            @Valid @RequestBody CreateTripFromWishlistRequest request
    ) {
        TripResult result = service.createTrip(principal.userId(), wishlistId, request.command());
        return ResponseEntity.created(URI.create("/api/v1/trips/" + result.id())).body(result);
    }

    public record SaveWishlistRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 100) String country,
            @Size(max = 100) String city
    ) {}

    public record SaveWishlistPlaceRequest(
            @NotNull Long placeId,
            WishlistPriority priority,
            ContentSourceType sourceType,
            @Size(max = 2048) String sourceUrl,
            @Size(max = 1000) String memo,
            @PositiveOrZero Long estimatedCostMinor
    ) {}

    public record UpdateWishlistPlaceRequest(
            WishlistPriority priority,
            ContentSourceType sourceType,
            @Size(max = 2048) String sourceUrl,
            @Size(max = 1000) String memo,
            @PositiveOrZero Long estimatedCostMinor
    ) {}

    public record CreateTripFromWishlistRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotBlank @Size(max = 100) String accommodationName,
            @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal accommodationLatitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal accommodationLongitude,
            @NotNull TransportMode transportMode,
            LocalTime dailyStartTime,
            LocalTime dailyEndTime,
            TripPace pace,
            @NotEmpty @Size(max = 50) List<@NotNull Long> wishlistPlaceIds
    ) {
        CreateTripFromWishlistCommand command() {
            return new CreateTripFromWishlistCommand(
                    name, startDate, endDate, accommodationName, accommodationLatitude,
                    accommodationLongitude, transportMode, dailyStartTime, dailyEndTime, pace,
                    List.copyOf(wishlistPlaceIds)
            );
        }
    }
}
