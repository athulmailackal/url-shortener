package com.devpeepu.urlshortener.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.devpeepu.urlshortener.dto.PageResponse;
import com.devpeepu.urlshortener.dto.UrlResponse;
import com.devpeepu.urlshortener.dto.UrlStatsResponse;
import com.devpeepu.urlshortener.exception.UrlExpiredException;
import com.devpeepu.urlshortener.exception.UrlNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Value;

import com.devpeepu.urlshortener.entity.Url;
import com.devpeepu.urlshortener.repository.UrlRepository;

@Service 
public class UrlService {

    @Value("${app.short-code-length}")
    private int shortCodeLength;
    

    private final UrlRepository urlRepository;

    private final String baseUrl;

    public UrlService(
            UrlRepository urlRepository,
            @Value("${app.base-url}") String baseUrl) {

        this.urlRepository = urlRepository;
        this.baseUrl = baseUrl;
    }

    private boolean isReservedShortCode(String shortCode) {

        return shortCode.equalsIgnoreCase("api");
    }

    public Url redirectUrl(String shortCode) {

        Url url = getByShortCode(shortCode);

        if (url.getExpiresAt() != null &&
                url.getExpiresAt().isBefore(LocalDateTime.now())) {

            throw new UrlExpiredException(
                    "Short URL has expired"
            );
        }

        url.incrementClickCount();

        return urlRepository.save(url);
    }

    private String generateUniqueShortCode(){
        String shortCode;

        do {
            shortCode = UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, shortCodeLength);
        }while(urlRepository.existsByShortCode(shortCode));
        return shortCode;
    }

    public UrlResponse createShortUrl(String originalUrl, String requestedShortCode, LocalDateTime expiresAt){

        if (shortCodeLength < 4) {
            throw new IllegalArgumentException(
                    "Short code length must be at least 4"
            );
        }
        if (expiresAt != null && expiresAt.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException(
                    "Expiration time must be in the future"
            );
        }
        String shortCode;

        if (requestedShortCode != null &&
                !requestedShortCode.isBlank()) {

            if (isReservedShortCode(requestedShortCode)) {
                throw new IllegalArgumentException(
                        "Short code is reserved"
                );
            }
            if (urlRepository.existsByShortCode(requestedShortCode)) {
                throw new IllegalArgumentException(
                        "Short code already exists"
                );
            }

            shortCode = requestedShortCode;

        } else {

            shortCode = generateUniqueShortCode();
        }
        Url url = new Url();
        url.setOriginalUrl(originalUrl);
        url.setShortCode(shortCode);
        url.setExpiresAt(expiresAt);

        Url savedUrl = urlRepository.save(url);

        String shortUrl = baseUrl + "/" + savedUrl.getShortCode();

        return new UrlResponse(
                savedUrl.getId(),
                savedUrl.getOriginalUrl(),
                savedUrl.getShortCode(),
                shortUrl,
                savedUrl.getClickCount(),
                savedUrl.getExpiresAt()
        );

    }
    public Url getByShortCode(String shortCode){
        return urlRepository.findByShortCode(shortCode).orElseThrow(()->
                new UrlNotFoundException("Short URL not found: "+ shortCode));
    }

    public UrlResponse getUrlDetails(String shortCode) {

        Url url = getByShortCode(shortCode);

        String shortUrl =
                baseUrl + "/" + url.getShortCode();

        return new UrlResponse(
                url.getId(),
                url.getOriginalUrl(),
                url.getShortCode(),
                shortUrl,
                url.getClickCount(),
                url.getExpiresAt()
        );
    }

    public void deleteUrl(String shortCode){
        Url url = getByShortCode(shortCode);

        urlRepository.delete(url);
    }

    public PageResponse<UrlResponse> getAllUrls(Pageable pageable) {

        Page<Url> urlPage = urlRepository.findAll(pageable);

        List<UrlResponse> responses = urlPage
                .getContent()
                .stream()
                .map(url -> new UrlResponse(
                        url.getId(),
                        url.getOriginalUrl(),
                        url.getShortCode(),
                        baseUrl + "/" + url.getShortCode(),
                        url.getClickCount(),
                        url.getExpiresAt()
                ))
                .toList();

        return new PageResponse<>(
                responses,
                urlPage.getNumber(),
                urlPage.getSize(),
                urlPage.getTotalElements(),
                urlPage.getTotalPages()
        );
    }

    public UrlResponse updateExpiration(
            String shortCode,
            LocalDateTime expiresAt) {

        Url url = getByShortCode(shortCode);

        if (expiresAt != null &&
                expiresAt.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException(
                    "Expiration time must be in the future"
            );
        }

        url.setExpiresAt(expiresAt);

        Url savedUrl = urlRepository.save(url);

        String shortUrl =
                "http://localhost:8080/" + savedUrl.getShortCode();

        return new UrlResponse(
                savedUrl.getId(),
                savedUrl.getOriginalUrl(),
                savedUrl.getShortCode(),
                shortUrl,
                savedUrl.getClickCount(),
                savedUrl.getExpiresAt()
        );
    }
    public UrlStatsResponse getUrlStats(String shortCode) {

        Url url = getByShortCode(shortCode);

        return new UrlStatsResponse(
                url.getShortCode(),
                url.getOriginalUrl(),
                url.getClickCount(),
                url.getCreatedAt(),
                url.getExpiresAt(),
                url.getLastClickedAt()
        );
    }
}
