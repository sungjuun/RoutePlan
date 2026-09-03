package com.routeplan.contentimport.domain;

import com.routeplan.user.domain.User;
import com.routeplan.wishlist.domain.Wishlist;
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
@Table(name = "content_imports")
public class ContentImport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wishlist_id")
    private Wishlist wishlist;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private ContentSourceType sourceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ContentImportStatus status;

    @Column(name = "source_url", nullable = false, length = 2048)
    private String sourceUrl;

    @Column(name = "input_text", length = 10000)
    private String inputText;

    @Column(name = "detected_title", length = 500)
    private String detectedTitle;

    @Column(length = 1000)
    private String warning;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;

    protected ContentImport() {
    }

    private ContentImport(User owner, Wishlist wishlist, ContentSourceType sourceType, String sourceUrl, String inputText) {
        if (owner == null || sourceType == null) throw new IllegalArgumentException("가져오기 소유자와 출처는 필수입니다.");
        this.owner = owner;
        this.wishlist = wishlist;
        this.sourceType = sourceType;
        this.status = ContentImportStatus.RECEIVED;
        this.sourceUrl = requireText(sourceUrl, "URL", 2048);
        this.inputText = nullableText(inputText, "콘텐츠 텍스트", 10000);
    }

    public static ContentImport create(User owner, Wishlist wishlist, ContentSourceType type, String url, String inputText) {
        return new ContentImport(owner, wishlist, type, url, inputText);
    }

    public void processing() {
        status = ContentImportStatus.PROCESSING;
        startedAt = Instant.now();
        completedAt = null;
        errorMessage = null;
        warning = null;
    }

    public void matching(String title) {
        status = ContentImportStatus.PLACE_MATCHING;
        detectedTitle = nullableText(title, "제목", 500);
    }

    public void complete(String warning) {
        status = ContentImportStatus.COMPLETED;
        this.warning = nullableText(warning, "경고", 1000);
        completedAt = Instant.now();
    }

    public void awaitInput(String warning) {
        status = ContentImportStatus.AWAITING_INPUT;
        this.warning = nullableText(warning, "경고", 1000);
        completedAt = Instant.now();
    }

    public void fail(String message) {
        status = ContentImportStatus.FAILED;
        errorMessage = nullableText(message, "오류", 1000);
        completedAt = Instant.now();
    }

    public void retry(String inputText) {
        this.inputText = requireText(inputText, "콘텐츠 텍스트", 10000);
        this.status = ContentImportStatus.RECEIVED;
        this.warning = null;
        this.errorMessage = null;
        this.completedAt = null;
    }

    public Long getId() { return id; }
    public User getOwner() { return owner; }
    public Wishlist getWishlist() { return wishlist; }
    public ContentSourceType getSourceType() { return sourceType; }
    public ContentImportStatus getStatus() { return status; }
    public String getSourceUrl() { return sourceUrl; }
    public String getInputText() { return inputText; }
    public String getDetectedTitle() { return detectedTitle; }
    public String getWarning() { return warning; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }

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
