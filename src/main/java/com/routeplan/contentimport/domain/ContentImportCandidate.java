package com.routeplan.contentimport.domain;

import com.routeplan.place.search.PlaceSearchResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "content_import_candidates")
public class ContentImportCandidate {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_import_id", nullable = false)
    private ContentImport contentImport;

    @Column(name = "mention_order", nullable = false) private int mentionOrder;
    @Column(name = "match_rank", nullable = false) private int matchRank;
    @Column(name = "extracted_name", nullable = false, length = 200) private String extractedName;
    @Column(name = "external_place_id", length = 200) private String externalPlaceId;
    @Column(name = "matched_name", length = 200) private String matchedName;
    @Column(name = "formatted_address", length = 500) private String formattedAddress;
    @Column(precision = 9, scale = 6) private BigDecimal latitude;
    @Column(precision = 10, scale = 6) private BigDecimal longitude;
    @Column(name = "primary_type", length = 100) private String primaryType;
    @Column(length = 50) private String provider;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected ContentImportCandidate() {
    }

    private ContentImportCandidate(ContentImport contentImport, int mentionOrder, int matchRank, String extractedName) {
        if (contentImport == null || mentionOrder < 1 || matchRank < 0) throw new IllegalArgumentException("가져오기 후보 순서가 올바르지 않습니다.");
        this.contentImport = contentImport;
        this.mentionOrder = mentionOrder;
        this.matchRank = matchRank;
        this.extractedName = text(extractedName, 200);
    }

    public static ContentImportCandidate unmatched(ContentImport job, int order, String extractedName) {
        return new ContentImportCandidate(job, order, 0, extractedName);
    }

    public static ContentImportCandidate matched(ContentImport job, int order, int rank, String extractedName, PlaceSearchResult result) {
        ContentImportCandidate candidate = new ContentImportCandidate(job, order, rank, extractedName);
        candidate.externalPlaceId = text(result.externalPlaceId(), 200);
        candidate.matchedName = text(result.name(), 200);
        candidate.formattedAddress = nullable(result.formattedAddress(), 500);
        candidate.latitude = result.latitude();
        candidate.longitude = result.longitude();
        candidate.primaryType = nullable(result.primaryType(), 100);
        candidate.provider = nullable(result.provider(), 50);
        return candidate;
    }

    public boolean matched() { return externalPlaceId != null && latitude != null && longitude != null; }
    public Long getId() { return id; }
    public ContentImport getContentImport() { return contentImport; }
    public int getMentionOrder() { return mentionOrder; }
    public int getMatchRank() { return matchRank; }
    public String getExtractedName() { return extractedName; }
    public String getExternalPlaceId() { return externalPlaceId; }
    public String getMatchedName() { return matchedName; }
    public String getFormattedAddress() { return formattedAddress; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public String getPrimaryType() { return primaryType; }
    public String getProvider() { return provider; }
    public Instant getCreatedAt() { return createdAt; }

    private static String text(String value, int max) {
        String normalized = nullable(value, max);
        if (normalized == null) throw new IllegalArgumentException("후보 장소 이름은 필수입니다.");
        return normalized;
    }
    private static String nullable(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }
}
