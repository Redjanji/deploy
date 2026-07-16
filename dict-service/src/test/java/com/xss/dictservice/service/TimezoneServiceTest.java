package com.xss.dictservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xss.dictservice.mapper.TimezoneMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TimezoneService 单元测试")
class TimezoneServiceTest {

    @Mock
    private TimezoneMapper timezoneMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private TimezoneService timezoneService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        timezoneService = new TimezoneService(timezoneMapper, redisTemplate);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private Map<String, Object> createTimezone(String id, String offsetUtc, String description) {
        Map<String, Object> timezone = new HashMap<>();
        timezone.put("timezone_id", id);
        timezone.put("offset_utc", offsetUtc);
        timezone.put("description", description);
        return timezone;
    }

    // ========== getTimezones 测试 ==========

    @Test
    @DisplayName("getTimezones: 缓存命中直接返回列表")
    void getTimezones_cacheHit_returnsFromCache() throws Exception {
        List<Map<String, Object>> expected = Arrays.asList(
                createTimezone("Asia/Shanghai", "+08:00", "中国标准时间")
        );
        String cachedJson = objectMapper.writeValueAsString(expected);

        when(valueOperations.get("timezones:list:all")).thenReturn(cachedJson);

        List<Map<String, Object>> result = timezoneService.getTimezones(null);

        assertEquals(1, result.size());
        assertEquals("Asia/Shanghai", result.get(0).get("timezone_id"));
        verify(timezoneMapper, never()).selectTimezones(anyString());
    }

    @Test
    @DisplayName("getTimezones: 缓存未命中查DB并写入缓存")
    void getTimezones_cacheMiss_queriesDbAndCaches() {
        List<Map<String, Object>> dbData = Arrays.asList(
                createTimezone("Asia/Shanghai", "+08:00", "中国标准时间")
        );

        when(valueOperations.get("timezones:list:all")).thenReturn(null);
        when(timezoneMapper.selectTimezones(isNull())).thenReturn(dbData);

        List<Map<String, Object>> result = timezoneService.getTimezones(null);

        assertEquals(1, result.size());
        assertEquals("Asia/Shanghai", result.get(0).get("timezone_id"));
        verify(valueOperations, times(1)).set(eq("timezones:list:all"), anyString(), eq(1L), eq(TimeUnit.HOURS));
    }

    @Test
    @DisplayName("getTimezones: 带keyword过滤")
    void getTimezones_withKeyword_filtersCorrectly() {
        List<Map<String, Object>> dbData = Arrays.asList(
                createTimezone("Asia/Tokyo", "+09:00", "日本标准时间")
        );

        when(valueOperations.get("timezones:list:Tokyo")).thenReturn(null);
        when(timezoneMapper.selectTimezones("Tokyo")).thenReturn(dbData);

        List<Map<String, Object>> result = timezoneService.getTimezones("Tokyo");

        assertEquals(1, result.size());
        assertEquals("Asia/Tokyo", result.get(0).get("timezone_id"));
    }

    @Test
    @DisplayName("getTimezones: Redis异常降级查DB")
    void getTimezones_redisException_fallsBackToDb() {
        List<Map<String, Object>> dbData = Arrays.asList(
                createTimezone("Asia/Shanghai", "+08:00", "中国标准时间")
        );

        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis连接失败"));
        when(timezoneMapper.selectTimezones(isNull())).thenReturn(dbData);

        List<Map<String, Object>> result = timezoneService.getTimezones(null);

        assertEquals(1, result.size());
        assertEquals("Asia/Shanghai", result.get(0).get("timezone_id"));
    }

    // ========== getTimezone 测试 ==========

    @Test
    @DisplayName("getTimezone: 缓存命中返回详情")
    void getTimezone_cacheHit_returnsFromCache() throws Exception {
        Map<String, Object> expected = createTimezone("Asia/Shanghai", "+08:00", "中国标准时间");
        String cachedJson = objectMapper.writeValueAsString(expected);

        when(valueOperations.get("timezones:detail:Asia/Shanghai")).thenReturn(cachedJson);

        Map<String, Object> result = timezoneService.getTimezone("Asia/Shanghai");

        assertNotNull(result);
        assertEquals("+08:00", result.get("offset_utc"));
    }

    @Test
    @DisplayName("getTimezone: 缓存未命中查DB")
    void getTimezone_cacheMiss_queriesDb() {
        Map<String, Object> dbData = createTimezone("Asia/Tokyo", "+09:00", "日本标准时间");

        when(valueOperations.get("timezones:detail:Asia/Tokyo")).thenReturn(null);
        when(timezoneMapper.selectTimezoneById("Asia/Tokyo")).thenReturn(dbData);

        Map<String, Object> result = timezoneService.getTimezone("Asia/Tokyo");

        assertNotNull(result);
        assertEquals("+09:00", result.get("offset_utc"));
    }

    @Test
    @DisplayName("getTimezone: 不存在返回null")
    void getTimezone_nonExistingId_returnsNull() {
        when(valueOperations.get("timezones:detail:Invalid/Zone")).thenReturn(null);
        when(timezoneMapper.selectTimezoneById("Invalid/Zone")).thenReturn(null);

        Map<String, Object> result = timezoneService.getTimezone("Invalid/Zone");

        assertNull(result);
    }

    // ========== clearCache 测试 ==========

    @Test
    @DisplayName("clearCache: 清除所有时区缓存")
    @SuppressWarnings("unchecked")
    void clearCache_deletesAllTimezoneKeys() {
        Set<String> keys = new HashSet<>(Arrays.asList(
                "timezones:list:all",
                "timezones:detail:Asia/Shanghai"
        ));

        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(keys);

        timezoneService.clearCache();

        verify(redisTemplate, times(1)).delete(keys);
    }

    @Test
    @DisplayName("clearCache: 无匹配键时不删除")
    @SuppressWarnings("unchecked")
    void clearCache_noKeys_doesNotDelete() {
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(Collections.emptySet());

        timezoneService.clearCache();

        verify(redisTemplate, never()).delete(anySet());
    }

    @Test
    @DisplayName("缓存反序列化失败降级查DB")
    void getTimezones_cacheDeserializeError_fallsBackToDb() {
        List<Map<String, Object>> dbData = Arrays.asList(
                createTimezone("Asia/Shanghai", "+08:00", "中国标准时间")
        );

        when(valueOperations.get("timezones:list:all")).thenReturn("invalid-json");
        when(timezoneMapper.selectTimezones(isNull())).thenReturn(dbData);

        List<Map<String, Object>> result = timezoneService.getTimezones(null);

        assertEquals(1, result.size());
        assertEquals("Asia/Shanghai", result.get(0).get("timezone_id"));
    }
}
