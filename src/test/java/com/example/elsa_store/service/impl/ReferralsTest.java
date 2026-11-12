package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.CoinTransactionType;
import com.example.elsa_store.constant.NotificationType;
import com.example.elsa_store.constant.OrderStatus;
import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.request.UserRequest;
import com.example.elsa_store.dto.response.ReferralResponse;
import com.example.elsa_store.entity.Order;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.repository.*;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.service.AuthenticationService;
import com.example.elsa_store.service.FileStorageService;
import com.example.elsa_store.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/** Giới thiệu bạn bè: mã riêng, nhập mã khi đăng ký / trong 7 ngày đầu, thưởng một lần khi đơn đầu tiên đủ điều kiện. */
class ReferralsTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 10, 0);

    private final UserRepository users = mock(UserRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final CoinTransactionRepository coinBook = mock(CoinTransactionRepository.class);
    private final CoinWallet wallet = mock(CoinWallet.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final Referrals referrals = new Referrals(users, orders, coinBook, wallet, notifications, 5000, 2000, 100_000,
            Clock.fixed(NOW.atZone(VN).toInstant(), VN));

    private User inviter;
    private User friend;

    private static User customer(long id, String username, String fullName) {
        User u = User.builder().username(username).fullName(fullName).role(Role.USER).build();
        u.setId(id);
        u.setCreatedAt(NOW.minusDays(1));
        return u;
    }

    @BeforeEach
    void setUp() {
        inviter = customer(1L, "an123456", "Nguyễn Văn An");
        inviter.setReferralCode("ABC234");
        friend = customer(2L, "binh1234", "Trần Bình");
        when(users.findByReferralCode("ABC234")).thenReturn(Optional.of(inviter));
        when(users.markReferralRewarded(eq(2L), any())).thenReturn(1);
    }

    private Order completed(User buyer, double goods) {
        Order order = new Order();
        order.setUser(buyer);
        order.setFinalAmount(goods + 30_000);
        order.setShippingFee(30_000.0);
        return order;
    }

    @Test
    void codeIsCreatedOnceFromAnUnambiguousAlphabet() {
        ReferralResponse first = referrals.mine(friend);
        String code = first.code();
        assertEquals(6, code.length());
        assertTrue(code.chars().allMatch(c -> Referrals.ALPHABET.indexOf(c) >= 0), code);
        assertTrue(first.canApply(), "tài khoản mới, chưa có đơn hoàn thành");
        assertEquals(5000, first.referrerReward());
        assertEquals(2000, first.friendReward());
        assertEquals(100_000L, first.minOrderValue());
        assertEquals(code, referrals.mine(friend).code(), "mở lại không sinh mã mới");
        verify(users, times(1)).save(friend);
    }

    @Test
    void codeIsCaseInsensitiveButNeverYourOwnOrALockedAccount() {
        assertSame(inviter, referrals.resolve("  abc234 ", null));
        assertEquals(ErrorCode.INVALID_REFERRAL_CODE,
                assertThrows(AppException.class, () -> referrals.resolve("ABC234", inviter)).getErrorCode());
        assertEquals(ErrorCode.INVALID_REFERRAL_CODE,
                assertThrows(AppException.class, () -> referrals.resolve("ZZZZZZ", null)).getErrorCode());
        assertEquals(ErrorCode.INVALID_REFERRAL_CODE,
                assertThrows(AppException.class, () -> referrals.resolve(" ", null)).getErrorCode());
        inviter.setEnabled(false);
        assertEquals(ErrorCode.INVALID_REFERRAL_CODE,
                assertThrows(AppException.class, () -> referrals.resolve("ABC234", null)).getErrorCode());
    }

    @Test
    void codeCanBeAddedLaterOnlyInTheFirstWeekBeforeAnyCompletedOrder() {
        ReferralResponse res = referrals.apply(friend, "abc234");
        assertSame(inviter, friend.getReferredBy());
        assertEquals("Nguyễn V** A**", res.referredBy(), "tên người giới thiệu được che, không lộ độ dài");
        assertFalse(res.canApply());
        assertEquals(ErrorCode.REFERRAL_NOT_ALLOWED,
                assertThrows(AppException.class, () -> referrals.apply(friend, "ABC234")).getErrorCode());

        User old = customer(3L, "cuong123", "Cường");
        old.setCreatedAt(NOW.minusDays(8));
        assertEquals(ErrorCode.REFERRAL_NOT_ALLOWED,
                assertThrows(AppException.class, () -> referrals.apply(old, "ABC234")).getErrorCode());

        User buyer = customer(4L, "dung1234", "Dũng");
        when(orders.countByUser_IdAndStatusIn(eq(4L), any())).thenReturn(1L);
        assertEquals(ErrorCode.REFERRAL_NOT_ALLOWED,
                assertThrows(AppException.class, () -> referrals.apply(buyer, "ABC234")).getErrorCode());
    }

    @Test
    void firstQualifyingOrderPaysBothSidesOnce() {
        friend.setReferredBy(inviter);

        referrals.orderCompleted(completed(friend, 99_000));
        verifyNoInteractions(wallet);

        referrals.orderCompleted(completed(friend, 150_000));
        verify(wallet).credit(eq(inviter), eq(5000), eq(CoinTransactionType.REFERRAL_REWARD), isNull(), anyString());
        verify(wallet).credit(eq(friend), eq(2000), eq(CoinTransactionType.REFERRAL_WELCOME), isNull(), anyString());
        verify(notifications).notify(eq(inviter), eq(NotificationType.REFERRAL_REWARD), any(), anyString(), isNull());
        verify(notifications).notify(eq(friend), eq(NotificationType.REFERRAL_REWARD), any(), anyString(), isNull());
        assertEquals(NOW, friend.getReferralRewardedAt());

        referrals.orderCompleted(completed(friend, 300_000));
        verify(wallet, times(2)).credit(any(), anyInt(), any(), any(), any());
    }

    @Test
    void aConcurrentCompletionThatLostTheRaceAndLockedReferrersPayNothingExtra() {
        friend.setReferredBy(inviter);
        when(users.markReferralRewarded(eq(2L), any())).thenReturn(0);
        referrals.orderCompleted(completed(friend, 500_000));
        verifyNoInteractions(wallet);

        when(users.markReferralRewarded(eq(2L), any())).thenReturn(1);
        inviter.setEnabled(false);
        referrals.orderCompleted(completed(friend, 500_000));
        verify(wallet, never()).credit(eq(inviter), anyInt(), any(), any(), any());
        verify(wallet).credit(eq(friend), eq(2000), eq(CoinTransactionType.REFERRAL_WELCOME), isNull(), anyString());
    }

    @Test
    void onlyCustomersHaveReferrals() {
        User staff = User.builder().username("nv").role(Role.STAFF).build();
        staff.setId(9L);
        assertEquals(ErrorCode.UN_AUTHORIZED, assertThrows(AppException.class, () -> referrals.mine(staff)).getErrorCode());
        referrals.orderCompleted(completed(staff, 500_000));
        verifyNoInteractions(wallet);
    }

    @Test
    void namesAreMasked() {
        assertEquals("Nguyễn V** A**", Referrals.masked(inviter));
        assertEquals("Tr******", Referrals.masked(User.builder().username("x").fullName("Trần Bình".replace(" ", "")).build()));
        assertEquals("bi******", Referrals.masked(User.builder().username("binh1234").build()));
    }

    @Test
    void registrationWithAWrongCodeCreatesNoAccount() {
        UserAccountFactory accounts = mock(UserAccountFactory.class);
        UserServiceImpl service = new UserServiceImpl(mock(WishlistItemRepository.class), users,
                mock(PasswordEncoder.class), mock(NotificationRepository.class), mock(DeviceTokenRepository.class),
                mock(CustomerRepository.class), accounts, mock(AccessGuard.class), mock(FileStorageService.class),
                mock(AuthenticationService.class));
        service.setReferrals(referrals);
        UserRequest wrong = UserRequest.builder().username("moi12345").password("123456").referralCode("SAI999").build();

        assertEquals(ErrorCode.INVALID_REFERRAL_CODE,
                assertThrows(AppException.class, () -> service.create(wrong)).getErrorCode());
        verifyNoInteractions(accounts);

        User created = customer(5L, "moi12345", null);
        when(accounts.create(anyString(), anyString(), eq(Role.USER), any(), any(), any(), isNull())).thenReturn(created);
        service.create(UserRequest.builder().username("moi12345").password("123456").referralCode("abc234").build());
        assertSame(inviter, created.getReferredBy());
        verify(users).save(created);
        verify(orders, never()).countByUser_IdAndStatusIn(anyLong(), any());
    }

    @Test
    void completedStatusIsWhatCountsAsAnOrderForApplying() {
        referrals.mine(friend);
        verify(orders).countByUser_IdAndStatusIn(eq(2L), eq(java.util.List.of(OrderStatus.HOAN_THANH)));
    }
}
