package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.NotificationType;
import com.example.elsa_store.entity.Order;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.repository.OrderRepository;
import com.example.elsa_store.service.NotificationService;
import com.example.elsa_store.utils.NotificationMessages;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Nhắc cửa hàng đơn chờ xác nhận quá lâu: gộp theo cửa hàng, đánh dấu đã nhắc, tắt khi 0 giờ. */
class PendingOrderRemindersTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-15T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    private static Order order(long id, String code, Store store) {
        Order o = new Order();
        o.setId(id);
        o.setCode(code);
        o.setStore(store);
        return o;
    }

    private static Store store(long id) {
        Store s = new Store();
        s.setId(id);
        return s;
    }

    @Test
    void groupsByStoreAndMarksReminded() {
        OrderRepository orders = mock(OrderRepository.class);
        NotificationService notifications = mock(NotificationService.class);
        Store a = store(1);
        Store b = store(2);
        List<Order> due = List.of(order(10, "DH10", a), order(20, "DH20", b), order(21, "DH21", b));
        LocalDateTime now = LocalDateTime.now(CLOCK);
        when(orders.findPendingToRemindStore(eq(now.minusHours(12)), any())).thenReturn(due);

        int sent = new PendingOrderReminders(orders, notifications, CLOCK, 12).run();

        assertEquals(3, sent);
        verify(notifications).notifyStore(eq(a), eq(NotificationType.ORDER_PENDING_REMINDER),
                eq(new NotificationMessages.Message("Đơn DH10 chờ xác nhận đã hơn 12 giờ",
                        "Khách đang chờ — xác nhận hoặc liên hệ khách sớm để không mất đơn.")),
                eq("elsastore://orders"), eq(10L));
        verify(notifications).notifyStore(eq(b), eq(NotificationType.ORDER_PENDING_REMINDER),
                eq(new NotificationMessages.Message("2 đơn chờ xác nhận đã hơn 12 giờ",
                        "Gồm DH20, DH21. Xác nhận hoặc liên hệ khách sớm để không mất đơn.")),
                eq("elsastore://orders"), isNull());
        due.forEach(o -> assertEquals(now, o.getStoreRemindedAt()));
        verify(orders).saveAll(due);
    }

    @Test
    void zeroHoursTurnsItOff() {
        OrderRepository orders = mock(OrderRepository.class);
        NotificationService notifications = mock(NotificationService.class);
        assertEquals(0, new PendingOrderReminders(orders, notifications, CLOCK, 0).run());
        verify(orders, never()).findPendingToRemindStore(any(), any());
    }

    @Test
    void manyOrdersShowOnlyTheFirstThreeCodes() {
        Store s = store(1);
        var msg = NotificationMessages.pendingOrders(
                List.of(order(1, "A", s), order(2, "B", s), order(3, "C", s), order(4, "D", s)), 6);
        assertEquals("4 đơn chờ xác nhận đã hơn 6 giờ", msg.title());
        assertEquals("Gồm A, B, C…. Xác nhận hoặc liên hệ khách sớm để không mất đơn.", msg.body());
    }
}
