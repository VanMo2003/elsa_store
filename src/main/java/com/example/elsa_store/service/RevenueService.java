package com.example.elsa_store.service;

import com.example.elsa_store.constant.RevenueGroupBy;
import com.example.elsa_store.dto.response.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface RevenueService {
    RevenueSummaryResponse getSummary(LocalDate from, LocalDate to, List<Integer> statuses);

    RevenueTimeSeriesResponse getTimeSeries(LocalDateTime from, LocalDateTime to, RevenueGroupBy groupBy, List<Integer> statuses);

    List<ProductRevenueResponse> getTopProducts(LocalDateTime from, LocalDateTime to, List<Integer> statuses, int limit);

    DashboardResponse getDashboard();
}
