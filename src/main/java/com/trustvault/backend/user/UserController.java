
package com.trustvault.backend.user;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @Valid @RequestBody RegisterRequest request) {

        try {
            AppUser user = userService.registerUser(
                    request.username(),
                    request.password(),
                    request.role()
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(
                    Map.of(
                            "id", user.getId(),
                            "username", user.getUsername(),
                            "role", user.getRole(),
                            "message", "Registration successful"
                    )
            );

        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", ex.getMessage())
            );
        }
    }
}
