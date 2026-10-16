package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.NotificationType;
import com.example.elsa_store.entity.Cart;
import com.example.elsa_store.repository.CartRepository;
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
 * Nhắc khách còn hàng trong giỏ: giỏ không đổi quá {@code app.cart.reminder-hours} giờ thì gửi một thông báo
 * {@code CART_REMINDER} (mở giỏ hàng), mỗi lần bỏ quên chỉ nhắc một lần; khách thêm / sửa món thì được nhắc lại về sau.
 * Khách tắt nhóm "Nhắc giỏ hàng" ở Cài đặt thì không nhận. Đặt 0 để tắt.
 */
@Slf4j
@Component
public class CartReminders {

    private static final int BATCH = 100;

    private final CartRepository cartRepository;
    private final NotificationService notificationService;
    private final Clock clock;
    private final int reminderHours;

    @Autowired
    public CartReminders(CartRepository cartRepository,
                         NotificationService notificationService,
                         @Value("${app.cart.reminder-hours:24}") int reminderHours) {
        this(cartRepository, notificationService, Clock.systemDefaultZone(), reminderHours);
    }

    CartReminders(CartRepository cartRepository, NotificationService notificationService, Clock clock, int reminderHours) {
        this.cartRepository = cartRepository;
        this.notificationService = notificationService;
        this.clock = clock;
        this.reminderHours = Math.max(reminderHours, 0);
    }

    @Scheduled(
            fixedDelayString = "${app.cart.reminder-interval-ms:3600000}",
            initialDelayString = "${app.cart.reminder-initial-delay-ms:300000}")
    @Transactional
    public int run() {
        if (reminderHours == 0) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        List<Cart> carts = cartRepository.findAbandoned(now.minusHours(reminderHours), PageRequest.of(0, BATCH));
        for (Cart cart : carts) {
            notificationService.notify(cart.getUser(), NotificationType.CART_REMINDER,
                    NotificationMessages.cartReminder(cart), AppLinks.cart(), cart.getId());
            cart.setRemindedAt(now);
        }
        cartRepository.saveAll(carts);
        if (!carts.isEmpty()) {
            log.info("Nhắc giỏ hàng cho {} khách", carts.size());
        }
        return carts.size();
    }
}
