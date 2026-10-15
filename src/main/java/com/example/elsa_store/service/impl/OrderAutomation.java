package com.example.elsa_store.service.impl;

import com.example.elsa_store.config.VNPayConfig;
import com.example.elsa_store.constant.OrderStatus;
import com.example.elsa_store.repository.OrderRepository;
import com.example.elsa_store.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Tự xử lý đơn bị bỏ quên, chạy mỗi 5 phút:
 * <ul>
 *   <li>Đơn chọn VNPay mà chưa trả sau {@code app.orders.unpaid-online-cancel-minutes} phút (tính từ lúc đặt và lần mở
 *       link thanh toán gần nhất) thì huỷ để trả hàng về kho. Phải dài hơn hạn của link VNPay
 *       ({@link VNPayConfig#EXPIRE_MINUTES}) để không huỷ đơn khách đang trả dở.</li>
 *   <li>Đơn đang giao quá {@code app.orders.auto-complete-days} ngày mà khách chưa bấm "đã nhận hàng" thì tự hoàn
 *       thành (đơn COD coi như đã thu tiền).</li>
 * </ul>
 * Đặt 0 để tắt từng việc. Mỗi đơn đổi trong một transaction riêng ({@link OrderService#autoTransition}), lỗi một đơn
 * không chặn các đơn khác.
 */
@Slf4j
@Component
public class OrderAutomation {

    static final String UNPAID_REASON = "Quá hạn thanh toán VNPay";
    static final String AUTO_COMPLETE_NOTE = "Tự hoàn thành vì khách chưa xác nhận sau %d ngày giao";
    private static final int BATCH = 100;

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final Clock clock;
    private final int unpaidCancelMinutes;
    private final int autoCompleteDays;

    @Autowired
    public OrderAutomation(
            OrderRepository orderRepository,
            OrderService orderService,
            @Value("${app.orders.unpaid-online-cancel-minutes:30}") int unpaidCancelMinutes,
            @Value("${app.orders.auto-complete-days:7}") int autoCompleteDays) {
        this(orderRepository, orderService, Clock.systemDefaultZone(), unpaidCancelMinutes, autoCompleteDays);
    }

    OrderAutomation(
            OrderRepository orderRepository,
            OrderService orderService,
            Clock clock,
            int unpaidCancelMinutes,
            int autoCompleteDays) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.clock = clock;
        this.unpaidCancelMinutes = unpaidCancelMinutes > 0
                ? Math.max(unpaidCancelMinutes, VNPayConfig.EXPIRE_MINUTES + 5)
                : 0;
        this.autoCompleteDays = Math.max(autoCompleteDays, 0);
    }

    @Scheduled(
            fixedDelayString = "${app.orders.automation-interval-ms:300000}",
            initialDelayString = "${app.orders.automation-initial-delay-ms:120000}")
    public void run() {
        cancelUnpaidOnlineOrders();
        completeLongDeliveries();
    }

    int cancelUnpaidOnlineOrders() {
        if (unpaidCancelMinutes == 0) {
            return 0;
        }
        LocalDateTime cutoff = LocalDateTime.now(clock).minusMinutes(unpaidCancelMinutes);
        List<Long> ids = orderRepository.findUnpaidOnlineOrderIds(cutoff, PageRequest.of(0, BATCH));
        return apply(ids, OrderStatus.CHUA_XAC_NHAN, OrderStatus.DA_HUY, UNPAID_REASON);
    }

    int completeLongDeliveries() {
        if (autoCompleteDays == 0) {
            return 0;
        }
        LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(autoCompleteDays);
        List<Long> ids = orderRepository.findLongShippingOrderIds(cutoff, PageRequest.of(0, BATCH));
        return apply(ids, OrderStatus.DANG_GIAO, OrderStatus.HOAN_THANH, AUTO_COMPLETE_NOTE.formatted(autoCompleteDays));
    }

    private int apply(List<Long> ids, OrderStatus from, OrderStatus to, String note) {
        int done = 0;
        for (Long id : ids) {
            try {
                if (orderService.autoTransition(id, from, to, note)) {
                    done++;
                }
            } catch (RuntimeException e) {
                log.error("Cannot move order {} from {} to {} automatically", id, from, to, e);
            }
        }
        if (done > 0) {
            log.info("Moved {} order(s) from {} to {} automatically ({})", done, from, to, note);
        }
        return done;
    }
}
