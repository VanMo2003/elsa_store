package com.example.elsa_store.controller;

import com.example.elsa_store.constant.RevenueGroupBy;
import com.example.elsa_store.dto.common.ApiResponse;
import com.example.elsa_store.dto.response.*;
import com.example.elsa_store.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * Báo cáo doanh thu và sản phẩm. Chỉ chủ và quản lý xem được (của cửa hàng mình); ADMIN chỉ xem và có thể chọn
 * {@code storeId}. {@code statuses} nhận tên enum (HOAN_THANH, DA_XAC_NHAN…) hoặc số thứ tự.
 */
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/dashboard")
    public ApiResponse<DashboardResponse> dashboard(@RequestParam(required = false) Long storeId) {
        return ApiResponse.ok(reportService.dashboard(storeId));
    }

    @GetMapping("/summary")
    public ApiResponse<RevenueSummaryResponse> summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(required = false) Long storeId) {
        return ApiResponse.ok(reportService.summary(from, to, statuses, storeId));
    }

    @GetMapping("/revenue")
    public ApiResponse<RevenueTimeSeriesResponse> revenue(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "DAY") RevenueGroupBy groupBy,
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(required = false) Long storeId) {
        return ApiResponse.ok(reportService.revenue(from, to, groupBy, statuses, storeId));
    }

    @GetMapping("/top-products")
    public ApiResponse<List<ProductRevenueResponse>> topProducts(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) Long storeId) {
        return ApiResponse.ok(reportService.topProducts(from, to, statuses, limit, storeId));
    }

    @GetMapping("/revenue-by-category")
    public ApiResponse<List<RevenueByCategoryResponse>> revenueByCategory(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(required = false) Long storeId) {
        return ApiResponse.ok(reportService.revenueByCategory(from, to, statuses, storeId));
    }

    @GetMapping("/top-customers")
    public ApiResponse<List<TopCustomerResponse>> topCustomers(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) Long storeId) {
        return ApiResponse.ok(reportService.topCustomers(from, to, statuses, limit, storeId));
    }

    @GetMapping("/staff-performance")
    public ApiResponse<List<StaffPerformanceResponse>> staffPerformance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(required = false) Long storeId) {
        return ApiResponse.ok(reportService.staffPerformance(from, to, statuses, storeId));
    }

    @GetMapping("/cancellation-rate")
    public ApiResponse<CancellationRateResponse> cancellationRate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long storeId) {
        return ApiResponse.ok(reportService.cancellationRate(from, to, storeId));
    }

    /** Doanh thu tách theo cách trả tiền (COD / VNPAY) và kênh bán (ONLINE / COUNTER). */
    @GetMapping("/breakdown")
    public ApiResponse<RevenueBreakdownResponse> breakdown(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(required = false) Long storeId) {
        return ApiResponse.ok(reportService.breakdown(from, to, statuses, storeId));
    }

    /** ADMIN: xếp hạng các cửa hàng theo doanh thu trong kỳ (kèm số đơn, tỉ lệ huỷ, điểm đánh giá, thị phần). */
    @GetMapping("/stores")
    public ApiResponse<List<StoreRankingResponse>> storeRanking(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(reportService.storeRanking(from, to));
    }

    @GetMapping("/low-stock")
    public ApiResponse<List<LowStockResponse>> lowStock(@RequestParam(required = false) Long storeId) {
        return ApiResponse.ok(reportService.lowStock(storeId));
    }

    /** Tệp Excel nhiều sheet gồm mọi báo cáo trên. */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(required = false) Long storeId) {
        byte[] file = reportService.exportExcel(from, to, statuses, storeId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("bao-cao-" + from + "_" + to + ".xlsx", StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(file);
    }
}
