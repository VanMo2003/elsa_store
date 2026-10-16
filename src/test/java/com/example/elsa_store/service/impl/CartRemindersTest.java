package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.NotificationGroup;
import com.example.elsa_store.constant.NotificationType;
import com.example.elsa_store.constant.Role;
import com.example.elsa_store.entity.Cart;
import com.example.elsa_store.entity.CartItem;
import com.example.elsa_store.entity.Product;
import com.example.elsa_store.entity.ProductVariant;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.repository.CartRepository;
import com.example.elsa_store.service.NotificationService;
import com.example.elsa_store.utils.NotificationMessages;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Nhắc giỏ hàng: tìm giỏ không đổi quá N giờ, gửi CART_REMINDER, ghi lúc nhắc; 0 giờ = tắt; khách tắt được nhóm này. */
class CartRemindersTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-16T05:00:00Z"), VN);

    private final CartRepository carts = mock(CartRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);

    private static Cart cart(String... names) {
        User fan = User.builder().username("khach01").role(Role.USER).build();
        fan.setId(7L);
        Cart cart = Cart.builder().user(fan).build();
        cart.setId(3L);
        for (String name : names) {
            ProductVariant variant = new ProductVariant();
            variant.setProduct(Product.builder().name(name).build());
            cart.getItems().add(CartItem.builder().cart(cart).productVariant(variant).quantity(2).build());
        }
        return cart;
    }

    @Test
    void remindsAbandonedCartsOnceAndRecordsWhen() {
        Cart cart = cart("Giày chạy", "Tất");
        when(carts.findAbandoned(eq(LocalDateTime.of(2026, 10, 15, 12, 0)), any())).thenReturn(List.of(cart));

        int sent = new CartReminders(carts, notifications, CLOCK, 24).run();

        assertEquals(1, sent);
        assertEquals(LocalDateTime.of(2026, 10, 16, 12, 0), cart.getRemindedAt());
        verify(notifications).notify(eq(cart.getUser()), eq(NotificationType.CART_REMINDER),
                argThat(m -> m.title().equals("Bạn còn 4 sản phẩm trong giỏ")
                        && m.body().contains("Giày chạy") && m.body().contains("1 sản phẩm khác")),
                eq("elsastore://cart"), eq(3L));
        verify(carts).saveAll(List.of(cart));
    }

    @Test
    void zeroHoursTurnsTheReminderOff() {
        assertEquals(0, new CartReminders(carts, notifications, CLOCK, 0).run());
        verifyNoInteractions(carts, notifications);
    }

    @Test
    void singleItemMessageAndTheGroupCanBeMuted() {
        NotificationMessages.Message message = NotificationMessages.cartReminder(cart("Dép"));
        assertEquals("Bạn còn 2 sản phẩm trong giỏ", message.title());
        assertTrue(message.body().startsWith("“Dép” đang chờ bạn đặt."));

        User fan = cart().getUser();
        fan.setMutedGroups(java.util.EnumSet.of(NotificationGroup.CART_REMINDER));
        assertTrue(fan.mutes(NotificationType.CART_REMINDER));
        assertTrue(NotificationGroup.CART_REMINDER.appliesTo(Role.USER));
    }
}
