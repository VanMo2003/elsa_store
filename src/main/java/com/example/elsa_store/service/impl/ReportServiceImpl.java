package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.OrderStatus;
import com.example.elsa_store.constant.RevenueGroupBy;
import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.response.*;
import com.example.elsa_store.entity.Inventory;
import com.example.elsa_store.entity.ProductVariant;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.exception.ResourceNotFoundException;
import com.example.elsa_store.repository.InventoryRepository;
import com.example.elsa_store.repository.OrderItemRepository;
import com.example.elsa_store.repository.OrderRepository;
import com.example.elsa_store.repository.ProductVariantRepository;
import com.example.elsa_store.repository.StoreRepository;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.service.ExcelReportService;
import com.example.elsa_store.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductVariantRepository productVariantRepository;
    private final StoreRepository storeRepository;
    private final AccessGuard accessGuard;
    private final ExcelReportService excelReportService;

    // ------------------------------------------------------------------ phạm vi và tham số

    /**
     * Phạm vi báo cáo: chủ / quản lý → cửa hàng của mình; ADMIN → cửa hàng được chọn hoặc toàn hệ thống (null).
     * Nhân viên, khách và người chưa đăng nhập bị từ chối.
     */
    private Long scope(Long requestedStoreId) {
        User current = accessGuard.currentUser();
        if (current.getRole() == Role.ADMIN) {
            if (requestedStoreId != null && !storeRepository.existsById(requestedStoreId)) {
                throw new ResourceNotFoundException("Store not found with id=" + requestedStoreId);
            }
            return requestedStoreId;
        }
        Long mine = accessGuard.requireStoreManager().getId();
        if (requestedStoreId != null && !requestedStoreId.equals(mine)) {
            throw new AppException(ErrorCode.UN_AUTHORIZED);
        }
        return mine;
    }

    /** Tên enum hoặc số thứ tự (cách cũ của app: 0 = CHUA_XAC_NHAN, 1 = DA_XAC_NHAN…); trống = chỉ đơn hoàn thành. */
    static List<OrderStatus> parseStatuses(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of(OrderStatus.HOAN_THANH);
        }
        List<OrderStatus> result = new ArrayList<>();
        for (String token : raw) {
            String value = token == null ? "" : token.trim();
            if (value.isEmpty()) {
                continue;
            }
            try {
                if (value.chars().allMatch(Character::isDigit)) {
                    result.add(OrderStatus.values()[Integer.parseInt(value)]);
                } else {
                    result.add(OrderStatus.valueOf(value.toUpperCase()));
                }
            } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
                throw new AppException(ErrorCode.BAD_REQUEST, "statuses: invalid value " + value);
            }
        }
        return result.isEmpty() ? List.of(OrderStatus.HOAN_THANH) : result;
    }

    private static void checkRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "from must not be after to");
        }
    }

    private static LocalDateTime start(LocalDate from) {
        return from.atStartOfDay();
    }

    private static LocalDateTime endExclusive(LocalDate to) {
        return to.plusDays(1).atStartOfDay();
    }

    // ------------------------------------------------------------------ báo cáo

    /** Tổng hợp kỳ đang xem kèm kỳ liền trước cùng độ dài (ví dụ 01–14/10 so với 17–30/09) và mức tăng trưởng. */
    @Override
    public RevenueSummaryResponse summary(LocalDate from, LocalDate to, List<String> statuses, Long storeId) {
        checkRange(from, to);
        Long scope = scope(storeId);
        List<OrderStatus> status = parseStatuses(statuses);
        RevenueSummaryResponse current = summarize(from, to, status, scope);
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        LocalDate previousTo = from.minusDays(1);
        RevenueSummaryResponse previous = summarize(previousTo.minusDays(days - 1), previousTo, status, scope);
        current.setPrevious(previous);
        current.setRevenueGrowth(growth(current.getNetRevenue(), previous.getNetRevenue()));
        current.setOrdersGrowth(growth(current.getOrdersCount(), previous.getOrdersCount()));
        current.setProfitGrowth(growth(current.getGrossProfit(), previous.getGrossProfit()));
        return current;
    }

    private RevenueSummaryResponse summarize(LocalDate from, LocalDate to, List<OrderStatus> status, Long scope) {
        List<Object[]> rows = orderRepository.revenueSummary(start(from), endExclusive(to), status, scope);
        Object[] row = rows.isEmpty() ? new Object[] {0L, 0.0, 0.0, 0.0} : rows.get(0);
        long ordersCount = number(row[0]).longValue();
        double gross = number(row[1]).doubleValue();
        double discount = number(row[2]).doubleValue();
        double net = number(row[3]).doubleValue();
        Cost cost = cost(start(from), endExclusive(to), status, scope);
        double profit = net - cost.amount();
        return RevenueSummaryResponse.builder()
                .from(from)
                .to(to)
                .ordersCount(ordersCount)
                .grossRevenue(gross)
                .discountTotal(discount)
                .netRevenue(net)
                .averageOrderValue(ordersCount == 0 ? 0.0 : net / ordersCount)
                .costOfGoods(cost.amount())
                .grossProfit(profit)
                .profitMargin(net > 0 ? round1(profit / net * 100) : null)
                .uncostedQuantity(cost.uncostedQuantity())
                .build();
    }

    /** Giá vốn hàng bán trong khoảng: tổng giá vốn đã biết và số lượng chưa có giá vốn. */
    private record Cost(double amount, long uncostedQuantity) {
    }

    private Cost cost(LocalDateTime from, LocalDateTime to, List<OrderStatus> status, Long scope) {
        List<Object[]> rows = orderItemRepository.costSummary(from, to, status, scope);
        if (rows.isEmpty()) {
            return new Cost(0, 0);
        }
        return new Cost(number(rows.get(0)[0]).doubleValue(), number(rows.get(0)[1]).longValue());
    }

    /** Phần trăm thay đổi so với kỳ trước, một chữ số thập phân; null khi kỳ trước bằng 0 (không so được). */
    static Double growth(Number current, Number previous) {
        double before = previous == null ? 0 : previous.doubleValue();
        if (before == 0) {
            return null;
        }
        double now = current == null ? 0 : current.doubleValue();
        return round1((now - before) / Math.abs(before) * 100);
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private static Number number(Object value) {
        return value == null ? 0 : (Number) value;
    }

    @Override
    public RevenueTimeSeriesResponse revenue(
            LocalDate from, LocalDate to, RevenueGroupBy groupBy, List<String> statuses, Long storeId) {
        checkRange(from, to);
        Long scope = scope(storeId);
        RevenueGroupBy group = groupBy == null ? RevenueGroupBy.DAY : groupBy;
        LocalDateTime start = start(from);
        LocalDateTime end = endExclusive(to);
        List<OrderStatus> status = parseStatuses(statuses);
        List<RevenuePointResponse> points = new ArrayList<>();

        switch (group) {
            case DAY -> {
                for (Object[] r : orderRepository.revenueByDay(start, end, status, scope)) {
                    points.add(point(dayString(r[0]), r[1], r[2]));
                }
            }
            case WEEK -> {
                for (Object[] r : orderRepository.revenueByWeek(start, end, status, scope)) {
                    int yearWeek = ((Number) r[0]).intValue();
                    points.add(point(String.format("%04d-W%02d", yearWeek / 100, yearWeek % 100), r[1], r[2]));
                }
            }
            case MONTH -> {
                for (Object[] r : orderRepository.revenueByMonth(start, end, status, scope)) {
                    String period = String.format("%04d-%02d", ((Number) r[0]).intValue(), ((Number) r[1]).intValue());
                    points.add(point(period, r[2], r[3]));
                }
            }
            case YEAR -> {
                for (Object[] r : orderRepository.revenueByYear(start, end, status, scope)) {
                    points.add(point(String.valueOf(((Number) r[0]).intValue()), r[1], r[2]));
                }
            }
        }
        return RevenueTimeSeriesResponse.builder()
                .from(start)
                .to(end)
                .groupBy(group)
                .points(points)
                .build();
    }

    @Override
    public List<ProductRevenueResponse> topProducts(
            LocalDate from, LocalDate to, List<String> statuses, int limit, Long storeId) {
        checkRange(from, to);
        Long scope = scope(storeId);
        int size = limit <= 0 ? 10 : limit;
        List<ProductRevenueResponse> result = new ArrayList<>();
        for (Object[] r : orderItemRepository.topProductRevenue(
                start(from), endExclusive(to), parseStatuses(statuses), scope, PageRequest.of(0, size))) {
            result.add(ProductRevenueResponse.builder()
                    .productVariantId(((Number) r[0]).longValue())
                    .productName((String) r[1])
                    .imageUrl((String) r[2])
                    .quantitySold(((Number) r[3]).longValue())
                    .revenue(((Number) r[4]).doubleValue())
                    .costOfGoods(number(r[5]).doubleValue())
                    .profit(number(r[6]).longValue() == 0
                            ? ((Number) r[4]).doubleValue() - number(r[5]).doubleValue()
                            : null)
                    .uncostedQuantity(number(r[6]).longValue())
                    .build());
        }
        return result;
    }

    @Override
    public List<RevenueByCategoryResponse> revenueByCategory(
            LocalDate from, LocalDate to, List<String> statuses, Long storeId) {
        checkRange(from, to);
        Long scope = scope(storeId);
        List<RevenueByCategoryResponse> result = new ArrayList<>();
        for (Object[] r : orderItemRepository.revenueByCategory(
                start(from), endExclusive(to), parseStatuses(statuses), scope)) {
            result.add(RevenueByCategoryResponse.builder()
                    .categoryId(r[0] == null ? null : ((Number) r[0]).longValue())
                    .categoryName((String) r[1])
                    .quantitySold(((Number) r[2]).longValue())
                    .revenue(((Number) r[3]).doubleValue())
                    .build());
        }
        return result;
    }

    @Override
    public List<TopCustomerResponse> topCustomers(
            LocalDate from, LocalDate to, List<String> statuses, int limit, Long storeId) {
        checkRange(from, to);
        Long scope = scope(storeId);
        int size = limit <= 0 ? 10 : limit;
        List<TopCustomerResponse> result = new ArrayList<>();
        for (Object[] r : orderRepository.topCustomers(
                start(from), endExclusive(to), parseStatuses(statuses), scope, PageRequest.of(0, size))) {
            result.add(TopCustomerResponse.builder()
                    .customerId(((Number) r[0]).longValue())
                    .name((String) r[1])
                    .phone((String) r[2])
                    .ordersCount(((Number) r[3]).longValue())
                    .totalSpent(((Number) r[4]).doubleValue())
                    .build());
        }
        return result;
    }

    @Override
    public List<StaffPerformanceResponse> staffPerformance(
            LocalDate from, LocalDate to, List<String> statuses, Long storeId) {
        checkRange(from, to);
        Long scope = scope(storeId);
        List<StaffPerformanceResponse> result = new ArrayList<>();
        for (Object[] r : orderRepository.staffPerformance(
                start(from), endExclusive(to), parseStatuses(statuses), scope)) {
            result.add(StaffPerformanceResponse.builder()
                    .userId(((Number) r[0]).longValue())
                    .name((String) r[1])
                    .role((Role) r[2])
                    .ordersCount(((Number) r[3]).longValue())
                    .revenue(((Number) r[4]).doubleValue())
                    .build());
        }
        return result;
    }

    @Override
    public CancellationRateResponse cancellationRate(LocalDate from, LocalDate to, Long storeId) {
        checkRange(from, to);
        Long scope = scope(storeId);
        long total = orderRepository.countInRange(start(from), endExclusive(to), null, scope);
        long cancelled = orderRepository.countInRange(start(from), endExclusive(to), OrderStatus.DA_HUY, scope);
        return CancellationRateResponse.builder()
                .from(from)
                .to(to)
                .totalOrders(total)
                .cancelledOrders(cancelled)
                .rate(total == 0 ? 0.0 : (double) cancelled / total)
                .build();
    }

    @Override
    public List<LowStockResponse> lowStock(Long storeId) {
        Long scope = scope(storeId);
        List<LowStockResponse> result = new ArrayList<>();
        for (Inventory inventory : inventoryRepository.findLowStock(scope)) {
            ProductVariant v = inventory.getProductVariant();
            result.add(LowStockResponse.builder()
                    .productVariantId(v.getId())
                    .productName(v.getProduct().getName())
                    .color(v.getColor())
                    .size(v.getSize())
                    .sku(v.getSku())
                    .quantity(inventory.getQuantity())
                    .minQuantity(inventory.getMinQuantity())
                    .build());
        }
        return result;
    }

    /**
     * Tổng quan: doanh thu hôm nay / hôm qua, tháng này / cùng kỳ tháng trước, lợi nhuận gộp tháng, các đơn cần xử lý, hàng
     * sắp hết và doanh thu 7 ngày gần nhất.
     */
    @Override
    public DashboardResponse dashboard(Long storeId) {
        Long scope = scope(storeId);
        LocalDate today = LocalDate.now();
        LocalDateTime startOfToday = today.atStartOfDay();
        LocalDateTime startOfTomorrow = today.plusDays(1).atStartOfDay();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate lastMonthStart = monthStart.minusMonths(1);
        // Cùng kỳ: tháng trước tính đúng bằng số ngày đã qua của tháng này (không vượt sang tháng này).
        LocalDate lastMonthEnd = lastMonthStart.plusDays(today.getDayOfMonth());
        if (lastMonthEnd.isAfter(monthStart)) {
            lastMonthEnd = monthStart;
        }

        double monthRevenue = orderRepository.revenueBetween(monthStart.atStartOfDay(), startOfTomorrow, scope);
        Cost monthCost = cost(monthStart.atStartOfDay(), startOfTomorrow, List.of(OrderStatus.HOAN_THANH), scope);

        Long lowStock = null;
        Long outOfStock = null;
        if (scope != null) {
            List<Object[]> stock = productVariantRepository.stockOverview(scope);
            StockOverviewResponse overview = stock.isEmpty()
                    ? StockOverviewResponse.empty()
                    : StockOverviewResponse.fromRow(stock.get(0));
            lowStock = overview.getLowStockVariants();
            outOfStock = overview.getOutOfStockVariants();
        }

        return DashboardResponse.builder()
                .todayRevenue(orderRepository.revenueBetween(startOfToday, startOfTomorrow, scope))
                .yesterdayRevenue(orderRepository.revenueBetween(today.minusDays(1).atStartOfDay(), startOfToday, scope))
                .monthRevenue(monthRevenue)
                .lastMonthSamePeriodRevenue(orderRepository.revenueBetween(
                        lastMonthStart.atStartOfDay(), lastMonthEnd.atStartOfDay(), scope))
                .monthGrossProfit(monthRevenue - monthCost.amount())
                .monthUncostedQuantity(monthCost.uncostedQuantity())
                .processingOrders(countStatus(scope, OrderStatus.CHUA_XAC_NHAN))
                .confirmedOrders(countStatus(scope, OrderStatus.DA_XAC_NHAN))
                .shippingOrders(countStatus(scope, OrderStatus.DANG_GIAO))
                .completedOrders(countStatus(scope, OrderStatus.HOAN_THANH))
                .todayOrders(orderRepository.countInRange(startOfToday, startOfTomorrow, null, scope))
                .lowStockVariants(lowStock)
                .outOfStockVariants(outOfStock)
                .last7Days(lastDays(today, 7, scope))
                .build();
    }

    private long countStatus(Long scope, OrderStatus status) {
        return scope == null
                ? orderRepository.countByStatus(status)
                : orderRepository.countByStore_IdAndStatus(scope, status);
    }

    /** Doanh thu từng ngày của {@code days} ngày gần nhất (tính cả hôm nay), ngày không có đơn = 0. */
    private List<RevenuePointResponse> lastDays(LocalDate today, int days, Long scope) {
        LocalDate first = today.minusDays(days - 1L);
        Map<String, RevenuePointResponse> byDay = new HashMap<>();
        for (Object[] r : orderRepository.revenueByDay(
                first.atStartOfDay(), today.plusDays(1).atStartOfDay(), List.of(OrderStatus.HOAN_THANH), scope)) {
            RevenuePointResponse p = point(dayString(r[0]), r[1], r[2]);
            byDay.put(p.getPeriod(), p);
        }
        List<RevenuePointResponse> result = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            String day = first.plusDays(i).toString();
            result.add(byDay.getOrDefault(day, RevenuePointResponse.builder()
                    .period(day)
                    .ordersCount(0L)
                    .netRevenue(0.0)
                    .build()));
        }
        return result;
    }

    /** Doanh thu theo cách trả tiền (COD / VNPAY / khác) và kênh bán (khách tự đặt online / lập tại quầy). */
    @Override
    public RevenueBreakdownResponse breakdown(LocalDate from, LocalDate to, List<String> statuses, Long storeId) {
        checkRange(from, to);
        Long scope = scope(storeId);
        Map<String, double[]> byMethod = new LinkedHashMap<>();
        Map<String, double[]> byChannel = new LinkedHashMap<>();
        double total = 0;
        for (Object[] r : orderRepository.revenueByChannel(
                start(from), endExclusive(to), parseStatuses(statuses), scope)) {
            String method = paymentKey((String) r[0]);
            String channel = r[1] == null ? "ONLINE" : "COUNTER";
            double count = number(r[2]).doubleValue();
            double revenue = number(r[3]).doubleValue();
            total += revenue;
            add(byMethod, method, count, revenue);
            add(byChannel, channel, count, revenue);
        }
        return RevenueBreakdownResponse.builder()
                .from(from)
                .to(to)
                .byPaymentMethod(slices(byMethod, total))
                .byChannel(slices(byChannel, total))
                .build();
    }

    static String paymentKey(String method) {
        if (method == null || method.isBlank()) return "OTHER";
        String key = method.trim().toUpperCase();
        return key.equals("COD") || key.equals("VNPAY") ? key : "OTHER";
    }

    private static void add(Map<String, double[]> target, String key, double count, double revenue) {
        double[] sum = target.computeIfAbsent(key, k -> new double[2]);
        sum[0] += count;
        sum[1] += revenue;
    }

    private static List<RevenueBreakdownResponse.Slice> slices(Map<String, double[]> sums, double total) {
        List<RevenueBreakdownResponse.Slice> result = new ArrayList<>();
        sums.forEach((key, sum) -> result.add(RevenueBreakdownResponse.Slice.builder()
                .key(key)
                .ordersCount((long) sum[0])
                .revenue(sum[1])
                .share(total > 0 ? round1(sum[1] / total * 100) : 0.0)
                .build()));
        result.sort(Comparator.comparing(RevenueBreakdownResponse.Slice::getRevenue).reversed());
        return result;
    }

    @Override
    public byte[] exportExcel(LocalDate from, LocalDate to, List<String> statuses, Long storeId) {
        checkRange(from, to);
        Long scope = scope(storeId); // kiểm quyền trước khi gom dữ liệu
        StockOverviewResponse stock = null;
        if (scope != null) {
            List<Object[]> rows = productVariantRepository.stockOverview(scope);
            stock = rows.isEmpty() ? StockOverviewResponse.empty() : StockOverviewResponse.fromRow(rows.get(0));
        }
        return excelReportService.export(
                summary(from, to, statuses, storeId),
                revenue(from, to, RevenueGroupBy.DAY, statuses, storeId),
                revenueByCategory(from, to, statuses, storeId),
                topProducts(from, to, statuses, 20, storeId),
                topCustomers(from, to, statuses, 20, storeId),
                staffPerformance(from, to, statuses, storeId),
                cancellationRate(from, to, storeId),
                lowStock(storeId),
                breakdown(from, to, statuses, storeId),
                stock);
    }

    private static RevenuePointResponse point(String period, Object orders, Object revenue) {
        return RevenuePointResponse.builder()
                .period(period)
                .ordersCount(((Number) orders).longValue())
                .netRevenue(((Number) revenue).doubleValue())
                .build();
    }

    private static String dayString(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Date d) return d.toLocalDate().toString();
        if (raw instanceof LocalDate ld) return ld.toString();
        if (raw instanceof LocalDateTime ldt) return ldt.toLocalDate().toString();
        return String.valueOf(raw);
    }
}
