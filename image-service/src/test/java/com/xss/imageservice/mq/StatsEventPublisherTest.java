package com.xss.imageservice.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatsEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private StatsEventPublisher statsEventPublisher;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(statsEventPublisher, "exchange", "test.exchange");
        ReflectionTestUtils.setField(statsEventPublisher, "routingKey", "test.routing.key");
    }

    @Test
    void publish_withAllFields_sendsMessage() throws Exception {
        String eventType = "IMAGE_UPLOAD";
        String appId = "test-app";
        Long userId = 1L;
        Long targetId = 10L;
        Map<String, Object> extra = Map.of("fileSize", 1024);

        ObjectMapper realMapper = new ObjectMapper();
        when(objectMapper.createObjectNode()).thenReturn(realMapper.createObjectNode());
        when(objectMapper.valueToTree(extra)).thenReturn(realMapper.valueToTree(extra));
        when(objectMapper.writeValueAsString(any(JsonNode.class))).thenAnswer(invocation -> {
            JsonNode node = invocation.getArgument(0);
            return realMapper.writeValueAsString(node);
        });

        statsEventPublisher.publish(eventType, appId, userId, targetId, extra);

        ArgumentCaptor<String> exchangeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> routingKeyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);

        verify(rabbitTemplate, times(1)).convertAndSend(
                exchangeCaptor.capture(),
                routingKeyCaptor.capture(),
                messageCaptor.capture()
        );

        assertEquals("test.exchange", exchangeCaptor.getValue());
        assertEquals("test.routing.key", routingKeyCaptor.getValue());

        String jsonMessage = messageCaptor.getValue();
        JsonNode parsed = realMapper.readTree(jsonMessage);
        assertEquals(eventType, parsed.get("eventType").asText());
        assertEquals(appId, parsed.get("appId").asText());
        assertEquals(userId, parsed.get("userId").asLong());
        assertEquals(targetId, parsed.get("targetId").asLong());
        assertTrue(parsed.has("timestamp"));
        assertTrue(parsed.has("extra"));
        assertEquals(1024, parsed.get("extra").get("fileSize").asInt());
    }

    @Test
    void publish_withoutExtra_noExtraField() throws Exception {
        String eventType = "IMAGE_DELETE";
        String appId = "test-app";
        Long userId = 1L;
        Long targetId = 10L;

        ObjectMapper realMapper = new ObjectMapper();
        when(objectMapper.createObjectNode()).thenReturn(realMapper.createObjectNode());
        when(objectMapper.writeValueAsString(any(JsonNode.class))).thenAnswer(invocation -> {
            JsonNode node = invocation.getArgument(0);
            return realMapper.writeValueAsString(node);
        });

        statsEventPublisher.publish(eventType, appId, userId, targetId);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate, times(1)).convertAndSend(
                anyString(), anyString(), messageCaptor.capture()
        );

        JsonNode parsed = realMapper.readTree(messageCaptor.getValue());
        assertEquals(eventType, parsed.get("eventType").asText());
        assertFalse(parsed.has("extra"));
    }

    @Test
    void publish_withNullUserId_noUserIdField() throws Exception {
        String eventType = "IMAGE_VIEW";
        String appId = "test-app";
        Long targetId = 10L;

        ObjectMapper realMapper = new ObjectMapper();
        when(objectMapper.createObjectNode()).thenReturn(realMapper.createObjectNode());
        when(objectMapper.writeValueAsString(any(JsonNode.class))).thenAnswer(invocation -> {
            JsonNode node = invocation.getArgument(0);
            return realMapper.writeValueAsString(node);
        });

        statsEventPublisher.publish(eventType, appId, null, targetId);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate, times(1)).convertAndSend(
                anyString(), anyString(), messageCaptor.capture()
        );

        JsonNode parsed = realMapper.readTree(messageCaptor.getValue());
        assertEquals(eventType, parsed.get("eventType").asText());
        assertFalse(parsed.has("userId"));
    }

    @Test
    void publish_withNullAppId_usesDefault() throws Exception {
        String eventType = "IMAGE_VIEW";
        Long targetId = 10L;

        ObjectMapper realMapper = new ObjectMapper();
        when(objectMapper.createObjectNode()).thenReturn(realMapper.createObjectNode());
        when(objectMapper.writeValueAsString(any(JsonNode.class))).thenAnswer(invocation -> {
            JsonNode node = invocation.getArgument(0);
            return realMapper.writeValueAsString(node);
        });

        statsEventPublisher.publish(eventType, null, null, targetId);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate, times(1)).convertAndSend(
                anyString(), anyString(), messageCaptor.capture()
        );

        JsonNode parsed = realMapper.readTree(messageCaptor.getValue());
        assertEquals("default", parsed.get("appId").asText());
    }

    @Test
    void publish_withException_logsError() throws Exception {
        String eventType = "IMAGE_UPLOAD";
        String appId = "test-app";
        Long userId = 1L;
        Long targetId = 10L;

        when(objectMapper.createObjectNode()).thenThrow(new RuntimeException("Test exception"));

        assertDoesNotThrow(() -> statsEventPublisher.publish(eventType, appId, userId, targetId));

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), anyString());
    }
}
