package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.NotificationType;
import com.example.elsa_store.entity.Order;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.repository.OrderRepository;
import com.example.elsa_store.service.NotificationService;
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

/** Nhắc xác nhận đã nhận hàng: 2 ngày trước khi tự hoàn thành, mỗi đơn một lần; tắt tự hoàn thành thì không nhắc. */
class DeliveryRemindersTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-16T05:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    private final OrderRepository orders = mock(OrderRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);

    @Test
    void remindsTwoDaysBeforeAutoCompletionAndMarksTheOrder() {
        User fan = User.builder().username("khach01").build();
        Order order = new Order();
        order.setId(50L);
        order.setCode("ORD-50");
        order.setUser(fan);
        when(orders.findShippingToRemind(eq(LocalDateTime.of(2026, 10, 11, 12, 0)), any())).thenReturn(List.of(order));

        DeliveryReminders reminders = new DeliveryReminders(orders, notifications, CLOCK, 7);
        assertEquals(5, reminders.remindAfterDays());
        assertEquals(1, reminders.run());

        assertEquals(LocalDateTime.of(2026, 10, 16, 12, 0), order.getDeliveryRemindedAt());
        verify(notifications).notify(eq(fan), eq(NotificationType.ORDER_DELIVERY_CHECK),
                argThat(m -> m.title().contains("ORD-50") && m.body().contains("sau 2 ngày")),
                eq("elsastore://orders"), eq(50L));
        verify(orders).saveAll(List.of(order));
    }

    @Test
    void shortWindowsRemindAfterOneDayAndZeroTurnsItOff() {
        assertEquals(1, new DeliveryReminders(orders, notifications, CLOCK, 2).remindAfterDays());
        assertEquals(0, new DeliveryReminders(orders, notifications, CLOCK, 0).run());
        verifyNoInteractions(orders, notifications);
    }
}
