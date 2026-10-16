package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.ApprovalStatus;
import com.example.elsa_store.constant.NotificationType;
import com.example.elsa_store.entity.Product;
import com.example.elsa_store.entity.Promotion;
import com.example.elsa_store.entity.PromotionProduct;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.entity.WishlistItem;
import com.example.elsa_store.repository.ProductRepository;
import com.example.elsa_store.repository.WishlistItemRepository;
import com.example.elsa_store.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PriceDropAlertsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 16);

    private final WishlistItemRepository wishlist = mock(WishlistItemRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final PriceDropAlerts alerts = new PriceDropAlerts(wishlist, products, notifications);

    private Store store;
    private User fan;

    private Product product(long id, int status) {
        Product p = Product.builder().name("Giày " + id).status(status).store(store).build();
        p.setId(id);
        return p;
    }

    private static Promotion promo(int type, double value) {
        Promotion p = Promotion.builder().name("Sale").type(type).value(value).status(1).build();
        p.setId(5L);
        return p;
    }

    @BeforeEach
    void setUp() {
        store = Store.builder().name("A").approvalStatus(ApprovalStatus.APPROVED).build();
        fan = User.builder().username("khach01").build();
        fan.setId(7L);
        when(products.findAllById(any())).thenAnswer(inv -> {
            List<Product> list = new ArrayList<>();
            for (Long id : (Iterable<Long>) inv.getArgument(0)) {
                list.add(product(id, id == 3L ? 0 : 1));
            }
            return list;
        });
        when(wishlist.findAllByProduct_Id(any())).thenAnswer(inv ->
                List.of(WishlistItem.builder().user(fan).product(product(inv.getArgument(0), 1)).build()));
    }

    @Test
    void onlyRunningAutomaticPromotionsCount() {
        assertTrue(PriceDropAlerts.running(promo(2, 20), TODAY));
        Promotion coupon = promo(2, 20);
        coupon.setCouponCode("GIAM20");
        assertFalse(PriceDropAlerts.running(coupon, TODAY));
        Promotion later = promo(2, 20);
        later.setStartDate(TODAY.plusDays(1));
        assertFalse(PriceDropAlerts.running(later, TODAY));
        Promotion off = promo(2, 20);
        off.setStatus(0);
        assertFalse(PriceDropAlerts.running(off, TODAY));
    }

    @Test
    void onlyNewlyDiscountedVisibleProductsAreAnnounced() {
        alerts.afterChange(promo(2, 20), Set.of(1L), List.of(1L, 2L, 3L), TODAY);
        verify(notifications, times(1)).notify(eq(fan), eq(NotificationType.PRICE_DROP),
                argThat(m -> m.body().contains("Giày 2") && m.body().contains("giảm 20%")),
                eq("elsastore://products/2"), eq(2L));
        verifyNoMoreInteractions(notifications);
    }

    @Test
    void discountedBeforeIsReadFromTheLinksOfARunningPromotion() {
        Promotion p = promo(1, 50_000);
        p.setPromotionProducts(new ArrayList<>(List.of(PromotionProduct.builder().promotion(p).product(product(9L, 1)).build())));
        assertEquals(Set.of(9L), PriceDropAlerts.discountedBy(p, TODAY));
        p.setStatus(0);
        assertEquals(Set.of(), PriceDropAlerts.discountedBy(p, TODAY), "khuyến mãi đang tắt thì coi như chưa giảm gì");
    }
}
