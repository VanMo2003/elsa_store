
package com.example.elsa_store.controller;

import com.example.elsa_store.constant.OrderStatus;
import com.example.elsa_store.constant.PaymentStatus;
import com.example.elsa_store.dto.request.OrderRequest;
import com.example.elsa_store.dto.response.OrderResponse;
import com.example.elsa_store.service.OrderService;
import jakarta.validation.Valid;
import com.example.elsa_store.dto.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ApiResponse<OrderResponse> create(@Valid @RequestBody OrderRequest request) {
        return ApiResponse.ok(orderService.create(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(orderService.getById(id));
    }

    @PutMapping("/{orderId}/status")
    public OrderResponse updateOrderStatus(
            @PathVariable Long orderId,
            @RequestParam OrderStatus status
    ) {
        return orderService.updateStatus(orderId, status);
    }

    @PutMapping("/{orderId}/payment-status")
    public OrderResponse updatePaymentStatus(
            @PathVariable Long orderId,
            @RequestParam PaymentStatus paymentStatus
    ) {
        return orderService.updatePaymentStatus(orderId, paymentStatus);
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
