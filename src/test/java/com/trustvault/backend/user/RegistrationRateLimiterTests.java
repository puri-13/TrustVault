package com.trustvault.backend.user;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistrationRateLimiterTests {

    @Test
    void limitsAttemptsPerClientWithoutBlockingOtherClients() {
        RegistrationRateLimiter limiter = new RegistrationRateLimiter(2, 900);

        assertTrue(limiter.allow("198.51.100.10"));
        assertTrue(limiter.allow("198.51.100.10"));
        assertFalse(limiter.allow("198.51.100.10"));
        assertTrue(limiter.allow("198.51.100.11"));
        assertTrue(limiter.retryAfterSeconds() == 900);
    }
}