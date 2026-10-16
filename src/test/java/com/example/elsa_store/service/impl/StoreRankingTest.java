package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.ApprovalStatus;
import com.example.elsa_store.dto.response.StoreRankingResponse;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.repository.*;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.service.ExcelReportService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Xếp hạng cửa hàng (ADMIN): theo doanh thu kỳ, gồm cả cửa hàng chưa bán được gì; thị phần, tỉ lệ huỷ, điểm đánh giá. */
class StoreRankingTest {

    private final OrderRepository orders = mock(OrderRepository.class);
    private final StoreRepository stores = mock(StoreRepository.class);
    private final AccessGuard accessGuard = mock(AccessGuard.class);
    private final ProductReviewRepository reviews = mock(ProductReviewRepository.class);
    private final ReportServiceImpl service = new ReportServiceImpl(orders, mock(OrderItemRepository.class),
            mock(InventoryRepository.class), mock(ProductVariantRepository.class), stores, accessGuard,
            mock(ExcelReportService.class), mock(ReturnRequestRepository.class), reviews);

    private static Store store(long id, String name) {
        Store s = Store.builder().name(name).approvalStatus(ApprovalStatus.APPROVED).build();
        s.setId(id);
        return s;
    }

    @Test
    void ranksByRevenueWithSharesCancelRatesAndRatings() {
        LocalDateTime start = LocalDate.of(2026, 10, 1).atStartOfDay();
        LocalDateTime end = LocalDate.of(2026, 10, 17).atStartOfDay();
        when(stores.findAllByApprovalStatus(eq(ApprovalStatus.APPROVED), any()))
                .thenReturn(new PageImpl<>(List.of(store(1, "Ít"), store(2, "Nhiều"), store(3, "Chưa bán"))));
        when(orders.revenueByStore(start, end)).thenReturn(List.of(
                new Object[] {1L, 1_000_000.0, 2L}, new Object[] {2L, 3_000_000.0, 5L}));
        when(orders.placedAndCancelledByStore(start, end)).thenReturn(List.of(
                new Object[] {1L, 4L, 2L}, new Object[] {2L, 5L, 0L}));
        when(reviews.statsByStore()).thenReturn(List.<Object[]>of(new Object[] {2L, 4.666, 3L}));

        List<StoreRankingResponse> rows = service.storeRanking(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 16));

        assertEquals(List.of("Nhiều", "Ít", "Chưa bán"), rows.stream().map(StoreRankingResponse::name).toList());
        assertEquals(List.of(1, 2, 3), rows.stream().map(StoreRankingResponse::rank).toList());
        StoreRankingResponse top = rows.get(0);
        assertEquals(0.75, top.revenueShare());
        assertEquals(5, top.completedOrders());
        assertEquals(4.7, top.ratingAverage());
        assertEquals(3, top.ratingCount());
        assertEquals(0.5, rows.get(1).cancelRate());
        assertNull(rows.get(1).ratingAverage());
        StoreRankingResponse idle = rows.get(2);
        assertEquals(0, idle.revenue());
        assertEquals(0, idle.cancelRate());
        verify(accessGuard).requireAdmin();
    }

    @Test
    void onlyAdminsAndValidRanges() {
        when(accessGuard.requireAdmin()).thenThrow(new AppException(ErrorCode.UN_AUTHORIZED));
        assertThrows(AppException.class, () -> service.storeRanking(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2)));
        assertThrows(AppException.class, () -> service.storeRanking(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 2)));
        verifyNoInteractions(orders);
    }
}
