package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.OrderStatus;
import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.response.OrderResponse.TimelineEntry;
import com.example.elsa_store.entity.Order;
import com.example.elsa_store.entity.OrderStatusHistory;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.repository.OrderStatusHistoryRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Ghi và đọc dòng thời gian trạng thái của đơn ({@code order_status_history}). */
@Component
public class OrderTimeline {

    static final String BY_CUSTOMER = "CUSTOMER";
    static final String BY_STORE = "STORE";
    static final String BY_SYSTEM = "SYSTEM";

    private final OrderStatusHistoryRepository historyRepository;

    public OrderTimeline(OrderStatusHistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
    }

    /** Ghi một mốc; {@code by} trống khi hệ thống tự đổi. */
    public void record(Order order, OrderStatus status, User by, String note) {
        historyRepository.save(OrderStatusHistory.builder()
                .order(order)
                .status(status)
                .changedBy(by)
                .note(note)
                .build());
    }

    public void deleteFor(Order order) {
        historyRepository.deleteByOrderIds(List.of(order.getId()));
    }

    /**
     * Dòng thời gian để hiện cho {@code viewer}. Khách không thấy tên nhân viên (chỉ "Cửa hàng"). Đơn cũ chưa có mốc nào:
     * dựng lại từ ngày đặt, ngày hoàn thành và trạng thái hiện tại.
     */
    public List<TimelineEntry> entries(Order order, User viewer) {
        List<OrderStatusHistory> rows = historyRepository.findByOrder_IdOrderByIdAsc(order.getId());
        boolean customerView = viewer != null && viewer.getRole() == Role.USER;
        List<TimelineEntry> result = new ArrayList<>();
        if (rows.isEmpty()) {
            result.add(entry(OrderStatus.CHUA_XAC_NHAN, order.getOrderDate(), null, null, null));
            if (order.getStatus() == OrderStatus.HOAN_THANH && order.getCompletedAt() != null) {
                result.add(entry(OrderStatus.HOAN_THANH, order.getCompletedAt(), null, null, null));
            } else if (order.getStatus() == OrderStatus.DA_HUY) {
                result.add(entry(OrderStatus.DA_HUY, null, null, null, order.getCancelReason()));
            }
            return result;
        }
        for (OrderStatusHistory row : rows) {
            User by = row.getChangedBy();
            String role = by == null ? BY_SYSTEM : by.getRole() == Role.USER ? BY_CUSTOMER : BY_STORE;
            String name = by == null || (customerView && BY_STORE.equals(role)) ? null : displayName(by);
            result.add(entry(row.getStatus(), row.getCreatedAt(), name, role, row.getNote()));
        }
        return result;
    }

    private static TimelineEntry entry(OrderStatus status, LocalDateTime at, String name, String role,
                                       String note) {
        TimelineEntry entry = new TimelineEntry();
        entry.setStatus(status);
        entry.setAt(at);
        entry.setByName(name);
        entry.setByRole(role);
        entry.setNote(note);
        return entry;
    }

    private static String displayName(User user) {
        return user.getFullName() == null || user.getFullName().isBlank() ? user.getUsername() : user.getFullName();
    }
}
