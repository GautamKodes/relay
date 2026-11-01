package dev.relay.service;

import dev.relay.domain.User;
import dev.relay.dto.Dtos.AuthResponse;
import dev.relay.dto.Dtos.LoginRequest;
import dev.relay.dto.Dtos.RegisterRequest;
import dev.relay.dto.Dtos.UserView;
import dev.relay.repo.UserRepository;
import dev.relay.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("That email is already registered");
        }
        User user = new User();
        user.setEmail(email);
        user.setDisplayName(request.displayName().trim());
        user.setPasswordHash(encoder.encode(request.password()));
        users.save(user);
        return new AuthResponse(jwtService.issue(user), UserView.of(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmail(request.email().trim().toLowerCase())
                .filter(found -> encoder.matches(request.password(), found.getPasswordHash()))
                .orElseThrow(() -> ApiException.unauthorized("Email or password is incorrect"));
        return new AuthResponse(jwtService.issue(user), UserView.of(user));
    }

    @Transactional(readOnly = true)
    public UserView me(Long userId) {
        return users.findById(userId)
                .map(UserView::of)
                .orElseThrow(() -> ApiException.unauthorized("Unknown user"));
    }
}
