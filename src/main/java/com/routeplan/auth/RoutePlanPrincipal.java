package com.routeplan.auth;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public final class RoutePlanPrincipal implements UserDetails, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final String email;
    private final String nickname;
    private final String passwordHash;
    private final Instant createdAt;

    public RoutePlanPrincipal(
            Long userId,
            String email,
            String nickname,
            String passwordHash,
            Instant createdAt
    ) {
        this.userId = userId;
        this.email = email;
        this.nickname = nickname;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }

    public Long userId() {
        return userId;
    }

    public String email() {
        return email;
    }

    public String nickname() {
        return nickname;
    }

    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }
}
