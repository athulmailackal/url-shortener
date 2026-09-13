package com.devpeepu.urlshortener.controller;

import java.net.URI;

import com.devpeepu.urlshortener.dto.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.devpeepu.urlshortener.entity.Url;
import com.devpeepu.urlshortener.service.UrlService;

@RestController
public class UrlController {
    private final UrlService urlService;

    public UrlController(UrlService urlService){
        this.urlService = urlService;

    }

    @PostMapping("/api/urls")
    public ResponseEntity<UrlResponse> createUrl(
            @Valid @RequestBody CreateUrlRequest request){
        UrlResponse response = urlService.createShortUrl(request.getUrl(), request.getShortCode(), request.getExpiresAt());

        return ResponseEntity
                .created(URI.create(response.getShortUrl()))
                .body(response);

    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode){

        Url url = urlService.redirectUrl(shortCode);
        
        return ResponseEntity
            .status(302)
            .location(URI.create(url.getOriginalUrl()))
            .build();

    }

    @GetMapping("/api/urls/{shortCode}")
    public ResponseEntity<UrlResponse> getUrlDetails(
            @PathVariable String shortCode) {

        UrlResponse response =
                urlService.getUrlDetails(shortCode);

        return ResponseEntity.ok(response);
    }


    @DeleteMapping("/api/urls/{shortCode}")
    public ResponseEntity<Void> deleteUrl(@PathVariable String shortCode){
        urlService.deleteUrl(shortCode);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/urls/{shortCode}/stats")
    public ResponseEntity<UrlStatsResponse> getUrlStats(
            @PathVariable String shortCode) {

        UrlStatsResponse response =
                urlService.getUrlStats(shortCode);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/urls")
    public ResponseEntity<PageResponse<UrlResponse>> getAllUrls(
            Pageable pageable) {

        return ResponseEntity.ok(
                urlService.getAllUrls(pageable)
        );
    }

    @PutMapping("/api/urls/{shortCode}")
    public ResponseEntity<UrlResponse> updateExpiration(
            @PathVariable String shortCode,
            @Valid @RequestBody UpdateUrlRequest request) {

        UrlResponse response = urlService.updateExpiration(
                shortCode,
                request.getExpiresAt()
        );

        return ResponseEntity.ok(response);
    }
}