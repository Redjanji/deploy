package com.xss.analyticsservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xss.analyticsservice.entity.StatsImageUpload;
import com.xss.analyticsservice.entity.StatsPropertyView;
import com.xss.analyticsservice.entity.StatsUserAction;
import com.xss.analyticsservice.mapper.StatsImageUploadMapper;
import com.xss.analyticsservice.mapper.StatsPropertyViewMapper;
import com.xss.analyticsservice.mapper.StatsUserActionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.HyperLogLogOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsFlushServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private StatsPropertyViewMapper propertyViewMapper;

    @Mock
    private StatsImageUploadMapper imageUploadMapper;

    @Mock
    private StatsUserActionMapper userActionMapper;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private HyperLogLogOperations<String, Object> hyperLogLogOperations;

    @InjectMocks
    private StatsFlushService statsFlushService;

    private String today;

    @BeforeEach
    void setUp() {
        today = LocalDate.now().toString();
    }

    @Test
    @DisplayName("flushToDatabase: 无数据时不执行任何数据库操作")
    void flushToDatabase_noData_shouldNotCallMappers() {
        when(redisTemplate.keys(anyString())).thenReturn(Collections.emptySet());

        statsFlushService.flushToDatabase();

        verify(propertyViewMapper, never()).selectOne(any(LambdaQueryWrapper.class));
        verify(propertyViewMapper, never()).insert(any(StatsPropertyView.class));
        verify(propertyViewMapper, never()).updateById(any(StatsPropertyView.class));
        verify(imageUploadMapper, never()).selectOne(any(LambdaQueryWrapper.class));
        verify(imageUploadMapper, never()).insert(any(StatsImageUpload.class));
        verify(imageUploadMapper, never()).updateById(any(StatsImageUpload.class));
        verify(userActionMapper, never()).selectOne(any(LambdaQueryWrapper.class));
        verify(userActionMapper, never()).insert(any(StatsUserAction.class));
        verify(userActionMapper, never()).updateById(any(StatsUserAction.class));
    }

    @Test
    @DisplayName("flushToDatabase: Redis keys返回null时不抛出异常")
    void flushToDatabase_nullKeys_shouldNotThrow() {
        when(redisTemplate.keys(anyString())).thenReturn(null);

        assertDoesNotThrow(() -> statsFlushService.flushToDatabase());
    }

    @Test
    @DisplayName("flushPropertyViews: 新记录时插入数据库")
    void flushPropertyViews_newRecord_shouldInsert() {
        String viewKey = "stats:property:view:test-app:" + today;
        Set<String> keys = new HashSet<>();
        keys.add(viewKey);

        Map<Object, Object> entries = new HashMap<>();
        entries.put("123:count", "10");

        when(redisTemplate.keys("stats:property:view:*:" + today)).thenReturn(keys);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(viewKey)).thenReturn(entries);
        when(redisTemplate.opsForHyperLogLog()).thenReturn(hyperLogLogOperations);
        when(hyperLogLogOperations.size(anyString())).thenReturn(5L);
        when(propertyViewMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        statsFlushService.flushToDatabase();

        verify(propertyViewMapper).insert(any(StatsPropertyView.class));
    }

    @Test
    @DisplayName("flushPropertyViews: 已有记录时更新数据库")
    void flushPropertyViews_existingRecord_shouldUpdate() {
        String viewKey = "stats:property:view:test-app:" + today;
        Set<String> keys = new HashSet<>();
        keys.add(viewKey);

        Map<Object, Object> entries = new HashMap<>();
        entries.put("123:count", "20");

        StatsPropertyView existing = new StatsPropertyView();
        existing.setId(1L);
        existing.setViewCount(10L);
        existing.setUniqueVisitors(3L);

        when(redisTemplate.keys("stats:property:view:*:" + today)).thenReturn(keys);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(viewKey)).thenReturn(entries);
        when(redisTemplate.opsForHyperLogLog()).thenReturn(hyperLogLogOperations);
        when(hyperLogLogOperations.size(anyString())).thenReturn(8L);
        when(propertyViewMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        statsFlushService.flushToDatabase();

        verify(propertyViewMapper).updateById(any(StatsPropertyView.class));
    }

    @Test
    @DisplayName("flushPropertyViews: UV为null时使用0")
    void flushPropertyViews_nullUv_shouldUseZero() {
        String viewKey = "stats:property:view:test-app:" + today;
        Set<String> keys = new HashSet<>();
        keys.add(viewKey);

        Map<Object, Object> entries = new HashMap<>();
        entries.put("123:count", "10");

        when(redisTemplate.keys("stats:property:view:*:" + today)).thenReturn(keys);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(viewKey)).thenReturn(entries);
        when(redisTemplate.opsForHyperLogLog()).thenReturn(hyperLogLogOperations);
        when(hyperLogLogOperations.size(anyString())).thenReturn(null);
        when(propertyViewMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        statsFlushService.flushToDatabase();

        verify(propertyViewMapper).insert(any(StatsPropertyView.class));
    }

    @Test
    @DisplayName("flushPropertyViews: 跳过非count字段")
    void flushPropertyViews_skipNonCountFields_shouldOnlyProcessCountFields() {
        String viewKey = "stats:property:view:test-app:" + today;
        Set<String> keys = new HashSet<>();
        keys.add(viewKey);

        Map<Object, Object> entries = new HashMap<>();
        entries.put("123:count", "10");
        entries.put("123:other", "value");
        entries.put("456:notcount", "20");

        when(redisTemplate.keys("stats:property:view:*:" + today)).thenReturn(keys);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(viewKey)).thenReturn(entries);
        when(redisTemplate.opsForHyperLogLog()).thenReturn(hyperLogLogOperations);
        when(hyperLogLogOperations.size(anyString())).thenReturn(5L);
        when(propertyViewMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        statsFlushService.flushToDatabase();

        verify(propertyViewMapper).insert(any(StatsPropertyView.class));
    }

    @Test
    @DisplayName("flushImageUploads: 新记录时插入数据库")
    void flushImageUploads_newRecord_shouldInsert() {
        String countKey = "stats:image:upload:test-app:" + today + ":count";
        Set<String> countKeys = new HashSet<>();
        countKeys.add(countKey);

        when(redisTemplate.keys("stats:image:upload:*:" + today + ":count")).thenReturn(countKeys);
        when(redisTemplate.keys("stats:property:view:*:" + today)).thenReturn(Collections.emptySet());
        when(redisTemplate.keys("stats:user:*:*:" + today)).thenReturn(Collections.emptySet());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(countKey)).thenReturn("15");
        when(valueOperations.get("stats:image:upload:test-app:" + today + ":size")).thenReturn("102400");
        when(imageUploadMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        statsFlushService.flushToDatabase();

        verify(imageUploadMapper).insert(any(StatsImageUpload.class));
    }

    @Test
    @DisplayName("flushImageUploads: 已有记录时更新数据库")
    void flushImageUploads_existingRecord_shouldUpdate() {
        String countKey = "stats:image:upload:test-app:" + today + ":count";
        Set<String> countKeys = new HashSet<>();
        countKeys.add(countKey);

        StatsImageUpload existing = new StatsImageUpload();
        existing.setId(1L);
        existing.setUploadCount(10L);
        existing.setTotalSize(51200L);

        when(redisTemplate.keys("stats:image:upload:*:" + today + ":count")).thenReturn(countKeys);
        when(redisTemplate.keys("stats:property:view:*:" + today)).thenReturn(Collections.emptySet());
        when(redisTemplate.keys("stats:user:*:*:" + today)).thenReturn(Collections.emptySet());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(countKey)).thenReturn("20");
        when(valueOperations.get("stats:image:upload:test-app:" + today + ":size")).thenReturn("204800");
        when(imageUploadMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        statsFlushService.flushToDatabase();

        verify(imageUploadMapper).updateById(any(StatsImageUpload.class));
    }

    @Test
    @DisplayName("flushImageUploads: 计数为null时使用0")
    void flushImageUploads_nullCount_shouldUseZero() {
        String countKey = "stats:image:upload:test-app:" + today + ":count";
        Set<String> countKeys = new HashSet<>();
        countKeys.add(countKey);

        when(redisTemplate.keys("stats:image:upload:*:" + today + ":count")).thenReturn(countKeys);
        when(redisTemplate.keys("stats:property:view:*:" + today)).thenReturn(Collections.emptySet());
        when(redisTemplate.keys("stats:user:*:*:" + today)).thenReturn(Collections.emptySet());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(countKey)).thenReturn(null);
        when(valueOperations.get("stats:image:upload:test-app:" + today + ":size")).thenReturn(null);
        when(imageUploadMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        statsFlushService.flushToDatabase();

        verify(imageUploadMapper).insert(any(StatsImageUpload.class));
    }

    @Test
    @DisplayName("flushUserActions: 新记录时插入数据库")
    void flushUserActions_newRecord_shouldInsert() {
        String actionKey = "stats:user:USER_LOGIN:test-app:" + today;
        Set<String> keys = new HashSet<>();
        keys.add(actionKey);

        when(redisTemplate.keys("stats:user:*:*:" + today)).thenReturn(keys);
        when(redisTemplate.keys("stats:property:view:*:" + today)).thenReturn(Collections.emptySet());
        when(redisTemplate.keys("stats:image:upload:*:" + today + ":count")).thenReturn(Collections.emptySet());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(actionKey)).thenReturn("50");
        when(userActionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        statsFlushService.flushToDatabase();

        verify(userActionMapper).insert(any(StatsUserAction.class));
    }

    @Test
    @DisplayName("flushUserActions: 已有记录时更新数据库")
    void flushUserActions_existingRecord_shouldUpdate() {
        String actionKey = "stats:user:USER_LOGIN:test-app:" + today;
        Set<String> keys = new HashSet<>();
        keys.add(actionKey);

        StatsUserAction existing = new StatsUserAction();
        existing.setId(1L);
        existing.setActionCount(30L);

        when(redisTemplate.keys("stats:user:*:*:" + today)).thenReturn(keys);
        when(redisTemplate.keys("stats:property:view:*:" + today)).thenReturn(Collections.emptySet());
        when(redisTemplate.keys("stats:image:upload:*:" + today + ":count")).thenReturn(Collections.emptySet());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(actionKey)).thenReturn("100");
        when(userActionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        statsFlushService.flushToDatabase();

        verify(userActionMapper).updateById(any(StatsUserAction.class));
    }

    @Test
    @DisplayName("flushUserActions: 计数为null时使用0")
    void flushUserActions_nullCount_shouldUseZero() {
        String actionKey = "stats:user:USER_LOGIN:test-app:" + today;
        Set<String> keys = new HashSet<>();
        keys.add(actionKey);

        when(redisTemplate.keys("stats:user:*:*:" + today)).thenReturn(keys);
        when(redisTemplate.keys("stats:property:view:*:" + today)).thenReturn(Collections.emptySet());
        when(redisTemplate.keys("stats:image:upload:*:" + today + ":count")).thenReturn(Collections.emptySet());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(actionKey)).thenReturn(null);
        when(userActionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        statsFlushService.flushToDatabase();

        verify(userActionMapper).insert(any(StatsUserAction.class));
    }

    @Test
    @DisplayName("flushToDatabase: 某个类型刷入失败不影响其他类型")
    void flushToDatabase_partialFailure_shouldContinueWithOthers() {
        String viewKey = "stats:property:view:test-app:" + today;
        Set<String> viewKeys = new HashSet<>();
        viewKeys.add(viewKey);

        String actionKey = "stats:user:USER_LOGIN:test-app:" + today;
        Set<String> actionKeys = new HashSet<>();
        actionKeys.add(actionKey);

        when(redisTemplate.keys("stats:property:view:*:" + today)).thenReturn(viewKeys);
        when(redisTemplate.opsForHash()).thenThrow(new RuntimeException("Redis error"));
        when(redisTemplate.keys("stats:image:upload:*:" + today + ":count")).thenReturn(Collections.emptySet());
        when(redisTemplate.keys("stats:user:*:*:" + today)).thenReturn(actionKeys);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(actionKey)).thenReturn("50");
        when(userActionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertDoesNotThrow(() -> statsFlushService.flushToDatabase());

        verify(userActionMapper).insert(any(StatsUserAction.class));
    }

    @Test
    @DisplayName("flushToDatabase: 多种数据类型同时刷入")
    void flushToDatabase_multipleTypes_shouldFlushAll() {
        String viewKey = "stats:property:view:test-app:" + today;
        Set<String> viewKeys = new HashSet<>();
        viewKeys.add(viewKey);

        String countKey = "stats:image:upload:test-app:" + today + ":count";
        Set<String> imageKeys = new HashSet<>();
        imageKeys.add(countKey);

        String actionKey = "stats:user:USER_LOGIN:test-app:" + today;
        Set<String> actionKeys = new HashSet<>();
        actionKeys.add(actionKey);

        Map<Object, Object> entries = new HashMap<>();
        entries.put("123:count", "10");

        when(redisTemplate.keys("stats:property:view:*:" + today)).thenReturn(viewKeys);
        when(redisTemplate.keys("stats:image:upload:*:" + today + ":count")).thenReturn(imageKeys);
        when(redisTemplate.keys("stats:user:*:*:" + today)).thenReturn(actionKeys);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(viewKey)).thenReturn(entries);
        when(redisTemplate.opsForHyperLogLog()).thenReturn(hyperLogLogOperations);
        when(hyperLogLogOperations.size(anyString())).thenReturn(5L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(countKey)).thenReturn("15");
        when(valueOperations.get("stats:image:upload:test-app:" + today + ":size")).thenReturn("102400");
        when(valueOperations.get(actionKey)).thenReturn("50");
        when(propertyViewMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(imageUploadMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(userActionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        statsFlushService.flushToDatabase();

        verify(propertyViewMapper).insert(any(StatsPropertyView.class));
        verify(imageUploadMapper).insert(any(StatsImageUpload.class));
        verify(userActionMapper).insert(any(StatsUserAction.class));
    }
}
