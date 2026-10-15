package com.example.elsa_store.event;

import com.example.elsa_store.entity.Store;

/** ADMIN vừa duyệt hoặc từ chối hồ sơ cửa hàng; mang đủ dữ liệu để gửi email kết quả. */
public record StoreReviewedEvent(
        boolean approved, String email, String ownerName, String ownerUsername, String storeName, String reason) {

    public static StoreReviewedEvent of(Store store) {
        return new StoreReviewedEvent(
                store.isApproved(),
                store.getEmail(),
                store.getOwnerName(),
                store.getOwner().getUsername(),
                store.getName(),
                store.getRejectionReason());
    }
}
