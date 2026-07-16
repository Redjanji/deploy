package com.xss.dictservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xss.dictservice.mapper.ChinaRegionMapper;
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
@DisplayName("ChinaRegionService 单元测试")
class ChinaRegionServiceTest {

    @Mock
    private ChinaRegionMapper regionMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ChinaRegionService regionService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        regionService = new ChinaRegionService(regionMapper, redisTemplate);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private Map<String, Object> createRegion(String code, String name, String type, int sortOrder) {
        Map<String, Object> region = new HashMap<>();
        region.put("region_code", code);
        region.put("region_name", name);
        region.put("region_type", type);
        region.put("sort_order", sortOrder);
        return region;
    }

    // ========== getProvinces 测试 ==========

    @Test
    @DisplayName("getProvinces: 缓存命中直接返回")
    void getProvinces_cacheHit_returnsFromCache() throws Exception {
        List<Map<String, Object>> expected = Arrays.asList(
                createRegion("440000", "广东省", "province", 1)
        );
        String cachedJson = objectMapper.writeValueAsString(expected);

        when(valueOperations.get("regions:provinces")).thenReturn(cachedJson);

        List<Map<String, Object>> result = regionService.getProvinces();

        assertEquals(1, result.size());
        assertEquals("广东省", result.get(0).get("region_name"));
        verify(regionMapper, never()).selectProvinces();
    }

    @Test
    @DisplayName("getProvinces: 缓存未命中查DB并写入缓存")
    void getProvinces_cacheMiss_queriesDbAndCaches() {
        List<Map<String, Object>> dbData = Arrays.asList(
                createRegion("440000", "广东省", "province", 1)
        );

        when(valueOperations.get("regions:provinces")).thenReturn(null);
        when(regionMapper.selectProvinces()).thenReturn(dbData);

        List<Map<String, Object>> result = regionService.getProvinces();

        assertEquals(1, result.size());
        assertEquals("广东省", result.get(0).get("region_name"));
        verify(valueOperations, times(1)).set(eq("regions:provinces"), anyString(), eq(1L), eq(TimeUnit.HOURS));
    }

    @Test
    @DisplayName("getProvinces: Redis异常降级查DB")
    void getProvinces_redisException_fallsBackToDb() {
        List<Map<String, Object>> dbData = Arrays.asList(
                createRegion("440000", "广东省", "province", 1)
        );

        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis连接失败"));
        when(regionMapper.selectProvinces()).thenReturn(dbData);

        List<Map<String, Object>> result = regionService.getProvinces();

        assertEquals(1, result.size());
        assertEquals("广东省", result.get(0).get("region_name"));
    }

    // ========== getCities 测试 ==========

    @Test
    @DisplayName("getCities: 带provinceCode查询")
    void getCities_withProvinceCode_returnsFiltered() throws Exception {
        String provinceCode = "440000";
        List<Map<String, Object>> expected = Arrays.asList(
                createRegion("440300", "深圳市", "city", 1)
        );
        String cachedJson = objectMapper.writeValueAsString(expected);

        when(valueOperations.get("regions:cities:440000")).thenReturn(cachedJson);

        List<Map<String, Object>> result = regionService.getCities(provinceCode);

        assertEquals(1, result.size());
        assertEquals("深圳市", result.get(0).get("region_name"));
    }

    @Test
    @DisplayName("getCities: provinceCode为null时使用all")
    void getCities_nullProvinceCode_usesAllCacheKey() {
        List<Map<String, Object>> dbData = Arrays.asList(
                createRegion("440300", "深圳市", "city", 1)
        );

        when(valueOperations.get("regions:cities:all")).thenReturn(null);
        when(regionMapper.selectCities(isNull())).thenReturn(dbData);

        List<Map<String, Object>> result = regionService.getCities(null);

        assertEquals(1, result.size());
        verify(regionMapper, times(1)).selectCities(isNull());
    }

    @Test
    @DisplayName("getCities: 缓存反序列化失败降级查DB")
    void getCities_cacheDeserializeError_fallsBackToDb() {
        String provinceCode = "440000";
        List<Map<String, Object>> dbData = Arrays.asList(
                createRegion("440300", "深圳市", "city", 1)
        );

        when(valueOperations.get("regions:cities:440000")).thenReturn("invalid-json");
        when(regionMapper.selectCities("440000")).thenReturn(dbData);

        List<Map<String, Object>> result = regionService.getCities(provinceCode);

        assertEquals(1, result.size());
        assertEquals("深圳市", result.get(0).get("region_name"));
    }

    // ========== getDistricts 测试 ==========

    @Test
    @DisplayName("getDistricts: 带cityCode查询")
    void getDistricts_withCityCode_returnsFiltered() {
        String cityCode = "440300";
        List<Map<String, Object>> dbData = Arrays.asList(
                createRegion("440305", "南山区", "district", 1)
        );

        when(valueOperations.get("regions:districts:440300")).thenReturn(null);
        when(regionMapper.selectDistricts("440300")).thenReturn(dbData);

        List<Map<String, Object>> result = regionService.getDistricts(cityCode);

        assertEquals(1, result.size());
        assertEquals("南山区", result.get(0).get("region_name"));
    }

    @Test
    @DisplayName("getDistricts: cityCode为null时使用all")
    void getDistricts_nullCityCode_usesAllCacheKey() {
        List<Map<String, Object>> dbData = new ArrayList<>();

        when(valueOperations.get("regions:districts:all")).thenReturn(null);
        when(regionMapper.selectDistricts(isNull())).thenReturn(dbData);

        regionService.getDistricts(null);

        verify(regionMapper, times(1)).selectDistricts(isNull());
    }

    // ========== getTowns 测试 ==========

    @Test
    @DisplayName("getTowns: 带districtCode查询")
    void getTowns_withDistrictCode_returnsFiltered() {
        String districtCode = "440305";
        List<Map<String, Object>> dbData = Arrays.asList(
                createRegion("440305001", "南头街道", "town", 1)
        );

        when(valueOperations.get("regions:towns:440305")).thenReturn(null);
        when(regionMapper.selectTowns("440305")).thenReturn(dbData);

        List<Map<String, Object>> result = regionService.getTowns(districtCode);

        assertEquals(1, result.size());
        assertEquals("南头街道", result.get(0).get("region_name"));
    }

    // ========== getVillages 测试 ==========

    @Test
    @DisplayName("getVillages: 带townCode查询")
    void getVillages_withTownCode_returnsFiltered() {
        String townCode = "440305001";
        List<Map<String, Object>> dbData = Arrays.asList(
                createRegion("440305001001", "南头城社区", "village", 1)
        );

        when(valueOperations.get("regions:villages:440305001")).thenReturn(null);
        when(regionMapper.selectVillages("440305001")).thenReturn(dbData);

        List<Map<String, Object>> result = regionService.getVillages(townCode);

        assertEquals(1, result.size());
        assertEquals("南头城社区", result.get(0).get("region_name"));
    }

    // ========== getPath 测试 ==========

    @Test
    @DisplayName("getPath: 带regionCode和level查询")
    void getPath_withRegionCodeAndLevel_returnsPath() {
        String regionCode = "440305";
        Integer level = 3;
        List<Map<String, Object>> dbData = new ArrayList<>();
        Map<String, Object> path = new HashMap<>();
        path.put("region_code", "440305");
        path.put("region_name", "南山区");
        path.put("full_path", "广东省 > 深圳市 > 南山区");
        dbData.add(path);

        when(valueOperations.get("regions:path:440305:3")).thenReturn(null);
        when(regionMapper.selectPath("440305", 3)).thenReturn(dbData);

        List<Map<String, Object>> result = regionService.getPath(regionCode, level);

        assertEquals(1, result.size());
        assertEquals("广东省 > 深圳市 > 南山区", result.get(0).get("full_path"));
    }

    @Test
    @DisplayName("getPath: regionCode和level都为null时使用all")
    void getPath_nullParams_usesAllCacheKey() {
        List<Map<String, Object>> dbData = new ArrayList<>();

        when(valueOperations.get("regions:path:all:all")).thenReturn(null);
        when(regionMapper.selectPath(isNull(), isNull())).thenReturn(dbData);

        regionService.getPath(null, null);

        verify(regionMapper, times(1)).selectPath(isNull(), isNull());
    }

    // ========== clearCache 测试 ==========

    @Test
    @DisplayName("clearCache: 清除所有region缓存")
    @SuppressWarnings("unchecked")
    void clearCache_deletesAllRegionKeys() {
        Set<String> keys = new HashSet<>(Arrays.asList(
                "regions:provinces",
                "regions:cities:440000"
        ));

        when(redisTemplate.execute(any(RedisCallback.class))).thenAnswer(invocation -> keys);

        regionService.clearCache();

        verify(redisTemplate, times(1)).delete(keys);
    }

    @Test
    @DisplayName("clearCache: 无匹配键时不删除")
    @SuppressWarnings("unchecked")
    void clearCache_noKeys_doesNotDelete() {
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(Collections.emptySet());

        regionService.clearCache();

        verify(redisTemplate, never()).delete(anySet());
    }

    @Test
    @DisplayName("缓存写入失败不影响返回结果")
    void getProvinces_cacheWriteFailure_stillReturnsData() {
        List<Map<String, Object>> dbData = Arrays.asList(
                createRegion("440000", "广东省", "province", 1)
        );

        when(valueOperations.get("regions:provinces")).thenReturn(null);
        when(regionMapper.selectProvinces()).thenReturn(dbData);
        doThrow(new RuntimeException("Redis写入失败")).when(valueOperations)
                .set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        List<Map<String, Object>> result = regionService.getProvinces();

        assertEquals(1, result.size());
        assertEquals("广东省", result.get(0).get("region_name"));
    }
}
