package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.NotificationType;
import com.example.elsa_store.entity.Order;
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
import java.util.List;

/**
 * Nhắc khách bấm "Đã nhận hàng": đơn đang giao được {@code app.orders.auto-complete-days} − 2 ngày (ít nhất 1 ngày) thì
 * gửi một thông báo {@code ORDER_DELIVERY_CHECK} kèm số ngày còn lại trước khi đơn tự hoàn thành ({@link OrderAutomation}).
 * Mỗi đơn nhắc một lần ({@code orders.delivery_reminded_at}); tắt tự hoàn thành (0) thì cũng không nhắc.
 */
@Slf4j
@Component
public class DeliveryReminders {

    static final int LEAD_DAYS = 2;
    private static final int BATCH = 100;

    private final OrderRepository orderRepository;
    private final NotificationService notificationService;
    private final Clock clock;
    private final int autoCompleteDays;

    @Autowired
    public DeliveryReminders(OrderRepository orderRepository,
                             NotificationService notificationService,
                             @Value("${app.orders.auto-complete-days:7}") int autoCompleteDays) {
        this(orderRepository, notificationService, Clock.systemDefaultZone(), autoCompleteDays);
    }

    DeliveryReminders(OrderRepository orderRepository, NotificationService notificationService, Clock clock,
                      int autoCompleteDays) {
        this.orderRepository = orderRepository;
        this.notificationService = notificationService;
        this.clock = clock;
        this.autoCompleteDays = Math.max(autoCompleteDays, 0);
    }

    /** Số ngày đang giao thì nhắc. */
    int remindAfterDays() {
        return Math.max(1, autoCompleteDays - LEAD_DAYS);
    }

    @Scheduled(
            fixedDelayString = "${app.orders.delivery-reminder-interval-ms:3600000}",
            initialDelayString = "${app.orders.delivery-reminder-initial-delay-ms:240000}")
    @Transactional
    public int run() {
        if (autoCompleteDays == 0) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        int daysLeft = Math.max(autoCompleteDays - remindAfterDays(), 0);
        List<Order> orders = orderRepository.findShippingToRemind(now.minusDays(remindAfterDays()), PageRequest.of(0, BATCH));
        for (Order order : orders) {
            notificationService.notify(order.getUser(), NotificationType.ORDER_DELIVERY_CHECK,
                    NotificationMessages.deliveryCheck(order, daysLeft), AppLinks.orders(), order.getId());
            order.setDeliveryRemindedAt(now);
        }
        orderRepository.saveAll(orders);
        if (!orders.isEmpty()) {
            log.info("Nhắc {} khách xác nhận đã nhận hàng", orders.size());
        }
        return orders.size();
    }
}
