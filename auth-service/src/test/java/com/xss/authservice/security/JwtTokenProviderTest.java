package com.xss.authservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQta2V5LTEyMzQ1Njc4OTAxMjM0NTY=";
    private static final long EXPIRATION = 7200000L;

    @BeforeEach
    void setUp() throws Exception {
        jwtTokenProvider = new JwtTokenProvider();
        setField(jwtTokenProvider, "secretBase64", SECRET_BASE64);
        setField(jwtTokenProvider, "expiration", EXPIRATION);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void generateToken_notNullAndContainsUserId() {
        Long userId = 123L;

        String token = jwtTokenProvider.generateToken(userId);

        assertNotNull(token);
        assertFalse(token.isEmpty());

        Claims claims = jwtTokenProvider.parseToken(token);
        assertEquals(userId.toString(), claims.getSubject());
    }

    @Test
    void parseToken_correctSubjectAndClaim() {
        Long userId = 456L;
        String token = jwtTokenProvider.generateToken(userId);

        Claims claims = jwtTokenProvider.parseToken(token);

        assertEquals(userId.toString(), claims.getSubject());
        assertEquals("user", claims.get("type"));
        assertEquals("auth-service", claims.getIssuer());
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
    }

    @Test
    void getUserIdFromToken_correctUserId() {
        Long userId = 789L;
        String token = jwtTokenProvider.generateToken(userId);

        Long result = jwtTokenProvider.getUserIdFromToken(token);

        assertEquals(userId, result);
    }

    @Test
    void parseToken_invalidToken_throwsException() {
        String invalidToken = "invalid.token.here";

        assertThrows(JwtException.class, () -> jwtTokenProvider.parseToken(invalidToken));
    }
}
