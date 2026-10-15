package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.NotificationType;
import com.example.elsa_store.entity.Order;
import com.example.elsa_store.entity.OrderItem;
import com.example.elsa_store.entity.ProductVariant;
import com.example.elsa_store.repository.InventoryRepository;
import com.example.elsa_store.service.NotificationService;
import com.example.elsa_store.utils.AppLinks;
import com.example.elsa_store.utils.NotificationMessages;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sau mỗi đơn vừa trừ kho: biến thể nào vừa <b>vượt xuống</b> mức cảnh báo ({@code minQuantity}) hoặc về 0 thì báo cho
 * chủ và quản lý cửa hàng (nhân viên không xem được kho). Chỉ báo lúc vượt ngưỡng, không báo lại ở mỗi đơn sau.
 */
@Component
public class StockAlerts {

    /** Một biến thể vừa sắp hết / hết hàng. */
    record Alert(ProductVariant variant, int remaining, Integer minQuantity) {
        boolean soldOut() {
            return remaining <= 0;
        }
    }

    private final InventoryRepository inventoryRepository;
    private final NotificationService notificationService;

    public StockAlerts(InventoryRepository inventoryRepository, NotificationService notificationService) {
        this.inventoryRepository = inventoryRepository;
        this.notificationService = notificationService;
    }

    public void afterSale(Order order) {
        if (order.getStore() == null || order.getItems() == null) {
            return;
        }
        List<OrderItem> sold = order.getItems().stream()
                .filter(item -> Boolean.TRUE.equals(item.getStockReserved()))
                .toList();
        if (sold.isEmpty()) {
            return;
        }
        Map<Long, InventoryRepository.AlertLevel> levels = new HashMap<>();
        inventoryRepository.findAlertLevels(sold.stream().map(i -> i.getProductVariant().getId()).toList())
                .forEach(level -> levels.put(level.getVariantId(), level));
        List<Alert> alerts = new ArrayList<>();
        for (OrderItem item : sold) {
            InventoryRepository.AlertLevel level = levels.get(item.getProductVariant().getId());
            if (level == null) {
                continue;
            }
            Alert alert = crossing(item.getProductVariant(), level.getQuantity(), level.getMinQuantity(),
                    item.getQuantity() == null ? 0 : item.getQuantity());
            if (alert != null) {
                alerts.add(alert);
            }
        }
        if (!alerts.isEmpty()) {
            notificationService.notifyStoreManagers(
                    order.getStore(),
                    NotificationType.LOW_STOCK,
                    NotificationMessages.lowStock(alerts.stream()
                            .map(a -> new NotificationMessages.StockLine(
                                    StockService.label(a.variant()), a.remaining(), a.soldOut()))
                            .toList()),
                    AppLinks.inventory(),
                    order.getId());
        }
    }

    /** {@code null} khi lần bán này không làm tồn kho vượt qua ngưỡng nào. */
    static Alert crossing(ProductVariant variant, Integer quantity, Integer min, int sold) {
        int after = quantity == null ? 0 : quantity;
        int before = after + sold;
        if (after <= 0 && before > 0) {
            return new Alert(variant, 0, min);
        }
        if (min != null && min > 0 && after <= min && before > min) {
            return new Alert(variant, after, min);
        }
        return null;
    }
}
