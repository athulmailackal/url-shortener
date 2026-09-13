package com.devpeepu.urlshortener.dto;

import jakarta.validation.constraints.Future;
import java.time.LocalDateTime;

public class UpdateUrlRequest {

    @Future(message = "Expiration time must be in the future")
    private LocalDateTime expiresAt;

    public UpdateUrlRequest() {
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}