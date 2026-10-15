package com.example.elsa_store.service.impl;

import com.example.elsa_store.dto.request.PromotionRequest;
import com.example.elsa_store.dto.response.ProductResponse;
import com.example.elsa_store.dto.response.PromotionResponse;
import com.example.elsa_store.entity.Promotion;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.repository.PromotionProductRepository;
import com.example.elsa_store.repository.PromotionRepository;
import com.example.elsa_store.repository.StoreRepository;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.service.PromotionProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PromotionServiceImplTest {

    private final PromotionRepository promotions = mock(PromotionRepository.class);
    private final StoreRepository stores = mock(StoreRepository.class);
    private final AccessGuard accessGuard = mock(AccessGuard.class);
    private final PromotionProductService links = mock(PromotionProductService.class);
    private final PricingService pricing = new PricingService(
            promotions, mock(PromotionProductRepository.class),
            Clock.fixed(Instant.parse("2026-10-14T03:00:00Z"), ZoneId.of("UTC")));
    private final PromotionServiceImpl service =
            new PromotionServiceImpl(promotions, stores, accessGuard, pricing, links);

    private Store store;

    @BeforeEach
    void setUp() {
        store = Store.builder().name("Store A").build();
        store.setId(1L);
        when(accessGuard.requireStoreManager()).thenReturn(store);
        when(promotions.findByStoreAndCoupon(anyLong(), anyString())).thenReturn(List.of());
        when(promotions.save(any(Promotion.class))).thenAnswer(inv -> {
            Promotion p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(40L);
            }
            return p;
        });
    }

    private static PromotionRequest request(List<Long> productIds) {
        PromotionRequest req = new PromotionRequest();
        req.setName("Giam 20% giay");
        req.setType(2);
        req.setValue(20.0);
        req.setStatus(1);
        req.setProductIds(productIds);
        return req;
    }

    private static ProductResponse product(long id) {
        ProductResponse p = new ProductResponse();
        p.setId(id);
        return p;
    }

    @Test
    void productsAreSavedTogetherWithThePromotion() {
        when(links.setProducts(eq(40L), any())).thenReturn(List.of(product(10L), product(11L)));

        PromotionResponse res = service.create(request(List.of(10L, 11L)));

        InOrder inOrder = inOrder(promotions, links);
        inOrder.verify(promotions).save(any(Promotion.class));
        inOrder.verify(links).setProducts(40L, List.of(10L, 11L));
        assertEquals(List.of(10L, 11L), res.getProductIds());
        assertEquals(Boolean.TRUE, res.getActiveNow());
    }

    @Test
    void omittedProductListLeavesLinksAlone() {
        Promotion existing = Promotion.builder().name("Cu").type(1).value(1.0).status(1).store(store).build();
        existing.setId(40L);
        when(promotions.findById(40L)).thenReturn(Optional.of(existing));

        service.update(40L, request(null));
        verify(links, never()).setProducts(anyLong(), any());

        service.update(40L, request(List.of()));
        verify(links).setProducts(40L, List.of());
    }

    @Test
    void invalidPromotionIsRejectedBeforeAnythingIsLinked() {
        PromotionRequest bad = request(List.of(10L));
        bad.setValue(150.0);
        assertThrows(AppException.class, () -> service.create(bad));
        verify(promotions, never()).save(any());
        verify(links, never()).setProducts(anyLong(), any());
    }

    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
