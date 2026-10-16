package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.MemberTier;
import com.example.elsa_store.constant.NotificationType;
import com.example.elsa_store.entity.Order;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.repository.OrderRepository;
import com.example.elsa_store.repository.ReturnRequestRepository;
import com.example.elsa_store.service.NotificationService;
import com.example.elsa_store.utils.AppLinks;
import com.example.elsa_store.utils.NotificationMessages;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Tính hạng thành viên của khách và báo khi khách vừa lên hạng sau một đơn hoàn thành. */
@Service
@Transactional(readOnly = true)
public class MemberTiers {

    /** Cửa sổ tính chi tiêu: 365 ngày gần nhất. */
    static final int WINDOW_DAYS = 365;

    private final OrderRepository orderRepository;
    private final ReturnRequestRepository returnRepository;
    private final NotificationService notificationService;

    public MemberTiers(OrderRepository orderRepository,
                       ReturnRequestRepository returnRepository,
                       NotificationService notificationService) {
        this.orderRepository = orderRepository;
        this.returnRepository = returnRepository;
        this.notificationService = notificationService;
    }

    /** Tiền hàng thực trả của các đơn hoàn thành trong cửa sổ, trừ tiền đã hoàn; bỏ qua đơn {@code excludeOrderId}. */
    public double spending(User user, Long excludeOrderId) {
        if (!CoinWallet.eligible(user)) {
            return 0;
        }
        LocalDateTime since = LocalDateTime.now().minusDays(WINDOW_DAYS);
        double paid = orderRepository.memberSpending(user.getId(), since, excludeOrderId);
        double refunded = returnRepository.memberRefunds(user.getId(), since, excludeOrderId);
        return Math.max(0, paid - refunded);
    }

    public MemberTier tierOf(User user, Long excludeOrderId) {
        return MemberTier.forSpending(spending(user, excludeOrderId));
    }

    /**
     * Đơn vừa hoàn thành: hạng trước đơn này quyết định số xu của chính nó; nếu cộng đơn này vào làm khách lên hạng thì
     * báo "Chúc mừng" (mở ví xu). Trả về hạng trước đơn.
     */
    @Transactional
    public MemberTier completing(Order order) {
        User user = order.getUser();
        double before = spending(user, order.getId());
        MemberTier tier = MemberTier.forSpending(before);
        MemberTier after = MemberTier.forSpending(before + CoinWallet.goodsPaid(order));
        if (CoinWallet.eligible(user) && after.ordinal() > tier.ordinal()) {
            notificationService.notify(user, NotificationType.TIER_UP, NotificationMessages.tierUp(after),
                    AppLinks.coins(), order.getId());
        }
        return tier;
    }
}
