package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.response.PasswordResetResponse;
import com.example.elsa_store.entity.PasswordResetCode;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.event.PasswordResetRequestedEvent;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.repository.PasswordResetCodeRepository;
import com.example.elsa_store.repository.UserRepository;
import com.example.elsa_store.security.LoginAttemptLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PasswordResetServiceImplTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-14T03:00:00Z"), VN);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    /** Băm giả: đủ để kiểm tra mã không bị lưu dạng thô mà không tốn thời gian như BCrypt. */
    private static final PasswordEncoder ENCODER = new PasswordEncoder() {
        @Override
        public String encode(CharSequence raw) {
            return "h:" + new StringBuilder(raw).reverse();
        }

        @Override
        public boolean matches(CharSequence raw, String encoded) {
            return encode(raw).equals(encoded);
        }
    };

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordResetCodeRepository codes = mock(PasswordResetCodeRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final LoginAttemptLimiter limiter = mock(LoginAttemptLimiter.class);
    private final PasswordResetServiceImpl service =
            new PasswordResetServiceImpl(users, codes, ENCODER, events, limiter, CLOCK);
    private final List<PasswordResetCode> saved = new ArrayList<>();

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().username("khach001").email("Khach1@Example.com").fullName("Khach 1").role(Role.USER)
                .password("h:old").build();
        user.setId(7L);
        when(users.findByUsername(anyString())).thenReturn(Optional.empty());
        when(users.findByUsername("khach001")).thenReturn(Optional.of(user));
        when(users.findFirstByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(users.findFirstByEmailIgnoreCase("khach1@example.com")).thenReturn(Optional.of(user));
        when(codes.findFirstByUser_IdOrderByIdDesc(anyLong())).thenReturn(Optional.empty());
        when(codes.save(any(PasswordResetCode.class))).thenAnswer(inv -> {
            PasswordResetCode c = inv.getArgument(0);
            if (!saved.contains(c)) {
                c.setCreatedAt(NOW);
                saved.add(c);
            }
            return c;
        });
        when(codes.saveAndFlush(any(PasswordResetCode.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /** Gửi mã rồi trả mã thô lấy từ sự kiện email. */
    private String requestCode() {
        service.requestCode("khach001", "1.1.1.1");
        ArgumentCaptor<PasswordResetRequestedEvent> event = ArgumentCaptor.forClass(PasswordResetRequestedEvent.class);
        verify(events, atLeastOnce()).publishEvent(event.capture());
        when(codes.findFirstByUser_IdOrderByIdDesc(7L)).thenReturn(Optional.of(saved.getLast()));
        return event.getValue().code();
    }

    @Test
    void codeIsSentByEmailAndStoredOnlyAsHash() {
        PasswordResetResponse res = service.requestCode("khach001", "1.1.1.1");

        assertEquals(60, res.getResendAfterSeconds());
        assertEquals(10, res.getValidMinutes());
        ArgumentCaptor<PasswordResetRequestedEvent> event = ArgumentCaptor.forClass(PasswordResetRequestedEvent.class);
        verify(events).publishEvent(event.capture());
        String code = event.getValue().code();
        assertTrue(code.matches("\\d{6}"), code);
        assertEquals("Khach1@Example.com", event.getValue().email());
        assertFalse(event.getValue().toString().contains(code), "mã không lọt vào log");
        PasswordResetCode stored = saved.getFirst();
        assertNotEquals(code, stored.getCodeHash());
        assertTrue(ENCODER.matches(code, stored.getCodeHash()));
        assertEquals(NOW.plusMinutes(10), stored.getExpiresAt());
        verify(codes).invalidateActive(7L, NOW);
    }

    @Test
    void unknownAccountsGetTheSameAnswerButNoEmail() {
        PasswordResetResponse res = service.requestCode("ai-do", "1.1.1.1");
        assertEquals(60, res.getResendAfterSeconds());
        user.setEmail(null);
        service.requestCode("khach001", "1.1.1.1");
        user.setEmail("k@x.com");
        user.setEnabled(false);
        service.requestCode("khach001", "1.1.1.1");
        verify(events, never()).publishEvent(any(PasswordResetRequestedEvent.class));
        verify(codes, never()).save(any());
    }

    @Test
    void emailWorksAsIdentifierRegardlessOfCase() {
        service.requestCode("  khach1@example.com ", "1.1.1.1");
        verify(events).publishEvent(any(PasswordResetRequestedEvent.class));
    }

    @Test
    void codesCannotBeRequestedTooOften() {
        requestCode();
        service.requestCode("khach001", "1.1.1.1"); // trong vòng 60 giây
        verify(events, times(1)).publishEvent(any(PasswordResetRequestedEvent.class));

        saved.getLast().setCreatedAt(NOW.minusMinutes(5));
        when(codes.countByUser_IdAndCreatedAtAfter(eq(7L), any())).thenReturn(5L);
        service.requestCode("khach001", "1.1.1.1"); // đủ 5 mã trong giờ
        verify(events, times(1)).publishEvent(any(PasswordResetRequestedEvent.class));
    }

    @Test
    void oneIpCannotSpamResetEmails() {
        for (int i = 0; i < 20; i++) {
            service.requestCode("nobody" + i, "9.9.9.9");
        }
        AppException ex = assertThrows(AppException.class, () -> service.requestCode("khach001", "9.9.9.9"));
        assertEquals(ErrorCode.TOO_MANY_RESET_REQUESTS, ex.getErrorCode());
        service.requestCode("khach001", "8.8.8.8");
    }

    @Test
    void wrongCodesBurnTheCodeAfterFiveTries() {
        String code = requestCode();
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 1; i <= 5; i++) {
            AppException ex = assertThrows(AppException.class, () -> service.verifyCode("khach001", wrong));
            assertEquals(ErrorCode.RESET_CODE_INVALID, ex.getErrorCode());
            assertEquals(i, saved.getLast().getAttempts());
        }
        assertNotNull(saved.getLast().getUsedAt());
        assertThrows(AppException.class, () -> service.verifyCode("khach001", code), "mã đúng cũng hết dùng được");
    }

    @Test
    void verifyingDoesNotConsumeButResettingDoes() {
        String code = requestCode();
        service.verifyCode("khach001", code);
        service.verifyCode("khach1@example.com", code);
        assertNull(saved.getLast().getUsedAt());

        int versionBefore = user.currentTokenVersion();
        service.resetPassword("khach001", code, "matkhaumoi");
        assertTrue(ENCODER.matches("matkhaumoi", user.getPassword()));
        assertEquals(versionBefore + 1, user.currentTokenVersion(), "mọi phiên cũ bị đăng xuất");
        assertNotNull(saved.getLast().getUsedAt());
        verify(limiter).recordSuccess("khach001");

        assertThrows(AppException.class, () -> service.resetPassword("khach001", code, "matkhau3"), "mã chỉ dùng một lần");
    }

    @Test
    void expiredCodesAndShortPasswordsAreRejected() {
        String code = requestCode();
        assertEquals(ErrorCode.BAD_REQUEST,
                assertThrows(AppException.class, () -> service.resetPassword("khach001", code, "123")).getErrorCode());
        saved.getLast().setExpiresAt(NOW.minusSeconds(1));
        assertEquals(ErrorCode.RESET_CODE_INVALID,
                assertThrows(AppException.class, () -> service.verifyCode("khach001", code)).getErrorCode());
        assertEquals("h:old", user.getPassword());
    }
}
