package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.CoinTransactionType;
import com.example.elsa_store.constant.Role;
import com.example.elsa_store.entity.CoinTransaction;
import com.example.elsa_store.entity.Order;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.repository.CoinTransactionRepository;
import com.example.elsa_store.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Ví xu: số dư từ sổ, dùng tối đa 50%, thu hồi khi trả hàng không vượt số đã nhận và không làm âm ví. */
class CoinWalletTest {

    private final CoinTransactionRepository book = mock(CoinTransactionRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final CoinWallet wallet = new CoinWallet(book, users, 1, 50);
    private final List<CoinTransaction> saved = new ArrayList<>();

    private User buyer;
    private Order order;

    @BeforeEach
    void setUp() {
        buyer = User.builder().username("khach01").role(Role.USER).build();
        buyer.setId(7L);
        order = new Order();
        order.setId(50L);
        order.setCode("ORD-1");
        order.setUser(buyer);
        order.setFinalAmount(530_000.0);
        order.setShippingFee(30_000.0);
        when(book.save(any())).thenAnswer(inv -> {
            saved.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
    }

    @Test
    void onlyCustomersHaveAWalletAndUsageIsCapped() {
        when(book.balanceOf(7L)).thenReturn(400_000L);
        when(book.lockedBalanceOf(7L)).thenReturn(400_000L);
        assertEquals(250_000, wallet.usable(buyer, 500_000), "50% tiền hàng");
        assertEquals(100_000, wallet.usable(buyer, 200_000));
        when(book.balanceOf(7L)).thenReturn(40_000L);
        when(book.lockedBalanceOf(7L)).thenReturn(40_000L);
        assertEquals(40_000, wallet.usable(buyer, 500_000), "không quá số dư");

        User staff = User.builder().username("nv").role(Role.STAFF).build();
        staff.setId(8L);
        assertEquals(0, wallet.usable(staff, 500_000));
        assertEquals(0, wallet.balance(staff));
        assertEquals(5_009, wallet.rewardFor(500_999.9), "làm tròn xuống");
    }

    @Test
    void redeemFailsWhenTheBalanceWasSpentMeanwhile() {
        when(book.balanceOf(7L)).thenReturn(1_000L);
        when(book.lockedBalanceOf(7L)).thenReturn(1_000L);
        AppException ex = assertThrows(AppException.class, () -> wallet.redeem(buyer, order, 5_000));
        assertTrue(ex.getMessage().contains("1000"), ex.getMessage());
        verify(users).lockById(7L);
        assertTrue(saved.isEmpty());
    }

    @Test
    void refundsRevokeProportionallyAndNeverMoreThanEarnedOrBelowZero() {
        order.setCoinsEarned(5_000);
        when(book.balanceOf(7L)).thenReturn(10_000L);
        when(book.lockedBalanceOf(7L)).thenReturn(10_000L);
        when(book.sumForOrder(50L, CoinTransactionType.REVOKE)).thenReturn(0L);
        wallet.revokeForRefund(order, 250_000); // nửa tiền hàng
        assertEquals(-2_500, saved.getLast().getAmount());

        when(book.sumForOrder(50L, CoinTransactionType.REVOKE)).thenReturn(-2_500L);
        wallet.revokeForRefund(order, 500_000); // hoàn tiếp cả phần còn lại: chỉ thu nốt 2.500
        assertEquals(-2_500, saved.getLast().getAmount());

        when(book.sumForOrder(50L, CoinTransactionType.REVOKE)).thenReturn(0L);
        when(book.balanceOf(7L)).thenReturn(800L);
        when(book.lockedBalanceOf(7L)).thenReturn(800L);
        wallet.revokeForRefund(order, 500_000);
        assertEquals(-800, saved.getLast().getAmount(), "đã tiêu bớt xu: chỉ thu phần còn lại");
        assertEquals(0, saved.getLast().getBalanceAfter());
    }

    @Test
    void refundOfUsedCoinsHappensOncePerOrder() {
        order.setCoinsUsed(3_000);
        when(book.balanceOf(7L)).thenReturn(0L);
        when(book.lockedBalanceOf(7L)).thenReturn(0L);
        when(book.existsByOrder_IdAndType(50L, CoinTransactionType.REFUND)).thenReturn(false, true);
        wallet.refundRedeemed(order);
        wallet.refundRedeemed(order);
        assertEquals(1, saved.size());
        assertEquals(3_000, saved.getFirst().getAmount());
        verify(book, times(2)).existsByOrder_IdAndType(eq(50L), eq(CoinTransactionType.REFUND));
    }
}
