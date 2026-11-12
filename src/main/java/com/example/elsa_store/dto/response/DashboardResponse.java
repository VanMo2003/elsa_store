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

    /** Đơn đã xác nhận, đang chuẩn bị (chưa giao cho đơn vị vận chuyển). */
    private Long confirmedOrders;

    /** Đơn đang giao (đã giao cho đơn vị vận chuyển, chờ khách nhận). */
    private Long shippingOrders;

    private Long completedOrders;

    /** Yêu cầu trả hàng chờ duyệt (khi xem một cửa hàng). */
    private Long pendingReturns;

    /** Đánh giá của khách chưa được trả lời (khi xem một cửa hàng). */
    private Long unrepliedReviews;

    /** ADMIN xem toàn sàn: báo cáo vi phạm chờ xử lý. */
    private Long pendingViolations;

    /** Cửa hàng đang xem bị ADMIN đình chỉ: lý do (null = đang hoạt động). */
    private String suspendReason;

    /** Câu hỏi của khách chưa được trả lời (khi xem một cửa hàng). */
    private Long unansweredQuestions;

    /** Đơn đặt hôm nay (mọi trạng thái). */
    private Long todayOrders;

    /** Biến thể sắp hết / hết hàng (chỉ khi xem một cửa hàng). */
    private Long lowStockVariants;

    private Long outOfStockVariants;

    /** Doanh thu 7 ngày gần nhất, đủ 7 điểm (ngày không có đơn = 0). */
    private List<RevenuePointResponse> last7Days;

    /** Mục tiêu doanh thu tháng của cửa hàng (null = chưa đặt / đang xem toàn sàn). */
    private Double monthTarget;

    /** Dự báo doanh thu cả tháng theo tốc độ từ đầu tháng tới nay (doanh thu / số ngày đã qua × số ngày của tháng). */
    private Double monthForecast;

    /** Số ngày còn lại của tháng, tính cả hôm nay. */
    private Integer daysLeftInMonth;

    /** Mỗi ngày còn lại cần bán bao nhiêu để đạt mục tiêu (0 khi đã đạt; null khi chưa đặt mục tiêu). */
    private Double neededPerDay;
}
