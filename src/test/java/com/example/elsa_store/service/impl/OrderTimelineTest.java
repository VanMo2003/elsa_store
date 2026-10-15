package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.OrderStatus;
import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.response.OrderResponse.TimelineEntry;
import com.example.elsa_store.entity.Order;
import com.example.elsa_store.entity.OrderStatusHistory;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.repository.OrderStatusHistoryRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderTimelineTest {

    private final OrderStatusHistoryRepository history = mock(OrderStatusHistoryRepository.class);
    private final OrderTimeline timeline = new OrderTimeline(history);

    private static User user(long id, String username, String fullName, Role role) {
        User user = User.builder().username(username).fullName(fullName).role(role).build();
        user.setId(id);
        return user;
    }

    private static OrderStatusHistory row(Order order, OrderStatus status, User by, String note, LocalDateTime at) {
        OrderStatusHistory row = OrderStatusHistory.builder().order(order).status(status).changedBy(by).note(note).build();
        row.setCreatedAt(at);
        return row;
    }

    @Test
    void customersSeeTheStoreButNotTheStaffMembersName() {
        Order order = new Order();
        order.setId(5L);
        User buyer = user(7, "khach001", "Nguyễn Văn A", Role.USER);
        User staff = user(8, "nv01", "Trần Thị Nhân Viên", Role.STAFF);
        LocalDateTime t = LocalDateTime.of(2026, 10, 15, 9, 0);
        when(history.findByOrder_IdOrderByIdAsc(5L)).thenReturn(List.of(
                row(order, OrderStatus.CHUA_XAC_NHAN, buyer, null, t),
                row(order, OrderStatus.DA_XAC_NHAN, null, "Đã thanh toán qua VNPay", t.plusMinutes(5)),
                row(order, OrderStatus.DANG_GIAO, staff, null, t.plusHours(2))));

        List<TimelineEntry> forCustomer = timeline.entries(order, buyer);
        assertEquals(3, forCustomer.size());
        assertEquals("CUSTOMER", forCustomer.get(0).getByRole());
        assertEquals("Nguyễn Văn A", forCustomer.get(0).getByName());
        assertEquals("SYSTEM", forCustomer.get(1).getByRole());
        assertEquals("Đã thanh toán qua VNPay", forCustomer.get(1).getNote());
        assertEquals("STORE", forCustomer.get(2).getByRole());
        assertNull(forCustomer.get(2).getByName(), "khách không thấy tên nhân viên");
        assertEquals(t.plusHours(2), forCustomer.get(2).getAt());

        assertEquals("Trần Thị Nhân Viên", timeline.entries(order, staff).get(2).getByName());
    }

    @Test
    void ordersFromBeforeTheHistoryTableAreRebuiltFromTheirDates() {
        Order done = new Order();
        done.setId(6L);
        done.setOrderDate(LocalDateTime.of(2026, 9, 30, 20, 0));
        done.setCompletedAt(LocalDateTime.of(2026, 10, 2, 10, 0));
        done.setStatus(OrderStatus.HOAN_THANH);
        when(history.findByOrder_IdOrderByIdAsc(6L)).thenReturn(List.of());

        List<TimelineEntry> entries = timeline.entries(done, null);
        assertEquals(List.of(OrderStatus.CHUA_XAC_NHAN, OrderStatus.HOAN_THANH),
                entries.stream().map(TimelineEntry::getStatus).toList());
        assertEquals(done.getOrderDate(), entries.get(0).getAt());
        assertEquals(done.getCompletedAt(), entries.get(1).getAt());

        Order cancelled = new Order();
        cancelled.setId(7L);
        cancelled.setStatus(OrderStatus.DA_HUY);
        when(history.findByOrder_IdOrderByIdAsc(7L)).thenReturn(List.of());
        List<TimelineEntry> cancelledEntries = timeline.entries(cancelled, null);
        assertEquals(OrderStatus.DA_HUY, cancelledEntries.get(1).getStatus());
        assertNull(cancelledEntries.get(1).getAt(), "không biết lúc huỷ thì để trống, không bịa giờ");
    }
}
