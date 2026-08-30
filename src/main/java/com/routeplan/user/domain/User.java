package com.routeplan.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50, unique = true)
    private String nickname;

    @Column(length = 254)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "security_version", nullable = false)
    private long securityVersion;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected User() {
    }

    private User(String nickname) {
        this.nickname = normalizeNickname(nickname);
    }

    private User(String email, String nickname, String passwordHash) {
        this.email = normalizeEmail(email);
        this.nickname = normalizeNickname(nickname);
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("비밀번호 해시는 필수입니다.");
        }
        this.passwordHash = passwordHash;
    }

    public static User create(String nickname) {
        return new User(nickname);
    }

    public static User register(String email, String nickname, String passwordHash) {
        return new User(email, nickname, passwordHash);
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("이메일은 필수입니다.");
        }
        String normalized = email.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.length() > 254) {
            throw new IllegalArgumentException("이메일은 254자를 초과할 수 없습니다.");
        }
        return normalized;
    }

    private static String normalizeNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("닉네임은 필수입니다.");
        }
        String normalized = nickname.trim();
        if (normalized.length() > 50) {
            throw new IllegalArgumentException("닉네임은 50자를 초과할 수 없습니다.");
        }
        return normalized;
    }

    public Long getId() {
        return id;
    }

    public String getNickname() {
        return nickname;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isEmailVerified() { return emailVerifiedAt != null; }

    public long getSecurityVersion() { return securityVersion; }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
