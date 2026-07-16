package com.xss.dictservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xss.dictservice.mapper.ChinaRegionMapper;
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
public class ChinaRegionService {
    private final ChinaRegionMapper regionMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<Map<String, Object>> getProvinces() {
        String cacheKey = "regions:provinces";
        return queryWithCache(cacheKey, regionMapper::selectProvinces);
    }

    public List<Map<String, Object>> getCities(String provinceCode) {
        String cacheKey = "regions:cities:" + (provinceCode != null ? provinceCode : "all");
        return queryWithCache(cacheKey, () -> regionMapper.selectCities(provinceCode));
    }

    public List<Map<String, Object>> getDistricts(String cityCode) {
        String cacheKey = "regions:districts:" + (cityCode != null ? cityCode : "all");
        return queryWithCache(cacheKey, () -> regionMapper.selectDistricts(cityCode));
    }

    public List<Map<String, Object>> getTowns(String districtCode) {
        String cacheKey = "regions:towns:" + (districtCode != null ? districtCode : "all");
        return queryWithCache(cacheKey, () -> regionMapper.selectTowns(districtCode));
    }

    public List<Map<String, Object>> getVillages(String townCode) {
        String cacheKey = "regions:villages:" + (townCode != null ? townCode : "all");
        return queryWithCache(cacheKey, () -> regionMapper.selectVillages(townCode));
    }

    public List<Map<String, Object>> getPath(String regionCode, Integer level) {
        String cacheKey = "regions:path:" + (regionCode != null ? regionCode : "all") + ":" + (level != null ? level : "all");
        return queryWithCache(cacheKey, () -> regionMapper.selectPath(regionCode, level));
    }

    public void clearCache() {
        Set<String> keys = redisTemplate.execute((RedisCallback<Set<String>>) connection -> {
            Set<String> result = new HashSet<>();
            try (Cursor<byte[]> cursor = connection.scan(
                    ScanOptions.scanOptions().match("regions:*").count(100).build())) {
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

    private List<Map<String, Object>> queryWithCache(String cacheKey,
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
}
