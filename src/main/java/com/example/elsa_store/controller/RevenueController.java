package com.example.elsa_store.controller;

import com.example.elsa_store.constant.RevenueGroupBy;
import com.example.elsa_store.dto.common.ApiResponse;
import com.example.elsa_store.dto.response.*;
import com.example.elsa_store.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Các đường dẫn doanh thu cũ mà app đang gọi — dùng chung {@link ReportService} nên cũng theo phạm vi cửa hàng:
 * chỉ chủ và quản lý xem được (ADMIN chỉ xem). Báo cáo đầy đủ nằm ở {@code /reports}.
 */
@RestController
@RequestMapping("/revenues")
public class RevenueController {

    private final ReportService reportService;

    public RevenueController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/summary")
    public ApiResponse<RevenueSummaryResponse> summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDate to,
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(required = false) Long storeId
    ) {
        return ApiResponse.ok(reportService.summary(from, to, statuses, storeId));
    }

    @GetMapping("/timeseries")
    public ApiResponse<RevenueTimeSeriesResponse> timeseries(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDate to,
            @RequestParam(required = false, defaultValue = "DAY") RevenueGroupBy groupBy,
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(required = false) Long storeId
    ) {
        return ApiResponse.ok(reportService.revenue(from, to, groupBy, statuses, storeId));
    }

    @GetMapping("/top-products")
    public ApiResponse<List<ProductRevenueResponse>> topProducts(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDate to,
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(required = false, defaultValue = "10") int limit,
            @RequestParam(required = false) Long storeId
    ) {
        return ApiResponse.ok(reportService.topProducts(from, to, statuses, limit, storeId));
    }

    @GetMapping("/dashboard")
    public ApiResponse<DashboardResponse> dashboard(@RequestParam(required = false) Long storeId) {
        return ApiResponse.ok(reportService.dashboard(storeId));
    }
}
