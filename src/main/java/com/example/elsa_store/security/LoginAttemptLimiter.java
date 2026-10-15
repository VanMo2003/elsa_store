package com.example.elsa_store.security;

import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chặn dò mật khẩu: sai quá {@link #MAX_FAILURES_PER_ACCOUNT} lần cho một tài khoản, hoặc quá
 * {@link #MAX_FAILURES_PER_IP} lần từ một địa chỉ IP (thử nhiều tài khoản), trong {@link #WINDOW} thì tạm khoá đăng
 * nhập tới khi lần sai cũ nhất ra khỏi cửa sổ. Đăng nhập đúng xoá các lần sai của tài khoản đó. Trong bộ nhớ, một
 * instance — cùng giả định với giới hạn lượt AI và danh sách socket chat.
 */
@Component
public class LoginAttemptLimiter {

    static final int MAX_FAILURES_PER_ACCOUNT = 5;
    static final int MAX_FAILURES_PER_IP = 20;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final Clock clock;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    @Autowired
    public LoginAttemptLimiter() {
        this(Clock.systemUTC());
    }

    LoginAttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Ném {@link ErrorCode#TOO_MANY_LOGIN_ATTEMPTS} nếu tài khoản hoặc IP đang bị tạm khoá. */
    public void checkAllowed(String username, String ip) {
        Instant now = clock.instant();
        if (count(accountKey(username), now) >= MAX_FAILURES_PER_ACCOUNT
                || (ip != null && count(ipKey(ip), now) >= MAX_FAILURES_PER_IP)) {
            throw new AppException(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS);
        }
    }

    public void recordFailure(String username, String ip) {
        Instant now = clock.instant();
        add(accountKey(username), now);
        if (ip != null) {
            add(ipKey(ip), now);
        }
    }

    public void recordSuccess(String username) {
        failures.remove(accountKey(username));
    }

    /** Bỏ các khoá không còn lần sai nào trong cửa sổ để bảng không phình mãi. */
    @Scheduled(fixedDelay = 15 * 60 * 1000, initialDelay = 15 * 60 * 1000)
    public void purgeStale() {
        Instant now = clock.instant();
        failures.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                prune(entry.getValue(), now);
                return entry.getValue().isEmpty();
            }
        });
    }

    private int count(String key, Instant now) {
        Deque<Instant> times = failures.get(key);
        if (times == null) {
            return 0;
        }
        synchronized (times) {
            prune(times, now);
            return times.size();
        }
    }

    private void add(String key, Instant now) {
        Deque<Instant> times = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            prune(times, now);
            times.addLast(now);
        }
    }

    private static void prune(Deque<Instant> times, Instant now) {
        Instant oldest = now.minus(WINDOW);
        while (!times.isEmpty() && times.peekFirst().isBefore(oldest)) {
            times.pollFirst();
        }
    }

    private static String accountKey(String username) {
        return "user:" + (username == null ? "" : username.trim().toLowerCase(Locale.ROOT));
    }

    private static String ipKey(String ip) {
        return "ip:" + ip;
    }
}
