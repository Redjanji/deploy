package com.xss.authservice.service.impl;

import com.xss.authservice.security.JwtTokenProvider;
import com.xss.authservice.service.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class TokenBlacklistServiceImpl implements TokenBlacklistService {

    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";

    private final StringRedisTemplate stringRedisTemplate;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public void addToBlacklist(String token) {
        Claims claims = jwtTokenProvider.parseToken(token);
        Date expiration = claims.getExpiration();
        long ttl = expiration.getTime() - System.currentTimeMillis();
        if (ttl > 0) {
            stringRedisTemplate.opsForValue().set(
                    BLACKLIST_PREFIX + token,
                    "1",
                    ttl,
                    TimeUnit.MILLISECONDS
            );
        }
    }

    @Override
    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(BLACKLIST_PREFIX + token));
    }
}
