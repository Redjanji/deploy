package com.xss.dictservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xss.dictservice.mapper.CurrencyMapper;
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
@DisplayName("CurrencyService 单元测试")
class CurrencyServiceTest {

    @Mock
    private CurrencyMapper currencyMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private CurrencyService currencyService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        currencyService = new CurrencyService(currencyMapper, redisTemplate);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private Map<String, Object> createCurrency(String code, String nameZh, String symbol, int status) {
        Map<String, Object> currency = new HashMap<>();
        currency.put("currency_code", code);
        currency.put("name_zh", nameZh);
        currency.put("symbol", symbol);
        currency.put("status", status);
        return currency;
    }

    // ========== getCurrencies 测试 ==========

    @Test
    @DisplayName("getCurrencies: 缓存命中直接返回列表")
    void getCurrencies_cacheHit_returnsFromCache() throws Exception {
        List<Map<String, Object>> expected = Arrays.asList(
                createCurrency("CNY", "人民币", "¥", 1)
        );
        String cachedJson = objectMapper.writeValueAsString(expected);

        when(valueOperations.get("currencies:list:all:all")).thenReturn(cachedJson);

        List<Map<String, Object>> result = currencyService.getCurrencies(null, null);

        assertEquals(1, result.size());
        assertEquals("人民币", result.get(0).get("name_zh"));
        verify(currencyMapper, never()).selectCurrencies(any(), anyString());
    }

    @Test
    @DisplayName("getCurrencies: 缓存未命中查DB并写入缓存")
    void getCurrencies_cacheMiss_queriesDbAndCaches() {
        List<Map<String, Object>> dbData = Arrays.asList(
                createCurrency("CNY", "人民币", "¥", 1)
        );

        when(valueOperations.get("currencies:list:all:all")).thenReturn(null);
        when(currencyMapper.selectCurrencies(isNull(), isNull())).thenReturn(dbData);

        List<Map<String, Object>> result = currencyService.getCurrencies(null, null);

        assertEquals(1, result.size());
        assertEquals("人民币", result.get(0).get("name_zh"));
        verify(valueOperations, times(1)).set(eq("currencies:list:all:all"), anyString(), eq(1L), eq(TimeUnit.HOURS));
    }

    @Test
    @DisplayName("getCurrencies: 带status和keyword过滤")
    void getCurrencies_withStatusAndKeyword_filtersCorrectly() {
        List<Map<String, Object>> dbData = Arrays.asList(
                createCurrency("USD", "美元", "$", 1)
        );

        when(valueOperations.get("currencies:list:1:USD")).thenReturn(null);
        when(currencyMapper.selectCurrencies(1, "USD")).thenReturn(dbData);

        List<Map<String, Object>> result = currencyService.getCurrencies(1, "USD");

        assertEquals(1, result.size());
        assertEquals("美元", result.get(0).get("name_zh"));
    }

    @Test
    @DisplayName("getCurrencies: Redis异常降级查DB")
    void getCurrencies_redisException_fallsBackToDb() {
        List<Map<String, Object>> dbData = Arrays.asList(
                createCurrency("CNY", "人民币", "¥", 1)
        );

        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis连接失败"));
        when(currencyMapper.selectCurrencies(isNull(), isNull())).thenReturn(dbData);

        List<Map<String, Object>> result = currencyService.getCurrencies(null, null);

        assertEquals(1, result.size());
        assertEquals("人民币", result.get(0).get("name_zh"));
    }

    // ========== getCurrency 测试 ==========

    @Test
    @DisplayName("getCurrency: 缓存命中返回详情")
    void getCurrency_cacheHit_returnsFromCache() throws Exception {
        Map<String, Object> expected = createCurrency("CNY", "人民币", "¥", 1);
        String cachedJson = objectMapper.writeValueAsString(expected);

        when(valueOperations.get("currencies:detail:CNY")).thenReturn(cachedJson);

        Map<String, Object> result = currencyService.getCurrency("CNY");

        assertNotNull(result);
        assertEquals("人民币", result.get("name_zh"));
    }

    @Test
    @DisplayName("getCurrency: 缓存未命中查DB")
    void getCurrency_cacheMiss_queriesDb() {
        Map<String, Object> dbData = createCurrency("USD", "美元", "$", 1);

        when(valueOperations.get("currencies:detail:USD")).thenReturn(null);
        when(currencyMapper.selectCurrencyByCode("USD")).thenReturn(dbData);

        Map<String, Object> result = currencyService.getCurrency("USD");

        assertNotNull(result);
        assertEquals("美元", result.get("name_zh"));
    }

    @Test
    @DisplayName("getCurrency: 不存在返回null")
    void getCurrency_nonExistingCode_returnsNull() {
        when(valueOperations.get("currencies:detail:XXX")).thenReturn(null);
        when(currencyMapper.selectCurrencyByCode("XXX")).thenReturn(null);

        Map<String, Object> result = currencyService.getCurrency("XXX");

        assertNull(result);
    }

    // ========== clearCache 测试 ==========

    @Test
    @DisplayName("clearCache: 清除所有货币缓存")
    @SuppressWarnings("unchecked")
    void clearCache_deletesAllCurrencyKeys() {
        Set<String> keys = new HashSet<>(Arrays.asList(
                "currencies:list:all:all",
                "currencies:detail:CNY"
        ));

        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(keys);

        currencyService.clearCache();

        verify(redisTemplate, times(1)).delete(keys);
    }

    @Test
    @DisplayName("clearCache: 无匹配键时不删除")
    @SuppressWarnings("unchecked")
    void clearCache_noKeys_doesNotDelete() {
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(Collections.emptySet());

        currencyService.clearCache();

        verify(redisTemplate, never()).delete(anySet());
    }

    @Test
    @DisplayName("缓存反序列化失败降级查DB")
    void getCurrencies_cacheDeserializeError_fallsBackToDb() {
        List<Map<String, Object>> dbData = Arrays.asList(
                createCurrency("CNY", "人民币", "¥", 1)
        );

        when(valueOperations.get("currencies:list:all:all")).thenReturn("invalid-json");
        when(currencyMapper.selectCurrencies(isNull(), isNull())).thenReturn(dbData);

        List<Map<String, Object>> result = currencyService.getCurrencies(null, null);

        assertEquals(1, result.size());
        assertEquals("人民币", result.get(0).get("name_zh"));
    }
}
