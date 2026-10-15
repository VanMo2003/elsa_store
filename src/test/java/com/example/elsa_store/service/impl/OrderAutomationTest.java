package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.OrderStatus;
import com.example.elsa_store.repository.OrderRepository;
import com.example.elsa_store.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderAutomationTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-15T05:00:00Z"), VN);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 15, 12, 0);

    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderService service = mock(OrderService.class);

    @Test
    void unpaidOnlineOrdersAreCancelledAfterTheHoldTimeOneByOne() {
        OrderAutomation automation = new OrderAutomation(orders, service, CLOCK, 30, 7);
        when(orders.findUnpaidOnlineOrderIds(eq(NOW.minusMinutes(30)), any(Pageable.class))).thenReturn(List.of(1L, 2L, 3L));
        when(service.autoTransition(1L, OrderStatus.CHUA_XAC_NHAN, OrderStatus.DA_HUY, OrderAutomation.UNPAID_REASON))
                .thenReturn(true);
        when(service.autoTransition(2L, OrderStatus.CHUA_XAC_NHAN, OrderStatus.DA_HUY, OrderAutomation.UNPAID_REASON))
                .thenThrow(new IllegalStateException("khoá dòng quá lâu"));
        when(service.autoTransition(3L, OrderStatus.CHUA_XAC_NHAN, OrderStatus.DA_HUY, OrderAutomation.UNPAID_REASON))
                .thenReturn(false);

        assertEquals(1, automation.cancelUnpaidOnlineOrders(), "lỗi ở một đơn không chặn các đơn sau");
        verify(service).autoTransition(3L, OrderStatus.CHUA_XAC_NHAN, OrderStatus.DA_HUY, OrderAutomation.UNPAID_REASON);
    }

    @Test
    void longDeliveriesAreCompletedWithANote() {
        OrderAutomation automation = new OrderAutomation(orders, service, CLOCK, 30, 7);
        when(orders.findLongShippingOrderIds(eq(NOW.minusDays(7)), any(Pageable.class))).thenReturn(List.of(9L));
        when(service.autoTransition(eq(9L), eq(OrderStatus.DANG_GIAO), eq(OrderStatus.HOAN_THANH), anyString()))
                .thenReturn(true);

        assertEquals(1, automation.completeLongDeliveries());
        verify(service).autoTransition(9L, OrderStatus.DANG_GIAO, OrderStatus.HOAN_THANH,
                "Tự hoàn thành vì khách chưa xác nhận sau 7 ngày giao");
    }

    @Test
    void holdTimeNeverEndsBeforeTheVnPayLinkExpiresAndZeroTurnsJobsOff() {
        OrderAutomation tooShort = new OrderAutomation(orders, service, CLOCK, 5, 7);
        tooShort.cancelUnpaidOnlineOrders();
        verify(orders).findUnpaidOnlineOrderIds(eq(NOW.minusMinutes(20)), any(Pageable.class));

        OrderAutomation off = new OrderAutomation(orders, service, CLOCK, 0, 0);
        off.run();
        verify(orders, never()).findLongShippingOrderIds(any(), any());
        verifyNoInteractions(service);
    }
}
