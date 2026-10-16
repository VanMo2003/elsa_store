package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.MemberTier;
import com.example.elsa_store.constant.NotificationType;
import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.response.CoinWalletResponse;
import com.example.elsa_store.entity.Order;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.repository.CoinTransactionRepository;
import com.example.elsa_store.repository.OrderRepository;
import com.example.elsa_store.repository.ReturnRequestRepository;
import com.example.elsa_store.repository.UserRepository;
import com.example.elsa_store.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/** Hạng thành viên: ngưỡng chi tiêu 365 ngày (trừ tiền hoàn), hệ số xu theo hạng trước đơn, báo khi lên hạng. */
class MemberTiersTest {

    private final OrderRepository orders = mock(OrderRepository.class);
    private final ReturnRequestRepository returns = mock(ReturnRequestRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final MemberTiers tiers = new MemberTiers(orders, returns, notifications);
    private final CoinTransactionRepository book = mock(CoinTransactionRepository.class);
    private final CoinWallet wallet = new CoinWallet(book, mock(UserRepository.class), 1, 50);

    private User buyer;
    private Order order;

    @BeforeEach
    void setUp() {
        wallet.setTiers(tiers);
        buyer = User.builder().username("khach01").role(Role.USER).build();
        buyer.setId(7L);
        order = new Order();
        order.setId(50L);
        order.setCode("ORD-50");
        order.setUser(buyer);
        order.setFinalAmount(1_030_000.0);
        order.setShippingFee(30_000.0);
        when(book.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void thresholdsPickTheHighestTierReached() {
        assertEquals(MemberTier.MEMBER, MemberTier.forSpending(1_999_999));
        assertEquals(MemberTier.SILVER, MemberTier.forSpending(2_000_000));
        assertEquals(MemberTier.GOLD, MemberTier.forSpending(14_999_999));
        assertEquals(MemberTier.DIAMOND, MemberTier.forSpending(20_000_000));
        assertNull(MemberTier.DIAMOND.next());
        assertEquals(MemberTier.GOLD, MemberTier.SILVER.next());
    }

    @Test
    void refundsCountAgainstSpendingAndStaffHaveNoTier() {
        when(orders.memberSpending(eq(7L), any(), isNull())).thenReturn(2_500_000.0);
        when(returns.memberRefunds(eq(7L), any(), isNull())).thenReturn(600_000.0);
        assertEquals(1_900_000, tiers.spending(buyer, null));
        assertEquals(MemberTier.MEMBER, tiers.tierOf(buyer, null));

        User staff = User.builder().username("nv").role(Role.STAFF).build();
        staff.setId(8L);
        assertEquals(0, tiers.spending(staff, null));
        verify(orders, never()).memberSpending(eq(8L), any(), any());
    }

    @Test
    void higherTiersEarnMoreCoinsOnTheTierHeldBeforeTheOrder() {
        when(orders.memberSpending(eq(7L), any(), any())).thenReturn(5_000_000.0);
        assertEquals(20_000, wallet.rewardFor(buyer, 1_000_000, null), "Vàng: 1% × 200%");
        assertEquals(10_000, wallet.rewardFor(1_000_000), "hạng thường");
        assertEquals(0, wallet.rewardFor(User.builder().role(Role.STAFF).build(), 1_000_000, null));

        wallet.earn(order);
        assertEquals(20_000, order.getCoinsEarned());
        verify(notifications, never()).notify(any(), any(), any(), any(), any());
    }

    @Test
    void anOrderThatCrossesAThresholdAnnouncesTheNewTier() {
        when(orders.memberSpending(eq(7L), any(), eq(50L))).thenReturn(1_500_000.0);

        wallet.earn(order);

        assertEquals(10_000, order.getCoinsEarned(), "đơn lên hạng vẫn tính xu theo hạng cũ");
        verify(notifications).notify(eq(buyer), eq(NotificationType.TIER_UP),
                argThat(m -> m.title().contains("Bạc") && m.body().contains("x1,5")), eq("elsastore://coins"), eq(50L));
    }

    @Test
    void walletShowsTierProgress() {
        when(book.findByUser_IdOrderByIdDesc(eq(7L), any())).thenReturn(Page.empty());
        when(orders.memberSpending(eq(7L), any(), isNull())).thenReturn(3_200_000.0);

        CoinWalletResponse res = wallet.wallet(buyer, 0, 10);

        assertEquals(MemberTier.SILVER, res.tier());
        assertEquals(1.5, res.earnRate());
        assertEquals(3_200_000, res.spending());
        assertEquals(MemberTier.GOLD, res.nextTier());
        assertEquals(5_000_000L, res.nextTierSpending());
    }
}
