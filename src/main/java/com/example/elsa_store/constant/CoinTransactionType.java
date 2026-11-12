package com.example.elsa_store.constant;

/** Loại biến động xu thưởng. Giá trị mới luôn thêm vào cuối. */
public enum CoinTransactionType {
    /** Nhận xu khi đơn hoàn thành. */
    EARN,
    /** Dùng xu để giảm giá lúc đặt đơn. */
    REDEEM,
    /** Hoàn lại xu đã dùng vì đơn bị huỷ / xoá. */
    REFUND,
    /** Thu lại xu đã nhận vì khách trả hàng được hoàn tiền. */
    REVOKE,
    /** Thưởng viết đánh giá sản phẩm (mỗi sản phẩm một lần). */
    REVIEW_REWARD,
    /** Thưởng đánh giá có ảnh (mỗi sản phẩm một lần). */
    PHOTO_REWARD,
    /** Điểm danh hằng ngày (mỗi ngày một lần, chuỗi 7 ngày). */
    DAILY_CHECK_IN,
    /** Người giới thiệu: bạn được mời có đơn đầu tiên hoàn thành. */
    REFERRAL_REWARD,
    /** Người được mời: thưởng chào mừng khi đơn đầu tiên hoàn thành. */
    REFERRAL_WELCOME
}
