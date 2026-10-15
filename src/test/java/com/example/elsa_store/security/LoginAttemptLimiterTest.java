package com.example.elsa_store.security;

import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginAttemptLimiterTest {

    /** Đồng hồ chỉnh tay được để thử cửa sổ 15 phút mà không phải chờ. */
    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-14T08:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock);

    private void fail(String user, String ip, int times) {
        for (int i = 0; i < times; i++) {
            limiter.recordFailure(user, ip);
        }
    }

    @Test
    void blocksAnAccountAfterFiveFailuresAndReleasesItAfterTheWindow() {
        fail("khach001", "1.1.1.1", LoginAttemptLimiter.MAX_FAILURES_PER_ACCOUNT - 1);
        assertDoesNotThrow(() -> limiter.checkAllowed("khach001", "1.1.1.1"));

        limiter.recordFailure("khach001", "1.1.1.1");
        AppException ex = assertThrows(AppException.class, () -> limiter.checkAllowed("khach001", "9.9.9.9"));
        assertEquals(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS, ex.getErrorCode());
        // Tên đăng nhập không phân biệt hoa thường / khoảng trắng — không lách được bằng "KHACH001 ".
        assertThrows(AppException.class, () -> limiter.checkAllowed(" KHACH001 ", "9.9.9.9"));
        // Tài khoản khác không bị ảnh hưởng.
        assertDoesNotThrow(() -> limiter.checkAllowed("khach002", "1.1.1.1"));

        clock.now = clock.now.plus(LoginAttemptLimiter.WINDOW).plus(Duration.ofSeconds(1));
        assertDoesNotThrow(() -> limiter.checkAllowed("khach001", "1.1.1.1"));
    }

    @Test
    void successfulLoginClearsTheAccountsFailures() {
        fail("khach001", "1.1.1.1", LoginAttemptLimiter.MAX_FAILURES_PER_ACCOUNT - 1);
        limiter.recordSuccess("khach001");
        fail("khach001", "1.1.1.1", LoginAttemptLimiter.MAX_FAILURES_PER_ACCOUNT - 1);
        assertDoesNotThrow(() -> limiter.checkAllowed("khach001", "1.1.1.1"));
    }

    @Test
    void blocksAnIpGuessingManyAccounts() {
        for (int i = 0; i < LoginAttemptLimiter.MAX_FAILURES_PER_IP; i++) {
            limiter.recordFailure("user" + i, "6.6.6.6");
        }
        assertThrows(AppException.class, () -> limiter.checkAllowed("brand-new-user", "6.6.6.6"));
        assertDoesNotThrow(() -> limiter.checkAllowed("brand-new-user", "7.7.7.7"));
    }

    @Test
    void purgeDropsExpiredEntries() {
        fail("khach001", "1.1.1.1", LoginAttemptLimiter.MAX_FAILURES_PER_ACCOUNT);
        clock.now = clock.now.plus(Duration.ofHours(1));
        limiter.purgeStale();
        assertDoesNotThrow(() -> limiter.checkAllowed("khach001", "1.1.1.1"));
    }
}
