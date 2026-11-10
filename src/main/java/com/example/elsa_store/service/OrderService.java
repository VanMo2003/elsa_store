package com.example.elsa_store.service;

import com.example.elsa_store.constant.OrderStatus;
import com.example.elsa_store.constant.PaymentStatus;
import com.example.elsa_store.dto.request.OrderRequest;
import com.example.elsa_store.dto.response.OrderQuoteResponse;
import com.example.elsa_store.dto.response.OrderResponse;
import com.example.elsa_store.dto.response.PageResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface OrderService {
    OrderResponse create(OrderRequest request);

    /** Báo giá trước khi đặt: khuyến mãi, mã giảm giá, tồn kho — không tạo đơn, không trừ kho. */
    OrderQuoteResponse quote(OrderRequest request);

    OrderResponse getById(Long id);

    List<OrderResponse> getAll();

    /**
     * Danh sách có phân trang theo người xem: khách thấy đơn của mình, người của cửa hàng thấy đơn của cửa hàng, ADMIN
     * thấy mọi đơn (hoặc một cửa hàng qua {@code storeId}). {@code keyword} tìm trong mã đơn, tên / SĐT người nhận và khách.
     */
    PageResponse<OrderResponse> search(OrderStatus status, String keyword, Long storeId, int page, int size);

    /**
     * Tệp Excel các đơn khớp bộ lọc (ngày đặt trong {@code [from, to]}, null = không giới hạn), tối đa
     * {@code MAX_EXPORT_ROWS} đơn mới nhất. Chỉ chủ / quản lý (cửa hàng mình) và ADMIN.
     */
    byte[] exportExcel(OrderStatus status, String keyword, LocalDate from, LocalDate to, Long storeId);

    /** Số đơn theo trạng thái trong cùng phạm vi với {@link #search}, kèm {@code ALL} = tổng. */
    Map<String, Long> countByStatus(Long storeId);

    List<OrderResponse> getAllByUser(Long userId);

    void delete(Long id);

    default OrderResponse updateStatus(Long orderId, OrderStatus status) {
        return updateStatus(orderId, status, null);
    }

    /** {@code reason}: lý do huỷ (lưu vào đơn và dòng thời gian) hoặc ghi chú của mốc; có thể trống. */
    default OrderResponse updateStatus(Long orderId, OrderStatus status, String reason) {
        return updateStatus(orderId, status, reason, null, null);
    }

    /**
     * Như {@link #updateStatus(Long, OrderStatus, String)}; khi chuyển sang đang giao có thể nhập luôn đơn vị vận chuyển
     * và mã vận đơn (trạng thái khác thì không được truyền).
     */
    OrderResponse updateStatus(Long orderId, OrderStatus status, String reason, String carrier, String trackingCode);

    /** Sửa đơn vị vận chuyển / mã vận đơn của đơn đang giao (nhân viên trở lên); báo khách khi có mã mới. */
    OrderResponse updateTracking(Long orderId, String carrier, String trackingCode);

    OrderResponse updatePaymentStatus(Long orderId, PaymentStatus paymentStatus);

    /**
     * Hệ thống tự đổi trạng thái (không có người dùng): chỉ đổi khi đơn vẫn đang ở {@code expected} và bước đổi hợp lệ
     * (huỷ thì chưa thanh toán, hoàn thành thì đã trả hoặc COD). Trả {@code true} khi đã đổi.
     */
    boolean autoTransition(Long orderId, OrderStatus expected, OrderStatus target, String note);
}
