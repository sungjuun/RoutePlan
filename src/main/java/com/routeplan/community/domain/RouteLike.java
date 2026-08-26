package com.routeplan.community.domain;

import com.routeplan.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
        name = "route_likes",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_route_likes_route_user",
                columnNames = {"shared_route_id", "user_id"}
        )
)
public class RouteLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shared_route_id", nullable = false)
    private SharedRoute sharedRoute;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RouteLike() {
    }

    private RouteLike(SharedRoute sharedRoute, User user) {
        if (sharedRoute == null || user == null) {
            throw new IllegalArgumentException("좋아요의 루트와 사용자는 필수입니다.");
        }
        this.sharedRoute = sharedRoute;
        this.user = user;
    }

    public static RouteLike create(SharedRoute sharedRoute, User user) {
        return new RouteLike(sharedRoute, user);
    }

    public Long getId() {
        return id;
    }

    public SharedRoute getSharedRoute() {
        return sharedRoute;
    }

    public User getUser() {
        return user;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
