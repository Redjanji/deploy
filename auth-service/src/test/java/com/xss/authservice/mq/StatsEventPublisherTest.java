package com.xss.authservice.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatsEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private ObjectMapper objectMapper;
    private StatsEventPublisher statsEventPublisher;

    private static final String EXCHANGE = "test.exchange";
    private static final String ROUTING_KEY = "test.routing.key";

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        statsEventPublisher = new StatsEventPublisher(rabbitTemplate, objectMapper);
        setField(statsEventPublisher, "exchange", EXCHANGE);
        setField(statsEventPublisher, "routingKey", ROUTING_KEY);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void publish_success() throws Exception {
        statsEventPublisher.publish("USER_LOGIN", "default", 1L, 100L);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq(ROUTING_KEY), jsonCaptor.capture());

        String json = jsonCaptor.getValue();
        JsonNode node = objectMapper.readTree(json);
        assertEquals("USER_LOGIN", node.get("eventType").asText());
        assertEquals("default", node.get("appId").asText());
        assertEquals(1L, node.get("userId").asLong());
        assertEquals(100L, node.get("targetId").asLong());
        assertNotNull(node.get("timestamp"));
    }

    @Test
    void publish_withExtraParams() throws Exception {
        Map<String, Object> extra = new HashMap<>();
        extra.put("ip", "127.0.0.1");
        extra.put("device", "mobile");

        statsEventPublisher.publish("USER_LOGIN", "app1", 2L, 200L, extra);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq(ROUTING_KEY), jsonCaptor.capture());

        String json = jsonCaptor.getValue();
        JsonNode node = objectMapper.readTree(json);
        assertEquals("USER_LOGIN", node.get("eventType").asText());
        assertEquals("app1", node.get("appId").asText());
        assertEquals(2L, node.get("userId").asLong());
        assertEquals(200L, node.get("targetId").asLong());
        assertNotNull(node.get("extra"));
        assertEquals("127.0.0.1", node.get("extra").get("ip").asText());
        assertEquals("mobile", node.get("extra").get("device").asText());
    }

    @Test
    void publish_rabbitTemplateException_doesNotThrow() {
        doThrow(new RuntimeException("RabbitMQ connection failed"))
                .when(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq(ROUTING_KEY), any(String.class));

        assertDoesNotThrow(() -> statsEventPublisher.publish("USER_LOGIN", "default", 1L, 1L));
    }
}
