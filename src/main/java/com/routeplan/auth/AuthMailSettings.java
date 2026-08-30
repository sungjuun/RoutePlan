package com.routeplan.auth;

import java.net.URI;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AuthMailSettings {
    public enum Mode { DISABLED, LOCAL, SMTP }
    private final Mode mode;
    private final String publicUrl;
    private final String from;

    public AuthMailSettings(@Value("${routeplan.auth.mail-mode:DISABLED}") Mode mode,
            @Value("${routeplan.auth.public-url:http://localhost:3100}") String publicUrl,
            @Value("${routeplan.auth.mail-from:RoutePlan <noreply@routeplan.local>}") String from) {
        URI origin = URI.create(publicUrl);
        boolean local = Set.of("localhost", "127.0.0.1", "[::1]").contains(
                origin.getHost() == null ? "" : origin.getHost());
        if (origin.getHost() == null || origin.getUserInfo() != null || origin.getQuery() != null
                || origin.getFragment() != null || !(origin.getPath().isEmpty() || origin.getPath().equals("/"))
                || !("https".equals(origin.getScheme()) || (local && "http".equals(origin.getScheme())))) {
            throw new IllegalArgumentException("ROUTEPLAN_PUBLIC_URL must be an HTTPS origin (HTTP is allowed only on localhost).");
        }
        this.mode = mode;
        this.publicUrl = publicUrl.replaceAll("/+$", "");
        this.from = from;
    }

    public Mode mode() { return mode; }
    public boolean enabled() { return mode != Mode.DISABLED; }
    public String publicUrl() { return publicUrl; }
    public String from() { return from; }
}
