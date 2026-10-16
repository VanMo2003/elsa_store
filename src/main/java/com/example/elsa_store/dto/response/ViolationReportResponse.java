package com.example.elsa_store.dto.response;

import com.example.elsa_store.constant.ViolationReason;
import com.example.elsa_store.constant.ViolationStatus;

import java.time.LocalDateTime;

/** Báo cáo vi phạm cho ADMIN (và cho người báo cáo ngay sau khi gửi). */
public record ViolationReportResponse(
        Long id,
        Long productId,
        String productName,
        String productImageUrl,
        Long storeId,
        String storeName,
        boolean storeSuspended,
        String reporterName,
        ViolationReason reason,
        String note,
        ViolationStatus status,
        String adminNote,
        LocalDateTime createdAt,
        LocalDateTime handledAt,
        long pendingForProduct) {}
