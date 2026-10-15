package com.example.elsa_store.controller;

import com.example.elsa_store.dto.common.ApiResponse;
import com.example.elsa_store.dto.request.PurchaseOrderRequest;
import com.example.elsa_store.dto.response.PurchaseOrderResponse;
import com.example.elsa_store.service.PurchaseOrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Phiếu nhập hàng của từng cửa hàng: chỉ chủ và quản lý thao tác; ADMIN chỉ xem. Phiếu nháp sửa / huỷ / xoá được;
 * {@code POST /{id}/receive} cộng hàng vào kho và khoá phiếu.
 */
@RestController
@RequestMapping("/purchase-orders")
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    public PurchaseOrderController(PurchaseOrderService purchaseOrderService) {
        this.purchaseOrderService = purchaseOrderService;
    }

    /** {@code status}: 0 nháp, 1 đã nhập kho, 2 đã huỷ; bỏ trống = tất cả. */
    @GetMapping
    public ApiResponse<List<PurchaseOrderResponse>> getAll(@RequestParam(required = false) Integer status,
                                                           @RequestParam(required = false) Long storeId) {
        return ApiResponse.ok(purchaseOrderService.list(status, storeId));
    }

    @GetMapping("/{id}")
    public ApiResponse<PurchaseOrderResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(purchaseOrderService.get(id));
    }

    @PostMapping
    public ApiResponse<PurchaseOrderResponse> create(@Valid @RequestBody PurchaseOrderRequest body) {
        return ApiResponse.ok(purchaseOrderService.create(body));
    }

    @PutMapping("/{id}")
    public ApiResponse<PurchaseOrderResponse> update(@PathVariable Long id,
                                                     @Valid @RequestBody PurchaseOrderRequest body) {
        return ApiResponse.ok(purchaseOrderService.update(id, body));
    }

    @PostMapping("/{id}/receive")
    public ApiResponse<PurchaseOrderResponse> receive(@PathVariable Long id) {
        return ApiResponse.ok(purchaseOrderService.receive(id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<PurchaseOrderResponse> cancel(@PathVariable Long id) {
        return ApiResponse.ok(purchaseOrderService.cancel(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        purchaseOrderService.delete(id);
        return ApiResponse.ok(null);
    }
}
