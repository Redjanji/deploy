package com.xss.propertyservice.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatsEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private ObjectMapper objectMapper;
    private StatsEventPublisher statsEventPublisher;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        statsEventPublisher = new StatsEventPublisher(rabbitTemplate, objectMapper);
        ReflectionTestUtils.setField(statsEventPublisher, "exchange", "test.exchange");
        ReflectionTestUtils.setField(statsEventPublisher, "routingKey", "test.routing");
    }

    @Test
    @DisplayName("正常发布事件")
    void publish_shouldSendEventSuccessfully() throws Exception {
        String eventType = "PROPERTY_VIEW";
        String appId = "test-app";
        Long userId = 1001L;
        Long targetId = 2001L;

        statsEventPublisher.publish(eventType, appId, userId, targetId);

        ArgumentCaptor<String> exchangeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> routingKeyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);

        verify(rabbitTemplate, times(1)).convertAndSend(
                exchangeCaptor.capture(),
                routingKeyCaptor.capture(),
                messageCaptor.capture()
        );

        assertEquals("test.exchange", exchangeCaptor.getValue());
        assertEquals("test.routing", routingKeyCaptor.getValue());

        JsonNode jsonNode = objectMapper.readTree(messageCaptor.getValue());
        assertEquals(eventType, jsonNode.get("eventType").asText());
        assertEquals(appId, jsonNode.get("appId").asText());
        assertEquals(userId, jsonNode.get("userId").asLong());
        assertEquals(targetId, jsonNode.get("targetId").asLong());
        assertTrue(jsonNode.has("timestamp"));
        assertFalse(jsonNode.has("extra"));
    }

    @Test
    @DisplayName("带extra参数发布")
    void publish_withExtraParams_shouldIncludeExtraInMessage() throws Exception {
        String eventType = "PROPERTY_VIEW";
        String appId = "test-app";
        Long userId = 1001L;
        Long targetId = 2001L;

        Map<String, Object> extra = new HashMap<>();
        extra.put("source", "mobile");
        extra.put("duration", 30);

        statsEventPublisher.publish(eventType, appId, userId, targetId, extra);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate, times(1)).convertAndSend(
                anyString(), anyString(), messageCaptor.capture()
        );

        JsonNode jsonNode = objectMapper.readTree(messageCaptor.getValue());
        assertTrue(jsonNode.has("extra"));
        JsonNode extraNode = jsonNode.get("extra");
        assertEquals("mobile", extraNode.get("source").asText());
        assertEquals(30, extraNode.get("duration").asInt());
    }

    @Test
    @DisplayName("appId为null时使用默认值default")
    void publish_withNullAppId_shouldUseDefaultAppId() throws Exception {
        statsEventPublisher.publish("TEST_EVENT", null, 1L, 2L);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate, times(1)).convertAndSend(
                anyString(), anyString(), messageCaptor.capture()
        );

        JsonNode jsonNode = objectMapper.readTree(messageCaptor.getValue());
        assertEquals("default", jsonNode.get("appId").asText());
    }

    @Test
    @DisplayName("userId为null时不包含该字段")
    void publish_withNullUserId_shouldNotIncludeUserId() throws Exception {
        statsEventPublisher.publish("TEST_EVENT", "app1", null, 2L);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate, times(1)).convertAndSend(
                anyString(), anyString(), messageCaptor.capture()
        );

        JsonNode jsonNode = objectMapper.readTree(messageCaptor.getValue());
        assertFalse(jsonNode.has("userId"));
    }

    @Test
    @DisplayName("targetId为null时不包含该字段")
    void publish_withNullTargetId_shouldNotIncludeTargetId() throws Exception {
        statsEventPublisher.publish("TEST_EVENT", "app1", 1L, null);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate, times(1)).convertAndSend(
                anyString(), anyString(), messageCaptor.capture()
        );

        JsonNode jsonNode = objectMapper.readTree(messageCaptor.getValue());
        assertFalse(jsonNode.has("targetId"));
    }

    @Test
    @DisplayName("extra为null时不包含extra字段")
    void publish_withNullExtra_shouldNotIncludeExtra() throws Exception {
        statsEventPublisher.publish("TEST_EVENT", "app1", 1L, 2L, null);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate, times(1)).convertAndSend(
                anyString(), anyString(), messageCaptor.capture()
        );

        JsonNode jsonNode = objectMapper.readTree(messageCaptor.getValue());
        assertFalse(jsonNode.has("extra"));
    }

    @Test
    @DisplayName("extra为空Map时不包含extra字段")
    void publish_withEmptyExtra_shouldNotIncludeExtra() throws Exception {
        Map<String, Object> extra = new HashMap<>();
        statsEventPublisher.publish("TEST_EVENT", "app1", 1L, 2L, extra);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate, times(1)).convertAndSend(
                anyString(), anyString(), messageCaptor.capture()
        );

        JsonNode jsonNode = objectMapper.readTree(messageCaptor.getValue());
        assertFalse(jsonNode.has("extra"));
    }

    @Test
    @DisplayName("RabbitTemplate异常时不抛出")
    void publish_whenRabbitTemplateThrows_shouldNotPropagateException() {
        doThrow(new RuntimeException("MQ connection failed"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), anyString());

        assertDoesNotThrow(() ->
                statsEventPublisher.publish("TEST_EVENT", "app1", 1L, 2L)
        );

        verify(rabbitTemplate, times(1)).convertAndSend(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("调用重载方法publish(eventType, appId, userId, targetId)应委托给带extra的方法")
    void publish_overloadWithoutExtra_shouldDelegateToFullMethod() throws Exception {
        StatsEventPublisher spyPublisher = spy(statsEventPublisher);
        doNothing().when(spyPublisher).publish(anyString(), anyString(), any(), any(), any());

        spyPublisher.publish("TEST_EVENT", "app1", 1L, 2L);

        verify(spyPublisher, times(1)).publish("TEST_EVENT", "app1", 1L, 2L, null);
    }
}
