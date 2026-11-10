package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.CoinTransactionType;
import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.response.CheckInResponse;
import com.example.elsa_store.entity.DailyCheckIn;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.repository.DailyCheckInRepository;
import com.example.elsa_store.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/** Điểm danh nhận xu: chuỗi 7 ngày, đứt thì về ngày 1, hết vòng thì sang vòng mới, mỗi ngày một lần. */
class DailyCheckInsTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    private final DailyCheckInRepository repository = mock(DailyCheckInRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final CoinWallet wallet = mock(CoinWallet.class);
    private final Clock clock = Clock.fixed(TODAY.atStartOfDay(VN).plusHours(9).toInstant(), VN);
    private final DailyCheckIns service = new DailyCheckIns(repository, users, wallet, clock);

    private User buyer;

    @BeforeEach
    void setUp() {
        buyer = User.builder().username("khach").role(Role.USER).build();
        buyer.setId(7L);
        when(repository.save(any(DailyCheckIn.class))).thenAnswer(inv -> inv.getArgument(0));
        when(wallet.balance(buyer)).thenReturn(1_000);
    }

    private void lastCheckIn(LocalDate day, int streakDay) {
        DailyCheckIn last = DailyCheckIn.builder().user(buyer).checkDate(day).streakDay(streakDay).coins(1).build();
        when(repository.findFirstByUser_IdOrderByCheckDateDesc(7L)).thenReturn(Optional.of(last));
    }

    @Test
    void firstCheckInStartsTheStreakAndCreditsTheWallet() {
        when(repository.findFirstByUser_IdOrderByCheckDateDesc(7L)).thenReturn(Optional.empty());

        CheckInResponse res = service.checkIn(buyer);

        assertEquals(50, res.awarded());
        assertEquals(1, res.streak());
        assertTrue(res.checkedInToday());
        assertEquals(2, res.nextDay(), "lần kế tiếp (ngày mai) là ngày 2");
        verify(users).lockById(7L);
        verify(wallet).credit(eq(buyer), eq(50), eq(CoinTransactionType.DAILY_CHECK_IN), isNull(), eq("Điểm danh ngày 1/7"));
    }

    @Test
    void yesterdaysCheckInContinuesTheStreakWithABiggerReward() {
        lastCheckIn(TODAY.minusDays(1), 6);
        when(repository.existsByUser_IdAndCheckDate(7L, TODAY)).thenReturn(false);

        CheckInResponse res = service.checkIn(buyer);

        assertEquals(500, res.awarded(), "ngày 7 thưởng lớn nhất");
        assertEquals(7, res.streak());
        assertEquals(1, res.nextDay(), "xong ngày 7 thì ngày mai sang vòng mới");
    }

    @Test
    void missingADayOrFinishingTheWeekStartsOver() {
        lastCheckIn(TODAY.minusDays(2), 4);
        assertEquals(50, service.checkIn(buyer).awarded(), "bỏ lỡ một ngày → ngày 1");

        lastCheckIn(TODAY.minusDays(1), 7);
        assertEquals(50, service.checkIn(buyer).awarded(), "hết vòng 7 ngày → ngày 1");
    }

    @Test
    void secondCheckInTheSameDayIsRefused() {
        lastCheckIn(TODAY, 3);
        when(repository.existsByUser_IdAndCheckDate(7L, TODAY)).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> service.checkIn(buyer));

        assertEquals(ErrorCode.ALREADY_CHECKED_IN, ex.getErrorCode());
        verify(repository, never()).save(any());
        verify(wallet, never()).credit(any(), anyInt(), any(), any(), any());
    }

    @Test
    void statusShowsTodayStateAndWhatTheNextCheckInGives() {
        when(repository.findFirstByUser_IdOrderByCheckDateDesc(7L)).thenReturn(Optional.empty());
        CheckInResponse fresh = service.status(buyer);
        assertFalse(fresh.checkedInToday());
        assertEquals(0, fresh.streak());
        assertEquals(1, fresh.nextDay());
        assertEquals(50, fresh.nextReward());
        assertEquals(7, fresh.rewards().size());
        assertEquals(1_000, fresh.balance());
        assertNull(fresh.awarded());

        lastCheckIn(TODAY.minusDays(1), 2);
        CheckInResponse alive = service.status(buyer);
        assertFalse(alive.checkedInToday());
        assertEquals(2, alive.streak(), "chuỗi còn sống tới hết hôm nay");
        assertEquals(3, alive.nextDay());
        assertEquals(100, alive.nextReward());

        lastCheckIn(TODAY.minusDays(3), 5);
        CheckInResponse broken = service.status(buyer);
        assertEquals(0, broken.streak(), "đã đứt chuỗi");
        assertEquals(1, broken.nextDay());
    }

    @Test
    void onlyCustomersHaveCheckIns() {
        User staff = User.builder().username("nv").role(Role.STAFF).build();
        staff.setId(8L);
        assertEquals(ErrorCode.UN_AUTHORIZED, assertThrows(AppException.class, () -> service.checkIn(staff)).getErrorCode());
        assertEquals(ErrorCode.UN_AUTHORIZED, assertThrows(AppException.class, () -> service.status(staff)).getErrorCode());
        verifyNoInteractions(repository);
    }
}
