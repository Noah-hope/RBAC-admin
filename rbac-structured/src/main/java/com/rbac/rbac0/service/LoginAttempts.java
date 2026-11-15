package com.rbac.rbac0.service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import static com.rbac.rbac0.service.AuthenticationFailure.Reason.TOO_MANY_REQUESTS;

@Component
class LoginAttempts {

    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final Map<String, Integer> failures = new HashMap<>();
    private Instant windowEndsAt = Instant.MIN;

    synchronized void requireAvailable(String address) {
        var now = Instant.now();
        resetExpiredWindow(now);
        if (failures.getOrDefault(address, 0) >= MAX_FAILURES) {
            long retryAfter = Math.max(1, Duration.between(now, windowEndsAt).toSeconds());
            throw new AuthenticationFailure(TOO_MANY_REQUESTS, retryAfter);
        }
    }

    synchronized void recordFailure(String address) {
        resetExpiredWindow(Instant.now());
        failures.merge(address, 1, Integer::sum);
    }

    synchronized void recordSuccess(String address) {
        failures.remove(address);
    }

    private void resetExpiredWindow(Instant now) {
        if (!now.isBefore(windowEndsAt)) {
            failures.clear();
            windowEndsAt = now.plus(WINDOW);
        }
    }
}
