package com.xss.dictservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xss.dictservice.mapper.LanguageMapper;
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
@DisplayName("LanguageService 单元测试")
class LanguageServiceTest {

    @Mock
    private LanguageMapper languageMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private LanguageService languageService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        languageService = new LanguageService(languageMapper, redisTemplate);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private Map<String, Object> createLanguage(String code, String nameZh, String nativeName) {
        Map<String, Object> language = new HashMap<>();
        language.put("lang_code", code);
        language.put("name_zh", nameZh);
        language.put("native_name", nativeName);
        return language;
    }

    // ========== getLanguages 测试 ==========

    @Test
    @DisplayName("getLanguages: 缓存命中直接返回列表")
    void getLanguages_cacheHit_returnsFromCache() throws Exception {
        List<Map<String, Object>> expected = Arrays.asList(
                createLanguage("chi", "中文", "中文")
        );
        String cachedJson = objectMapper.writeValueAsString(expected);

        when(valueOperations.get("languages:list:all")).thenReturn(cachedJson);

        List<Map<String, Object>> result = languageService.getLanguages(null);

        assertEquals(1, result.size());
        assertEquals("中文", result.get(0).get("name_zh"));
        verify(languageMapper, never()).selectLanguages(anyString());
    }

    @Test
    @DisplayName("getLanguages: 缓存未命中查DB并写入缓存")
    void getLanguages_cacheMiss_queriesDbAndCaches() {
        List<Map<String, Object>> dbData = Arrays.asList(
                createLanguage("chi", "中文", "中文")
        );

        when(valueOperations.get("languages:list:all")).thenReturn(null);
        when(languageMapper.selectLanguages(isNull())).thenReturn(dbData);

        List<Map<String, Object>> result = languageService.getLanguages(null);

        assertEquals(1, result.size());
        assertEquals("中文", result.get(0).get("name_zh"));
        verify(valueOperations, times(1)).set(eq("languages:list:all"), anyString(), eq(1L), eq(TimeUnit.HOURS));
    }

    @Test
    @DisplayName("getLanguages: 带keyword过滤")
    void getLanguages_withKeyword_filtersCorrectly() {
        List<Map<String, Object>> dbData = Arrays.asList(
                createLanguage("eng", "英语", "English")
        );

        when(valueOperations.get("languages:list:eng")).thenReturn(null);
        when(languageMapper.selectLanguages("eng")).thenReturn(dbData);

        List<Map<String, Object>> result = languageService.getLanguages("eng");

        assertEquals(1, result.size());
        assertEquals("英语", result.get(0).get("name_zh"));
    }

    @Test
    @DisplayName("getLanguages: Redis异常降级查DB")
    void getLanguages_redisException_fallsBackToDb() {
        List<Map<String, Object>> dbData = Arrays.asList(
                createLanguage("chi", "中文", "中文")
        );

        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis连接失败"));
        when(languageMapper.selectLanguages(isNull())).thenReturn(dbData);

        List<Map<String, Object>> result = languageService.getLanguages(null);

        assertEquals(1, result.size());
        assertEquals("中文", result.get(0).get("name_zh"));
    }

    // ========== getLanguage 测试 ==========

    @Test
    @DisplayName("getLanguage: 缓存命中返回详情")
    void getLanguage_cacheHit_returnsFromCache() throws Exception {
        Map<String, Object> expected = createLanguage("chi", "中文", "中文");
        String cachedJson = objectMapper.writeValueAsString(expected);

        when(valueOperations.get("languages:detail:chi")).thenReturn(cachedJson);

        Map<String, Object> result = languageService.getLanguage("chi");

        assertNotNull(result);
        assertEquals("中文", result.get("name_zh"));
    }

    @Test
    @DisplayName("getLanguage: 缓存未命中查DB")
    void getLanguage_cacheMiss_queriesDb() {
        Map<String, Object> dbData = createLanguage("eng", "英语", "English");

        when(valueOperations.get("languages:detail:eng")).thenReturn(null);
        when(languageMapper.selectLanguageByCode("eng")).thenReturn(dbData);

        Map<String, Object> result = languageService.getLanguage("eng");

        assertNotNull(result);
        assertEquals("英语", result.get("name_zh"));
    }

    @Test
    @DisplayName("getLanguage: 不存在返回null")
    void getLanguage_nonExistingCode_returnsNull() {
        when(valueOperations.get("languages:detail:xxx")).thenReturn(null);
        when(languageMapper.selectLanguageByCode("xxx")).thenReturn(null);

        Map<String, Object> result = languageService.getLanguage("xxx");

        assertNull(result);
    }

    // ========== clearCache 测试 ==========

    @Test
    @DisplayName("clearCache: 清除所有语言缓存")
    @SuppressWarnings("unchecked")
    void clearCache_deletesAllLanguageKeys() {
        Set<String> keys = new HashSet<>(Arrays.asList(
                "languages:list:all",
                "languages:detail:chi"
        ));

        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(keys);

        languageService.clearCache();

        verify(redisTemplate, times(1)).delete(keys);
    }

    @Test
    @DisplayName("clearCache: 无匹配键时不删除")
    @SuppressWarnings("unchecked")
    void clearCache_noKeys_doesNotDelete() {
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(Collections.emptySet());

        languageService.clearCache();

        verify(redisTemplate, never()).delete(anySet());
    }

    @Test
    @DisplayName("缓存反序列化失败降级查DB")
    void getLanguages_cacheDeserializeError_fallsBackToDb() {
        List<Map<String, Object>> dbData = Arrays.asList(
                createLanguage("chi", "中文", "中文")
        );

        when(valueOperations.get("languages:list:all")).thenReturn("invalid-json");
        when(languageMapper.selectLanguages(isNull())).thenReturn(dbData);

        List<Map<String, Object>> result = languageService.getLanguages(null);

        assertEquals(1, result.size());
        assertEquals("中文", result.get(0).get("name_zh"));
    }
}
