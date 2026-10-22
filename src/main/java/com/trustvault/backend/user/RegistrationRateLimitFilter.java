package com.trustvault.backend.user;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RegistrationRateLimitFilter extends OncePerRequestFilter {

    private static final String REGISTRATION_PATH = "/api/auth/register";

    private final RegistrationRateLimiter rateLimiter;

    public RegistrationRateLimitFilter(RegistrationRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !REGISTRATION_PATH.equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!rateLimiter.allow(request.getRemoteAddr())) {
            response.setStatus(429);
            response.setHeader(
                    "Retry-After",
                    Long.toString(rateLimiter.retryAfterSeconds()));
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(
                    "{\"error\":\"Too many registration attempts. Please try again later.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}