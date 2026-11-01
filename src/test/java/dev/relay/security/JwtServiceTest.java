package dev.relay.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.relay.domain.User;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    private User user() {
        User user = new User();
        user.setId(7L);
        user.setEmail("ada@example.com");
        return user;
    }

    @Test
    void issuedTokenRoundTrips() {
        JwtService service = new JwtService(SECRET, 5);
        AuthUser parsed = service.parse(service.issue(user())).orElseThrow();
        assertEquals(7L, parsed.id());
        assertEquals("ada@example.com", parsed.email());
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtService issuer = new JwtService(SECRET, 5);
        JwtService verifier = new JwtService("fedcba9876543210fedcba9876543210", 5);
        assertTrue(verifier.parse(issuer.issue(user())).isEmpty());
    }

    @Test
    void garbageIsRejected() {
        assertTrue(new JwtService(SECRET, 5).parse("not-a-token").isEmpty());
    }
}
