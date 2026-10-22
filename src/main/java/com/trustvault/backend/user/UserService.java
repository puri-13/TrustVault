
package com.trustvault.backend.user;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public AppUser registerUser(
            String username,
            String password,
            UserRole role) {

        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }

        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException(
                    "Password must contain at least 8 characters");
        }

        if (role == null || role == UserRole.ADMIN) {
            throw new IllegalArgumentException(
                    "Only OWNER or VIEWER registration is allowed");
        }

        String normalizedUsername = username.trim();

        if (appUserRepository.existsByUsername(normalizedUsername)) {
            throw new DuplicateUsernameException("Username already exists");
        }

        AppUser user = new AppUser();
        user.setUsername(normalizedUsername);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role.name());

        try {
            return appUserRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            if (appUserRepository.existsByUsername(normalizedUsername)) {
                throw new DuplicateUsernameException(
                        "Username already exists", ex);
            }
            throw ex;
        }
    }
}
