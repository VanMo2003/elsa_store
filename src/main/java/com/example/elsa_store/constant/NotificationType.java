package com.example.elsa_store.constant;

public enum NotificationType {
    /** Gửi cho chủ, quản lý và nhân viên của cửa hàng khi khách vừa đặt đơn mới. */
    NEW_ORDER,
    ORDER_PLACED,
    ORDER_CONFIRMED,
    ORDER_CANCELLED,
    ORDER_COMPLETED,
    PAYMENT_SUCCESS,
    PAYMENT_FAILED,
    /** Gửi cho ADMIN khi có cửa hàng đăng ký (hoặc gửi lại hồ sơ) cần duyệt. */
    STORE_REGISTERED,
    STORE_APPROVED,
    STORE_REJECTED,
    /** Đơn đã giao cho đơn vị vận chuyển. Giá trị mới luôn thêm vào cuối. */
    ORDER_SHIPPING
}
