package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.ApprovalStatus;
import com.example.elsa_store.constant.Role;
import com.example.elsa_store.entity.Product;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.entity.WishlistItem;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ResourceNotFoundException;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class WishlistServiceImplTest {

    private final WishlistItemRepository wishlist = mock(WishlistItemRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final AccessGuard accessGuard = mock(AccessGuard.class);
    private final CatalogPresenter catalog = mock(CatalogPresenter.class);
    private final WishlistServiceImpl service = new WishlistServiceImpl(wishlist, products, accessGuard, catalog);

    private User buyer;
    private Product shoes;
    private Product hidden;

    @BeforeEach
    void setUp() {
        Store store = Store.builder().name("A").approvalStatus(ApprovalStatus.APPROVED).build();
        buyer = User.builder().username("khach001").role(Role.USER).build();
        buyer.setId(7L);
        shoes = Product.builder().name("Giay").status(1).store(store).build();
        shoes.setId(10L);
        hidden = Product.builder().name("An").status(0).store(store).build();
        hidden.setId(11L);
        when(accessGuard.currentUser()).thenReturn(buyer);
        when(products.findById(10L)).thenReturn(Optional.of(shoes));
        when(products.findById(11L)).thenReturn(Optional.of(hidden));
        when(wishlist.findByUser_IdAndProduct_Id(anyLong(), anyLong())).thenReturn(Optional.empty());
        when(wishlist.productIds(7L)).thenReturn(List.of(10L));
    }

    @Test
    void addingIsIdempotent() {
        assertEquals(List.of(10L), service.add(10L));
        ArgumentCaptor<WishlistItem> saved = ArgumentCaptor.forClass(WishlistItem.class);
        verify(wishlist).save(saved.capture());
        assertSame(shoes, saved.getValue().getProduct());

        when(wishlist.findByUser_IdAndProduct_Id(7L, 10L)).thenReturn(Optional.of(saved.getValue()));
        service.add(10L);
        verify(wishlist, times(1)).save(any());
    }

    @Test
    void hiddenProductsCannotBeSavedOrListed() {
        assertThrows(ResourceNotFoundException.class, () -> service.add(11L));
        WishlistItem a = WishlistItem.builder().user(buyer).product(shoes).build();
        WishlistItem b = WishlistItem.builder().user(buyer).product(hidden).build();
        when(wishlist.findByUser_IdOrderByIdDesc(7L)).thenReturn(List.of(a, b));
        service.list();
        verify(catalog).products(List.of(shoes));
    }

    @Test
    void onlyCustomersHaveAWishlist() {
        when(accessGuard.currentUser()).thenReturn(User.builder().username("staff01").role(Role.STAFF).build());
        assertThrows(AppException.class, () -> service.productIds());
        verify(wishlist, never()).productIds(anyLong());
    }

    @Test
    void wishlistHasACap() {
        when(wishlist.countByUser_Id(7L)).thenReturn((long) WishlistServiceImpl.MAX_ITEMS);
        assertThrows(AppException.class, () -> service.add(10L));
    }
}
