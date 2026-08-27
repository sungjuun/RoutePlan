package com.routeplan.place.api;

import com.routeplan.place.application.PlaceService;
import com.routeplan.place.application.PlaceService.PlaceResult;
import com.routeplan.place.application.PlaceService.OpeningHourResult;
import com.routeplan.place.application.PlaceService.ImportPlaceResult;
import com.routeplan.place.domain.PlaceEnvironment;
import com.routeplan.place.search.PlaceSearchQuery;
import com.routeplan.place.search.PlaceSearchResult;
import com.routeplan.optimization.domain.Location;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/places")
public class PlaceController {

    private final PlaceService placeService;

    public PlaceController(PlaceService placeService) {
        this.placeService = placeService;
    }

    @PostMapping
    public ResponseEntity<PlaceResult> create(@Valid @RequestBody CreatePlaceRequest request) {
        PlaceResult result = placeService.create(
                request.name(),
                request.latitude(),
                request.longitude(),
                request.category(),
                request.averageStayMinutes() == null ? 60 : request.averageStayMinutes(),
                request.environment()
        );
        return ResponseEntity.created(URI.create("/api/v1/places/" + result.id())).body(result);
    }

    @GetMapping("/{placeId}")
    public PlaceResult get(@PathVariable Long placeId) {
        return placeService.get(placeId);
    }

    @GetMapping("/search")
    public List<PlaceSearchResult> search(
            @RequestParam String query,
            @RequestParam(required = false) BigDecimal latitude,
            @RequestParam(required = false) BigDecimal longitude,
            @RequestParam(defaultValue = "5000") int radiusMeters,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "ko") String languageCode
    ) {
        if ((latitude == null) != (longitude == null)) {
            throw new IllegalArgumentException("검색 중심의 위도와 경도는 함께 입력해야 합니다.");
        }
        Location locationBias = latitude == null ? null : Location.of(latitude, longitude);
        return placeService.search(new PlaceSearchQuery(
                query,
                locationBias,
                locationBias == null ? 0 : radiusMeters,
                limit,
                languageCode
        ));
    }

    @PostMapping("/import")
    public ResponseEntity<PlaceResult> importExternal(
            @Valid @RequestBody ImportPlaceRequest request
    ) {
        ImportPlaceResult result = placeService.importExternal(
                request.externalPlaceId(),
                request.name(),
                request.latitude(),
                request.longitude(),
                request.category(),
                request.averageStayMinutes() == null ? 60 : request.averageStayMinutes(),
                request.environment()
        );
        if (!result.created()) {
            return ResponseEntity.ok(result.place());
        }
        return ResponseEntity.created(
                URI.create("/api/v1/places/" + result.place().id())
        ).body(result.place());
    }

    @PutMapping("/{placeId}/opening-hours/{dayOfWeek}")
    public OpeningHourResult setOpeningHour(
            @PathVariable Long placeId,
            @PathVariable DayOfWeek dayOfWeek,
            @Valid @RequestBody SetOpeningHourRequest request
    ) {
        return placeService.setOpeningHour(
                placeId,
                dayOfWeek,
                request.openTime(),
                request.closeTime(),
                request.closed()
        );
    }

    @GetMapping("/{placeId}/opening-hours")
    public List<OpeningHourResult> getOpeningHours(@PathVariable Long placeId) {
        return placeService.getOpeningHours(placeId);
    }

    public record CreatePlaceRequest(
            @NotBlank @Size(max = 150) String name,
            @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
            @Size(max = 50) String category,
            @Min(1) @Max(1_440) Integer averageStayMinutes,
            PlaceEnvironment environment
    ) {
    }

    public record SetOpeningHourRequest(
            @NotNull Boolean closed,
            LocalTime openTime,
            LocalTime closeTime
    ) {
    }

    public record ImportPlaceRequest(
            @NotBlank @Size(max = 200) String externalPlaceId,
            @NotBlank @Size(max = 150) String name,
            @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
            @Size(max = 50) String category,
            @Min(1) @Max(1_440) Integer averageStayMinutes,
            PlaceEnvironment environment
    ) {
    }
}
