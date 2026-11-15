
package com.example.elsa_store.controller;

import com.example.elsa_store.constant.OrderStatus;
import com.example.elsa_store.constant.PaymentStatus;
import com.example.elsa_store.dto.request.OrderRequest;
import com.example.elsa_store.dto.request.TrackingRequest;
import com.example.elsa_store.dto.response.OrderQuoteResponse;
import com.example.elsa_store.dto.response.OrderResponse;
import com.example.elsa_store.dto.response.PageResponse;
import com.example.elsa_store.service.OrderService;
import jakarta.validation.Valid;
import com.example.elsa_store.dto.common.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;
    private final com.example.elsa_store.service.Reorders reorders;

    public OrderController(OrderService orderService, com.example.elsa_store.service.Reorders reorders) {
        this.orderService = orderService;
        this.reorders = reorders;
    }

    /**
     * Khách: "Mua lại" — thêm các món của đơn cũ vào giỏ với giá hiện tại; món hết hàng / ngừng bán bị bỏ qua và báo lại.
     * Giỏ đang có hàng của cửa hàng khác thì lỗi 1033, gửi {@code replace=true} để xoá giỏ cũ trước.
     */
    @PostMapping("/{id}/reorder")
    public ApiResponse<com.example.elsa_store.dto.response.ReorderResponse> reorder(
            @PathVariable Long id, @RequestParam(defaultValue = "false") boolean replace) {
        return ApiResponse.ok(reorders.reorder(id, replace));
    }

    @PostMapping
    public ApiResponse<OrderResponse> create(@Valid @RequestBody OrderRequest request) {
        return ApiResponse.ok(orderService.create(request));
    }

    /** Báo giá ở bước thanh toán (khuyến mãi, mã giảm giá, tồn kho) — không tạo đơn. */
    @PostMapping("/quote")
    public ApiResponse<OrderQuoteResponse> quote(@Valid @RequestBody OrderRequest request) {
        return ApiResponse.ok(orderService.quote(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(orderService.getById(id));
    }

    @PutMapping("/{orderId}/status")
    public ApiResponse<OrderResponse> updateOrderStatus(
            @PathVariable Long orderId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            // Lý do huỷ / ghi chú của mốc (tuỳ chọn).
            @RequestParam(required = false) String reason,
            // Khi chuyển sang đang giao: đơn vị vận chuyển và mã vận đơn (tuỳ chọn).
            @RequestParam(required = false) String carrier,
            @RequestParam(required = false) String trackingCode
    ) {
        if (status == null && paymentStatus == null) {
            throw new IllegalArgumentException("Bạn phải truyền status hoặc paymentStatus");
        }

        if (status != null && paymentStatus != null) {
            throw new IllegalArgumentException("Chỉ được truyền 1 trong 2: status hoặc paymentStatus");
        }

        OrderResponse orderResponse =
                (status != null)
                        ? orderService.updateStatus(orderId, status, reason, carrier, trackingCode)
                        : orderService.updatePaymentStatus(orderId, paymentStatus);

        return ApiResponse.ok(orderResponse);
    }

    /** Ghi chú nội bộ của cửa hàng cho đơn (nhân viên trở lên; khách không thấy); gửi trống để xoá. */
    @PutMapping("/{orderId}/internal-note")
    public ApiResponse<OrderResponse> updateInternalNote(
            @PathVariable Long orderId, @Valid @RequestBody com.example.elsa_store.dto.request.InternalNoteRequest request) {
        return ApiResponse.ok(orderService.updateInternalNote(orderId, request.note()));
    }

    /** Sửa đơn vị vận chuyển / mã vận đơn của đơn đang giao (nhân viên trở lên). */
    @PutMapping("/{orderId}/tracking")
    public ApiResponse<OrderResponse> updateTracking(
            @PathVariable Long orderId, @Valid @RequestBody TrackingRequest request) {
        return ApiResponse.ok(orderService.updateTracking(orderId, request.getCarrier(), request.getTrackingCode()));
    }

    @PutMapping("/{orderId}/payment-status")
    public ApiResponse<OrderResponse> updatePaymentStatus(
            @PathVariable Long orderId,
            @RequestParam PaymentStatus paymentStatus
    ) {
        return ApiResponse.ok(orderService.updatePaymentStatus(orderId, paymentStatus));
    }

    /**
     * Danh sách có phân trang cho màn Đơn hàng: {@code status} (tên trạng thái), {@code keyword} (mã đơn, tên / SĐT người
     * nhận hoặc khách), {@code storeId} (chỉ ADMIN). Phạm vi theo người xem như {@code GET /orders}.
     */
    @GetMapping("/page")
    public ApiResponse<PageResponse<OrderResponse>> page(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long storeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(orderService.search(status, keyword, storeId, page, size));
    }

    /** Tải danh sách đơn ra Excel theo bộ lọc (chủ / quản lý, ADMIN); {@code from}, {@code to} là ngày đặt. */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long storeId) {
        byte[] file = orderService.exportExcel(status, keyword, from, to, storeId);
        String name = "don-hang" + (from == null ? "" : "-" + from) + (to == null ? "" : "_" + to) + ".xlsx";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .body(file);
    }

    /** Số đơn theo từng trạng thái (và {@code ALL}) để hiện trên các bộ lọc. */
    @GetMapping("/counts")
    public ApiResponse<Map<String, Long>> counts(@RequestParam(required = false) Long storeId) {
        return ApiResponse.ok(orderService.countByStatus(storeId));
    }

    @GetMapping
    public ApiResponse<List<OrderResponse>> getAll() {
        return ApiResponse.ok(orderService.getAll());
    }

    @GetMapping("/by-user/{userId}")
    public ApiResponse<List<OrderResponse>> getAllByUserId(@PathVariable Long userId) {
        return ApiResponse.ok(orderService.getAllByUser(userId));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        orderService.delete(id);
        return ApiResponse.ok(null);
    }
}
