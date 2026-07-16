package com.xss.dictservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xss.dictservice.mapper.CountryMapper;
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
@DisplayName("CountryService 单元测试")
class CountryServiceTest {

    @Mock
    private CountryMapper countryMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private CountryService countryService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        countryService = new CountryService(countryMapper, redisTemplate);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private Map<String, Object> createCountry(String code, String nameZh, String phoneCode) {
        Map<String, Object> country = new HashMap<>();
        country.put("country_code", code);
        country.put("name_zh", nameZh);
        country.put("phone_code", phoneCode);
        return country;
    }

    // ========== getCountries 测试 ==========

    @Test
    @DisplayName("getCountries: 缓存命中直接返回列表")
    void getCountries_cacheHit_returnsFromCache() throws Exception {
        List<Map<String, Object>> expected = Arrays.asList(
                createCountry("CN", "中国", "86")
        );
        String cachedJson = objectMapper.writeValueAsString(expected);

        when(valueOperations.get("countries:list:all:all")).thenReturn(cachedJson);

        List<Map<String, Object>> result = countryService.getCountries(null, null);

        assertEquals(1, result.size());
        assertEquals("中国", result.get(0).get("name_zh"));
        verify(countryMapper, never()).selectCountries(anyString(), anyString());
    }

    @Test
    @DisplayName("getCountries: 缓存未命中查DB并写入缓存")
    void getCountries_cacheMiss_queriesDbAndCaches() {
        List<Map<String, Object>> dbData = Arrays.asList(
                createCountry("CN", "中国", "86")
        );

        when(valueOperations.get("countries:list:all:all")).thenReturn(null);
        when(countryMapper.selectCountries(isNull(), isNull())).thenReturn(dbData);

        List<Map<String, Object>> result = countryService.getCountries(null, null);

        assertEquals(1, result.size());
        assertEquals("中国", result.get(0).get("name_zh"));
        verify(valueOperations, times(1)).set(eq("countries:list:all:all"), anyString(), eq(1L), eq(TimeUnit.HOURS));
    }

    @Test
    @DisplayName("getCountries: 带continentCode和keyword过滤")
    void getCountries_withContinentAndKeyword_filtersCorrectly() {
        List<Map<String, Object>> dbData = Arrays.asList(
                createCountry("CN", "中国", "86")
        );

        when(valueOperations.get("countries:list:AS:中国")).thenReturn(null);
        when(countryMapper.selectCountries("AS", "中国")).thenReturn(dbData);

        List<Map<String, Object>> result = countryService.getCountries("AS", "中国");

        assertEquals(1, result.size());
        assertEquals("中国", result.get(0).get("name_zh"));
    }

    @Test
    @DisplayName("getCountries: Redis异常降级查DB")
    void getCountries_redisException_fallsBackToDb() {
        List<Map<String, Object>> dbData = Arrays.asList(
                createCountry("CN", "中国", "86")
        );

        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis连接失败"));
        when(countryMapper.selectCountries(isNull(), isNull())).thenReturn(dbData);

        List<Map<String, Object>> result = countryService.getCountries(null, null);

        assertEquals(1, result.size());
        assertEquals("中国", result.get(0).get("name_zh"));
    }

    // ========== getCountry 测试 ==========

    @Test
    @DisplayName("getCountry: 缓存命中返回详情")
    void getCountry_cacheHit_returnsFromCache() throws Exception {
        Map<String, Object> expected = createCountry("CN", "中国", "86");
        String cachedJson = objectMapper.writeValueAsString(expected);

        when(valueOperations.get("countries:detail:CN")).thenReturn(cachedJson);

        Map<String, Object> result = countryService.getCountry("CN");

        assertNotNull(result);
        assertEquals("中国", result.get("name_zh"));
    }

    @Test
    @DisplayName("getCountry: 缓存未命中查DB")
    void getCountry_cacheMiss_queriesDb() {
        Map<String, Object> dbData = createCountry("US", "美国", "1");

        when(valueOperations.get("countries:detail:US")).thenReturn(null);
        when(countryMapper.selectCountryByCode("US")).thenReturn(dbData);

        Map<String, Object> result = countryService.getCountry("US");

        assertNotNull(result);
        assertEquals("美国", result.get("name_zh"));
    }

    @Test
    @DisplayName("getCountry: 不存在返回null")
    void getCountry_nonExistingCode_returnsNull() {
        when(valueOperations.get("countries:detail:XX")).thenReturn(null);
        when(countryMapper.selectCountryByCode("XX")).thenReturn(null);

        Map<String, Object> result = countryService.getCountry("XX");

        assertNull(result);
    }

    @Test
    @DisplayName("getCountry: 缓存反序列化失败降级查DB")
    void getCountry_cacheDeserializeError_fallsBackToDb() {
        Map<String, Object> dbData = createCountry("CN", "中国", "86");

        when(valueOperations.get("countries:detail:CN")).thenReturn("invalid-json");
        when(countryMapper.selectCountryByCode("CN")).thenReturn(dbData);

        Map<String, Object> result = countryService.getCountry("CN");

        assertNotNull(result);
        assertEquals("中国", result.get("name_zh"));
    }

    // ========== clearCache 测试 ==========

    @Test
    @DisplayName("clearCache: 清除所有国家缓存")
    @SuppressWarnings("unchecked")
    void clearCache_deletesAllCountryKeys() {
        Set<String> keys = new HashSet<>(Arrays.asList(
                "countries:list:all:all",
                "countries:detail:CN"
        ));

        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(keys);

        countryService.clearCache();

        verify(redisTemplate, times(1)).delete(keys);
    }

    @Test
    @DisplayName("clearCache: 无匹配键时不删除")
    @SuppressWarnings("unchecked")
    void clearCache_noKeys_doesNotDelete() {
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(Collections.emptySet());

        countryService.clearCache();

        verify(redisTemplate, never()).delete(anySet());
    }

    @Test
    @DisplayName("缓存写入失败不影响返回结果")
    void getCountries_cacheWriteFailure_stillReturnsData() {
        List<Map<String, Object>> dbData = Arrays.asList(
                createCountry("CN", "中国", "86")
        );

        when(valueOperations.get("countries:list:all:all")).thenReturn(null);
        when(countryMapper.selectCountries(isNull(), isNull())).thenReturn(dbData);
        doThrow(new RuntimeException("Redis写入失败")).when(valueOperations)
                .set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        List<Map<String, Object>> result = countryService.getCountries(null, null);

        assertEquals(1, result.size());
        assertEquals("中国", result.get(0).get("name_zh"));
    }
}
