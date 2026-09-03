package com.routeplan.wishlist.domain;

import com.routeplan.contentimport.domain.ContentSourceType;
import com.routeplan.place.domain.Place;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "wishlist_places")
public class WishlistPlace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wishlist_id", nullable = false)
    private Wishlist wishlist;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WishlistPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private ContentSourceType sourceType;

    @Column(name = "source_url", length = 2048)
    private String sourceUrl;

    @Column(length = 1000)
    private String memo;

    @Column(name = "estimated_cost_minor")
    private Long estimatedCostMinor;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WishlistPlace() {
    }

    public WishlistPlace(
            Wishlist wishlist,
            Place place,
            WishlistPriority priority,
            ContentSourceType sourceType,
            String sourceUrl,
            String memo,
            Long estimatedCostMinor
    ) {
        if (wishlist == null || place == null) throw new IllegalArgumentException("위시리스트와 장소는 필수입니다.");
        this.wishlist = wishlist;
        this.place = place;
        update(priority, sourceType, sourceUrl, memo, estimatedCostMinor);
    }

    public void update(WishlistPriority priority, ContentSourceType sourceType, String sourceUrl, String memo, Long estimatedCostMinor) {
        if (estimatedCostMinor != null && estimatedCostMinor < 0) throw new IllegalArgumentException("예상 비용은 0 이상이어야 합니다.");
        this.priority = priority == null ? WishlistPriority.NORMAL : priority;
        this.sourceType = sourceType == null ? ContentSourceType.MANUAL : sourceType;
        this.sourceUrl = nullableText(sourceUrl, "원본 URL", 2048);
        this.memo = nullableText(memo, "메모", 1000);
        this.estimatedCostMinor = estimatedCostMinor;
    }

    public Long getId() { return id; }
    public Wishlist getWishlist() { return wishlist; }
    public Place getPlace() { return place; }
    public WishlistPriority getPriority() { return priority; }
    public ContentSourceType getSourceType() { return sourceType; }
    public String getSourceUrl() { return sourceUrl; }
    public String getMemo() { return memo; }
    public Long getEstimatedCostMinor() { return estimatedCostMinor; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    private static String nullableText(String value, String label, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(label + "은 " + max + "자를 초과할 수 없습니다.");
        return normalized;
    }
}
