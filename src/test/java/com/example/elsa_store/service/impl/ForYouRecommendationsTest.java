package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.Role;
import com.example.elsa_store.entity.Category;
import com.example.elsa_store.entity.Product;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.repository.OrderItemRepository;
import com.example.elsa_store.repository.ProductRepository;
import com.example.elsa_store.repository.WishlistItemRepository;
import com.example.elsa_store.security.AccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/** "Gợi ý cho bạn": danh mục hay xem / mua nhất trước, xen kẽ danh mục, bỏ hàng đã mua / đã thích / vừa xem. */
class ForYouRecommendationsTest {

    private final ProductRepository products = mock(ProductRepository.class);
    private final OrderItemRepository orderItems = mock(OrderItemRepository.class);
    private final WishlistItemRepository wishlist = mock(WishlistItemRepository.class);
    private final AccessGuard accessGuard = mock(AccessGuard.class);
    private final CatalogPresenter catalog = mock(CatalogPresenter.class);
    private final ForYouRecommendations service =
            new ForYouRecommendations(products, orderItems, wishlist, accessGuard, catalog);

    private Category shoes;
    private Category bags;

    private Product product(long id, Category category) {
        Product p = Product.builder().name("P" + id).status(1).category(category).build();
        p.setId(id);
        return p;
    }

    @BeforeEach
    void setUp() {
        shoes = new Category();
        shoes.setId(1L);
        bags = new Category();
        bags.setId(2L);
        when(catalog.products(any())).thenReturn(List.of());
        when(accessGuard.currentUserOrEmpty()).thenReturn(Optional.empty());
    }

    @SuppressWarnings("unchecked")
    private List<Long> picked() {
        ArgumentCaptor<List<Product>> captor = ArgumentCaptor.forClass(List.class);
        verify(catalog).products(captor.capture());
        return captor.getValue().stream().map(Product::getId).toList();
    }

    @Test
    void mixesTheMostSeenCategoriesAndSkipsSeedProducts() {
        User fan = User.builder().username("k").role(Role.USER).build();
        fan.setId(7L);
        when(accessGuard.currentUserOrEmpty()).thenReturn(Optional.of(fan));
        when(orderItems.purchasedProductIds(7L)).thenReturn(List.of(11L));
        when(wishlist.productIds(7L)).thenReturn(List.of(20L));
        when(products.findAllById(any())).thenReturn(List.of(product(10, shoes), product(11, shoes), product(20, bags)));
        when(products.findPublic(eq(1L), isNull(), any())).thenReturn(List.of(product(10, shoes), product(12, shoes), product(13, shoes)));
        when(products.findPublic(eq(2L), isNull(), any())).thenReturn(List.of(product(20, bags), product(21, bags)));

        service.forYou(List.of(10L), 4);

        assertEquals(List.of(12L, 21L, 13L), picked(), "giày (2 lần) trước túi (1 lần), xen kẽ, bỏ 10 / 11 / 20");
    }

    @Test
    void nothingToGoOnMeansNoSuggestions() {
        assertTrue(service.forYou(null, 10).isEmpty());
        assertTrue(service.forYou(List.of(), 10).isEmpty());
        verifyNoInteractions(catalog);
    }
}
