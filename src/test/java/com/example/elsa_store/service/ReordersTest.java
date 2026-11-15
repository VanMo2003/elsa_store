package com.example.elsa_store.service;

import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.common.ApiResponse;
import com.example.elsa_store.dto.response.CartItemResponse;
import com.example.elsa_store.dto.response.CartResponse;
import com.example.elsa_store.dto.response.ReorderResponse;
import com.example.elsa_store.entity.Order;
import com.example.elsa_store.entity.OrderItem;
import com.example.elsa_store.entity.Product;
import com.example.elsa_store.entity.ProductVariant;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.repository.OrderRepository;
import com.example.elsa_store.security.AccessGuard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** "Mua lại": thêm lại món của đơn cũ, bớt số lượng khi thiếu hàng, bỏ qua món ngừng bán, giỏ khác cửa hàng, quyền. */
class ReordersTest {

    private final AccessGuard guard = mock(AccessGuard.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final CartService carts = mock(CartService.class);
    private final Reorders service = new Reorders(guard, orders, carts);

    private static OrderItem item(long variantId, String name, String color, int qty) {
        Product p = Product.builder().name(name).build();
        ProductVariant v = ProductVariant.builder().product(p).color(color).size("42").build();
        v.setId(variantId);
        OrderItem i = new OrderItem();
        i.setProductVariant(v);
        i.setQuantity(qty);
        return i;
    }

    private static CartResponse cart(Long storeId, int items) {
        List<CartItemResponse> list = new ArrayList<>();
        for (int i = 0; i < items; i++) {
            list.add(mock(CartItemResponse.class));
        }
        return CartResponse.builder().cartId(1L).userId(7L).items(list).totalAmount(0.0).storeId(storeId).build();
    }

    private Order order(long ownerId) {
        User me = User.builder().role(Role.USER).build();
        me.setId(7L);
        when(guard.requireRole(Role.USER)).thenReturn(me);
        User owner = User.builder().build();
        owner.setId(ownerId);
        Store store = new Store();
        store.setId(1L);
        Order o = new Order();
        o.setUser(owner);
        o.setStore(store);
        o.setItems(List.of(item(10, "Giày A", "Đen", 4), item(11, "Túi B", "", 1)));
        when(orders.findById(5L)).thenReturn(Optional.of(o));
        return o;
    }

    @Test
    void addsWhatIsAvailableAndReportsTheRest() {
        order(7L);
        when(carts.getMyCart(7L)).thenReturn(ApiResponse.ok(cart(null, 0)));
        // Giày A: 4 và 2 không đủ, 1 được; Túi B: ngừng bán.
        when(carts.addItem(eq(7L), any())).thenAnswer(inv -> {
            var req = inv.getArgument(1, com.example.elsa_store.dto.request.AddToCartRequest.class);
            if (req.getProductVariantId() == 10L && req.getQuantity() == 1) {
                return ApiResponse.ok(cart(1L, 1));
            }
            throw new AppException(ErrorCode.BAD_REQUEST, "no");
        });
        ReorderResponse out = service.reorder(5L, false);
        assertEquals(1, out.added());
        assertEquals(List.of("Giày A (Đen / 42) (chỉ còn đủ 1)", "Túi B (42) (hết hàng hoặc ngừng bán)"), out.skipped());
        verify(carts).addItem(eq(7L), argThat(r -> r.getProductVariantId() == 10L && r.getQuantity() == 4));
        verify(carts).addItem(eq(7L), argThat(r -> r.getProductVariantId() == 10L && r.getQuantity() == 2));
    }

    @Test
    void cartOfAnotherStoreNeedsReplace() {
        order(7L);
        when(carts.getMyCart(7L)).thenReturn(ApiResponse.ok(cart(2L, 1)));
        var e = assertThrows(AppException.class, () -> service.reorder(5L, false));
        assertEquals(ErrorCode.CART_OTHER_STORE, e.getErrorCode());
        verify(carts, never()).clearCart(any());

        when(carts.addItem(eq(7L), any())).thenReturn(ApiResponse.ok(cart(1L, 2)));
        ReorderResponse out = service.reorder(5L, true);
        verify(carts).clearCart(7L);
        assertEquals(2, out.added());
        assertEquals(List.of(), out.skipped());
    }

    @Test
    void onlyTheBuyerCanReorder() {
        order(99L);
        assertEquals(ErrorCode.UN_AUTHORIZED, assertThrows(AppException.class, () -> service.reorder(5L, false)).getErrorCode());
    }
}
