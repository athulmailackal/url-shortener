package com.devpeepu.urlshortener.service;

import com.devpeepu.urlshortener.dto.UrlResponse;
import com.devpeepu.urlshortener.dto.UrlStatsResponse;
import com.devpeepu.urlshortener.entity.Url;
import com.devpeepu.urlshortener.exception.UrlExpiredException;
import com.devpeepu.urlshortener.exception.UrlNotFoundException;
import com.devpeepu.urlshortener.repository.UrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.devpeepu.urlshortener.dto.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UrlServiceTest {

    @Mock
    private UrlRepository urlRepository;

    @InjectMocks
    private UrlService urlService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                urlService,
                "baseUrl",
                "http://localhost:8080"
        );
        ReflectionTestUtils.setField(
                urlService,
                "shortCodeLength",
                6
        );
    }

    @Test
    void createShortUrl_shouldCreateUrl() {

        Url savedUrl = new Url(
                1L,
                "https://www.google.com",
                "abc123"
        );

        when(urlRepository.existsByShortCode(any()))
                .thenReturn(false);

        when(urlRepository.save(any(Url.class)))
                .thenReturn(savedUrl);

        UrlResponse response =
                urlService.createShortUrl("https://www.google.com", null,null);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals(
                "https://www.google.com",
                response.getOriginalUrl()
        );
        assertEquals("abc123", response.getShortCode());

        verify(urlRepository).save(any(Url.class));
    }

    @Test
    void getByShortCode_shouldThrowExceptionWhenNotFound() {

        when(urlRepository.findByShortCode("abc123"))
                .thenReturn(java.util.Optional.empty());

        assertThrows(
                UrlNotFoundException.class,
                () -> urlService.getByShortCode("abc123")
        );
    }

    @Test
    void createShortUrl_shouldRejectPastExpirationDate() {

        LocalDateTime pastDate =
                LocalDateTime.now().minusDays(1);

        assertThrows(
                IllegalArgumentException.class,
                () -> urlService.createShortUrl(
                        "https://www.google.com",null,
                        pastDate
                )
        );
    }

    @Test
    void redirectUrl_shouldRejectExpiredUrl() {

        Url expiredUrl = new Url(
                1L,
                "https://www.google.com",
                "abc123"
        );

        expiredUrl.setExpiresAt(
                LocalDateTime.now().minusDays(1)
        );

        when(urlRepository.findByShortCode("abc123"))
                .thenReturn(Optional.of(expiredUrl));

        assertThrows(
                UrlExpiredException.class,
                () -> urlService.redirectUrl("abc123")
        );

        verify(urlRepository, never()).save(any());
    }

    @Test
    void createShortUrl_shouldUseCustomShortCode() {

        Url savedUrl = new Url(
                1L,
                "https://www.google.com",
                "my-google"
        );

        when(urlRepository.save(any(Url.class)))
                .thenReturn(savedUrl);

        UrlResponse response = urlService.createShortUrl(
                "https://www.google.com",
                "my-google",
                null
        );

        assertEquals("my-google", response.getShortCode());

        verify(urlRepository).save(any(Url.class));
    }
    @Test
    void createShortUrl_shouldRejectDuplicateShortCode() {

        when(urlRepository.existsByShortCode("my-google"))
                .thenReturn(true);

        assertThrows(
                IllegalArgumentException.class,
                () -> urlService.createShortUrl(
                        "https://www.google.com",
                        "my-google",
                        null
                )
        );

        verify(urlRepository, never()).save(any(Url.class));
    }

    @Test
    void createShortUrl_shouldRejectReservedShortCode() {

        assertThrows(
                IllegalArgumentException.class,
                () -> urlService.createShortUrl(
                        "https://www.google.com",
                        "api",
                        null
                )
        );

        verify(urlRepository, never()).save(any(Url.class));
    }
    @Test
    void createShortUrl_shouldRejectReservedShortCodeRegardlessOfCase() {

        assertThrows(
                IllegalArgumentException.class,
                () -> urlService.createShortUrl(
                        "https://www.google.com",
                        "API",
                        null
                )
        );

        verify(urlRepository, never()).save(any(Url.class));
    }

    @Test
    void getAllUrls_shouldReturnPaginatedResponses() {

        Url url1 = new Url(
                1L,
                "https://www.google.com",
                "google"
        );

        Url url2 = new Url(
                2L,
                "https://www.youtube.com",
                "youtube"
        );

        Page<Url> page = new PageImpl<>(
                List.of(url1, url2),
                PageRequest.of(0, 2),
                2
        );

        when(urlRepository.findAll(any(Pageable.class)))
                .thenReturn(page);

        PageResponse<UrlResponse> result =
                urlService.getAllUrls(PageRequest.of(0, 2));

        assertEquals(2, result.getContent().size());
        assertEquals(0, result.getPage());
        assertEquals(2, result.getSize());
        assertEquals(2, result.getTotalElements());
        assertEquals(1, result.getTotalPages());

        assertEquals(
                "https://www.google.com",
                result.getContent().get(0).getOriginalUrl()
        );

        assertEquals(
                "google",
                result.getContent().get(0).getShortCode()
        );

        assertEquals(
                "https://www.youtube.com",
                result.getContent().get(1).getOriginalUrl()
        );

        assertEquals(
                "youtube",
                result.getContent().get(1).getShortCode()
        );
    }
    @Test
    void getAllUrls_shouldReturnEmptyPageWhenNoResults() {

        Page<Url> page = new PageImpl<>(
                List.of(),
                PageRequest.of(2, 2),
                4
        );

        when(urlRepository.findAll(any(Pageable.class)))
                .thenReturn(page);

        PageResponse<UrlResponse> result =
                urlService.getAllUrls(PageRequest.of(2, 2));

        assertEquals(0, result.getContent().size());
        assertEquals(2, result.getPage());
        assertEquals(2, result.getSize());
        assertEquals(4, result.getTotalElements());
        assertEquals(2, result.getTotalPages());
    }

    @Test
    void updateExpiration_shouldUpdateExpirationTime() {

        Url url = new Url(
                1L,
                "https://www.google.com",
                "google"
        );

        when(urlRepository.findByShortCode("google"))
                .thenReturn(Optional.of(url));

        when(urlRepository.save(any(Url.class)))
                .thenReturn(url);

        LocalDateTime newExpiration =
                LocalDateTime.now().plusDays(7);

        UrlResponse result =
                urlService.updateExpiration(
                        "google",
                        newExpiration
                );

        assertEquals(
                newExpiration,
                result.getExpiresAt()
        );

        assertEquals(
                "google",
                result.getShortCode()
        );

        verify(urlRepository).save(url);
    }

    @Test
    void updateExpiration_whenUrlNotFound_shouldThrowException() {

        when(urlRepository.findByShortCode("unknown"))
                .thenReturn(Optional.empty());

        assertThrows(
                UrlNotFoundException.class,
                () -> urlService.updateExpiration(
                        "unknown",
                        LocalDateTime.now().plusDays(7)
                )
        );

        verify(urlRepository, never()).save(any(Url.class));
    }

    @Test
    void deleteUrl_shouldDeleteExistingUrl() {

        Url url = new Url(
                1L,
                "https://www.google.com",
                "google"
        );

        when(urlRepository.findByShortCode("google"))
                .thenReturn(Optional.of(url));

        urlService.deleteUrl("google");

        verify(urlRepository).delete(url);
    }
    @Test
    void createShortUrl_shouldGenerateSixCharacterShortCode() {

        Url savedUrl = new Url(
                1L,
                "https://www.google.com",
                "abc123"
        );

        when(urlRepository.existsByShortCode(any()))
                .thenReturn(false);

        when(urlRepository.save(any(Url.class)))
                .thenReturn(savedUrl);

        UrlResponse response = urlService.createShortUrl(
                "https://www.google.com",
                null,
                null
        );

        assertNotNull(response.getShortCode());
        assertEquals(6, response.getShortCode().length());
    }
    @Test
    void createShortUrl_shouldRejectInvalidShortCodeLength() {

        ReflectionTestUtils.setField(
                urlService,
                "shortCodeLength",
                3
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> urlService.createShortUrl(
                        "https://www.google.com",
                        null,
                        null
                )
        );

        verify(urlRepository, never()).save(any(Url.class));
    }

    @Test
    void getUrlStats_shouldReturnCreatedAt() {

        Url url = new Url(
                1L,
                "https://www.google.com",
                "google"
        );

        LocalDateTime createdAt = url.getCreatedAt();

        url.setExpiresAt(
                LocalDateTime.now().plusDays(7)
        );

        when(urlRepository.findByShortCode("google"))
                .thenReturn(Optional.of(url));

        UrlStatsResponse result =
                urlService.getUrlStats("google");

        assertEquals("google", result.getShortCode());
        assertEquals(
                "https://www.google.com",
                result.getOriginalUrl()
        );
        assertEquals(0L, result.getClickCount());
        assertEquals(createdAt, result.getCreatedAt());
        assertEquals(url.getExpiresAt(), result.getExpiresAt());
    }

    @Test
    void incrementClickCount_shouldUpdateLastClickedAt() {

        Url url = new Url(
                1L,
                "https://www.google.com",
                "google"
        );

        assertEquals(0L, url.getClickCount());
        assertNull(url.getLastClickedAt());

        url.incrementClickCount();

        assertEquals(1L, url.getClickCount());
        assertNotNull(url.getLastClickedAt());
    }

    @Test
    void redirectUrl_shouldUpdateLastClickedAt() {

        Url url = new Url(
                1L,
                "https://www.google.com",
                "google"
        );

        when(urlRepository.findByShortCode("google"))
                .thenReturn(Optional.of(url));

        when(urlRepository.save(any(Url.class)))
                .thenReturn(url);

        LocalDateTime beforeClick = LocalDateTime.now();

        Url result = urlService.redirectUrl("google");

        LocalDateTime afterClick = LocalDateTime.now();

        assertEquals(1L, result.getClickCount());
        assertNotNull(result.getLastClickedAt());

        assertFalse(result.getLastClickedAt().isBefore(beforeClick));
        assertFalse(result.getLastClickedAt().isAfter(afterClick));

        verify(urlRepository).save(url);
    }
}