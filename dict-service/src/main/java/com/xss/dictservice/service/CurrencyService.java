package com.xss.dictservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xss.dictservice.mapper.CurrencyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CurrencyService {
    private final CurrencyMapper currencyMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<Map<String, Object>> getCurrencies(Integer status, String keyword) {
        String st = status != null ? String.valueOf(status) : "all";
        String kw = keyword != null ? keyword : "all";
        String cacheKey = "currencies:list:" + st + ":" + kw;
        return queryListWithCache(cacheKey, () -> currencyMapper.selectCurrencies(status, keyword));
    }

    public Map<String, Object> getCurrency(String currencyCode) {
        String cacheKey = "currencies:detail:" + currencyCode;
        return queryMapWithCache(cacheKey, () -> currencyMapper.selectCurrencyByCode(currencyCode));
    }

    public void clearCache() {
        Set<String> keys = redisTemplate.execute((RedisCallback<Set<String>>) connection -> {
            Set<String> result = new HashSet<>();
            try (Cursor<byte[]> cursor = connection.scan(
                    ScanOptions.scanOptions().match("currencies:*").count(100).build())) {
                while (cursor.hasNext()) {
                    result.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            }
            return result;
        });
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private List<Map<String, Object>> queryListWithCache(String cacheKey,
                                                         java.util.function.Supplier<List<Map<String, Object>>> supplier) {
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                try {
                    return objectMapper.readValue(cached, new TypeReference<List<Map<String, Object>>>() {});
                } catch (Exception e) {
                    log.warn("缓存反序列化失败，降级查 DB: key={}, err={}", cacheKey, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Redis 读取失败，降级查 DB: key={}, err={}", cacheKey, e.getMessage());
        }
        List<Map<String, Object>> data = supplier.get();
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(data), 1, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("缓存写入失败: key={}, err={}", cacheKey, e.getMessage());
        }
        return data;
    }

    private Map<String, Object> queryMapWithCache(String cacheKey,
                                                   java.util.function.Supplier<Map<String, Object>> supplier) {
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                try {
                    return objectMapper.readValue(cached, new TypeReference<Map<String, Object>>() {});
                } catch (Exception e) {
                    log.warn("缓存反序列化失败，降级查 DB: key={}, err={}", cacheKey, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Redis 读取失败，降级查 DB: key={}, err={}", cacheKey, e.getMessage());
        }
        Map<String, Object> data = supplier.get();
        try {
            if (data != null) {
                redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(data), 1, TimeUnit.HOURS);
            }
        } catch (Exception e) {
            log.warn("缓存写入失败: key={}, err={}", cacheKey, e.getMessage());
        }
        return data;
    }
}
