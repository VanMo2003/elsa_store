package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.response.ProductViewStatResponse;
import com.example.elsa_store.entity.Product;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.repository.*;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.service.ExcelReportService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Lượt xem sản phẩm: chỉ khách / người chưa đăng nhập được tính; báo cáo ghép lượt xem với số đơn và tính chuyển đổi. */
class ProductViewsTest {

    private final ProductViewDayRepository views = mock(ProductViewDayRepository.class);
    private final AccessGuard accessGuard = mock(AccessGuard.class);

    @Test
    void onlyGuestsAndCustomersAreCounted() {
        ProductViewCounter counter = new ProductViewCounter(views, accessGuard);
        when(accessGuard.currentUserOrEmpty()).thenReturn(Optional.empty());
        counter.record(10L, 3L);
        when(accessGuard.currentUserOrEmpty()).thenReturn(Optional.of(User.builder().role(Role.USER).build()));
        counter.record(10L, 3L);
        when(accessGuard.currentUserOrEmpty()).thenReturn(Optional.of(User.builder().role(Role.STAFF).build()));
        counter.record(10L, 3L);
        when(accessGuard.currentUserOrEmpty()).thenReturn(Optional.of(User.builder().role(Role.ADMIN).build()));
        counter.record(10L, 3L);

        verify(views, times(2)).increment(eq(10L), eq(3L), any());
    }

    @Test
    void aFailingCounterNeverBreaksTheProductPage() {
        when(accessGuard.currentUserOrEmpty()).thenReturn(Optional.empty());
        when(views.increment(any(), any(), any())).thenThrow(new IllegalStateException("db down"));
        assertDoesNotThrow(() -> new ProductViewCounter(views, accessGuard).record(10L, 3L));
    }

    @Test
    void reportJoinsViewsWithOrdersAndComputesConversion() {
        OrderItemRepository orderItems = mock(OrderItemRepository.class);
        ProductRepository products = mock(ProductRepository.class);
        ReportServiceImpl service = new ReportServiceImpl(mock(OrderRepository.class), orderItems,
                mock(InventoryRepository.class), mock(ProductVariantRepository.class), mock(StoreRepository.class),
                accessGuard, mock(ExcelReportService.class), mock(ReturnRequestRepository.class),
                mock(ProductReviewRepository.class));
        service.setViewRepositories(views, products);
        Store mine = Store.builder().name("A").build();
        mine.setId(3L);
        when(accessGuard.currentUser()).thenReturn(User.builder().role(Role.STORE_OWNER).build());
        when(accessGuard.requireStoreManager()).thenReturn(mine);
        LocalDate from = LocalDate.of(2026, 10, 1);
        LocalDate to = LocalDate.of(2026, 10, 16);
        when(views.topViewed(eq(3L), eq(from), eq(to), any())).thenReturn(List.of(
                new Object[] {10L, 200L}, new Object[] {11L, 50L}));
        when(orderItems.ordersForProducts(eq(List.of(10L, 11L)), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] {10L, 8L, 9L}));
        Product shoes = Product.builder().name("Giày").build();
        shoes.setId(10L);
        when(products.findAllById(List.of(10L, 11L))).thenReturn(List.of(shoes));

        List<ProductViewStatResponse> rows = service.productViews(from, to, 10, null);

        assertEquals(2, rows.size());
        assertEquals("Giày", rows.get(0).productName());
        assertEquals(0.04, rows.get(0).conversionRate(), 1e-9);
        assertEquals(9, rows.get(0).unitsSold());
        assertEquals("Sản phẩm đã xoá", rows.get(1).productName());
        assertEquals(0, rows.get(1).orders());
    }
}
