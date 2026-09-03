package com.routeplan.contentimport.api;

import com.routeplan.auth.RoutePlanPrincipal;
import com.routeplan.contentimport.application.ContentImportService;
import com.routeplan.contentimport.application.ContentImportService.ContentImportResult;
import com.routeplan.wishlist.application.WishlistService.WishlistResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/imports")
public class ContentImportController {
    private final ContentImportService service;

    public ContentImportController(ContentImportService service) {
        this.service = service;
    }

    @PostMapping("/url")
    public ResponseEntity<ContentImportResult> start(
            @AuthenticationPrincipal RoutePlanPrincipal principal,
            @Valid @RequestBody StartImportRequest request
    ) {
        ContentImportResult result = service.start(principal.userId(), request.url(), request.inputText(), request.wishlistId());
        return ResponseEntity.accepted().location(URI.create("/api/v1/imports/" + result.id())).body(result);
    }

    @GetMapping("/{importId}")
    public ContentImportResult get(@PathVariable Long importId, @AuthenticationPrincipal RoutePlanPrincipal principal) {
        return service.get(principal.userId(), importId);
    }

    @PostMapping("/{importId}/retry")
    public ResponseEntity<ContentImportResult> retry(
            @PathVariable Long importId,
            @AuthenticationPrincipal RoutePlanPrincipal principal,
            @Valid @RequestBody RetryImportRequest request
    ) {
        return ResponseEntity.accepted().body(service.retry(principal.userId(), importId, request.inputText()));
    }

    @PostMapping("/{importId}/save")
    public WishlistResult save(
            @PathVariable Long importId,
            @AuthenticationPrincipal RoutePlanPrincipal principal,
            @Valid @RequestBody SaveCandidatesRequest request
    ) {
        return service.saveCandidates(principal.userId(), importId, request.wishlistId(), request.candidateIds());
    }

    public record StartImportRequest(
            @NotBlank @Size(max = 2048) String url,
            @Size(max = 10000) String inputText,
            Long wishlistId
    ) {}

    public record RetryImportRequest(@NotBlank @Size(max = 10000) String inputText) {}

    public record SaveCandidatesRequest(
            @NotNull Long wishlistId,
            @NotEmpty @Size(max = 50) List<@NotNull Long> candidateIds
    ) {}
}
