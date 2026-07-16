package com.xss.analyticsservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xss.analyticsservice.entity.StatsImageUpload;
import com.xss.analyticsservice.entity.StatsPropertyView;
import com.xss.analyticsservice.entity.StatsUserAction;
import com.xss.analyticsservice.mapper.StatsImageUploadMapper;
import com.xss.analyticsservice.mapper.StatsPropertyViewMapper;
import com.xss.analyticsservice.mapper.StatsUserActionMapper;
import com.xss.analyticsservice.vo.DashboardSummaryVO;
import com.xss.analyticsservice.vo.ImageUploadSummaryVO;
import com.xss.analyticsservice.vo.PropertyViewStatsVO;
import com.xss.analyticsservice.vo.UserActionStatsVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsQueryServiceTest {

    @Mock
    private StatsPropertyViewMapper propertyViewMapper;

    @Mock
    private StatsImageUploadMapper imageUploadMapper;

    @Mock
    private StatsUserActionMapper userActionMapper;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private StatsQueryService statsQueryService;

    private LocalDate today;
    private String appId;

    @BeforeEach
    void setUp() {
        today = LocalDate.now();
        appId = "test-app";
    }

    @Test
    @DisplayName("getPropertyViews: 按时间范围查询房产浏览统计")
    void getPropertyViews_withDateRange_shouldReturnRecords() {
        String startDate = today.minusDays(7).toString();
        String endDate = today.toString();

        List<StatsPropertyView> records = new ArrayList<>();
        StatsPropertyView record1 = new StatsPropertyView();
        record1.setAppId(appId);
        record1.setPropertyId(1L);
        record1.setStatsDate(today);
        record1.setViewCount(100L);
        record1.setUniqueVisitors(50L);
        records.add(record1);

        when(propertyViewMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(records);

        List<PropertyViewStatsVO> result = statsQueryService.getPropertyViews(appId, startDate, endDate);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(appId, result.get(0).getAppId());
        assertEquals(1L, result.get(0).getPropertyId());
        assertEquals(100L, result.get(0).getViewCount());
        assertEquals(50L, result.get(0).getUniqueVisitors());
    }

    @Test
    @DisplayName("getPropertyViews: endDate为null时使用当天")
    void getPropertyViews_nullEndDate_shouldUseToday() {
        String startDate = today.minusDays(7).toString();

        when(propertyViewMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        List<PropertyViewStatsVO> result = statsQueryService.getPropertyViews(appId, startDate, null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getPropertyViews: 空数据返回空列表")
    void getPropertyViews_emptyData_shouldReturnEmptyList() {
        String startDate = today.minusDays(7).toString();
        String endDate = today.toString();

        when(propertyViewMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        List<PropertyViewStatsVO> result = statsQueryService.getPropertyViews(appId, startDate, endDate);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getImageUploadSummary: 从数据库获取图片上传统计")
    void getImageUploadSummary_fromDatabase_shouldReturnRecords() {
        List<StatsImageUpload> records = new ArrayList<>();
        StatsImageUpload record1 = new StatsImageUpload();
        record1.setAppId(appId);
        record1.setStatsDate(today);
        record1.setUploadCount(10L);
        record1.setTotalSize(102400L);
        records.add(record1);

        when(imageUploadMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(records);

        List<ImageUploadSummaryVO> result = statsQueryService.getImageUploadSummary(appId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(appId, result.get(0).getAppId());
        assertEquals(10L, result.get(0).getUploadCount());
        assertEquals(102400L, result.get(0).getTotalSize());
        assertNotNull(result.get(0).getTotalSizeFormatted());
    }

    @Test
    @DisplayName("getImageUploadSummary: 数据库为空时从Redis获取")
    void getImageUploadSummary_databaseEmpty_shouldFallbackToRedis() {
        String date = today.toString();
        Set<String> countKeys = new HashSet<>();
        countKeys.add("stats:image:upload:app1:" + date + ":count");
        countKeys.add("stats:image:upload:app2:" + date + ":count");

        when(imageUploadMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(redisTemplate.keys("stats:image:upload:*:" + date + ":count")).thenReturn(countKeys);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("stats:image:upload:app1:" + date + ":count")).thenReturn("5");
        when(valueOperations.get("stats:image:upload:app1:" + date + ":size")).thenReturn("51200");
        when(valueOperations.get("stats:image:upload:app2:" + date + ":count")).thenReturn("10");
        when(valueOperations.get("stats:image:upload:app2:" + date + ":size")).thenReturn("204800");

        List<ImageUploadSummaryVO> result = statsQueryService.getImageUploadSummary(appId);

        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("getImageUploadSummary: 数据库和Redis都为空时返回空列表")
    void getImageUploadSummary_bothEmpty_shouldReturnEmptyList() {
        String date = today.toString();

        when(imageUploadMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(redisTemplate.keys("stats:image:upload:*:" + date + ":count")).thenReturn(Collections.emptySet());

        List<ImageUploadSummaryVO> result = statsQueryService.getImageUploadSummary(appId);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getImageUploadSummary: Redis keys为null时返回空列表")
    void getImageUploadSummary_nullRedisKeys_shouldReturnEmptyList() {
        String date = today.toString();

        when(imageUploadMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(redisTemplate.keys("stats:image:upload:*:" + date + ":count")).thenReturn(null);

        List<ImageUploadSummaryVO> result = statsQueryService.getImageUploadSummary(appId);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getUserActions: 按条件查询用户行为统计")
    void getUserActions_withConditions_shouldReturnRecords() {
        String startDate = today.minusDays(7).toString();
        String endDate = today.toString();
        String eventType = "USER_LOGIN";

        List<StatsUserAction> records = new ArrayList<>();
        StatsUserAction record1 = new StatsUserAction();
        record1.setAppId(appId);
        record1.setEventType(eventType);
        record1.setStatsDate(today);
        record1.setActionCount(50L);
        records.add(record1);

        when(userActionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(records);

        List<UserActionStatsVO> result = statsQueryService.getUserActions(appId, eventType, startDate, endDate);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(appId, result.get(0).getAppId());
        assertEquals(eventType, result.get(0).getEventType());
        assertEquals(50L, result.get(0).getActionCount());
    }

    @Test
    @DisplayName("getUserActions: startDate为null时使用当天")
    void getUserActions_nullStartDate_shouldUseToday() {
        when(userActionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        List<UserActionStatsVO> result = statsQueryService.getUserActions(appId, "USER_LOGIN", null, null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getUserActions: endDate为null时使用当天")
    void getUserActions_nullEndDate_shouldUseToday() {
        String startDate = today.minusDays(7).toString();

        when(userActionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        List<UserActionStatsVO> result = statsQueryService.getUserActions(appId, "USER_LOGIN", startDate, null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getUserActions: 空数据返回空列表")
    void getUserActions_emptyData_shouldReturnEmptyList() {
        String startDate = today.minusDays(7).toString();
        String endDate = today.toString();

        when(userActionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        List<UserActionStatsVO> result = statsQueryService.getUserActions(appId, "USER_LOGIN", startDate, endDate);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getDashboard: 返回仪表盘总览数据")
    void getDashboard_shouldReturnSummaryData() {
        String date = today.toString();
        Set<String> propertyViewKeys = new HashSet<>();
        propertyViewKeys.add("stats:property:view:app1:" + date);

        Set<String> imageUploadKeys = new HashSet<>();
        imageUploadKeys.add("stats:image:upload:app1:" + date + ":count");

        Set<String> userRegisterKeys = new HashSet<>();
        userRegisterKeys.add("stats:user:USER_REGISTER:app1:" + date);

        Set<String> userLoginKeys = new HashSet<>();
        userLoginKeys.add("stats:user:USER_LOGIN:app1:" + date);

        Set<String> propertyCreateKeys = new HashSet<>();
        propertyCreateKeys.add("stats:user:PROPERTY_CREATE:app1:" + date);

        List<StatsPropertyView> topProperties = new ArrayList<>();
        StatsPropertyView top1 = new StatsPropertyView();
        top1.setAppId(appId);
        top1.setPropertyId(1L);
        top1.setStatsDate(today);
        top1.setViewCount(200L);
        top1.setUniqueVisitors(100L);
        topProperties.add(top1);

        when(redisTemplate.keys("stats:property:view:*:" + date)).thenReturn(propertyViewKeys);
        when(redisTemplate.keys("stats:image:upload:*:" + date + ":count")).thenReturn(imageUploadKeys);
        when(redisTemplate.keys("stats:user:USER_REGISTER:*:" + date)).thenReturn(userRegisterKeys);
        when(redisTemplate.keys("stats:user:USER_LOGIN:*:" + date)).thenReturn(userLoginKeys);
        when(redisTemplate.keys("stats:user:PROPERTY_CREATE:*:" + date)).thenReturn(propertyCreateKeys);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(hashOperations.entries(anyString())).thenReturn(Collections.singletonMap("1:count", "100"));
        when(valueOperations.get(anyString())).thenReturn("50");
        when(propertyViewMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(topProperties);
        when(imageUploadMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        DashboardSummaryVO result = statsQueryService.getDashboard();

        assertNotNull(result);
        assertEquals(today, result.getToday());
        assertNotNull(result.getTodayPropertyViews());
        assertNotNull(result.getTodayImageUploads());
        assertNotNull(result.getTodayUserRegisters());
        assertNotNull(result.getTodayUserLogins());
        assertNotNull(result.getTodayPropertyCreates());
        assertNotNull(result.getTopProperties());
        assertNotNull(result.getAppImageSummary());
    }

    @Test
    @DisplayName("getDashboard: 无数据时返回0值")
    void getDashboard_noData_shouldReturnZeroValues() {
        String date = today.toString();

        when(redisTemplate.keys(anyString())).thenReturn(Collections.emptySet());
        when(propertyViewMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(imageUploadMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        DashboardSummaryVO result = statsQueryService.getDashboard();

        assertNotNull(result);
        assertEquals(0L, result.getTodayPropertyViews());
        assertEquals(0L, result.getTodayImageUploads());
        assertEquals(0L, result.getTodayUserRegisters());
        assertEquals(0L, result.getTodayUserLogins());
        assertEquals(0L, result.getTodayPropertyCreates());
        assertTrue(result.getTopProperties().isEmpty());
    }

    @Test
    @DisplayName("formatSize: 0字节返回0 B")
    void formatSize_zeroBytes_shouldReturnZeroB() {
        List<StatsImageUpload> records = new ArrayList<>();
        StatsImageUpload record = new StatsImageUpload();
        record.setAppId(appId);
        record.setStatsDate(today);
        record.setUploadCount(0L);
        record.setTotalSize(0L);
        records.add(record);

        when(imageUploadMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(records);

        List<ImageUploadSummaryVO> result = statsQueryService.getImageUploadSummary(appId);

        assertEquals("0 B", result.get(0).getTotalSizeFormatted());
    }

    @Test
    @DisplayName("formatSize: 1024字节返回1.00 KB")
    void formatSize_1024Bytes_shouldReturnKB() {
        List<StatsImageUpload> records = new ArrayList<>();
        StatsImageUpload record = new StatsImageUpload();
        record.setAppId(appId);
        record.setStatsDate(today);
        record.setUploadCount(1L);
        record.setTotalSize(1024L);
        records.add(record);

        when(imageUploadMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(records);

        List<ImageUploadSummaryVO> result = statsQueryService.getImageUploadSummary(appId);

        assertTrue(result.get(0).getTotalSizeFormatted().contains("KB"));
    }

    @Test
    @DisplayName("formatSize: 1MB返回正确格式")
    void formatSize_1MB_shouldReturnMB() {
        List<StatsImageUpload> records = new ArrayList<>();
        StatsImageUpload record = new StatsImageUpload();
        record.setAppId(appId);
        record.setStatsDate(today);
        record.setUploadCount(1L);
        record.setTotalSize(1024 * 1024L);
        records.add(record);

        when(imageUploadMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(records);

        List<ImageUploadSummaryVO> result = statsQueryService.getImageUploadSummary(appId);

        assertTrue(result.get(0).getTotalSizeFormatted().contains("MB"));
    }
}
