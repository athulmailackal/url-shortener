package com.devpeepu.urlshortener.dto;

import java.time.LocalDateTime;

public class UrlStatsResponse {

    private String shortCode;
    private String originalUrl;
    private Long clickCount;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime lastClickedAt;

    public UrlStatsResponse(
            String shortCode,
            String originalUrl,
            Long clickCount,
            LocalDateTime createdAt,
            LocalDateTime expiresAt,
            LocalDateTime lastClickedAt) {
        this.shortCode = shortCode;
        this.originalUrl = originalUrl;
        this.clickCount = clickCount;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.lastClickedAt = lastClickedAt;
    }

    public String getShortCode() {
        return shortCode;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public Long getClickCount() {
        return clickCount;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public LocalDateTime getLastClickedAt() {
        return lastClickedAt;
    }
}