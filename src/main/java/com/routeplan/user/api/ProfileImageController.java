package com.routeplan.user.api;

import com.routeplan.auth.RoutePlanPrincipal;
import com.routeplan.user.application.ProfileImageService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/profile/avatar")
public class ProfileImageController {
    private final ProfileImageService images;
    public ProfileImageController(ProfileImageService images) { this.images = images; }

    @GetMapping
    public ResponseEntity<byte[]> read(@AuthenticationPrincipal RoutePlanPrincipal principal) {
        byte[] image = images.read(principal.userId());
        if (image == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff").body(image);
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImageView upload(@AuthenticationPrincipal RoutePlanPrincipal principal, @RequestParam("file") MultipartFile file) {
        return new ImageView(images.upload(principal.userId(), file));
    }

    @DeleteMapping
    public ImageView remove(@AuthenticationPrincipal RoutePlanPrincipal principal) {
        images.remove(principal.userId());
        return new ImageView(null);
    }

    public record ImageView(String profileImageUrl) {}
}
