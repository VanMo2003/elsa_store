package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.OrderStatus;
import com.example.elsa_store.entity.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** Thời hạn trả hàng: {@code app.orders.return-window-days} ngày kể từ lúc đơn hoàn thành (đơn cũ: từ ngày đặt). */
@Component
public class ReturnPolicy {

    private final int windowDays;

    public ReturnPolicy(@Value("${app.orders.return-window-days:7}") int windowDays) {
        this.windowDays = Math.max(windowDays, 0);
    }

    public int windowDays() {
        return windowDays;
    }

    /** Hạn cuối gửi yêu cầu trả hàng; {@code null} khi đơn chưa hoàn thành hoặc cửa hàng tắt đổi trả (0 ngày). */
    public LocalDateTime deadline(Order order) {
        if (order.getStatus() != OrderStatus.HOAN_THANH || windowDays == 0) {
            return null;
        }
        LocalDateTime base = order.getCompletedAt() != null ? order.getCompletedAt() : order.getOrderDate();
        return base == null ? null : base.plusDays(windowDays);
    }
}
