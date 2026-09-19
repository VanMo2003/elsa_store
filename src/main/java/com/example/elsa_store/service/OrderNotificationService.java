package com.example.elsa_store.service;

import com.example.elsa_store.constant.OrderStatus;
import com.example.elsa_store.constant.PaymentStatus;
import com.example.elsa_store.entity.Order;

/** Gắn thông báo (trong app + push) và email vào các mốc của đơn hàng. Gọi bên trong transaction của đơn. */
public interface OrderNotificationService {
    /** Khách vừa đặt đơn: báo cho khách và cho admin/nhân viên. */
    void orderPlaced(Order order);

    /** Chỉ báo khi trạng thái thật sự đổi và là mốc khách quan tâm (xác nhận, huỷ, hoàn thành). */
    void statusChanged(Order order, OrderStatus previous);

    /** Chỉ báo khi thanh toán thật sự đổi sang thành công hoặc thất bại. */
    void paymentChanged(Order order, PaymentStatus previous);
}
