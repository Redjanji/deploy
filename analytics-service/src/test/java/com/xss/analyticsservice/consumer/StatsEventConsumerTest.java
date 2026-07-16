package com.xss.analyticsservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xss.analyticsservice.dto.StatsEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.HyperLogLogOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsEventConsumerTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private HyperLogLogOperations<String, Object> hyperLogLogOperations;

    private ObjectMapper objectMapper;
    private StatsEventConsumer statsEventConsumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        statsEventConsumer = new StatsEventConsumer(redisTemplate, objectMapper);
    }

    @Test
    @DisplayName("handleEvent: 正常接收PROPERTY_VIEW事件并记录到Redis")
    void handleEvent_propertyViewEvent_shouldRecordToRedis() throws Exception {
        StatsEvent event = new StatsEvent();
        event.setEventType("PROPERTY_VIEW");
        event.setAppId("test-app");
        event.setTargetId(123L);
        event.setUserId(456L);
        String message = objectMapper.writeValueAsString(event);

        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForHyperLogLog()).thenReturn(hyperLogLogOperations);

        statsEventConsumer.handleEvent(message);

        verify(hashOperations).increment(anyString(), eq("123:count"), eq(1L));
        verify(hyperLogLogOperations).add(anyString(), eq("456"));
    }

    @Test
    @DisplayName("handleEvent: PROPERTY_VIEW事件无userId时只记录浏览量")
    void handleEvent_propertyViewEventWithoutUserId_shouldOnlyIncrementCount() throws Exception {
        StatsEvent event = new StatsEvent();
        event.setEventType("PROPERTY_VIEW");
        event.setAppId("test-app");
        event.setTargetId(123L);
        String message = objectMapper.writeValueAsString(event);

        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        statsEventConsumer.handleEvent(message);

        verify(hashOperations).increment(anyString(), eq("123:count"), eq(1L));
    }

    @Test
    @DisplayName("handleEvent: PROPERTY_VIEW事件无targetId时不处理")
    void handleEvent_propertyViewEventWithoutTargetId_shouldNotProcess() throws Exception {
        StatsEvent event = new StatsEvent();
        event.setEventType("PROPERTY_VIEW");
        event.setAppId("test-app");
        String message = objectMapper.writeValueAsString(event);

        statsEventConsumer.handleEvent(message);
    }

    @Test
    @DisplayName("handleEvent: PROPERTY_CREATE事件正确增加用户行为计数")
    void handleEvent_propertyCreateEvent_shouldIncrementUserAction() throws Exception {
        StatsEvent event = new StatsEvent();
        event.setEventType("PROPERTY_CREATE");
        event.setAppId("test-app");
        String message = objectMapper.writeValueAsString(event);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        statsEventConsumer.handleEvent(message);

        verify(valueOperations).increment(anyString());
    }

    @Test
    @DisplayName("handleEvent: USER_REGISTER事件正确增加用户行为计数")
    void handleEvent_userRegisterEvent_shouldIncrementUserAction() throws Exception {
        StatsEvent event = new StatsEvent();
        event.setEventType("USER_REGISTER");
        event.setAppId("test-app");
        String message = objectMapper.writeValueAsString(event);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        statsEventConsumer.handleEvent(message);

        verify(valueOperations).increment(anyString());
    }

    @Test
    @DisplayName("handleEvent: USER_LOGIN事件正确增加用户行为计数")
    void handleEvent_userLoginEvent_shouldIncrementUserAction() throws Exception {
        StatsEvent event = new StatsEvent();
        event.setEventType("USER_LOGIN");
        event.setAppId("test-app");
        String message = objectMapper.writeValueAsString(event);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        statsEventConsumer.handleEvent(message);

        verify(valueOperations).increment(anyString());
    }

    @Test
    @DisplayName("handleEvent: IMAGE_UPLOAD事件带fileSize时正确记录数量和大小")
    void handleEvent_imageUploadEventWithFileSize_shouldRecordCountAndSize() throws Exception {
        StatsEvent event = new StatsEvent();
        event.setEventType("IMAGE_UPLOAD");
        event.setAppId("test-app");
        Map<String, Object> extra = new HashMap<>();
        extra.put("fileSize", 102400L);
        event.setExtra(extra);
        String message = objectMapper.writeValueAsString(event);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        statsEventConsumer.handleEvent(message);

        verify(valueOperations).increment(anyString());
        verify(valueOperations).increment(anyString(), eq(102400L));
    }

    @Test
    @DisplayName("handleEvent: IMAGE_UPLOAD事件不带fileSize时只记录数量")
    void handleEvent_imageUploadEventWithoutFileSize_shouldOnlyRecordCount() throws Exception {
        StatsEvent event = new StatsEvent();
        event.setEventType("IMAGE_UPLOAD");
        event.setAppId("test-app");
        String message = objectMapper.writeValueAsString(event);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        statsEventConsumer.handleEvent(message);

        verify(valueOperations).increment(anyString());
    }

    @Test
    @DisplayName("handleEvent: IMAGE_UPLOAD事件fileSize为字符串时正确解析")
    void handleEvent_imageUploadEventWithStringFileSize_shouldParseAndRecord() throws Exception {
        StatsEvent event = new StatsEvent();
        event.setEventType("IMAGE_UPLOAD");
        event.setAppId("test-app");
        Map<String, Object> extra = new HashMap<>();
        extra.put("fileSize", "204800");
        event.setExtra(extra);
        String message = objectMapper.writeValueAsString(event);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        statsEventConsumer.handleEvent(message);

        verify(valueOperations).increment(anyString(), eq(204800L));
    }

    @Test
    @DisplayName("handleEvent: FAVORITE_ADD事件正确增加用户行为计数")
    void handleEvent_favoriteAddEvent_shouldIncrementUserAction() throws Exception {
        StatsEvent event = new StatsEvent();
        event.setEventType("FAVORITE_ADD");
        event.setAppId("test-app");
        String message = objectMapper.writeValueAsString(event);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        statsEventConsumer.handleEvent(message);

        verify(valueOperations).increment(anyString());
    }

    @Test
    @DisplayName("handleEvent: FAVORITE_REMOVE事件正确增加用户行为计数")
    void handleEvent_favoriteRemoveEvent_shouldIncrementUserAction() throws Exception {
        StatsEvent event = new StatsEvent();
        event.setEventType("FAVORITE_REMOVE");
        event.setAppId("test-app");
        String message = objectMapper.writeValueAsString(event);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        statsEventConsumer.handleEvent(message);

        verify(valueOperations).increment(anyString());
    }

    @Test
    @DisplayName("handleEvent: IMAGE_DELETE事件正确增加用户行为计数")
    void handleEvent_imageDeleteEvent_shouldIncrementUserAction() throws Exception {
        StatsEvent event = new StatsEvent();
        event.setEventType("IMAGE_DELETE");
        event.setAppId("test-app");
        String message = objectMapper.writeValueAsString(event);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        statsEventConsumer.handleEvent(message);

        verify(valueOperations).increment(anyString());
    }

    @Test
    @DisplayName("handleEvent: JSON解析失败时记录错误不抛出异常")
    void handleEvent_invalidJson_shouldLogErrorAndNotThrow() {
        String invalidMessage = "invalid json {{{";

        assertDoesNotThrow(() -> statsEventConsumer.handleEvent(invalidMessage));
    }

    @Test
    @DisplayName("handleEvent: 未知事件类型时记录警告不抛出异常")
    void handleEvent_unknownEventType_shouldLogWarnAndNotThrow() throws Exception {
        StatsEvent event = new StatsEvent();
        event.setEventType("UNKNOWN_EVENT");
        event.setAppId("test-app");
        String message = objectMapper.writeValueAsString(event);

        assertDoesNotThrow(() -> statsEventConsumer.handleEvent(message));
    }

    @Test
    @DisplayName("handleEvent: appId为null时使用default")
    void handleEvent_nullAppId_shouldUseDefaultAppId() throws Exception {
        StatsEvent event = new StatsEvent();
        event.setEventType("USER_REGISTER");
        event.setAppId(null);
        String message = objectMapper.writeValueAsString(event);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        statsEventConsumer.handleEvent(message);

        verify(valueOperations).increment(contains("default"));
    }
}
