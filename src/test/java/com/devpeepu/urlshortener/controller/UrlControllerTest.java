package com.devpeepu.urlshortener.controller;

import com.devpeepu.urlshortener.dto.PageResponse;
import com.devpeepu.urlshortener.dto.UrlResponse;
import com.devpeepu.urlshortener.dto.UrlStatsResponse;
import com.devpeepu.urlshortener.exception.UrlExpiredException;
import com.devpeepu.urlshortener.exception.UrlNotFoundException;
import com.devpeepu.urlshortener.service.UrlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import com.devpeepu.urlshortener.dto.UpdateUrlRequest;
import java.time.LocalDateTime;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.springframework.test.web.servlet.MockMvc;
import com.devpeepu.urlshortener.entity.Url;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UrlController.class)
class UrlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UrlService urlService;

    @Test
    void createUrl_shouldReturn201() throws Exception {

        UrlResponse response = new UrlResponse(
                1L,
                "https://www.google.com",
                "abc123",
                "http://localhost:8080/abc123",
                0L,null
        );

        when(urlService.createShortUrl(any(),any(), any()))
                .thenReturn(response);

        mockMvc.perform(
                        post("/api/urls")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                    "url": "https://www.google.com"
                                }
                                """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.originalUrl")
                        .value("https://www.google.com"))
                .andExpect(jsonPath("$.shortCode")
                        .value("abc123"))
                .andExpect(jsonPath("$.clickCount").value(0));
    }

    @Test
    void getUrlStats_shouldReturn200() throws Exception {

        UrlStatsResponse response = new UrlStatsResponse(
                "google",
                "https://www.google.com",
                5L,
                LocalDateTime.of(2026, 9, 1, 12, 0),
                null,
                LocalDateTime.of(2026, 9, 10, 15, 30)
        );

        when(urlService.getUrlStats("google"))
                .thenReturn(response);

        mockMvc.perform(
                        get("/api/urls/google/stats")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode")
                        .value("google"))
                .andExpect(jsonPath("$.originalUrl")
                        .value("https://www.google.com"))
                .andExpect(jsonPath("$.clickCount")
                        .value(5))
                .andExpect(jsonPath("$.createdAt")
                        .value("2026-09-01T12:00:00"))
                .andExpect(jsonPath("$.expiresAt")
                        .doesNotExist())
        .andExpect(jsonPath("$.lastClickedAt")
                .value("2026-09-10T15:30:00"));
    }

    @Test
    void redirect_shouldReturn410ForExpiredUrl() throws Exception {

        when(urlService.redirectUrl("abc123"))
                .thenThrow(new UrlExpiredException("Short URL has expired"));

        mockMvc.perform(
                        get("/abc123")
                )
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value(410))
                .andExpect(jsonPath("$.message")
                        .value("Short URL has expired"));
    }

    @Test
    void createUrl_shouldReturn400ForInvalidShortCode() throws Exception {

        mockMvc.perform(
                        post("/api/urls")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                            {
                                "url": "https://www.google.com",
                                "shortCode": "hello world!"
                            }
                            """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Short code can only contain letters, numbers, hyphens, and underscores"));
    }

    @Test
    void getAllUrls_shouldReturn200() throws Exception {

        UrlResponse response1 = new UrlResponse(
                1L,
                "https://www.google.com",
                "google",
                "http://localhost:8080/google",
                0L,
                null
        );

        UrlResponse response2 = new UrlResponse(
                2L,
                "https://www.youtube.com",
                "youtube",
                "http://localhost:8080/youtube",
                0L,
                null
        );

        PageResponse<UrlResponse> page = new PageResponse<>(
                List.of(response1, response2),
                0,
                10,
                2,
                1
        );

        when(urlService.getAllUrls(any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(
                        get("/api/urls")
                                .param("page", "0")
                                .param("size", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].originalUrl")
                        .value("https://www.google.com"))
                .andExpect(jsonPath("$.content[1].shortCode")
                        .value("youtube"))
                .andExpect(jsonPath("$.totalElements")
                        .value(2))
                .andExpect(jsonPath("$.size")
                        .value(10))
                .andExpect(jsonPath("$.page")
                        .value(0));
    }
    @Test
    void updateExpiration_shouldReturn200() throws Exception {

        LocalDateTime expiration =
                LocalDateTime.of(2026, 12, 31, 23, 59, 59);

        UrlResponse response = new UrlResponse(
                1L,
                "https://www.google.com",
                "google",
                "http://localhost:8080/google",
                5L,
                expiration
        );

        when(urlService.updateExpiration(
                eq("google"),
                any(LocalDateTime.class)
        )).thenReturn(response);

        mockMvc.perform(
                        put("/api/urls/google")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                        "expiresAt": "2026-12-31T23:59:59"
                                    }
                                    """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode")
                        .value("google"))
                .andExpect(jsonPath("$.originalUrl")
                        .value("https://www.google.com"))
                .andExpect(jsonPath("$.expiresAt")
                        .value("2026-12-31T23:59:59"));
    }
    @Test
    void updateExpiration_withPastDate_shouldReturn400() throws Exception {

        mockMvc.perform(
                        put("/api/urls/google")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                        "expiresAt": "2020-01-01T00:00:00"
                                    }
                                    """)
                )
                .andExpect(status().isBadRequest());
    }
    @Test
    void updateExpiration_whenUrlNotFound_shouldReturn404() throws Exception {

        when(urlService.updateExpiration(
                eq("unknown"),
                any(LocalDateTime.class)
        )).thenThrow(
                new UrlNotFoundException("Short URL not found: unknown")
        );

        mockMvc.perform(
                        put("/api/urls/unknown")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                        "expiresAt": "2026-12-31T23:59:59"
                                    }
                                    """)
                )
                .andExpect(status().isNotFound());
    }
    @Test
    void getUrlStats_whenUrlNotFound_shouldReturn404() throws Exception {

        when(urlService.getUrlStats("unknown"))
                .thenThrow(
                        new UrlNotFoundException(
                                "Short URL not found: unknown"
                        )
                );

        mockMvc.perform(
                        get("/api/urls/unknown/stats")
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteUrl_shouldReturn204() throws Exception {

        doNothing().when(urlService).deleteUrl("google");

        mockMvc.perform(
                        delete("/api/urls/google")
                )
                .andExpect(status().isNoContent());

        verify(urlService).deleteUrl("google");
    }
    @Test
    void deleteUrl_whenUrlNotFound_shouldReturn404() throws Exception {

        doThrow(
                new UrlNotFoundException(
                        "Short URL not found: unknown"
                )
        ).when(urlService).deleteUrl("unknown");

        mockMvc.perform(
                        delete("/api/urls/unknown")
                )
                .andExpect(status().isNotFound());
    }
}