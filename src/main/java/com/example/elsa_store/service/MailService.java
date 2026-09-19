package com.example.elsa_store.service;

import com.example.elsa_store.event.OrderMailEvent;

public interface MailService {
    /** Gửi email đơn hàng/thanh toán kèm liên kết mở app. Không ném lỗi; thất bại chỉ được ghi log. */
    void sendOrderMail(OrderMailEvent event);
}
