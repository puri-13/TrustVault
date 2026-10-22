package com.trustvault.backend.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTests {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void registrationStoresEncodedPasswordAndNormalizedUsername() {
        when(appUserRepository.existsByUsername("new-owner")).thenReturn(false);
        when(passwordEncoder.encode("plain-password")).thenReturn("encoded-password");
        when(appUserRepository.save(org.mockito.ArgumentMatchers.any(AppUser.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        userService.registerUser(" new-owner ", "plain-password", UserRole.OWNER);

        ArgumentCaptor<AppUser> savedUser = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(savedUser.capture());
        assertEquals("new-owner", savedUser.getValue().getUsername());
        assertEquals("encoded-password", savedUser.getValue().getPassword());
        assertEquals("OWNER", savedUser.getValue().getRole());
    }

    @Test
    void duplicateUsernameIsRejectedBeforeSaving() {
        when(appUserRepository.existsByUsername("existing-user")).thenReturn(true);

        assertThrows(DuplicateUsernameException.class, () ->
                userService.registerUser(
                        "existing-user", "plain-password", UserRole.OWNER));

        verify(appUserRepository, never()).save(any());
        }

        @Test
        void concurrentDuplicateInsertIsRejectedByDatabaseConstraint() {
            when(appUserRepository.existsByUsername("new-owner"))
                .thenReturn(false, true);
        when(passwordEncoder.encode("plain-password")).thenReturn("encoded-password");
        when(appUserRepository.save(any(AppUser.class)))
            .thenThrow(new org.springframework.dao.DataIntegrityViolationException(
                "duplicate username"));

        assertThrows(DuplicateUsernameException.class, () ->
            userService.registerUser("new-owner", "plain-password", UserRole.OWNER));
    }

        @Test
        void unrelatedIntegrityFailureIsNotReportedAsDuplicateUsername() {
        when(appUserRepository.existsByUsername("new-owner")).thenReturn(false);
        when(passwordEncoder.encode("plain-password")).thenReturn("encoded-password");
        when(appUserRepository.save(any(AppUser.class)))
            .thenThrow(new org.springframework.dao.DataIntegrityViolationException(
                "unrelated constraint"));

        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () ->
            userService.registerUser("new-owner", "plain-password", UserRole.OWNER));
        }
}