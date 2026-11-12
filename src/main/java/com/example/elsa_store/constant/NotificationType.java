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
    REVIEW_REPLIED,
    /** Cho khách đã thả tim: sản phẩm đang hết hàng vừa có hàng lại. */
    BACK_IN_STOCK,
    /** Cho khách đã thả tim: sản phẩm vừa được khuyến mãi tự động giảm giá. */
    PRICE_DROP,
    /** Cho khách theo dõi cửa hàng: cửa hàng mở bán sản phẩm mới. */
    NEW_PRODUCT,
    /** Cho cửa hàng (chủ, quản lý, nhân viên): khách hỏi về một sản phẩm. */
    QUESTION_NEW,
    /** Cho khách: cửa hàng trả lời câu hỏi của mình. */
    QUESTION_ANSWERED,
    /** Cho khách: vừa lên hạng thành viên (nhận nhiều xu hơn). */
    TIER_UP,
    /** Cho khách: còn hàng trong giỏ đã lâu chưa đặt. */
    CART_REMINDER,
    /** Cho chủ và quản lý: ADMIN đình chỉ cửa hàng. */
    STORE_SUSPENDED,
    /** Cho chủ và quản lý: ADMIN mở lại cửa hàng. */
    STORE_REINSTATED,
    /** Cho ADMIN: khách báo cáo một sản phẩm vi phạm. */
    PRODUCT_REPORTED,
    /** Cho khách: báo cáo vi phạm của mình đã được xử lý / bỏ qua. */
    REPORT_HANDLED,
    /** Cho khách: đơn đang giao đã lâu, nhắc bấm "Đã nhận hàng" trước khi đơn tự hoàn thành. */
    ORDER_DELIVERY_CHECK,
    /** Cho khách: nhận xu giới thiệu bạn bè (người mời và người được mời). */
    REFERRAL_REWARD
}
