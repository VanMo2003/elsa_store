package com.example.elsa_store.controller;

import com.example.elsa_store.dto.common.ApiResponse;
import com.example.elsa_store.dto.response.HomeHighlightsResponse;
import com.example.elsa_store.dto.response.ProductResponse;
import com.example.elsa_store.service.HomeHighlightsService;
import com.example.elsa_store.service.impl.ForYouRecommendations;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Dữ liệu trang chủ (công khai). */
@RestController
@RequestMapping("/home")
public class HomeController {

    private final HomeHighlightsService highlightsService;
    private final ForYouRecommendations forYou;

    public HomeController(HomeHighlightsService highlightsService, ForYouRecommendations forYou) {
        this.highlightsService = highlightsService;
        this.forYou = forYou;
    }

    /**
     * "Gợi ý cho bạn" (công khai; đăng nhập khách thì dùng thêm sản phẩm đã thả tim / đã mua). {@code recent}: id sản
     * phẩm vừa xem lưu trên máy, mới nhất trước.
     */
    @GetMapping("/for-you")
    public ApiResponse<List<ProductResponse>> forYou(@RequestParam(required = false) List<Long> recent,
                                                     @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(forYou.forYou(recent, size));
    }

    /** Sản phẩm đang giảm giá và bán chạy. */
    @GetMapping("/highlights")
    public ApiResponse<HomeHighlightsResponse> highlights(@RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(highlightsService.highlights(size));
    }
}
