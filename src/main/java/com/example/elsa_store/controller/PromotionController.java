
package com.example.elsa_store.controller;

import com.example.elsa_store.dto.request.PromotionRequest;
import com.example.elsa_store.dto.response.ProductResponse;
import com.example.elsa_store.dto.response.PromotionResponse;
import com.example.elsa_store.service.PromotionProductService;
import com.example.elsa_store.service.PromotionService;
import jakarta.validation.Valid;
import com.example.elsa_store.dto.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/promotions")
public class PromotionController {

    private final PromotionService promotionService;
    private final PromotionProductService promotionProductService;

    public PromotionController(PromotionService promotionService, PromotionProductService promotionProductService) {
        this.promotionService = promotionService;
        this.promotionProductService = promotionProductService;
    }

    @PostMapping
    public ApiResponse<PromotionResponse> create(@Valid @RequestBody PromotionRequest req) {
        return ApiResponse.ok(promotionService.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<PromotionResponse> update(@PathVariable Long id,
                                                    @Valid @RequestBody PromotionRequest req) {
        return ApiResponse.ok(promotionService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        promotionService.delete(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}")
    public ApiResponse<PromotionResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(promotionService.getById(id));
    }

    @GetMapping
    public ApiResponse<List<PromotionResponse>> getAll() {
        return ApiResponse.ok(promotionService.getAll());
    }

    /** Thay danh sách sản phẩm được áp khuyến mãi; danh sách rỗng = áp cho cả đơn. Chủ / quản lý. */
    @PutMapping("/{id}/products")
    public ApiResponse<List<ProductResponse>> setProducts(
            @PathVariable Long id, @RequestBody PromotionProductsRequest req) {
        return ApiResponse.ok(promotionProductService.setProducts(id, req.productIds()));
    }

    /** Mã giảm giá đang chạy của một cửa hàng — khách xem ở bước thanh toán. */
    @GetMapping("/coupons")
    public ApiResponse<List<PromotionResponse>> coupons(@RequestParam Long storeId) {
        return ApiResponse.ok(promotionService.activeCoupons(storeId));
    }

    public record PromotionProductsRequest(List<Long> productIds) {}
}
