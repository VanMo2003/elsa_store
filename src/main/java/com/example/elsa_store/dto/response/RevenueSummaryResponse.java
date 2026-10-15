package com.example.elsa_store.dto.response;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RevenueSummaryResponse {
    private LocalDate from;
    private LocalDate to;

    private Long ordersCount;

    private Double grossRevenue;   // sum(totalAmount)
    private Double discountTotal;  // sum(discountAmount)
    private Double netRevenue;     // sum(finalAmount)
    /** Phí giao hàng khách đã trả (nằm trong doanh thu thực thu): gộp − giảm + phí giao = thực thu. */
    private Double shippingTotal;  // sum(shippingFee)

    private Double averageOrderValue; // netRevenue / ordersCount
    /** Tiền hoàn cho khách trả hàng trong kỳ (theo ngày hoàn) và số yêu cầu đã hoàn. */
    private Double refundTotal;
    private Long refundCount;
    /** Doanh thu thực thu trừ tiền hoàn trả hàng. */
    private Double netAfterRefunds;
    /** Giá vốn hàng đã bán (chỉ các dòng đã có giá vốn). */
    private Double costOfGoods;
    /** Lợi nhuận gộp = doanh thu thực thu − giá vốn. */
    private Double grossProfit;
    /** Biên lợi nhuận gộp, phần trăm; null khi chưa có doanh thu. */
    private Double profitMargin;
    /** Số lượng đã bán chưa có giá vốn — lợi nhuận có thể cao hơn thực tế khi khác 0. */
    private Long uncostedQuantity;
    /** Kỳ liền trước cùng độ dài (chỉ có ở kỳ đang xem). */
    private RevenueSummaryResponse previous;
    /** Tăng trưởng so với kỳ trước, phần trăm; null khi kỳ trước bằng 0. */
    private Double revenueGrowth;
    private Double ordersGrowth;
    private Double profitGrowth;
}
