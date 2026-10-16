package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.ApprovalStatus;
import com.example.elsa_store.dto.request.ShippingSettingsRequest;
import com.example.elsa_store.dto.request.StorePauseRequest;
import com.example.elsa_store.dto.response.StoreResponse;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.exception.ResourceNotFoundException;
import com.example.elsa_store.repository.OrderItemRepository;
import com.example.elsa_store.repository.ProductRepository;
import com.example.elsa_store.repository.ProductReviewRepository;
import com.example.elsa_store.repository.StoreFollowRepository;
import com.example.elsa_store.repository.StoreRepository;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.service.FileStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StoreServiceImplTest {

    private final StoreRepository stores = mock(StoreRepository.class);
    private final AccessGuard accessGuard = mock(AccessGuard.class);
    private final ProductReviewRepository reviews = mock(ProductReviewRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final OrderItemRepository orderItems = mock(OrderItemRepository.class);
    private final FileStorageService storage = mock(FileStorageService.class);
    private final StoreFollowRepository follows = mock(StoreFollowRepository.class);
    private final StoreServiceImpl service = new StoreServiceImpl(stores, accessGuard, reviews, products, orderItems, storage, follows);

    private Store store(ApprovalStatus status) {
        Store store = Store.builder().name("Elsa").approvalStatus(status).email("chu@example.com").build();
        store.setId(3L);
        when(stores.findById(3L)).thenReturn(Optional.of(store));
        return store;
    }

    @Test
    void publicPageCarriesRatingProductsAndSoldCount() {
        store(ApprovalStatus.APPROVED);
        when(reviews.storeStats(3L)).thenReturn(List.<Object[]>of(new Object[] {4.333, 3L}));
        when(products.countOnSale(3L)).thenReturn(12L);
        when(orderItems.soldQuantity(3L)).thenReturn(250L);

        StoreResponse res = service.getPublic(3L);
        assertEquals(4.3, res.getRatingAverage());
        assertEquals(3L, res.getRatingCount());
        assertEquals(12L, res.getProductCount());
        assertEquals(250L, res.getSoldCount());
        assertNull(res.getEmail(), "trang công khai không lộ email của chủ");
    }

    @Test
    void noReviewsYetMeansNoAverageAndUnapprovedStoresAreHidden() {
        store(ApprovalStatus.APPROVED);
        when(reviews.storeStats(3L)).thenReturn(List.<Object[]>of(new Object[] {null, 0L}));
        StoreResponse res = service.getPublic(3L);
        assertNull(res.getRatingAverage());
        assertEquals(0L, res.getRatingCount());

        store(ApprovalStatus.PENDING);
        assertThrows(ResourceNotFoundException.class, () -> service.getPublic(3L));
    }

    @Test
    void shippingSettingsAreRoundedAndFreeShippingDropsTheThreshold() {
        Store mine = Store.builder().name("Elsa").build();
        mine.setId(3L);
        when(accessGuard.requireStoreManager()).thenReturn(mine);
        when(stores.save(any(Store.class))).thenAnswer(inv -> inv.getArgument(0));

        StoreResponse charged = service.updateShipping(new ShippingSettingsRequest(30_000.4, 499_999.6));
        assertEquals(30_000.0, charged.getShippingFee());
        assertEquals(500_000.0, charged.getFreeShippingThreshold());

        StoreResponse free = service.updateShipping(new ShippingSettingsRequest(0.0, 500_000.0));
        assertEquals(0.0, free.getShippingFee());
        assertNull(free.getFreeShippingThreshold());
        assertNull(mine.getShippingFee());
    }

    @Test
    void pauseKeepsTheNoteOnlyWhilePaused() {
        Store mine = Store.builder().name("Elsa").build();
        mine.setId(3L);
        when(accessGuard.requireStoreManager()).thenReturn(mine);
        when(stores.save(any(Store.class))).thenAnswer(inv -> inv.getArgument(0));

        StoreResponse paused = service.updatePause(new StorePauseRequest(true, "  Nghỉ Tết  "));
        assertEquals(true, paused.getPaused());
        assertEquals("Nghỉ Tết", paused.getPauseNote());

        StoreResponse open = service.updatePause(new StorePauseRequest(false, "bỏ qua"));
        assertEquals(false, open.getPaused());
        assertNull(open.getPauseNote());
        assertNull(mine.getPaused());
    }

    @Test
    void logoIsStoredInTheStoresFolderAndCanBeRemoved() {
        Store mine = Store.builder().name("Elsa").build();
        mine.setId(3L);
        when(accessGuard.requireStoreManager()).thenReturn(mine);
        when(stores.save(any(Store.class))).thenAnswer(inv -> inv.getArgument(0));
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", new byte[] {1});
        when(storage.storeStoreImage(file)).thenReturn("/uploads/stores/a.png");

        assertEquals("/uploads/stores/a.png", service.updateImage(file).getImageUrl());
        assertNull(service.removeImage().getImageUrl());
        assertNull(mine.getImageUrl());
    }
}
