package com.example.elsa_store.controller;

import com.example.elsa_store.constant.ApprovalStatus;
import com.example.elsa_store.dto.common.ApiResponse;
import com.example.elsa_store.dto.request.StoreRegistrationRequest;
import com.example.elsa_store.dto.request.StoreRejectRequest;
import com.example.elsa_store.dto.request.StoreResubmitRequest;
import com.example.elsa_store.dto.response.PageResponse;
import com.example.elsa_store.dto.response.StoreResponse;
import com.example.elsa_store.service.StoreRegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/store-registrations")
@RequiredArgsConstructor
public class StoreRegistrationController {

    private final StoreRegistrationService storeRegistrationService;

    /** Đăng ký công khai: tạo tài khoản STORE_OWNER và cửa hàng chờ ADMIN duyệt. */
    @PostMapping
    public ApiResponse<StoreResponse> register(@Valid @RequestBody StoreRegistrationRequest req) {
        return ApiResponse.ok(storeRegistrationService.register(req));
    }

    @GetMapping("/me")
    public ApiResponse<StoreResponse> getMine() {
        return ApiResponse.ok(storeRegistrationService.getMine());
    }

    @PutMapping("/me")
    public ApiResponse<StoreResponse> resubmit(@Valid @RequestBody StoreResubmitRequest req) {
        return ApiResponse.ok(storeRegistrationService.resubmit(req));
    }

    @GetMapping
    public ApiResponse<PageResponse<StoreResponse>> getAll(
            @RequestParam(required = false) ApprovalStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(storeRegistrationService.getAll(status, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<StoreResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(storeRegistrationService.getById(id));
    }

    @PutMapping("/{id}/approve")
    public ApiResponse<StoreResponse> approve(@PathVariable Long id) {
        return ApiResponse.ok(storeRegistrationService.approve(id));
    }

    @PutMapping("/{id}/reject")
    public ApiResponse<StoreResponse> reject(@PathVariable Long id, @Valid @RequestBody StoreRejectRequest req) {
        return ApiResponse.ok(storeRegistrationService.reject(id, req));
    }

    /** ADMIN đình chỉ cửa hàng đã duyệt: ẩn khỏi sàn, khách không đặt đơn mới; body {@code {reason}}. */
    @PutMapping("/{id}/suspend")
    public ApiResponse<StoreResponse> suspend(@PathVariable Long id, @Valid @RequestBody StoreRejectRequest req) {
        return ApiResponse.ok(storeRegistrationService.suspend(id, req));
    }

    @PutMapping("/{id}/reinstate")
    public ApiResponse<StoreResponse> reinstate(@PathVariable Long id) {
        return ApiResponse.ok(storeRegistrationService.reinstate(id));
    }
}
