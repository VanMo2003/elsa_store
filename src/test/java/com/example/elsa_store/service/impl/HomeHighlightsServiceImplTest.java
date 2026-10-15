package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.ApprovalStatus;
import com.example.elsa_store.dto.response.HomeHighlightsResponse;
import com.example.elsa_store.dto.response.ProductResponse;
import com.example.elsa_store.entity.Product;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.repository.OrderItemRepository;
import com.example.elsa_store.repository.ProductRepository;
import com.example.elsa_store.repository.PromotionProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Trang chủ: đang giảm giá (giảm nhiều trước, bỏ hàng ẩn), bán chạy (30 ngày, không có thì toàn thời gian). */
class HomeHighlightsServiceImplTest {

    private final ProductRepository products = mock(ProductRepository.class);
    private final PromotionProductRepository links = mock(PromotionProductRepository.class);
    private final OrderItemRepository orderItems = mock(OrderItemRepository.class);
    private final PricingService pricing = mock(PricingService.class);
    private final CatalogPresenter catalog = mock(CatalogPresenter.class);
    private final HomeHighlightsServiceImpl service =
            new HomeHighlightsServiceImpl(products, links, orderItems, pricing, catalog);

    private Store store;

    private Product product(long id, int status) {
        Product p = Product.builder().name("P" + id).status(status).store(store).build();
        p.setId(id);
        return p;
    }

    private static ProductResponse response(Product p, Integer percent) {
        ProductResponse r = new ProductResponse();
        r.setId(p.getId());
        if (percent != null) {
            r.setSalePrice(1.0);
            r.setDiscountPercent(percent);
        }
        return r;
    }

    @BeforeEach
    void setUp() {
        store = Store.builder().name("A").approvalStatus(ApprovalStatus.APPROVED).build();
        when(pricing.today()).thenReturn(LocalDate.of(2026, 10, 16));
        when(links.productIdsOnSale(any())).thenReturn(List.of());
        when(orderItems.bestSellingProductIds(any(), any())).thenReturn(List.of());
        when(products.findAllById(any())).thenAnswer(inv -> {
            Collection<Long> ids = inv.getArgument(0);
            return ids.stream().map(id -> product(id, id == 3L ? 0 : 1)).toList();
        });
        when(catalog.products(any())).thenAnswer(inv -> {
            List<Product> list = inv.getArgument(0);
            // Sản phẩm 4: khuyến mãi không làm giảm được giá (đã 0đ) nên không tính là đang giảm.
            return list.stream().map(p -> response(p, p.getId() == 4L ? null : (int) (p.getId() * 10))).toList();
        });
    }

    @Test
    void onSaleIsSortedByBiggestDiscountWithoutHiddenProducts() {
        when(links.productIdsOnSale(LocalDate.of(2026, 10, 16))).thenReturn(List.of(1L, 2L, 3L, 4L, 5L));
        HomeHighlightsResponse res = service.highlights(2);
        assertEquals(List.of(5L, 2L), res.onSale().stream().map(ProductResponse::getId).toList());
    }

    @Test
    void bestSellersFallBackToAllTimeWhenTheLastMonthIsEmpty() {
        ArgumentCaptor<LocalDateTime> since = ArgumentCaptor.forClass(LocalDateTime.class);
        when(orderItems.bestSellingProductIds(any(), any()))
                .thenReturn(List.of())
                .thenReturn(List.of(7L, 3L, 6L));

        HomeHighlightsResponse res = service.highlights(10);
        assertEquals(List.of(7L, 6L), res.bestSellers().stream().map(ProductResponse::getId).toList(),
                "giữ thứ tự bán chạy, bỏ sản phẩm đang ẩn");
        verify(orderItems, times(2)).bestSellingProductIds(since.capture(), any());
        assertTrue(since.getAllValues().get(0).isAfter(LocalDateTime.now().minusDays(31)));
        assertEquals(2000, since.getAllValues().get(1).getYear());
    }
}
