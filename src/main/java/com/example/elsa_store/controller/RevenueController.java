package com.example.elsa_store.controller;

import com.example.elsa_store.constant.RevenueGroupBy;
import com.example.elsa_store.dto.common.ApiResponse;
import com.example.elsa_store.dto.response.*;
import com.example.elsa_store.service.RevenueService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/revenues")
public class RevenueController {

    private final RevenueService revenueService;

    public RevenueController(RevenueService revenueService) {
        this.revenueService = revenueService;
    }

    @GetMapping("/summary")
    public ApiResponse<RevenueSummaryResponse> summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDate to,
            @RequestParam(required = false) List<Integer> statuses
    ) {
        return ApiResponse.ok(revenueService.getSummary(from, to, statuses));
    }

    @GetMapping("/timeseries")
    public ApiResponse<RevenueTimeSeriesResponse> timeseries(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDate to,
            @RequestParam(required = false, defaultValue = "DAY") RevenueGroupBy groupBy,
            @RequestParam(required = false) List<Integer> statuses
    ) {

        return ApiResponse.ok(revenueService.getTimeSeries(from.atStartOfDay(), to.plusDays(1).atStartOfDay(), groupBy, statuses));
    }

    @GetMapping("/top-products")
    public ApiResponse<List<ProductRevenueResponse>> topProducts(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDate to,
            @RequestParam(required = false) List<Integer> statuses,
            @RequestParam(required = false, defaultValue = "10") int limit
    ) {
        return ApiResponse.ok(revenueService.getTopProducts(from.atStartOfDay(), to.plusDays(1).atStartOfDay(), statuses, limit));
    }

    @GetMapping("/dashboard")
    public ApiResponse<DashboardResponse> dashboard() {
        return ApiResponse.ok(revenueService.getDashboard());
    }
}
