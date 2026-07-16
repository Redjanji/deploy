package com.xss.authservice.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class StatsEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${analytics.exchange:analytics.event.exchange}")
    private String exchange;

    @Value("${analytics.routing-key:analytics.event}")
    private String routingKey;

    public void publish(String eventType, String appId, Long userId, Long targetId) {
        publish(eventType, appId, userId, targetId, null);
    }

    public void publish(String eventType, String appId, Long userId, Long targetId,
                        Map<String, Object> extra) {
        try {
            ObjectNode event = objectMapper.createObjectNode();
            event.put("eventType", eventType);
            event.put("appId", appId != null ? appId : "default");
            if (userId != null) {
                event.put("userId", userId);
            }
            if (targetId != null) {
                event.put("targetId", targetId);
            }
            event.put("timestamp", System.currentTimeMillis());
            if (extra != null && !extra.isEmpty()) {
                event.set("extra", objectMapper.valueToTree(extra));
            }

            String json = objectMapper.writeValueAsString(event);
            rabbitTemplate.convertAndSend(exchange, routingKey, json);
            log.info("Published stats event: type={}, appId={}, userId={}, targetId={}",
                    eventType, appId, userId, targetId);
        } catch (Exception e) {
            log.error("Failed to publish stats event: type={}, targetId={}", eventType, targetId, e);
        }
    }
}
