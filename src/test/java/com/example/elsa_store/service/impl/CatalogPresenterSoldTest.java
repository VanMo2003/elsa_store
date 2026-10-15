package com.example.elsa_store.service.impl;

import com.example.elsa_store.dto.response.ProductResponse;
import com.example.elsa_store.entity.Product;
import com.example.elsa_store.repository.InventoryRepository;
import com.example.elsa_store.repository.OrderItemRepository;
import com.example.elsa_store.repository.ProductReviewRepository;
import com.example.elsa_store.repository.ProductVariantRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** "Đã bán" lấy gộp một truy vấn cho cả trang; sản phẩm chưa bán được món nào là 0. */
class CatalogPresenterSoldTest {

    private final PricingService pricing = mock(PricingService.class);
    private final OrderItemRepository orderItems = mock(OrderItemRepository.class);
    private final CatalogPresenter presenter = new CatalogPresenter(pricing, mock(StockService.class),
            mock(InventoryRepository.class), mock(ProductVariantRepository.class), mock(ProductReviewRepository.class),
            orderItems);

    private static Product product(long id) {
        Product p = Product.builder().name("P" + id).basePrice(100_000.0).status(1).build();
        p.setId(id);
        return p;
    }

    @Test
    void soldCountsComeFromOneGroupedQuery() {
        when(pricing.productPromotions(any())).thenReturn(Map.of());
        when(orderItems.soldByProduct(List.of(1L, 2L))).thenReturn(List.<Object[]>of(new Object[] {1L, 12L}));

        List<ProductResponse> res = presenter.products(List.of(product(1L), product(2L)));
        assertEquals(12L, res.get(0).getSoldCount());
        assertEquals(0L, res.get(1).getSoldCount());
        verify(orderItems, times(1)).soldByProduct(any());

        when(orderItems.soldByProduct(List.of(1L))).thenReturn(List.<Object[]>of(new Object[] {1L, 12L}));
        assertEquals(12L, presenter.detail(product(1L), List.of(), List.of()).getSoldCount());
    }
}
