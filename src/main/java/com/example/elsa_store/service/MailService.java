package com.example.elsa_store.service;

import com.example.elsa_store.event.OrderMailEvent;
import com.example.elsa_store.event.PasswordResetRequestedEvent;
import com.example.elsa_store.event.StoreReviewedEvent;

public interface MailService {
    /** Gửi email đơn hàng/thanh toán kèm liên kết mở app. Không ném lỗi; thất bại chỉ được ghi log. */
    void sendOrderMail(OrderMailEvent event);

    /** Gửi chủ cửa hàng kết quả xét duyệt hồ sơ (duyệt hoặc từ chối kèm lý do). Không ném lỗi. */
    void sendStoreReview(StoreReviewedEvent review);

    /** Mã khôi phục mật khẩu. Chưa cấu hình mail thì ghi mã ra log (chỉ để thử khi phát triển). */
    void sendPasswordReset(PasswordResetRequestedEvent event);
}
