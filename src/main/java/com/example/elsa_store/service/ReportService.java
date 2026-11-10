package com.example.elsa_store.service;

import com.example.elsa_store.constant.RevenueGroupBy;
import com.example.elsa_store.dto.response.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Báo cáo doanh thu và sản phẩm. Chỉ chủ và quản lý xem được báo cáo của cửa hàng mình (nhân viên và khách thì
 * không); ADMIN chỉ xem, và có thể chọn một cửa hàng bằng {@code storeId} hoặc xem toàn hệ thống khi bỏ trống.
 *
 * <p>{@code statuses}: các trạng thái đơn được tính (tên enum hoặc số thứ tự); mặc định chỉ đơn HOAN_THANH.
 * Khoảng {@code from}..{@code to} tính cả hai đầu theo ngày.
 */
public interface ReportService {
    RevenueSummaryResponse summary(LocalDate from, LocalDate to, List<String> statuses, Long storeId);

    RevenueTimeSeriesResponse revenue(
            LocalDate from, LocalDate to, RevenueGroupBy groupBy, List<String> statuses, Long storeId);

    List<ProductRevenueResponse> topProducts(
            LocalDate from, LocalDate to, List<String> statuses, int limit, Long storeId);

    List<RevenueByCategoryResponse> revenueByCategory(
            LocalDate from, LocalDate to, List<String> statuses, Long storeId);

    List<TopCustomerResponse> topCustomers(
            LocalDate from, LocalDate to, List<String> statuses, int limit, Long storeId);

    List<StaffPerformanceResponse> staffPerformance(
            LocalDate from, LocalDate to, List<String> statuses, Long storeId);

    CancellationRateResponse cancellationRate(LocalDate from, LocalDate to, Long storeId);

    List<LowStockResponse> lowStock(Long storeId);

    /** Hàng còn tồn mà {@code days} ngày qua không bán được, vốn tồn nhiều nhất trước (tối đa {@code limit} dòng). */
    List<SlowMoverResponse> slowMovers(int days, int limit, Long storeId);

    /** Sản phẩm được xem nhiều nhất trong kỳ kèm số đơn và tỉ lệ chuyển đổi. */
    List<ProductViewStatResponse> productViews(LocalDate from, LocalDate to, int limit, Long storeId);

    /** ADMIN: xếp hạng mọi cửa hàng đã duyệt theo doanh thu trong kỳ. */
    List<StoreRankingResponse> storeRanking(LocalDate from, LocalDate to);

    DashboardResponse dashboard(Long storeId);

    /** Doanh thu tách theo cách trả tiền và kênh bán (online / tại quầy). */
    RevenueBreakdownResponse breakdown(LocalDate from, LocalDate to, List<String> statuses, Long storeId);

    /** Tệp Excel nhiều sheet gồm mọi báo cáo trên. */
    byte[] exportExcel(LocalDate from, LocalDate to, List<String> statuses, Long storeId);
}
