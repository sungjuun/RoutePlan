package com.routeplan.place.api;

import com.routeplan.place.application.PlaceService;
import com.routeplan.place.application.PlaceService.PlaceResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
                request.name(), request.latitude(), request.longitude(), request.category()
        );
        return ResponseEntity.created(URI.create("/api/v1/places/" + result.id())).body(result);
    }

    @GetMapping("/{placeId}")
    public PlaceResult get(@PathVariable Long placeId) {
        return placeService.get(placeId);
    }

    public record CreatePlaceRequest(
            @NotBlank @Size(max = 150) String name,
            @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
            @Size(max = 50) String category
    ) {
    }
}
