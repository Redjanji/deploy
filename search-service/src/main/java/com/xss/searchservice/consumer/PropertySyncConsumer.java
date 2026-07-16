package com.xss.searchservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xss.searchservice.dto.PropertySyncMessage;
import com.xss.searchservice.service.PropertySearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PropertySyncConsumer {

    private final PropertySearchService propertySearchService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PropertySyncConsumer(PropertySearchService propertySearchService) {
        this.propertySearchService = propertySearchService;
    }

    @RabbitListener(queues = "property.sync.queue")
    public void handlePropertySync(String messageJson) {
        try {
            PropertySyncMessage message = objectMapper.readValue(messageJson, PropertySyncMessage.class);
            log.info("Received property sync message: type={}, propertyId={}, appId={}",
                    message.getOperation(), message.getPropertyId(), message.getAppId());

            String eventType = message.getOperation();
            if ("CREATE".equals(eventType) || "UPDATE".equals(eventType)) {
                propertySearchService.syncProperty(message.getPropertyId(), message.getAppId());
            } else if ("DELETE".equals(eventType)) {
                propertySearchService.deleteProperty(message.getPropertyId(), message.getAppId());
            } else {
                log.warn("Unknown event type: {}", eventType);
            }
        } catch (Exception e) {
            log.error("Failed to parse/handle sync message: {}", e.getMessage(), e);
        }
    }
}
