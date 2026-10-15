package com.example.elsa_store.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** Số liệu nhanh trang tổng quan. Doanh thu chỉ tính đơn hoàn thành, theo ngày hoàn thành. */
@Data
@Builder
public class DashboardResponse {

    private Double todayRevenue;

    private Double yesterdayRevenue;

    private Double monthRevenue;

    /** Tháng trước, tính cùng số ngày đầu tháng như tháng này (so sánh công bằng). */
    private Double lastMonthSamePeriodRevenue;

    /** Lợi nhuận gộp tháng này (doanh thu − giá vốn các món đã có giá vốn). */
    private Double monthGrossProfit;

    /** Số lượng bán trong tháng chưa có giá vốn. */
    private Long monthUncostedQuantity;

    /** Đơn chờ xác nhận. */
    private Long processingOrders;

    /** Đơn đã xác nhận, chờ giao xong. */
    private Long confirmedOrders;

    private Long completedOrders;

    /** Đơn đặt hôm nay (mọi trạng thái). */
    private Long todayOrders;

    /** Biến thể sắp hết / hết hàng (chỉ khi xem một cửa hàng). */
    private Long lowStockVariants;

    private Long outOfStockVariants;

    /** Doanh thu 7 ngày gần nhất, đủ 7 điểm (ngày không có đơn = 0). */
    private List<RevenuePointResponse> last7Days;
}
