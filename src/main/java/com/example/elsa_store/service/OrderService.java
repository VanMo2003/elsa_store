package com.example.elsa_store.service;

import com.example.elsa_store.constant.OrderStatus;
import com.example.elsa_store.constant.PaymentStatus;
import com.example.elsa_store.dto.request.OrderRequest;
import com.example.elsa_store.dto.response.OrderQuoteResponse;
import com.example.elsa_store.dto.response.OrderResponse;

import java.util.List;

public interface OrderService {
    OrderResponse create(OrderRequest request);

    /** Báo giá trước khi đặt: khuyến mãi, mã giảm giá, tồn kho — không tạo đơn, không trừ kho. */
    OrderQuoteResponse quote(OrderRequest request);

    OrderResponse getById(Long id);

    List<OrderResponse> getAll();

    List<OrderResponse> getAllByUser(Long userId);

    void delete(Long id);

    default OrderResponse updateStatus(Long orderId, OrderStatus status) {
        return updateStatus(orderId, status, null);
    }

    /** {@code reason}: lý do huỷ (lưu vào đơn và dòng thời gian) hoặc ghi chú của mốc; có thể trống. */
    OrderResponse updateStatus(Long orderId, OrderStatus status, String reason);

    OrderResponse updatePaymentStatus(Long orderId, PaymentStatus paymentStatus);

    /**
     * Hệ thống tự đổi trạng thái (không có người dùng): chỉ đổi khi đơn vẫn đang ở {@code expected} và bước đổi hợp lệ
     * (huỷ thì chưa thanh toán, hoàn thành thì đã trả hoặc COD). Trả {@code true} khi đã đổi.
     */
    boolean autoTransition(Long orderId, OrderStatus expected, OrderStatus target, String note);
}
