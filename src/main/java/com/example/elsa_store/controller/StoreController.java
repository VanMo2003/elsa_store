package com.example.elsa_store.controller;

import com.example.elsa_store.dto.common.ApiResponse;
import com.example.elsa_store.dto.request.ShippingSettingsRequest;
import com.example.elsa_store.dto.request.StoreUpdateRequest;
import com.example.elsa_store.dto.response.PageResponse;
import com.example.elsa_store.dto.response.StoreResponse;
import com.example.elsa_store.service.StoreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/stores")
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;

    /** Công khai: các cửa hàng đã được duyệt. */
    @GetMapping
    public ApiResponse<PageResponse<StoreResponse>> getAll(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(storeService.getAllPublic(page, size));
    }

    /** Cửa hàng của chủ / quản lý / nhân viên đang đăng nhập. */
    @GetMapping("/me")
    public ApiResponse<StoreResponse> getMine() {
        return ApiResponse.ok(storeService.getMine());
    }

    @PutMapping("/me")
    public ApiResponse<StoreResponse> updateMine(@Valid @RequestBody StoreUpdateRequest req) {
        return ApiResponse.ok(storeService.updateMine(req));
    }

    /** Phí giao hàng và mức miễn phí giao (chủ / quản lý). */
    @PutMapping("/me/shipping")
    public ApiResponse<StoreResponse> updateShipping(@Valid @RequestBody ShippingSettingsRequest req) {
        return ApiResponse.ok(storeService.updateShipping(req));
    }

    @GetMapping("/{id:\\d+}")
    public ApiResponse<StoreResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(storeService.getPublic(id));
    }
}
