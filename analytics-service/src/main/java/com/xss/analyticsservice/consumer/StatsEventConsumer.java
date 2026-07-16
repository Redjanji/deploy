package com.xss.analyticsservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xss.analyticsservice.dto.StatsEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class StatsEventConsumer {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "${analytics.queue:analytics.event.queue}")
    public void handleEvent(String message) {
        try {
            StatsEvent event = objectMapper.readValue(message, StatsEvent.class);
            String appId = event.getAppId() != null ? event.getAppId() : "default";
            String date = LocalDate.now().toString();

            switch (event.getEventType()) {
                case "PROPERTY_VIEW" -> recordPropertyView(appId, event.getTargetId(), date, event.getUserId());
                case "PROPERTY_CREATE" -> incrementUserAction(appId, "PROPERTY_CREATE", date);
                case "IMAGE_UPLOAD" -> recordImageUpload(appId, date, event.getExtra());
                case "IMAGE_DELETE" -> incrementUserAction(appId, "IMAGE_DELETE", date);
                case "USER_REGISTER" -> incrementUserAction(appId, "USER_REGISTER", date);
                case "USER_LOGIN" -> incrementUserAction(appId, "USER_LOGIN", date);
                case "FAVORITE_ADD" -> incrementUserAction(appId, "FAVORITE_ADD", date);
                case "FAVORITE_REMOVE" -> incrementUserAction(appId, "FAVORITE_REMOVE", date);
                default -> log.warn("Unknown event type: {}", event.getEventType());
            }
            log.debug("Stats event processed: type={}, appId={}, targetId={}", event.getEventType(), appId, event.getTargetId());
        } catch (Exception e) {
            log.error("Failed to process stats event: {}", message, e);
        }
    }

    private void recordPropertyView(String appId, Long propertyId, String date, Long userId) {
        if (propertyId == null) return;
        String viewKey = String.format("stats:property:view:%s:%s", appId, date);
        redisTemplate.opsForHash().increment(viewKey, propertyId + ":count", 1);
        if (userId != null) {
            String uvKey = String.format("stats:property:uv:%s:%s:%s", appId, propertyId, date);
            redisTemplate.opsForHyperLogLog().add(uvKey, userId.toString());
        }
    }

    private void recordImageUpload(String appId, String date, java.util.Map<String, Object> extra) {
        String countKey = String.format("stats:image:upload:%s:%s:count", appId, date);
        redisTemplate.opsForValue().increment(countKey);
        if (extra != null && extra.containsKey("fileSize")) {
            String sizeKey = String.format("stats:image:upload:%s:%s:size", appId, date);
            Object sizeObj = extra.get("fileSize");
            long fileSize = 0;
            if (sizeObj instanceof Number) {
                fileSize = ((Number) sizeObj).longValue();
            } else if (sizeObj instanceof String) {
                fileSize = Long.parseLong((String) sizeObj);
            }
            redisTemplate.opsForValue().increment(sizeKey, fileSize);
        }
    }

    private void incrementUserAction(String appId, String eventType, String date) {
        String key = String.format("stats:user:%s:%s:%s", eventType, appId, date);
        redisTemplate.opsForValue().increment(key);
    }
}
