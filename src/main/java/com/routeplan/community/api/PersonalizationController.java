package com.routeplan.community.api;

import com.routeplan.auth.RoutePlanPrincipal;
import com.routeplan.community.application.PersonalizationService;
import com.routeplan.community.application.PersonalizationService.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/me")
public class PersonalizationController {
    private final PersonalizationService service;
    public PersonalizationController(PersonalizationService service) { this.service = service; }
    @GetMapping("/preferences")
    public Preferences get(@AuthenticationPrincipal RoutePlanPrincipal user) { return service.get(user.userId()); }
    @PutMapping("/preferences")
    public Preferences save(@AuthenticationPrincipal RoutePlanPrincipal user, @RequestBody Preferences p) { return service.save(user.userId(), p); }
    @GetMapping("/recommendations")
    public List<Recommendation> recommend(@AuthenticationPrincipal RoutePlanPrincipal user) { return service.recommend(user.userId()); }
}
