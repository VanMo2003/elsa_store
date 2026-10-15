package com.example.elsa_store.controller;

import com.example.elsa_store.dto.common.ApiResponse;
import com.example.elsa_store.dto.response.ProductResponse;
import com.example.elsa_store.service.WishlistService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Sản phẩm yêu thích của khách đang đăng nhập. */
@RestController
@RequestMapping("/wishlist")
public class WishlistController {

    private final WishlistService wishlistService;

    public WishlistController(WishlistService wishlistService) {
        this.wishlistService = wishlistService;
    }

    /** Sản phẩm đã lưu (giá khuyến mãi, còn hàng, điểm đánh giá như danh sách sản phẩm), mới lưu trước. */
    @GetMapping
    public ApiResponse<List<ProductResponse>> list() {
        return ApiResponse.ok(wishlistService.list());
    }

    /** Chỉ id — để app tô trái tim trên mọi thẻ sản phẩm. */
    @GetMapping("/ids")
    public ApiResponse<List<Long>> ids() {
        return ApiResponse.ok(wishlistService.productIds());
    }

    @PostMapping("/{productId}")
    public ApiResponse<List<Long>> add(@PathVariable Long productId) {
        return ApiResponse.ok(wishlistService.add(productId));
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<List<Long>> remove(@PathVariable Long productId) {
        return ApiResponse.ok(wishlistService.remove(productId));
    }
}
