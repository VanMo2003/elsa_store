package com.example.elsa_store.event;

import com.example.elsa_store.constant.NotificationType;
import com.example.elsa_store.entity.Order;
import com.example.elsa_store.entity.OrderItem;
import com.example.elsa_store.entity.User;

import java.util.List;

/**
 * Một sự kiện đơn hàng/thanh toán cần gửi email. Mang sẵn mọi dữ liệu email cần để listener chạy ở luồng khác,
 * sau khi transaction đã đóng, mà không phải đọc lazy entity.
 */
public record OrderMailEvent(
        NotificationType type,
        String email,
        String customerName,
        String orderCode,
        Double finalAmount,
        List<Line> items) {

    public record Line(String name, int quantity, double lineTotal) {}

    public static OrderMailEvent of(NotificationType type, Order order, User recipient) {
        List<Line> lines = order.getItems() == null
                ? List.of()
                : order.getItems().stream().map(OrderMailEvent::line).toList();
        String name = recipient.getFullName() == null || recipient.getFullName().isBlank()
                ? recipient.getUsername()
                : recipient.getFullName();
        return new OrderMailEvent(type, recipient.getEmail(), name, order.getCode(), order.getFinalAmount(), lines);
    }

    private static Line line(OrderItem item) {
        var variant = item.getProductVariant();
        StringBuilder name = new StringBuilder(variant.getProduct().getName());
        String color = variant.getColor();
        String size = variant.getSize();
        if ((color != null && !color.isBlank()) || (size != null && !size.isBlank())) {
            name.append(" (");
            if (color != null && !color.isBlank()) {
                name.append(color);
            }
            if (size != null && !size.isBlank()) {
                name.append(color != null && !color.isBlank() ? ", " : "").append("size ").append(size);
            }
            name.append(')');
        }
        return new Line(
                name.toString(),
                item.getQuantity() == null ? 0 : item.getQuantity(),
                item.getLineTotal() == null ? 0 : item.getLineTotal());
    }
}
