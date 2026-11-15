package com.example.elsa_store.dto.response;

import com.example.elsa_store.constant.CustomerSegment;

import java.time.LocalDateTime;

/** Một lần gửi ưu đãi cho nhóm khách. */
public record SegmentCampaignResponse(
        Long id, CustomerSegment segment, String message, String couponCode, int recipients, LocalDateTime sentAt) {}
