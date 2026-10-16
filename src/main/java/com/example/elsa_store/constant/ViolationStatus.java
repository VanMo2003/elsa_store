package com.example.elsa_store.constant;

/** Trạng thái xử lý báo cáo vi phạm của ADMIN. */
public enum ViolationStatus {
    PENDING,
    /** ADMIN đã xử lý (nhắc cửa hàng, ẩn hàng, đình chỉ…). */
    RESOLVED,
    /** Báo cáo không có cơ sở. */
    DISMISSED
}
