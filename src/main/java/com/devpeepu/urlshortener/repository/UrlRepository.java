package com.devpeepu.urlshortener.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devpeepu.urlshortener.entity.Url;

public interface UrlRepository extends JpaRepository<Url, Long>{
    Optional<Url> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);
    
}
