package com.example.elsa_store.dto.response;

import com.example.elsa_store.constant.BannerLinkType;

import java.time.LocalDateTime;

public record BannerResponse(
        Long id,
        String title,
        String subtitle,
        String imageUrl,
        BannerLinkType linkType,
        String linkValue,
        int sortOrder,
        boolean active,
        LocalDateTime startsAt,
        LocalDateTime endsAt) {}
