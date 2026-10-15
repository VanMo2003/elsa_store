package com.example.elsa_store.mapper;

import com.example.elsa_store.dto.response.StoreResponse;
import com.example.elsa_store.entity.Store;

public class StoreMapper {

    public static StoreResponse toResponse(Store s) {
        if (s == null) return null;
        return StoreResponse.builder()
                .id(s.getId())
                .name(s.getName())
                .description(s.getDescription())
                .imageUrl(s.getImageUrl())
                .ownerName(s.getOwnerName())
                .email(s.getEmail())
                .phone(s.getPhone())
                .address(s.getAddress())
                .approvalStatus(s.getApprovalStatus())
                .rejectionReason(s.getRejectionReason())
                .submittedAt(s.getSubmittedAt())
                .reviewedAt(s.getReviewedAt())
                .ownerId(s.getOwner() != null ? s.getOwner().getId() : null)
                .ownerUsername(s.getOwner() != null ? s.getOwner().getUsername() : null)
                .shippingFee(shippingFee(s))
                .freeShippingThreshold(s.getFreeShippingThreshold())
                .build();
    }

    /** Thông tin công khai cho khách: không lộ liên hệ riêng của chủ hay lý do từ chối. */
    public static StoreResponse toPublicResponse(Store s) {
        if (s == null) return null;
        return StoreResponse.builder()
                .id(s.getId())
                .name(s.getName())
                .description(s.getDescription())
                .imageUrl(s.getImageUrl())
                .phone(s.getPhone())
                .address(s.getAddress())
                .approvalStatus(s.getApprovalStatus())
                .shippingFee(shippingFee(s))
                .freeShippingThreshold(shippingFee(s) > 0 ? s.getFreeShippingThreshold() : null)
                .build();
    }

    private static double shippingFee(Store s) {
        return s.getShippingFee() == null ? 0 : s.getShippingFee();
    }
}
