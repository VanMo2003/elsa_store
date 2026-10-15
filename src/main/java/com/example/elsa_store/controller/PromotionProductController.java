package com.example.elsa_store.controller;

import com.example.elsa_store.dto.common.ApiResponse;
import com.example.elsa_store.dto.request.PromotionProductRequest;
import com.example.elsa_store.dto.response.ProductResponse;
import com.example.elsa_store.dto.response.PromotionProductResponse;
import com.example.elsa_store.service.PromotionProductService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Sản phẩm của một khuyến mãi. Trước đây controller này đọc / sửa / xoá thẳng bảng liên kết mà không kiểm tra quyền —
 * ai đăng nhập cũng gắn được khuyến mãi của cửa hàng khác vào sản phẩm. Nay mọi thao tác qua service có kiểm tra chủ /
 * quản lý của cửa hàng sở hữu khuyến mãi.
 */
@RestController
@RequestMapping("/promotion-products")
public class PromotionProductController {

    private final PromotionProductService promotionProductService;

    public PromotionProductController(PromotionProductService promotionProductService) {
        this.promotionProductService = promotionProductService;
    }

    @GetMapping
    public ApiResponse<List<ProductResponse>> getByPromotion(@RequestParam Long promotionId) {
        return ApiResponse.ok(promotionProductService.products(promotionId));
    }

    @PostMapping
    public ApiResponse<PromotionProductResponse> create(@Valid @RequestBody PromotionProductRequest body) {
        return ApiResponse.ok(promotionProductService.create(body));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        promotionProductService.delete(id);
        return ApiResponse.ok(null);
    }
}
