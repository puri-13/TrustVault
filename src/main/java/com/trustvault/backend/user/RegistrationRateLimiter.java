package com.trustvault.backend.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class RegistrationRateLimiter {

    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private final int maxAttempts;
    private final long windowNanos;
    private final long windowSeconds;
    private final Map<String, Window> clients = new LinkedHashMap<>(16, 0.75f, true);

    public RegistrationRateLimiter(
            @Value("${trustvault.registration.rate-limit.max-attempts:10}")
            int maxAttempts,
            @Value("${trustvault.registration.rate-limit.window-seconds:900}")
            long windowSeconds) {
        if (maxAttempts < 1 || windowSeconds < 1) {
            throw new IllegalArgumentException(
                    "Registration rate-limit settings must be positive");
        }
        this.maxAttempts = maxAttempts;
        this.windowSeconds = windowSeconds;
        this.windowNanos = windowSeconds * 1_000_000_000L;
    }

    public synchronized boolean allow(String clientAddress) {
        long now = System.nanoTime();
        String key = clientAddress == null || clientAddress.isBlank()
                ? "unknown"
                : clientAddress;
        removeExpired(now);

        Window window = clients.get(key);
        if (window == null || now - window.startedAtNanos() >= windowNanos) {
            clients.put(key, new Window(now, 1));
            evictExcessClients();
            return true;
        }

        if (window.attempts() >= maxAttempts) {
            return false;
        }

        clients.put(key, new Window(window.startedAtNanos(), window.attempts() + 1));
        return true;
    }

    public long retryAfterSeconds() {
        return windowSeconds;
    }

    private void removeExpired(long now) {
        Iterator<Window> windows = clients.values().iterator();
        while (windows.hasNext()) {
            if (now - windows.next().startedAtNanos() >= windowNanos) {
                windows.remove();
            }
        }
    }

    private void evictExcessClients() {
        Iterator<String> keys = clients.keySet().iterator();
        while (clients.size() > MAX_TRACKED_CLIENTS && keys.hasNext()) {
            keys.next();
            keys.remove();
        }
    }

    private record Window(long startedAtNanos, int attempts) {
    }
}