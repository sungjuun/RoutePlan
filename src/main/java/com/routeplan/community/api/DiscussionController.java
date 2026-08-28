package com.routeplan.community.api;

import com.routeplan.auth.RoutePlanPrincipal;
import com.routeplan.community.application.DiscussionService;
import com.routeplan.community.application.DiscussionService.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class DiscussionController {
    private final DiscussionService service;
    public DiscussionController(DiscussionService service) { this.service=service; }
    @GetMapping("/routes/{routeId}/discussion")
    public Discussion get(@PathVariable long routeId,@RequestParam(defaultValue="0") int page) { return service.get(routeId,page); }
    @PostMapping("/routes/{routeId}/comments")
    public Discussion comment(@PathVariable long routeId,@AuthenticationPrincipal RoutePlanPrincipal user,@Valid @RequestBody Body body) { return service.comment(routeId,user.userId(),null,body.body()); }
    @PutMapping("/routes/{routeId}/comments/{commentId}")
    public Discussion edit(@PathVariable long routeId,@PathVariable long commentId,@AuthenticationPrincipal RoutePlanPrincipal user,@Valid @RequestBody Body body) { return service.comment(routeId,user.userId(),commentId,body.body()); }
    @PutMapping("/routes/{routeId}/review")
    public Discussion review(@PathVariable long routeId,@AuthenticationPrincipal RoutePlanPrincipal user,@Valid @RequestBody Review body) { return service.review(routeId,user.userId(),body.rating(),body.body()); }
    @DeleteMapping("/routes/{routeId}/comments/{id}")
    public Discussion deleteComment(@PathVariable long routeId,@PathVariable long id,@AuthenticationPrincipal RoutePlanPrincipal user) { return service.delete(routeId,user.userId(),id,Target.COMMENT); }
    @DeleteMapping("/routes/{routeId}/reviews/{id}")
    public Discussion deleteReview(@PathVariable long routeId,@PathVariable long id,@AuthenticationPrincipal RoutePlanPrincipal user) { return service.delete(routeId,user.userId(),id,Target.REVIEW); }
    @PostMapping("/routes/{routeId}/reports")
    public Report report(@PathVariable long routeId,@AuthenticationPrincipal RoutePlanPrincipal user,@Valid @RequestBody ReportRequest body) { return service.report(routeId,user.userId(),body.targetType(),body.targetId(),body.reason(),body.detail()); }
    @GetMapping("/moderation/access")
    public Map<String,Boolean> access(@AuthenticationPrincipal RoutePlanPrincipal user) { return Map.of("allowed",service.canModerate(user)); }
    @GetMapping("/moderation/reports")
    public List<ModerationReport> queue(@AuthenticationPrincipal RoutePlanPrincipal user) { return service.queue(user); }
    @PostMapping("/moderation/reports/{id}/resolve")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void resolve(@PathVariable long id,@AuthenticationPrincipal RoutePlanPrincipal user,@Valid @RequestBody Resolve body) { service.resolve(user,id,body.resolution()); }
    public record Body(@NotBlank @Size(max=2000) String body) {}
    public record Review(@Min(1) @Max(5) int rating,@NotBlank @Size(max=2000) String body) {}
    public record ReportRequest(@NotNull Target targetType,@Min(1) long targetId,@NotNull Reason reason,@NotNull @Size(max=1000) String detail) {}
    public record Resolve(@NotNull Resolution resolution) {}
}
