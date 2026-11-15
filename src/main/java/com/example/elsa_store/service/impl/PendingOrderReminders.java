package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.NotificationType;
import com.example.elsa_store.entity.Order;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.repository.OrderRepository;
import com.example.elsa_store.service.NotificationService;
import com.example.elsa_store.utils.AppLinks;
import com.example.elsa_store.utils.NotificationMessages;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Nhắc cửa hàng xử lý đơn bị bỏ quên: đơn "chờ xác nhận" đặt quá {@code app.orders.pending-remind-hours} giờ (mặc định
 * 12; 0 = tắt) thì gửi người của cửa hàng một thông báo {@code ORDER_PENDING_REMINDER} — gộp theo cửa hàng (một tin cho
 * cả lô) để không thành spam. Mỗi đơn nhắc một lần ({@code orders.store_reminded_at}).
 */
@Slf4j
@Component
public class PendingOrderReminders {

    private static final int BATCH = 200;

    private final OrderRepository orderRepository;
    private final NotificationService notificationService;
    private final Clock clock;
    private final int hours;

    @Autowired
    public PendingOrderReminders(OrderRepository orderRepository,
                                 NotificationService notificationService,
                                 @Value("${app.orders.pending-remind-hours:12}") int hours) {
        this(orderRepository, notificationService, Clock.systemDefaultZone(), hours);
    }

    PendingOrderReminders(OrderRepository orderRepository, NotificationService notificationService, Clock clock, int hours) {
        this.orderRepository = orderRepository;
        this.notificationService = notificationService;
        this.clock = clock;
        this.hours = Math.max(hours, 0);
    }

    @Scheduled(
            fixedDelayString = "${app.orders.pending-reminder-interval-ms:900000}",
            initialDelayString = "${app.orders.pending-reminder-initial-delay-ms:300000}")
    @Transactional
    public int run() {
        if (hours == 0) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        List<Order> orders = orderRepository.findPendingToRemindStore(now.minusHours(hours), PageRequest.of(0, BATCH));
        Map<Long, List<Order>> byStore = new LinkedHashMap<>();
        Map<Long, Store> stores = new LinkedHashMap<>();
        for (Order order : orders) {
            Store store = order.getStore();
            byStore.computeIfAbsent(store.getId(), id -> new java.util.ArrayList<>()).add(order);
            stores.putIfAbsent(store.getId(), store);
            order.setStoreRemindedAt(now);
        }
        byStore.forEach((storeId, list) -> notificationService.notifyStore(stores.get(storeId),
                NotificationType.ORDER_PENDING_REMINDER, NotificationMessages.pendingOrders(list, hours), AppLinks.orders(),
                list.size() == 1 ? list.get(0).getId() : null));
        orderRepository.saveAll(orders);
        if (!orders.isEmpty()) {
            log.info("Nhắc {} cửa hàng xử lý {} đơn chờ xác nhận quá {} giờ", byStore.size(), orders.size(), hours);
        }
        return orders.size();
    }
}
