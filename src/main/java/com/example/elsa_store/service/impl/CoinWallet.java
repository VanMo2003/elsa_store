package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.CoinTransactionType;
import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.response.CoinWalletResponse;
import com.example.elsa_store.dto.response.PageResponse;
import com.example.elsa_store.entity.CoinTransaction;
import com.example.elsa_store.entity.Order;
import com.example.elsa_store.entity.Product;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.repository.CoinTransactionRepository;
import com.example.elsa_store.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Xu thưởng của khách (chỉ tài khoản {@code USER}): nhận {@code earnPercent}% tiền hàng thực trả khi đơn hoàn thành;
 * dùng 1 xu = 1đ, tối đa {@code maxRedeemPercent}% tiền hàng của đơn. Đơn huỷ / xoá thì hoàn xu đã dùng; trả hàng được
 * hoàn tiền thì thu lại xu đã nhận theo tỉ lệ. Mọi lần ghi sổ đều khoá dòng tài khoản để số dư không âm khi hai đơn đặt
 * cùng lúc.
 */
@Service
@Transactional
public class CoinWallet {

    private final CoinTransactionRepository repository;
    private final UserRepository userRepository;
    private final int earnPercent;
    private final int maxRedeemPercent;

    public CoinWallet(CoinTransactionRepository repository,
                      UserRepository userRepository,
                      @Value("${app.coins.earn-percent:1}") int earnPercent,
                      @Value("${app.coins.max-redeem-percent:50}") int maxRedeemPercent) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.earnPercent = Math.max(0, earnPercent);
        this.maxRedeemPercent = Math.min(100, Math.max(0, maxRedeemPercent));
    }

    /** Chỉ tài khoản khách có ví xu. */
    public static boolean eligible(User user) {
        return user != null && user.getId() != null && user.getRole() == Role.USER;
    }

    @Transactional(readOnly = true)
    public int balance(User user) {
        return eligible(user) ? (int) repository.balanceOf(user.getId()) : 0;
    }

    /** Số xu tối đa dùng được cho {@code goodsTotal} tiền hàng (sau khuyến mãi, chưa gồm phí giao). */
    @Transactional(readOnly = true)
    public int usable(User user, double goodsTotal) {
        if (!eligible(user) || goodsTotal <= 0) {
            return 0;
        }
        int cap = (int) Math.floor(goodsTotal * maxRedeemPercent / 100.0);
        return Math.max(0, Math.min(balance(user), cap));
    }

    /** Số xu nhận được cho {@code goodsPaid} tiền hàng thực trả. */
    public int rewardFor(double goodsPaid) {
        return goodsPaid <= 0 ? 0 : (int) Math.floor(goodsPaid * earnPercent / 100.0);
    }

    /** Xu sẽ nhận khi đơn hoàn thành (đơn chưa xong, chưa huỷ); null nếu không áp dụng. */
    public Integer expectedReward(Order order) {
        if (!eligible(order.getUser()) || order.getCoinsEarned() != null) {
            return null;
        }
        return rewardFor(goodsPaid(order));
    }

    /** Trừ xu lúc đặt đơn; không đủ số dư (vừa dùng ở đơn khác) thì báo lỗi. */
    public void redeem(User user, Order order, int coins) {
        if (coins <= 0) {
            return;
        }
        int balance = lockedBalance(user);
        if (balance < coins) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không đủ xu (còn " + balance + " xu)");
        }
        write(user, -coins, balance - coins, CoinTransactionType.REDEEM, order, "Dùng xu cho đơn " + order.getCode());
    }

    /** Hoàn xu đã dùng khi đơn bị huỷ / xoá — mỗi đơn một lần. */
    public void refundRedeemed(Order order) {
        Integer used = order.getCoinsUsed();
        User user = order.getUser();
        if (used == null || used <= 0 || !eligible(user) || order.getId() == null
                || repository.existsByOrder_IdAndType(order.getId(), CoinTransactionType.REFUND)) {
            return;
        }
        int balance = lockedBalance(user);
        write(user, used, balance + used, CoinTransactionType.REFUND, order, "Hoàn xu đơn huỷ " + order.getCode());
    }

    /** Cộng xu khi đơn hoàn thành — mỗi đơn một lần ({@code coinsEarned} đánh dấu đã cộng). */
    public void earn(Order order) {
        User user = order.getUser();
        if (!eligible(user) || order.getCoinsEarned() != null) {
            return;
        }
        int coins = rewardFor(goodsPaid(order));
        order.setCoinsEarned(coins);
        if (coins <= 0) {
            return;
        }
        int balance = lockedBalance(user);
        write(user, coins, balance + coins, CoinTransactionType.EARN, order, "Nhận xu từ đơn " + order.getCode());
    }

    /**
     * Trả hàng đã hoàn tiền: thu lại phần xu đã nhận tương ứng số tiền hoàn (cộng dồn các lần trả không vượt số đã
     * nhận). Ví không đủ (đã tiêu) thì chỉ thu phần còn lại, số dư không âm.
     */
    public void revokeForRefund(Order order, double refundAmount) {
        User user = order.getUser();
        Integer earned = order.getCoinsEarned();
        double paid = goodsPaid(order);
        if (!eligible(user) || earned == null || earned <= 0 || refundAmount <= 0 || paid <= 0 || order.getId() == null) {
            return;
        }
        long alreadyRevoked = -repository.sumForOrder(order.getId(), CoinTransactionType.REVOKE);
        long wanted = Math.round(earned * Math.min(1.0, refundAmount / paid));
        long revoke = Math.min(wanted, earned - alreadyRevoked);
        int balance = lockedBalance(user);
        int taken = (int) Math.min(revoke, balance);
        if (taken <= 0) {
            return;
        }
        write(user, -taken, balance - taken, CoinTransactionType.REVOKE, order,
                "Thu lại xu do trả hàng đơn " + order.getCode());
    }

    /** Cộng xu thưởng không gắn đơn (thưởng đánh giá…). */
    public void credit(User user, int amount, CoinTransactionType type, Product product, String note) {
        if (!eligible(user) || amount <= 0) {
            return;
        }
        int balance = lockedBalance(user);
        repository.save(CoinTransaction.builder()
                .user(user)
                .amount(amount)
                .balanceAfter(balance + amount)
                .type(type)
                .product(product)
                .note(note)
                .build());
    }

    /** Xoá đơn: giữ lịch sử xu nhưng bỏ liên kết tới đơn. */
    public void detach(Order order) {
        if (order.getId() != null) {
            repository.detachOrder(order.getId());
        }
    }

    @Transactional(readOnly = true)
    public CoinWalletResponse wallet(User user, int page, int size) {
        if (!eligible(user)) {
            return new CoinWalletResponse(0, earnPercent, maxRedeemPercent, PageResponse.from(Page.empty()));
        }
        var history = repository.findByUser_IdOrderByIdDesc(user.getId(),
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 50)));
        return new CoinWalletResponse(balance(user), earnPercent, maxRedeemPercent,
                PageResponse.from(history.map(CoinWallet::toResponse)));
    }

    static CoinWalletResponse.Entry toResponse(CoinTransaction t) {
        return new CoinWalletResponse.Entry(t.getId(), t.getAmount(), t.getBalanceAfter(), t.getType(),
                t.getOrder() == null ? null : t.getOrder().getId(),
                t.getOrder() == null ? null : t.getOrder().getCode(), t.getNote(), t.getCreatedAt());
    }

    /** Tiền hàng thực trả của đơn: đã trừ mọi khoản giảm (kể cả xu), không gồm phí giao. */
    static double goodsPaid(Order order) {
        double total = order.getFinalAmount() == null ? 0 : order.getFinalAmount();
        double shipping = order.getShippingFee() == null ? 0 : order.getShippingFee();
        return Math.max(0, total - shipping);
    }

    /** Khoá tài khoản (xếp hàng các lần ghi sổ của cùng khách) rồi đọc số dư mới nhất bằng truy vấn khoá. */
    private int lockedBalance(User user) {
        userRepository.lockById(user.getId());
        Number balance = repository.lockedBalanceOf(user.getId());
        return balance == null ? 0 : balance.intValue();
    }

    private void write(User user, int amount, int balanceAfter, CoinTransactionType type, Order order, String note) {
        repository.save(CoinTransaction.builder()
                .user(user)
                .amount(amount)
                .balanceAfter(balanceAfter)
                .type(type)
                .order(order)
                .note(note)
                .build());
    }
}
