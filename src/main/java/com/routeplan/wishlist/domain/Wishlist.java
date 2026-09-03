package com.routeplan.wishlist.domain;

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
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "wishlists")
public class Wishlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String country;

    @Column(length = 100)
    private String city;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Wishlist() {
    }

    private Wishlist(User owner, String name, String country, String city) {
        if (owner == null) throw new IllegalArgumentException("위시리스트 소유자는 필수입니다.");
        this.owner = owner;
        this.name = requireText(name, "위시리스트 이름", 100);
        this.country = nullableText(country, "국가", 100);
        this.city = nullableText(city, "도시", 100);
    }

    public static Wishlist create(User owner, String name, String country, String city) {
        return new Wishlist(owner, name, country, city);
    }

    public void update(String name, String country, String city) {
        this.name = requireText(name, "위시리스트 이름", 100);
        this.country = nullableText(country, "국가", 100);
        this.city = nullableText(city, "도시", 100);
    }

    public Long getId() { return id; }
    public User getOwner() { return owner; }
    public String getName() { return name; }
    public String getCountry() { return country; }
    public String getCity() { return city; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    private static String requireText(String value, String label, int max) {
        String normalized = nullableText(value, label, max);
        if (normalized == null) throw new IllegalArgumentException(label + "은 필수입니다.");
        return normalized;
    }

    private static String nullableText(String value, String label, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(label + "은 " + max + "자를 초과할 수 없습니다.");
        return normalized;
    }
}
