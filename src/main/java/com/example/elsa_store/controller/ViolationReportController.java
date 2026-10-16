package com.example.elsa_store.controller;

import com.example.elsa_store.constant.ViolationStatus;
import com.example.elsa_store.dto.common.ApiResponse;
import com.example.elsa_store.dto.request.ViolationHandleRequest;
import com.example.elsa_store.dto.request.ViolationReportRequest;
import com.example.elsa_store.dto.response.PageResponse;
import com.example.elsa_store.dto.response.ViolationReportResponse;
import com.example.elsa_store.service.ViolationReportService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** Báo cáo vi phạm: khách gửi về một sản phẩm; ADMIN xem và xử lý. */
@RestController
public class ViolationReportController {

    private final ViolationReportService service;

    public ViolationReportController(ViolationReportService service) {
        this.service = service;
    }

    @PostMapping("/products/{productId}/violations")
    public ApiResponse<ViolationReportResponse> report(@PathVariable Long productId,
                                                       @Valid @RequestBody ViolationReportRequest body) {
        return ApiResponse.ok(service.report(productId, body));
    }

    @GetMapping("/violations")
    public ApiResponse<PageResponse<ViolationReportResponse>> list(
            @RequestParam(required = false) ViolationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.list(status, page, size));
    }

    @PutMapping("/violations/{id}")
    public ApiResponse<ViolationReportResponse> handle(@PathVariable Long id,
                                                       @Valid @RequestBody ViolationHandleRequest body) {
        return ApiResponse.ok(service.handle(id, body));
    }
}
