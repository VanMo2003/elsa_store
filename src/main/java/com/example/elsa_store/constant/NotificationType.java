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
    ORDER_SHIPPING,
    /** Cho chủ / quản lý: sản phẩm vừa bán xuống dưới mức cảnh báo tồn kho hoặc hết hàng. */
    LOW_STOCK,
    /** Cho cửa hàng: khách gửi yêu cầu trả hàng. */
    RETURN_REQUESTED,
    /** Cho khách: yêu cầu trả hàng được duyệt / bị từ chối / đã hoàn tiền. */
    RETURN_UPDATED,
    /** Cho cửa hàng (chủ, quản lý, nhân viên): khách vừa viết đánh giá mới. */
    REVIEW_NEW,
    /** Cho khách: cửa hàng trả lời đánh giá của mình. */
    REVIEW_REPLIED
}
