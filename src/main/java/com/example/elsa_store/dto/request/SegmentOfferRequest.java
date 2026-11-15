package com.example.elsa_store.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Ưu đãi gửi cho một nhóm khách.
 *
 * @param message lời nhắn của cửa hàng
 * @param promotionId mã giảm giá đang chạy của cửa hàng kèm theo (tuỳ chọn)
 */
public record SegmentOfferRequest(@NotBlank @Size(max = 300) String message, Long promotionId) {}
