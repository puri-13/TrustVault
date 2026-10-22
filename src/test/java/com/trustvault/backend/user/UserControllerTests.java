package com.trustvault.backend.user;

import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserControllerTests {

    @Test
    void rateLimitedRegistrationReturnsGeneric429BeforeValidation() throws Exception {
        RegistrationRateLimiter limiter = new RegistrationRateLimiter(1, 900);
        RegistrationRateLimitFilter filter = new RegistrationRateLimitFilter(limiter);
        MockHttpServletRequest firstRequest = registrationRequest();
        filter.doFilter(firstRequest, new MockHttpServletResponse(), new MockFilterChain());

        MockHttpServletResponse limitedResponse = new MockHttpServletResponse();
        filter.doFilter(registrationRequest(), limitedResponse, new MockFilterChain());

        assertEquals(429, limitedResponse.getStatus());
        assertEquals("900", limitedResponse.getHeader("Retry-After"));
        assertFalse(limitedResponse.getContentAsString().contains("198.51.100.12"));
        assertTrue(limitedResponse.getContentAsString().contains("Too many registration attempts"));
    }

    private MockHttpServletRequest registrationRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setServletPath("/api/auth/register");
        request.setRemoteAddr("198.51.100.12");
        return request;
    }
}