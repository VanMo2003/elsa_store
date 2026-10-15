package com.example.elsa_store.service;

import com.example.elsa_store.dto.response.*;

import java.util.List;

public interface ExcelReportService {
    /** Dựng tệp .xlsx nhiều sheet từ các báo cáo đã tính sẵn. {@code stockOverview} null = không có sheet kho. */
    byte[] export(
            RevenueSummaryResponse summary,
            RevenueTimeSeriesResponse revenue,
            List<RevenueByCategoryResponse> byCategory,
            List<ProductRevenueResponse> topProducts,
            List<TopCustomerResponse> topCustomers,
            List<StaffPerformanceResponse> staff,
            CancellationRateResponse cancellation,
            List<LowStockResponse> lowStock,
            RevenueBreakdownResponse breakdown,
            StockOverviewResponse stockOverview);
}
