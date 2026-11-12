package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.CoinTransactionType;
import com.example.elsa_store.constant.NotificationType;
import com.example.elsa_store.constant.OrderStatus;
import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.response.ReferralResponse;
import com.example.elsa_store.entity.Order;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.repository.CoinTransactionRepository;
import com.example.elsa_store.repository.OrderRepository;
import com.example.elsa_store.repository.UserRepository;
import com.example.elsa_store.service.NotificationService;
import com.example.elsa_store.utils.AppLinks;
import com.example.elsa_store.utils.NotificationMessages;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Giới thiệu bạn bè: mỗi khách có một mã; người mới nhập mã khi đăng ký (hoặc trong 7 ngày đầu). Khi người mới có đơn
 * hoàn thành với tiền hàng thực trả từ {@code minOrderValue}, người giới thiệu nhận {@code referrerReward} xu, người mới
 * nhận {@code friendReward} xu — mỗi người mới một lần.
 */
@Service
@Transactional
public class Referrals {

    /** Bỏ các ký tự dễ nhầm (0/O, 1/I/L). */
    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    static final int CODE_LENGTH = 6;
    static final int APPLY_DAYS = 7;

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final CoinTransactionRepository coinBook;
    private final CoinWallet coinWallet;
    private final NotificationService notificationService;
    private final int referrerReward;
    private final int friendReward;
    private final long minOrderValue;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public Referrals(UserRepository userRepository,
                     OrderRepository orderRepository,
                     CoinTransactionRepository coinBook,
                     CoinWallet coinWallet,
                     NotificationService notificationService,
                     @Value("${app.referral.referrer-reward:5000}") int referrerReward,
                     @Value("${app.referral.friend-reward:2000}") int friendReward,
                     @Value("${app.referral.min-order-value:100000}") long minOrderValue) {
        this(userRepository, orderRepository, coinBook, coinWallet, notificationService, referrerReward, friendReward,
                minOrderValue, Clock.systemDefaultZone());
    }

    Referrals(UserRepository userRepository, OrderRepository orderRepository, CoinTransactionRepository coinBook,
              CoinWallet coinWallet, NotificationService notificationService, int referrerReward, int friendReward,
              long minOrderValue, Clock clock) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.coinBook = coinBook;
        this.coinWallet = coinWallet;
        this.notificationService = notificationService;
        this.referrerReward = Math.max(0, referrerReward);
        this.friendReward = Math.max(0, friendReward);
        this.minOrderValue = Math.max(0, minOrderValue);
        this.clock = clock;
    }

    /** Mã giới thiệu và kết quả của khách; lần đầu mở thì sinh mã. */
    public ReferralResponse mine(User user) {
        requireCustomer(user);
        if (user.getReferralCode() == null) {
            user.setReferralCode(newCode());
            userRepository.save(user);
        }
        User referrer = user.getReferredBy();
        return new ReferralResponse(
                user.getReferralCode(),
                userRepository.countByReferredBy_Id(user.getId()),
                userRepository.countByReferredBy_IdAndReferralRewardedAtIsNotNull(user.getId()),
                coinBook.sumForUser(user.getId(), CoinTransactionType.REFERRAL_REWARD),
                referrerReward,
                friendReward,
                minOrderValue,
                referrer == null ? null : masked(referrer),
                canApply(user));
    }

    /**
     * Người giới thiệu ứng với mã (không phân biệt hoa thường, bỏ khoảng trắng); {@code self} là tài khoản đang nhập mã
     * (null khi đang đăng ký). Mã sai, của tài khoản bị khoá / không phải khách, hoặc của chính mình thì báo 1031.
     */
    @Transactional(readOnly = true)
    public User resolve(String code, User self) {
        String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        User referrer = normalized.isEmpty() ? null : userRepository.findByReferralCode(normalized).orElse(null);
        if (referrer == null || referrer.getRole() != Role.USER || !referrer.isEnabled()
                || (self != null && referrer.getId().equals(self.getId()))) {
            throw new AppException(ErrorCode.INVALID_REFERRAL_CODE);
        }
        return referrer;
    }

    /** Nhập mã sau khi đăng ký: chỉ khi chưa có người giới thiệu, tài khoản tạo trong 7 ngày và chưa có đơn hoàn thành. */
    public ReferralResponse apply(User user, String code) {
        requireCustomer(user);
        if (!canApply(user)) {
            throw new AppException(ErrorCode.REFERRAL_NOT_ALLOWED);
        }
        user.setReferredBy(resolve(code, user));
        userRepository.save(user);
        return mine(user);
    }

    /**
     * Đơn vừa hoàn thành: nếu là đơn đủ điều kiện đầu tiên của người được mời thì trả thưởng cho cả hai (chỉ một lần —
     * đánh dấu bằng câu update có điều kiện nên hai đơn hoàn thành cùng lúc cũng không trả hai lần).
     */
    public void orderCompleted(Order order) {
        User friend = order.getUser();
        if (!CoinWallet.eligible(friend) || friend.getReferredBy() == null || friend.getReferralRewardedAt() != null
                || CoinWallet.goodsPaid(order) < minOrderValue) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (userRepository.markReferralRewarded(friend.getId(), now) != 1) {
            return;
        }
        friend.setReferralRewardedAt(now);
        User referrer = friend.getReferredBy();
        String friendName = masked(friend);
        if (referrer.isEnabled() && referrerReward > 0) {
            coinWallet.credit(referrer, referrerReward, CoinTransactionType.REFERRAL_REWARD, null,
                    "Bạn " + friendName + " hoàn thành đơn đầu tiên");
            notificationService.notify(referrer, NotificationType.REFERRAL_REWARD,
                    NotificationMessages.referralReward(friendName, referrerReward), AppLinks.coins(), null);
        }
        if (friendReward > 0) {
            coinWallet.credit(friend, friendReward, CoinTransactionType.REFERRAL_WELCOME, null,
                    "Thưởng bạn mới qua mã giới thiệu");
            notificationService.notify(friend, NotificationType.REFERRAL_REWARD,
                    NotificationMessages.referralWelcome(friendReward), AppLinks.coins(), null);
        }
    }

    private boolean canApply(User user) {
        if (user.getReferredBy() != null || user.getCreatedAt() == null
                || user.getCreatedAt().isBefore(LocalDateTime.now(clock).minusDays(APPLY_DAYS))) {
            return false;
        }
        return orderRepository.countByUser_IdAndStatusIn(user.getId(), List.of(OrderStatus.HOAN_THANH)) == 0;
    }

    private String newCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder code = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            if (!userRepository.existsByReferralCode(code.toString())) {
                return code.toString();
            }
        }
        throw new IllegalStateException("Cannot generate a unique referral code");
    }

    /** "Nguyễn Văn An" → "Nguyễn V*** A**"; không có họ tên thì che tên đăng nhập. */
    static String masked(User user) {
        String name = user.getFullName() == null || user.getFullName().isBlank() ? user.getUsername() : user.getFullName();
        String[] words = name.trim().split("\\s+");
        StringBuilder out = new StringBuilder(words[0]);
        for (int i = 1; i < words.length; i++) {
            out.append(' ').append(words[i].charAt(0)).append("*".repeat(Math.max(2, words[i].length() - 1)));
        }
        if (words.length == 1 && name.length() > 2) {
            return name.substring(0, 2) + "*".repeat(name.length() - 2);
        }
        return out.toString();
    }

    private static void requireCustomer(User user) {
        if (!CoinWallet.eligible(user)) {
            throw new AppException(ErrorCode.UN_AUTHORIZED);
        }
    }
}
