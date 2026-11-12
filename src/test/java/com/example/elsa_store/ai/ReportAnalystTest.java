package com.example.elsa_store.ai;

import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.response.CancellationRateResponse;
import com.example.elsa_store.dto.response.DashboardResponse;
import com.example.elsa_store.dto.response.LowStockResponse;
import com.example.elsa_store.dto.response.ProductRevenueResponse;
import com.example.elsa_store.dto.response.ReportInsightResponse;
import com.example.elsa_store.dto.response.RevenueByCategoryResponse;
import com.example.elsa_store.dto.response.RevenueSummaryResponse;
import com.example.elsa_store.dto.response.SalesByTimeResponse;
import com.example.elsa_store.dto.response.SlowMoverResponse;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.service.ReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** AI nhận xét báo cáo: bảng số đưa cho mô hình, nhận xét theo quy tắc, đọc kết quả công cụ và dự phòng khi AI lỗi. */
class ReportAnalystTest {

    private static final LocalDate FROM = LocalDate.of(2026, 11, 1);
    private static final LocalDate TO = LocalDate.of(2026, 11, 12);

    private static RevenueSummaryResponse summary(long orders, double revenue, Double growth) {
        return RevenueSummaryResponse.builder()
                .ordersCount(orders)
                .netRevenue(revenue)
                .averageOrderValue(orders == 0 ? 0 : revenue / orders)
                .discountTotal(150_000.0)
                .grossProfit(revenue * 0.4)
                .profitMargin(orders == 0 ? null : 40.0)
                .uncostedQuantity(3L)
                .revenueGrowth(growth)
                .ordersGrowth(growth)
                .previous(RevenueSummaryResponse.builder().ordersCount(10L).netRevenue(8_000_000.0).build())
                .build();
    }

    private static ReportAnalyst.Facts facts(RevenueSummaryResponse summary, boolean busy) {
        var peak = new SalesByTimeResponse.Cell(6, 20, 7, 3_150_000);
        return new ReportAnalyst.Facts(
                FROM,
                TO,
                false,
                summary,
                busy ? List.of(ProductRevenueResponse.builder().productName("Giày chạy bộ Elsa").quantitySold(9L)
                        .revenue(4_050_000.0).build()) : List.of(),
                busy ? List.of(
                        RevenueByCategoryResponse.builder().categoryName("Giày").revenue(4_500_000.0).build(),
                        RevenueByCategoryResponse.builder().categoryName("Túi").revenue(1_500_000.0).build()) : List.of(),
                CancellationRateResponse.builder().totalOrders(20L).cancelledOrders(busy ? 4L : 0L).rate(busy ? 0.2 : 0.0).build(),
                busy ? new SalesByTimeResponse(List.of(peak), List.of(), List.of(), 20, peak, 20, 6)
                        : new SalesByTimeResponse(List.of(), List.of(), List.of(), 0, null, null, null),
                busy ? List.of(LowStockResponse.builder().productName("Túi tote").color("Be").size("").quantity(2).build()) : List.of(),
                busy ? List.of(new SlowMoverResponse(1L, 2L, "Áo khoác dạ", null, "Đen", "M", "AK", 5, 400_000.0, 2_000_000,
                        null, null, false)) : List.of(),
                DashboardResponse.builder().monthRevenue(6_000_000.0).monthForecast(15_000_000.0).monthTarget(20_000_000.0)
                        .neededPerDay(736_842.0).daysLeftInMonth(19).build());
    }

    @Test
    void factSheetHasTheRealNumbersInVietnameseFormat() {
        String sheet = ReportAnalyst.factSheet(facts(summary(20, 6_000_000, -25.0), true));
        assertTrue(sheet.contains("Kỳ báo cáo: 01/11/2026 – 12/11/2026 (12 ngày)"), sheet);
        assertTrue(sheet.contains("Doanh thu thực thu: 6.000.000đ (kỳ trước cùng độ dài: 8.000.000đ, giảm 25%)"), sheet);
        assertTrue(sheet.contains("1. Giày chạy bộ Elsa — 9 sản phẩm, 4.050.000đ"), sheet);
        assertTrue(sheet.contains("Giày: 4.500.000đ (75%)"), sheet);
        assertTrue(sheet.contains("tỉ lệ huỷ 20%"), sheet);
        assertTrue(sheet.contains("đông nhất thứ bảy 20:00–21:00 (7 đơn)"), sheet);
        assertTrue(sheet.contains("Sắp hết hàng: 1 mẫu, ví dụ Túi tote (Be) còn 2"), sheet);
        assertTrue(sheet.contains("vốn tồn 2.000.000đ"), sheet);
        assertTrue(sheet.contains("mục tiêu 20.000.000đ, cần khoảng 736.842đ mỗi ngày trong 19 ngày còn lại"), sheet);
    }

    @Test
    void rulesSummariseAndSuggestActionsFromTheNumbers() {
        ReportAnalyst.Insight insight = ReportAnalyst.rules(facts(summary(20, 6_000_000, -25.0), true));
        assertEquals("Từ 01/11/2026 đến 12/11/2026 có 20 đơn hoàn thành, doanh thu 6.000.000đ, giảm 25% so với kỳ trước."
                + " Lợi nhuận gộp 2.400.000đ, biên 40%.", insight.summary());
        assertEquals("Bán chạy nhất: Giày chạy bộ Elsa (9 sản phẩm, 4.050.000đ).", insight.highlights().get(0));
        assertTrue(insight.highlights().contains("Khách đặt nhiều nhất vào thứ bảy 20:00–21:00 (7 đơn)."), insight.highlights().toString());
        assertEquals(ReportAnalyst.MAX_ITEMS, insight.actions().size());
        assertEquals("Nhập thêm 1 mẫu sắp hết, trước tiên là Túi tote (Be) còn 2.", insight.actions().get(0));
        assertTrue(insight.actions().get(1).startsWith("Xem lại lý do huỷ đơn: tỉ lệ huỷ 20% (4/20 đơn)"));
        assertTrue(insight.actions().get(2).contains("cần khoảng 736.842đ mỗi ngày trong 19 ngày còn lại"));
        assertTrue(insight.actions().get(3).contains("Áo khoác dạ"));
    }

    @Test
    void emptyPeriodSaysSoAndSuggestsBringingCustomersBack() {
        ReportAnalyst.Insight insight = ReportAnalyst.rules(facts(summary(0, 0, null), false));
        assertEquals("Từ 01/11/2026 đến 12/11/2026 chưa có đơn hoàn thành nào. Có 20 đơn được đặt nhưng chưa hoàn thành"
                + " hoặc đã huỷ.", insight.summary());
        assertTrue(insight.highlights().isEmpty());
        assertTrue(insight.actions().contains("Tạo mã giảm giá hoặc banner để kéo khách quay lại."), insight.actions().toString());
    }

    @Test
    void parseDropsBulletsCapsItemsAndRejectsAMissingSummary() throws Exception {
        var json = new ObjectMapper();
        var insight = ReportAnalyst.parse(json.readTree(
                "{\"summary\":\" **Tốt** \",\"highlights\":[\"- a\",\"2. b\",\"\",\"c\",\"d\",\"e\"],\"actions\":[\"• Nhập hàng\"]}"));
        assertEquals("Tốt", insight.summary());
        assertEquals(List.of("a", "b", "c", "d"), insight.highlights());
        assertEquals(List.of("Nhập hàng"), insight.actions());
        assertNull(ReportAnalyst.parse(json.readTree("{\"highlights\":[\"a\"]}")));
    }

    // ---------- gọi qua dịch vụ ----------

    private static ReportService reports(RevenueSummaryResponse summary) {
        ReportAnalyst.Facts f = facts(summary, true);
        ReportService reports = mock(ReportService.class);
        when(reports.summary(eq(FROM), eq(TO), isNull(), isNull())).thenReturn(summary);
        when(reports.topProducts(eq(FROM), eq(TO), isNull(), anyInt(), isNull())).thenReturn(f.topProducts());
        when(reports.revenueByCategory(eq(FROM), eq(TO), isNull(), isNull())).thenReturn(f.categories());
        when(reports.cancellationRate(FROM, TO, null)).thenReturn(f.cancellation());
        when(reports.salesByTime(FROM, TO, null)).thenReturn(f.salesByTime());
        when(reports.lowStock(null)).thenReturn(f.lowStock());
        when(reports.slowMovers(anyInt(), anyInt(), isNull())).thenReturn(f.slowMovers());
        when(reports.dashboard(null)).thenReturn(f.month());
        return reports;
    }

    private static AccessGuard owner() {
        AccessGuard guard = mock(AccessGuard.class);
        User user = new User();
        user.setId(7L);
        user.setRole(Role.STORE_OWNER);
        when(guard.currentUser()).thenReturn(user);
        return guard;
    }

    @Test
    void withoutAnApiKeyTheRulesAnswer() {
        AiClient off = new AiClient(new ObjectMapper(), "", "", "", "", "", "", "", "", "", 5);
        var analyst = new ReportAnalyst(off, reports(summary(20, 6_000_000, -25.0)), new AiRateLimiter(), owner());
        ReportInsightResponse out = analyst.analyse(FROM, TO, null);
        assertFalse(out.ai());
        assertTrue(out.notice().contains("chưa bật trợ lý AI"));
        assertTrue(out.summary().startsWith("Từ 01/11/2026 đến 12/11/2026 có 20 đơn"));
    }

    @Test
    void theModelWritesTheInsightFromTheDataSheet() throws Exception {
        try (var stub = new StubModelServer()) {
            stub.toolCall("write_insights", "{\"summary\":\"Doanh thu giảm 25% còn 6.000.000đ.\","
                    + "\"highlights\":[\"Giày chạy bộ Elsa bán chạy nhất.\"],"
                    + "\"actions\":[\"Nhập thêm Túi tote.\",\"Chạy khuyến mãi tối thứ bảy.\"]}");
            var analyst = new ReportAnalyst(stub.client(), reports(summary(20, 6_000_000, -25.0)), new AiRateLimiter(), owner());
            ReportInsightResponse out = analyst.analyse(FROM, TO, null);
            assertTrue(out.ai());
            assertNull(out.notice());
            assertEquals("Doanh thu giảm 25% còn 6.000.000đ.", out.summary());
            assertEquals(List.of("Nhập thêm Túi tote.", "Chạy khuyến mãi tối thứ bảy."), out.actions());
            String request = stub.requests.get(0).toString();
            assertTrue(request.contains("write_insights"), request);
            assertTrue(request.contains("\"tool_choice\":\"required\""), request);
            assertTrue(request.contains("Doanh thu thực thu: 6.000.000đ"), request);
        }
    }

    @Test
    void plainJsonTextFromTheModelIsAcceptedToo() throws Exception {
        try (var stub = new StubModelServer()) {
            stub.text("```json\n{\"summary\":\"Kỳ này ổn.\",\"highlights\":[],\"actions\":[\"Giữ nhịp.\"]}\n```");
            var analyst = new ReportAnalyst(stub.client(), reports(summary(20, 6_000_000, 5.0)), new AiRateLimiter(), owner());
            ReportInsightResponse out = analyst.analyse(FROM, TO, null);
            assertTrue(out.ai());
            assertEquals("Kỳ này ổn.", out.summary());
        }
    }

    @Test
    void aModelFailureFallsBackToTheRules() throws Exception {
        try (var stub = new StubModelServer()) {
            stub.then(400, "{\"error\":{\"message\":\"bad\"}}");
            var analyst = new ReportAnalyst(stub.client(), reports(summary(20, 6_000_000, -25.0)), new AiRateLimiter(), owner());
            ReportInsightResponse out = analyst.analyse(FROM, TO, null);
            assertFalse(out.ai());
            assertTrue(out.notice().contains("đang bận"));
            assertTrue(out.summary().contains("giảm 25%"));
        }
    }

    @Test
    void tooManyRequestsUseTheRulesWithoutCallingTheModel() throws Exception {
        try (var stub = new StubModelServer()) {
            AiRateLimiter limiter = new AiRateLimiter();
            for (int i = 0; i < 10; i++) {
                limiter.check("report-insight:7", 10, java.time.Duration.ofMinutes(10));
            }
            var analyst = new ReportAnalyst(stub.client(), reports(summary(20, 6_000_000, -25.0)), limiter, owner());
            ReportInsightResponse out = analyst.analyse(FROM, TO, null);
            assertFalse(out.ai());
            assertTrue(out.notice().contains("nhiều lần"));
            assertTrue(stub.requests.isEmpty());
        }
    }

    @Test
    void noReportAccessMeansNoInsight() {
        ReportService reports = mock(ReportService.class);
        when(reports.summary(any(), any(), any(), any())).thenThrow(new AppException(ErrorCode.UN_AUTHORIZED));
        AiClient off = new AiClient(new ObjectMapper(), "", "", "", "", "", "", "", "", "", 5);
        var analyst = new ReportAnalyst(off, reports, new AiRateLimiter(), owner());
        assertThrows(AppException.class, () -> analyst.analyse(FROM, TO, null));
        verify(reports, never()).dashboard(any());
    }
}
