package com.privacyguardian.service;

import com.privacyguardian.dto.AuthDtos;
import com.privacyguardian.entity.User;
import com.privacyguardian.repo.UserRepository;
import com.privacyguardian.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder enc;
    private final JwtService jwt;
    private final AuditService audit;

    public AuthService(UserRepository u, PasswordEncoder e, JwtService j, AuditService a) {
        users = u;
        enc = e;
        jwt = j;
        audit = a;
    }

    public AuthDtos.AuthResponse signup(AuthDtos.Signup x) {
        if (x == null || x.username() == null || x.password() == null || x.email() == null
                || x.username().isBlank() || x.email().isBlank() || x.password().isBlank()) {
            throw new IllegalArgumentException("All fields are required");
        }

        String username = x.username().trim();
        String email = x.email().trim().toLowerCase();

        if (users.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (users.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists");
        }

        User u = new User();
        u.setUsername(username);
        u.setEmail(email);
        u.setPasswordHash(enc.encode(x.password()));
        u.setRole("USER");
        u.setEnabled(true);
        u.setFailedAttempts(0);
        u.setLockedUntil(null);

        users.save(u);
        audit.log(u.getUsername(), "SIGNUP", "New user account created", "INFO");

        return new AuthDtos.AuthResponse(
                jwt.generate(u.getUsername(), u.getRole()),
                u.getUsername(),
                u.getRole()
        );
    }

    public AuthDtos.AuthResponse login(AuthDtos.Login x) {
        if (x == null || x.username() == null || x.password() == null
                || x.username().isBlank() || x.password().isBlank()) {
            throw new IllegalArgumentException("Invalid username/email or password");
        }

        String identifier = x.username().trim();

        // Login accepts either the username OR the registered email.
        User u = users.findByUsernameOrEmail(identifier, identifier.toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Invalid username/email or password"));

        if (!u.isEnabled()) {
            throw new IllegalArgumentException("Account disabled");
        }

        if (u.getLockedUntil() != null && u.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Account temporarily locked");
        }

        // passwordHash is the actual BCrypt column used by the current entity.
        if (u.getPasswordHash() == null || u.getPasswordHash().isBlank()
                || !enc.matches(x.password(), u.getPasswordHash())) {
            u.setFailedAttempts(u.getFailedAttempts() + 1);

            if (u.getFailedAttempts() >= 5) {
                u.setLockedUntil(LocalDateTime.now().plusMinutes(5));
                audit.log(u.getUsername(), "ACCOUNT_LOCK", "5 failed login attempts", "HIGH");
            }

            users.save(u);
            throw new IllegalArgumentException("Invalid username/email or password");
        }

        u.setFailedAttempts(0);
        u.setLockedUntil(null);
        users.save(u);

        audit.log(u.getUsername(), "LOGIN", "Successful login", "INFO");

        return new AuthDtos.AuthResponse(
                jwt.generate(u.getUsername(), u.getRole()),
                u.getUsername(),
                u.getRole()
        );
    }
}
