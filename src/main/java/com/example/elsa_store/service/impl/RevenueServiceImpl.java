package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.OrderStatus;
import com.example.elsa_store.constant.RevenueGroupBy;
import com.example.elsa_store.dto.response.*;
import com.example.elsa_store.repository.OrderItemRepository;
import com.example.elsa_store.repository.OrderRepository;
import com.example.elsa_store.service.RevenueService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class RevenueServiceImpl implements RevenueService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public RevenueServiceImpl(OrderRepository orderRepository, OrderItemRepository orderItemRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
    }

    private List<Integer> normalizeStatuses(List<Integer> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return List.of(OrderStatus.HOAN_THANH.ordinal());
        }
        return statuses;
    }

    @Override
    public RevenueSummaryResponse getSummary(LocalDate from, LocalDate to, List<Integer> statuses) {
        statuses = normalizeStatuses(statuses);

        Object[] row = orderRepository.revenueSummary(from.atStartOfDay(), to.plusDays(1).atStartOfDay(), statuses);
        if (row != null && row.length == 1 && row[0] instanceof Object[]) {
            row = (Object[]) row[0];
        }
        long ordersCount = ((Number) row[0]).longValue();
        double gross = ((Number) row[1]).doubleValue();
        double discount = ((Number) row[2]).doubleValue();
        double net = ((Number) row[3]).doubleValue();

        double aov = (ordersCount == 0) ? 0.0 : (net / ordersCount);

        return RevenueSummaryResponse.builder()
                .from(from)
                .to(to)
                .ordersCount(ordersCount)
                .grossRevenue(gross)
                .discountTotal(discount)
                .netRevenue(net)
                .averageOrderValue(aov)
                .build();
    }

    @Override
    public RevenueTimeSeriesResponse getTimeSeries(LocalDateTime from, LocalDateTime to, RevenueGroupBy groupBy, List<Integer> statuses) {
        statuses = normalizeStatuses(statuses);
        if (groupBy == null) groupBy = RevenueGroupBy.DAY;

        List<RevenuePointResponse> points = new ArrayList<>();

        if (groupBy == RevenueGroupBy.DAY) {
            List<Object[]> rows = orderRepository.revenueByDay(from, to, statuses);
            for (Object[] r : rows) {
                String period = toDayString(r[0]);
                points.add(RevenuePointResponse.builder()
                        .period(period)
                        .ordersCount(((Number) r[1]).longValue())
                        .netRevenue(((Number) r[2]).doubleValue())
                        .build());
            }
        } else if (groupBy == RevenueGroupBy.MONTH) {
            List<Object[]> rows = orderRepository.revenueByMonth(from, to, statuses);
            for (Object[] r : rows) {
                int year = ((Number) r[0]).intValue();
                int month = ((Number) r[1]).intValue();
                String period = String.format("%04d-%02d", year, month);

                points.add(RevenuePointResponse.builder()
                        .period(period)
                        .ordersCount(((Number) r[2]).longValue())
                        .netRevenue(((Number) r[3]).doubleValue())
                        .build());
            }
        } else {
            List<Object[]> rows = orderRepository.revenueByYear(from, to, statuses);
            for (Object[] r : rows) {
                String period = String.valueOf(((Number) r[0]).intValue());
                points.add(RevenuePointResponse.builder()
                        .period(period)
                        .ordersCount(((Number) r[1]).longValue())
                        .netRevenue(((Number) r[2]).doubleValue())
                        .build());
            }
        }

        return RevenueTimeSeriesResponse.builder()
                .from(from)
                .to(to)
                .groupBy(groupBy)
                .points(points)
                .build();
    }

    @Override
    public List<ProductRevenueResponse> getTopProducts(LocalDateTime from, LocalDateTime to, List<Integer> statuses, int limit) {
        statuses = normalizeStatuses(statuses);
        if (limit <= 0) limit = 10;

        List<Object[]> rows = orderItemRepository.topProductRevenue(from, to, statuses, PageRequest.of(0, limit));
        List<ProductRevenueResponse> result = new ArrayList<>();

        for (Object[] r : rows) {
            result.add(ProductRevenueResponse.builder()
                    .productVariantId(((Number) r[0]).longValue())
                    .productName((String) r[1])
                    .imageUrl((String) r[2])
                    .quantitySold(((Number) r[3]).longValue())
                    .revenue(((Number) r[4]).doubleValue())
                    .build());
        }
        return result;
    }

    private String toDayString(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Date d) return d.toLocalDate().toString();
        if (raw instanceof java.time.LocalDate ld) return ld.toString();
        if (raw instanceof java.time.LocalDateTime ldt) return ldt.toLocalDate().toString();
        return String.valueOf(raw);
    }
}
