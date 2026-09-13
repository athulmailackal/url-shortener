package com.devpeepu.urlshortener;

import com.devpeepu.urlshortener.entity.Url;
import com.devpeepu.urlshortener.repository.UrlRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.api.BeforeEach;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc
class UrlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UrlRepository urlRepository;


    @BeforeEach
    void cleanDatabase() {
        urlRepository.deleteAll();
    }

    @Test
    void createUrl_shouldPersistUrl() throws Exception {

        urlRepository.deleteAll();

        mockMvc.perform(
                        post("/api/urls")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                    "url": "https://www.google.com",
                                    "shortCode": "integration-test"
                                }
                                """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalUrl")
                        .value("https://www.google.com"))
                .andExpect(jsonPath("$.shortCode")
                        .value("integration-test"))
                .andExpect(jsonPath("$.clickCount")
                        .value(0));

        assertTrue(
                urlRepository
                        .findByShortCode("integration-test")
                        .isPresent()
        );
    }

    @Test
    void redirect_shouldIncrementClickCount() throws Exception {

        Url url = new Url(
                null,
                "https://www.google.com",
                "click-test"
        );

        Url savedUrl = urlRepository.save(url);

        mockMvc.perform(
                        get("/click-test")
                )
                .andExpect(status().isFound());

        Url updatedUrl = urlRepository
                .findByShortCode(savedUrl.getShortCode())
                .orElseThrow();

        assertEquals(1L, updatedUrl.getClickCount());
        assertNotNull(updatedUrl.getLastClickedAt());
    }

    @Test
    void redirect_shouldReturn410ForExpiredUrl() throws Exception {

        Url url = new Url(
                null,
                "https://www.google.com",
                "expired-test"
        );

        url.setExpiresAt(
                LocalDateTime.now().minusDays(1)
        );

        Url savedUrl = urlRepository.save(url);

        mockMvc.perform(
                        get("/expired-test")
                )
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value(410))
                .andExpect(jsonPath("$.message")
                        .value("Short URL has expired"));

        Url updatedUrl = urlRepository
                .findById(savedUrl.getId())
                .orElseThrow();

        assertEquals(0L, updatedUrl.getClickCount());
    }

    @Test
    void createUrl_shouldUseConfiguredBaseUrl() throws Exception {

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
                .andExpect(jsonPath("$.shortUrl")
                        .value(org.hamcrest.Matchers.startsWith(
                                "http://localhost:8080/"
                        )));
    }
}