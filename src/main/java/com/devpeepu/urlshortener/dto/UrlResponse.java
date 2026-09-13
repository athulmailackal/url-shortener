package com.devpeepu.urlshortener.dto;

import java.time.LocalDateTime;

public class UrlResponse {

    private long id;
    private String originalUrl;
    private String shortCode;
    private String shortUrl;
    private Long clickCount;
    private LocalDateTime expiresAt;

    public UrlResponse() {}

    public UrlResponse(
            Long id,
            String originalUrl,
            String shortCode,
            String shortUrl,
            Long clickCount,
            LocalDateTime expiresAt) {

        this.id = id;
        this.originalUrl = originalUrl;
        this.shortCode = shortCode;
        this.shortUrl = shortUrl;
        this.clickCount = clickCount;
        this.expiresAt = expiresAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }

    public String getShortCode() {
        return shortCode;
    }

    public void setShortCode(String shortCode) {
        this.shortCode = shortCode;
    }

    public String getShortUrl() {
        return shortUrl;
    }

    public void setShortUrl(String shortUrl) {
        this.shortUrl = shortUrl;
    }

    public Long getClickCount() {
        return clickCount;
    }
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
}