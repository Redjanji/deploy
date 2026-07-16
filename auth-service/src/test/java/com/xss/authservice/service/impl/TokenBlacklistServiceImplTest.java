package com.xss.authservice.service.impl;

import com.xss.authservice.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceImplTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private TokenBlacklistServiceImpl tokenBlacklistService;

    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void addToBlacklist_tokenNotExpired_addsToRedis() {
        String token = "valid-token";
        Claims claims = mock(Claims.class);
        Date futureDate = new Date(System.currentTimeMillis() + 3600000);

        when(jwtTokenProvider.parseToken(token)).thenReturn(claims);
        when(claims.getExpiration()).thenReturn(futureDate);

        tokenBlacklistService.addToBlacklist(token);

        verify(valueOperations).set(
                eq(BLACKLIST_PREFIX + token),
                eq("1"),
                anyLong(),
                eq(TimeUnit.MILLISECONDS)
        );
    }

    @Test
    void addToBlacklist_tokenExpired_doesNotAdd() {
        String token = "expired-token";
        Claims claims = mock(Claims.class);
        Date pastDate = new Date(System.currentTimeMillis() - 1000);

        when(jwtTokenProvider.parseToken(token)).thenReturn(claims);
        when(claims.getExpiration()).thenReturn(pastDate);

        tokenBlacklistService.addToBlacklist(token);

        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void isBlacklisted_inBlacklist_returnsTrue() {
        String token = "blacklisted-token";
        when(stringRedisTemplate.hasKey(BLACKLIST_PREFIX + token)).thenReturn(true);

        boolean result = tokenBlacklistService.isBlacklisted(token);

        assertTrue(result);
    }

    @Test
    void isBlacklisted_notInBlacklist_returnsFalse() {
        String token = "valid-token";
        when(stringRedisTemplate.hasKey(BLACKLIST_PREFIX + token)).thenReturn(false);

        boolean result = tokenBlacklistService.isBlacklisted(token);

        assertFalse(result);
    }
}
