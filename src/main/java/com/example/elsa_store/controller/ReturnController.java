package com.example.elsa_store.controller;

import com.example.elsa_store.constant.ReturnStatus;
import com.example.elsa_store.dto.common.ApiResponse;
import com.example.elsa_store.dto.request.ReturnCreateRequest;
import com.example.elsa_store.dto.request.ReturnDecisionRequest;
import com.example.elsa_store.dto.response.PageResponse;
import com.example.elsa_store.dto.response.ReturnResponse;
import com.example.elsa_store.service.ReturnService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Đổi / trả hàng (xem README BE, mục "Đổi / trả hàng"). */
@RestController
@RequestMapping("/returns")
@RequiredArgsConstructor
public class ReturnController {

    private final ReturnService returnService;

    /** Khách gửi yêu cầu trả hàng cho đơn đã hoàn thành. */
    @PostMapping
    public ApiResponse<ReturnResponse> create(@Valid @RequestBody ReturnCreateRequest request) {
        return ApiResponse.ok(returnService.create(request));
    }

    /** Yêu cầu của khách đang đăng nhập; {@code orderId} để lấy yêu cầu của một đơn. */
    @GetMapping("/me")
    public ApiResponse<PageResponse<ReturnResponse>> mine(
            @RequestParam(required = false) Long orderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(returnService.mine(orderId, page, size));
    }

    /** Yêu cầu gửi tới cửa hàng (người của cửa hàng; ADMIN xem được, có thể lọc {@code storeId}). */
    @GetMapping
    public ApiResponse<PageResponse<ReturnResponse>> forStore(
            @RequestParam(required = false) ReturnStatus status,
            @RequestParam(required = false) Long storeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(returnService.forStore(status, storeId, page, size));
    }

    @GetMapping("/{id:\\d+}")
    public ApiResponse<ReturnResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(returnService.get(id));
    }

    @PutMapping("/{id}/cancel")
    public ApiResponse<ReturnResponse> cancel(@PathVariable Long id) {
        return ApiResponse.ok(returnService.cancel(id));
    }

    @PutMapping("/{id}/approve")
    public ApiResponse<ReturnResponse> approve(
            @PathVariable Long id, @Valid @RequestBody(required = false) ReturnDecisionRequest request) {
        return ApiResponse.ok(returnService.approve(id, request));
    }

    @PutMapping("/{id}/reject")
    public ApiResponse<ReturnResponse> reject(@PathVariable Long id, @Valid @RequestBody ReturnDecisionRequest request) {
        return ApiResponse.ok(returnService.reject(id, request));
    }

    /** Đã nhận lại hàng và hoàn tiền (chủ / quản lý). */
    @PutMapping("/{id}/refund")
    public ApiResponse<ReturnResponse> refund(
            @PathVariable Long id, @Valid @RequestBody(required = false) ReturnDecisionRequest request) {
        return ApiResponse.ok(returnService.refund(id, request));
    }
}
