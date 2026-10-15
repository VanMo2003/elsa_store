package com.example.elsa_store.service.impl;

import com.example.elsa_store.dto.response.PasswordResetResponse;
import com.example.elsa_store.entity.PasswordResetCode;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.event.PasswordResetRequestedEvent;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.repository.PasswordResetCodeRepository;
import com.example.elsa_store.repository.UserRepository;
import com.example.elsa_store.security.LoginAttemptLimiter;
import com.example.elsa_store.service.PasswordResetService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@Transactional
public class PasswordResetServiceImpl implements PasswordResetService {

    static final int VALID_MINUTES = 10;
    static final int RESEND_AFTER_SECONDS = 60;
    static final int MAX_CODES_PER_HOUR = 5;
    static final int MAX_ATTEMPTS = 5;
    static final int MAX_REQUESTS_PER_IP_PER_HOUR = 20;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetCodeRepository codeRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final Clock clock;

    /** Số lần xin mã theo IP (cửa sổ trượt 1 giờ, trong bộ nhớ) — chặn dùng API để spam email. */
    private final Map<String, Deque<Instant>> requestsByIp = new ConcurrentHashMap<>();

    @Autowired
    public PasswordResetServiceImpl(UserRepository userRepository,
                                    PasswordResetCodeRepository codeRepository,
                                    PasswordEncoder passwordEncoder,
                                    ApplicationEventPublisher events,
                                    LoginAttemptLimiter loginAttemptLimiter) {
        this(userRepository, codeRepository, passwordEncoder, events, loginAttemptLimiter, Clock.systemDefaultZone());
    }

    PasswordResetServiceImpl(UserRepository userRepository,
                             PasswordResetCodeRepository codeRepository,
                             PasswordEncoder passwordEncoder,
                             ApplicationEventPublisher events,
                             LoginAttemptLimiter loginAttemptLimiter,
                             Clock clock) {
        this.userRepository = userRepository;
        this.codeRepository = codeRepository;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.loginAttemptLimiter = loginAttemptLimiter;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ gửi mã

    @Override
    public PasswordResetResponse requestCode(String identifier, String clientIp) {
        throttleIp(clientIp);
        PasswordResetResponse response = PasswordResetResponse.builder()
                .resendAfterSeconds(RESEND_AFTER_SECONDS)
                .validMinutes(VALID_MINUTES)
                .build();
        Optional<User> found = find(identifier)
                .filter(User::isEnabled)
                .filter(u -> u.getEmail() != null && !u.getEmail().isBlank());
        if (found.isEmpty()) {
            return response; // trả giống hệt trường hợp có tài khoản
        }
        User user = found.get();
        LocalDateTime now = now();
        Optional<PasswordResetCode> latest = codeRepository.findFirstByUser_IdOrderByIdDesc(user.getId());
        boolean tooSoon = latest.isPresent() && latest.get().getCreatedAt() != null
                && latest.get().getCreatedAt().isAfter(now.minusSeconds(RESEND_AFTER_SECONDS));
        if (tooSoon || codeRepository.countByUser_IdAndCreatedAtAfter(user.getId(), now.minusHours(1)) >= MAX_CODES_PER_HOUR) {
            return response; // im lặng bỏ qua: không gửi dồn email cho chủ tài khoản
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        codeRepository.invalidateActive(user.getId(), now);
        PasswordResetCode entity = new PasswordResetCode();
        entity.setUser(user);
        entity.setCodeHash(passwordEncoder.encode(code));
        entity.setExpiresAt(now.plusMinutes(VALID_MINUTES));
        entity.setAttempts(0);
        codeRepository.save(entity);
        events.publishEvent(new PasswordResetRequestedEvent(
                user.getEmail(),
                user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : user.getUsername(),
                user.getUsername(),
                code,
                VALID_MINUTES));
        return response;
    }

    // ------------------------------------------------------------------ kiểm mã, đặt lại

    /** Sai mã vẫn phải lưu số lần thử nên không rollback khi ném lỗi nghiệp vụ. */
    @Override
    @Transactional(noRollbackFor = AppException.class)
    public void verifyCode(String identifier, String code) {
        check(identifier, code);
    }

    @Override
    @Transactional(noRollbackFor = AppException.class)
    public void resetPassword(String identifier, String code, String newPassword) {
        if (newPassword == null || newPassword.length() < 6) {
            throw new AppException(ErrorCode.BAD_REQUEST, "newPassword: size must be between 6 and 100");
        }
        Checked checked = check(identifier, code);
        User user = checked.user();
        user.setPassword(passwordEncoder.encode(newPassword));
        // Mọi phiên cũ (kể cả của người đã chiếm tài khoản) hết hiệu lực.
        user.markCredentialsChanged();
        userRepository.save(user);
        checked.code().setUsedAt(now());
        codeRepository.save(checked.code());
        loginAttemptLimiter.recordSuccess(user.getUsername());
    }

    private record Checked(User user, PasswordResetCode code) {
    }

    /**
     * Kiểm mã của mã còn hiệu lực mới nhất. Sai thì tăng số lần thử (lưu lại dù ném lỗi — xem {@code noRollbackFor}), quá
     * {@link #MAX_ATTEMPTS} lần thì mã hỏng hẳn. Mọi kiểu sai đều trả cùng một lỗi.
     */
    private Checked check(String identifier, String code) {
        User user = find(identifier).filter(User::isEnabled).orElseThrow(PasswordResetServiceImpl::invalid);
        PasswordResetCode latest = codeRepository.findFirstByUser_IdOrderByIdDesc(user.getId())
                .orElseThrow(PasswordResetServiceImpl::invalid);
        LocalDateTime now = now();
        if (latest.getUsedAt() != null || latest.getExpiresAt().isBefore(now) || latest.getAttempts() >= MAX_ATTEMPTS) {
            throw invalid();
        }
        if (code == null || !code.matches("\\d{6}") || !passwordEncoder.matches(code, latest.getCodeHash())) {
            latest.setAttempts(latest.getAttempts() + 1);
            if (latest.getAttempts() >= MAX_ATTEMPTS) {
                latest.setUsedAt(now);
            }
            codeRepository.saveAndFlush(latest);
            throw invalid();
        }
        return new Checked(user, latest);
    }

    private Optional<User> find(String identifier) {
        String value = identifier == null ? "" : identifier.trim();
        if (value.isEmpty()) {
            return Optional.empty();
        }
        Optional<User> byUsername = userRepository.findByUsername(value);
        return byUsername.isPresent() ? byUsername : userRepository.findFirstByEmailIgnoreCase(value);
    }

    private void throttleIp(String ip) {
        if (ip == null) {
            return;
        }
        Instant now = clock.instant();
        Deque<Instant> hits = requestsByIp.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (hits) {
            while (!hits.isEmpty() && hits.peekFirst().isBefore(now.minus(Duration.ofHours(1)))) {
                hits.pollFirst();
            }
            if (hits.size() >= MAX_REQUESTS_PER_IP_PER_HOUR) {
                throw new AppException(ErrorCode.TOO_MANY_RESET_REQUESTS);
            }
            hits.addLast(now);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private static AppException invalid() {
        return new AppException(ErrorCode.RESET_CODE_INVALID);
    }

    /** Dọn mã đã hết hạn quá một ngày và bảng đếm IP. */
    @Scheduled(fixedDelay = 60 * 60 * 1000, initialDelay = 10 * 60 * 1000)
    public void cleanUp() {
        int removed = codeRepository.deleteExpiredBefore(now().minusDays(1));
        if (removed > 0) {
            log.debug("Removed {} expired password reset codes", removed);
        }
        Instant cutoff = clock.instant().minus(Duration.ofHours(1));
        requestsByIp.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                return entry.getValue().isEmpty() || entry.getValue().peekLast().isBefore(cutoff);
            }
        });
    }
}
