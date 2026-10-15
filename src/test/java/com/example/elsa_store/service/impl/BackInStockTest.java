package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.ApprovalStatus;
import com.example.elsa_store.constant.NotificationType;
import com.example.elsa_store.entity.Product;
import com.example.elsa_store.entity.ProductVariant;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.entity.WishlistItem;
import com.example.elsa_store.repository.InventoryRepository;
import com.example.elsa_store.repository.ProductVariantRepository;
import com.example.elsa_store.repository.WishlistItemRepository;
import com.example.elsa_store.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class BackInStockTest {

    private final InventoryRepository inventories = mock(InventoryRepository.class);
    private final ProductVariantRepository variants = mock(ProductVariantRepository.class);
    private final WishlistItemRepository wishlist = mock(WishlistItemRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final BackInStock backInStock = new BackInStock(inventories, variants, wishlist, notifications);

    private Product product;
    private ProductVariant black;
    private User fan;

    private void stock(long tracked, long available, long total) {
        InventoryRepository.ProductStock s = mock(InventoryRepository.ProductStock.class);
        when(s.getProductId()).thenReturn(10L);
        when(s.getTracked()).thenReturn(tracked);
        when(s.getAvailable()).thenReturn(available);
        when(inventories.findProductStock(List.of(10L))).thenReturn(List.of(s));
        ProductVariantRepository.VariantCount c = mock(ProductVariantRepository.VariantCount.class);
        when(c.getProductId()).thenReturn(10L);
        when(c.getTotal()).thenReturn(total);
        when(variants.countByProducts(List.of(10L))).thenReturn(List.of(c));
    }

    @BeforeEach
    void setUp() {
        Store store = Store.builder().name("A").approvalStatus(ApprovalStatus.APPROVED).build();
        product = Product.builder().name("Giày chạy bộ").status(1).store(store).build();
        product.setId(10L);
        black = ProductVariant.builder().product(product).build();
        black.setId(100L);
        fan = User.builder().username("khach01").build();
        fan.setId(7L);
        when(wishlist.findAllByProduct_Id(10L)).thenReturn(List.of(WishlistItem.builder().user(fan).product(product).build()));
    }

    @Test
    void soldOutOnlyWhenEveryVariantIsTrackedAndEmpty() {
        stock(2, 0, 2);
        assertTrue(backInStock.soldOut(black));
        stock(2, 1, 2);
        assertFalse(backInStock.soldOut(black));
        stock(1, 0, 2);
        assertFalse(backInStock.soldOut(black), "còn phân loại không theo dõi kho thì vẫn bán được");
        when(inventories.findProductStock(List.of(10L))).thenReturn(List.of());
        assertFalse(backInStock.soldOut(black), "chưa theo dõi kho");
    }

    @Test
    void wishlistersAreToldOnlyWhenTheProductComesBack() {
        stock(2, 1, 2);
        backInStock.afterIncrease(black, true);
        verify(notifications).notify(eq(fan), eq(NotificationType.BACK_IN_STOCK),
                argThat(m -> m.body().contains("Giày chạy bộ")), eq("elsastore://products/10"), eq(10L));

        backInStock.afterIncrease(black, false);
        stock(2, 0, 2);
        backInStock.afterIncrease(black, true);
        verify(notifications, times(1)).notify(any(), any(), any(), any(), any());
    }

    @Test
    void hiddenProductsDoNotNotify() {
        product.setStatus(0);
        stock(2, 1, 2);
        backInStock.afterIncrease(black, true);
        verifyNoInteractions(notifications);
    }
}
