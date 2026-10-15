package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.OrderStatus;
import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.response.DashboardResponse;
import com.example.elsa_store.dto.response.ProductRevenueResponse;
import com.example.elsa_store.dto.response.RevenueBreakdownResponse;
import com.example.elsa_store.dto.response.RevenueSummaryResponse;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.repository.*;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.service.ExcelReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ReportServiceImplTest {

    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderItemRepository orderItems = mock(OrderItemRepository.class);
    private final InventoryRepository inventories = mock(InventoryRepository.class);
    private final ProductVariantRepository variants = mock(ProductVariantRepository.class);
    private final StoreRepository stores = mock(StoreRepository.class);
    private final AccessGuard accessGuard = mock(AccessGuard.class);
    private final ExcelReportService excel = mock(ExcelReportService.class);
    private final ReturnRequestRepository returns = mock(ReturnRequestRepository.class);
    private final ReportServiceImpl service =
            new ReportServiceImpl(orders, orderItems, inventories, variants, stores, accessGuard, excel, returns);

    private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
    private static final LocalDate TO = LocalDate.of(2026, 10, 14);

    @BeforeEach
    void setUp() {
        Store store = Store.builder().name("Store A").build();
        store.setId(1L);
        User owner = User.builder().username("owner01").role(Role.STORE_OWNER).build();
        when(accessGuard.currentUser()).thenReturn(owner);
        when(accessGuard.requireStoreManager()).thenReturn(store);
    }

    private static List<Object[]> rows(Object[]... rows) {
        return new ArrayList<>(List.of(rows));
    }

    @Test
    void growthIsAPercentageAndUndefinedWithoutABase() {
        assertEquals(50.0, ReportServiceImpl.growth(150, 100));
        assertEquals(-25.0, ReportServiceImpl.growth(150, 200));
        assertEquals(33.3, ReportServiceImpl.growth(4, 3));
        assertNull(ReportServiceImpl.growth(150, 0), "kỳ trước bằng 0 thì không so được");
        assertEquals(150.0, ReportServiceImpl.growth(50, -100), "kỳ trước lỗ, nay lãi");
    }

    @Test
    void summaryHasProfitAndComparesWithThePreviousPeriodOfTheSameLength() {
        LocalDateTime start = FROM.atStartOfDay();
        LocalDateTime end = TO.plusDays(1).atStartOfDay();
        LocalDateTime previousStart = LocalDate.of(2026, 9, 17).atStartOfDay();
        when(orders.revenueSummary(eq(start), eq(end), any(), eq(1L)))
                .thenReturn(rows(new Object[] {10L, 12_000_000.0, 2_200_000.0, 10_000_000.0, 200_000.0}));
        when(orderItems.costSummary(eq(start), eq(end), any(), eq(1L)))
                .thenReturn(rows(new Object[] {6_000_000.0, 3L}));
        when(orders.revenueSummary(eq(previousStart), eq(start), any(), eq(1L)))
                .thenReturn(rows(new Object[] {8L, 8_000_000.0, 0.0, 8_000_000.0, 0.0}));
        when(orderItems.costSummary(eq(previousStart), eq(start), any(), eq(1L)))
                .thenReturn(rows(new Object[] {5_000_000.0, 0L}));

        RevenueSummaryResponse res = service.summary(FROM, TO, null, null);

        assertEquals(10_000_000, res.getNetRevenue());
        // Gộp − giảm + phí giao = thực thu.
        assertEquals(200_000, res.getShippingTotal());
        assertEquals(6_000_000, res.getCostOfGoods());
        assertEquals(4_000_000, res.getGrossProfit());
        assertEquals(40.0, res.getProfitMargin());
        assertEquals(3L, res.getUncostedQuantity());
        assertEquals(1_000_000, res.getAverageOrderValue());

        RevenueSummaryResponse previous = res.getPrevious();
        assertEquals(LocalDate.of(2026, 9, 17), previous.getFrom());
        assertEquals(LocalDate.of(2026, 9, 30), previous.getTo());
        assertNull(previous.getPrevious());
        assertEquals(3_000_000, previous.getGrossProfit());
        assertEquals(25.0, res.getRevenueGrowth());
        assertEquals(25.0, res.getOrdersGrowth());
        assertEquals(33.3, res.getProfitGrowth());
    }

    @Test
    void emptyPeriodHasNoMarginAndNoGrowth() {
        when(orders.revenueSummary(any(), any(), any(), any())).thenReturn(rows(new Object[] {0L, null, null, null, null}));
        when(orderItems.costSummary(any(), any(), any(), any())).thenReturn(rows(new Object[] {null, null}));

        RevenueSummaryResponse res = service.summary(FROM, TO, null, null);
        assertEquals(0, res.getNetRevenue());
        assertNull(res.getProfitMargin());
        assertNull(res.getRevenueGrowth());
        assertEquals(0, res.getAverageOrderValue());
    }

    @Test
    void breakdownGroupsByPaymentMethodAndChannel() {
        when(orders.revenueByChannel(any(), any(), any(), eq(1L))).thenReturn(rows(
                new Object[] {"COD", null, 3L, 300_000.0},
                new Object[] {"COD", 8L, 2L, 100_000.0},
                new Object[] {"VNPAY", null, 1L, 600_000.0},
                new Object[] {null, 9L, 1L, 0.0}));

        RevenueBreakdownResponse res = service.breakdown(FROM, TO, null, null);

        var methods = res.getByPaymentMethod();
        assertEquals(List.of("VNPAY", "COD", "OTHER"), methods.stream().map(RevenueBreakdownResponse.Slice::getKey).toList());
        assertEquals(400_000, methods.get(1).getRevenue());
        assertEquals(5L, methods.get(1).getOrdersCount());
        assertEquals(40.0, methods.get(1).getShare());
        var channels = res.getByChannel();
        assertEquals("ONLINE", channels.get(0).getKey());
        assertEquals(900_000, channels.get(0).getRevenue());
        assertEquals(4L, channels.get(0).getOrdersCount());
        assertEquals("COUNTER", channels.get(1).getKey());
        assertEquals(10.0, channels.get(1).getShare());
    }

    @Test
    void productProfitIsShownOnlyWhenEveryUnitHasACost() {
        when(orderItems.topProductRevenue(any(), any(), any(), eq(1L), any())).thenReturn(rows(
                new Object[] {200L, "Giay", null, 4L, 2_000_000.0, 1_200_000.0, 0L},
                new Object[] {100L, "Ao", null, 3L, 300_000.0, 100_000.0, 1L}));

        List<ProductRevenueResponse> res = service.topProducts(FROM, TO, null, 10, null);
        assertEquals(800_000, res.get(0).getProfit());
        assertNull(res.get(1).getProfit());
        assertEquals(1L, res.get(1).getUncostedQuantity());
    }

    @Test
    void dashboardComparesWithYesterdayAndTheSameDaysOfLastMonth() {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate lastMonthStart = monthStart.minusMonths(1);
        LocalDate lastMonthEnd = lastMonthStart.plusDays(today.getDayOfMonth());
        if (lastMonthEnd.isAfter(monthStart)) lastMonthEnd = monthStart;
        when(orders.revenueBetween(any(), any(), eq(1L))).thenReturn(0.0);
        when(orders.revenueBetween(today.atStartOfDay(), today.plusDays(1).atStartOfDay(), 1L)).thenReturn(500_000.0);
        when(orders.revenueBetween(today.minusDays(1).atStartOfDay(), today.atStartOfDay(), 1L)).thenReturn(400_000.0);
        when(orders.revenueBetween(monthStart.atStartOfDay(), today.plusDays(1).atStartOfDay(), 1L))
                .thenReturn(9_000_000.0);
        when(orders.revenueBetween(lastMonthStart.atStartOfDay(), lastMonthEnd.atStartOfDay(), 1L))
                .thenReturn(6_000_000.0);
        when(orderItems.costSummary(any(), any(), any(), eq(1L))).thenReturn(rows(new Object[] {5_000_000.0, 2L}));
        when(orders.countByStore_IdAndStatus(1L, OrderStatus.CHUA_XAC_NHAN)).thenReturn(3L);
        when(orders.countByStore_IdAndStatus(1L, OrderStatus.DA_XAC_NHAN)).thenReturn(2L);
        when(variants.stockOverview(1L)).thenReturn(rows(new Object[] {20L, 5L, 1L, 4L, 100L, 1.0, 0L, 2.0}));
        when(orders.revenueByDay(any(), any(), any(), eq(1L)))
                .thenReturn(rows(new Object[] {Date.valueOf(today.minusDays(2)), 2L, 700_000.0}));

        DashboardResponse res = service.dashboard(null);

        assertEquals(500_000, res.getTodayRevenue());
        assertEquals(400_000, res.getYesterdayRevenue());
        assertEquals(9_000_000, res.getMonthRevenue());
        assertEquals(6_000_000, res.getLastMonthSamePeriodRevenue());
        assertEquals(4_000_000, res.getMonthGrossProfit());
        assertEquals(2L, res.getMonthUncostedQuantity());
        assertEquals(3L, res.getProcessingOrders());
        assertEquals(2L, res.getConfirmedOrders());
        assertEquals(4L, res.getLowStockVariants());
        assertEquals(1L, res.getOutOfStockVariants());
        assertEquals(7, res.getLast7Days().size());
        assertEquals(today.minusDays(6).toString(), res.getLast7Days().getFirst().getPeriod());
        assertEquals(today.toString(), res.getLast7Days().getLast().getPeriod());
        assertEquals(700_000, res.getLast7Days().get(4).getNetRevenue());
        assertEquals(0, res.getLast7Days().get(5).getNetRevenue(), "ngày không có đơn vẫn có điểm 0");
    }

    @Test
    void paymentKeysAreNormalised() {
        assertEquals("COD", ReportServiceImpl.paymentKey(" cod "));
        assertEquals("VNPAY", ReportServiceImpl.paymentKey("VNPAY"));
        assertEquals("OTHER", ReportServiceImpl.paymentKey(null));
        assertEquals("OTHER", ReportServiceImpl.paymentKey("MOMO"));
    }
}
