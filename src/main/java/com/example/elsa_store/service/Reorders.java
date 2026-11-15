package com.example.elsa_store.service;

import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.request.AddToCartRequest;
import com.example.elsa_store.dto.response.CartResponse;
import com.example.elsa_store.dto.response.ReorderResponse;
import com.example.elsa_store.entity.Order;
import com.example.elsa_store.entity.OrderItem;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.exception.ResourceNotFoundException;
import com.example.elsa_store.repository.OrderRepository;
import com.example.elsa_store.security.AccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * "Mua lại": khách thêm lại các món của một đơn cũ vào giỏ, với giá và khuyến mãi hiện tại. Món ngừng bán / hết hàng / không
 * đủ số lượng thì thử thêm số còn lại, không được nữa thì bỏ qua và báo lại. Giỏ chỉ chứa hàng của một cửa hàng: giỏ đang
 * có hàng cửa hàng khác thì báo lỗi, trừ khi khách chọn {@code replace} (xoá giỏ cũ trước).
 */
@Service
@RequiredArgsConstructor
public class Reorders {

    private final AccessGuard accessGuard;
    private final OrderRepository orderRepository;
    private final CartService cartService;

    /**
     * Không bọc trong một transaction: mỗi lần thêm món là một transaction riêng của giỏ hàng, nên một món lỗi (hết hàng)
     * không làm hỏng cả lượt. Đọc đơn dựa vào open-in-view của request web.
     */
    public ReorderResponse reorder(Long orderId, boolean replace) {
        User me = accessGuard.requireRole(Role.USER);
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (order.getUser() == null || !order.getUser().getId().equals(me.getId())) {
            throw new AppException(ErrorCode.UN_AUTHORIZED);
        }
        CartResponse cart = cartService.getMyCart(me.getId()).getData();
        Long orderStore = order.getStore() == null ? null : order.getStore().getId();
        boolean otherStore = cart != null && cart.getItems() != null && !cart.getItems().isEmpty()
                && !Objects.equals(cart.getStoreId(), orderStore);
        if (otherStore) {
            if (!replace) {
                throw new AppException(ErrorCode.CART_OTHER_STORE);
            }
            cartService.clearCart(me.getId());
        }
        int added = 0;
        List<String> skipped = new ArrayList<>();
        for (OrderItem item : order.getItems() == null ? List.<OrderItem>of() : order.getItems()) {
            String name = label(item);
            int wanted = item.getQuantity() == null ? 1 : item.getQuantity();
            boolean ok = false;
            // Không đủ số lượng cũ thì thử bớt dần tới 1.
            for (int qty = wanted; qty >= 1 && !ok; qty = qty > 1 ? Math.max(1, qty / 2) : 0) {
                try {
                    AddToCartRequest req = new AddToCartRequest();
                    req.setProductVariantId(item.getProductVariant().getId());
                    req.setQuantity(qty);
                    cart = cartService.addItem(me.getId(), req).getData();
                    ok = true;
                    added++;
                    if (qty < wanted) {
                        skipped.add(name + " (chỉ còn đủ " + qty + ")");
                    }
                } catch (AppException e) {
                    if (qty == 1) {
                        break;
                    }
                }
            }
            if (!ok) {
                skipped.add(name + " (hết hàng hoặc ngừng bán)");
            }
        }
        return new ReorderResponse(cart, added, skipped);
    }

    private static String label(OrderItem item) {
        var v = item.getProductVariant();
        String variant = String.join(" / ", java.util.stream.Stream.of(v.getColor(), v.getSize())
                .filter(s -> s != null && !s.isBlank()).toList());
        return v.getProduct().getName() + (variant.isEmpty() ? "" : " (" + variant + ")");
    }
}
