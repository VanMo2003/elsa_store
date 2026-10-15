package com.example.elsa_store.ai;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;

/**
 * Sliding-window limit per caller (account id, or client IP for anonymous search) so one user can't run up the
 * Claude bill. In memory, single instance - same assumption as the chat socket registry.
 */
@Component
public class AiRateLimiter {

    private final Map<String, Deque<Long>> calls = new ConcurrentHashMap<>();

    public void check(String key, int limit, Duration window) {
        long now = System.currentTimeMillis();
        long oldest = now - window.toMillis();
        Deque<Long> times = calls.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst() < oldest) {
                times.pollFirst();
            }
            if (times.size() >= limit) {
                throw new AppException(ErrorCode.AI_RATE_LIMITED);
            }
            times.addLast(now);
        }
    }
}
