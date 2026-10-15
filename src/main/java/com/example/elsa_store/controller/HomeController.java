package com.example.elsa_store.controller;

import com.example.elsa_store.dto.common.ApiResponse;
import com.example.elsa_store.dto.response.HomeHighlightsResponse;
import com.example.elsa_store.service.HomeHighlightsService;
import org.springframework.web.bind.annotation.*;

/** Dữ liệu trang chủ (công khai). */
@RestController
@RequestMapping("/home")
public class HomeController {

    private final HomeHighlightsService highlightsService;

    public HomeController(HomeHighlightsService highlightsService) {
        this.highlightsService = highlightsService;
    }

    /** Sản phẩm đang giảm giá và bán chạy. */
    @GetMapping("/highlights")
    public ApiResponse<HomeHighlightsResponse> highlights(@RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(highlightsService.highlights(size));
    }
}
