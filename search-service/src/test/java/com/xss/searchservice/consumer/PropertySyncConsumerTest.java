package com.xss.searchservice.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xss.searchservice.dto.PropertySyncMessage;
import com.xss.searchservice.service.PropertySearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PropertySyncConsumerTest {

    @Mock
    private PropertySearchService propertySearchService;

    @InjectMocks
    private PropertySyncConsumer propertySyncConsumer;

    @Test
    void handlePropertySync_CREATE类型消息同步房源() {
        PropertySyncMessage message = PropertySyncMessage.builder()
                .operation("CREATE")
                .propertyId(1L)
                .appId("test-app")
                .timestamp(System.currentTimeMillis())
                .build();

        String messageJson = toJson(message);

        propertySyncConsumer.handlePropertySync(messageJson);

        verify(propertySearchService).syncProperty(1L, "test-app");
        verify(propertySearchService, never()).deleteProperty(anyLong(), anyString());
    }

    @Test
    void handlePropertySync_UPDATE类型消息同步房源() {
        PropertySyncMessage message = PropertySyncMessage.builder()
                .operation("UPDATE")
                .propertyId(2L)
                .appId("test-app")
                .timestamp(System.currentTimeMillis())
                .build();

        String messageJson = toJson(message);

        propertySyncConsumer.handlePropertySync(messageJson);

        verify(propertySearchService).syncProperty(2L, "test-app");
        verify(propertySearchService, never()).deleteProperty(anyLong(), anyString());
    }

    @Test
    void handlePropertySync_DELETE类型消息删除房源() {
        PropertySyncMessage message = PropertySyncMessage.builder()
                .operation("DELETE")
                .propertyId(3L)
                .appId("test-app")
                .timestamp(System.currentTimeMillis())
                .build();

        String messageJson = toJson(message);

        propertySyncConsumer.handlePropertySync(messageJson);

        verify(propertySearchService).deleteProperty(3L, "test-app");
        verify(propertySearchService, never()).syncProperty(anyLong(), anyString());
    }

    @Test
    void handlePropertySync_JSON解析失败不抛出异常() {
        String invalidJson = "this is not valid json";

        assertDoesNotThrow(() -> propertySyncConsumer.handlePropertySync(invalidJson));

        verify(propertySearchService, never()).syncProperty(anyLong(), anyString());
        verify(propertySearchService, never()).deleteProperty(anyLong(), anyString());
    }

    @Test
    void handlePropertySync_未知类型消息记录警告() {
        PropertySyncMessage message = PropertySyncMessage.builder()
                .operation("UNKNOWN_OP")
                .propertyId(4L)
                .appId("test-app")
                .timestamp(System.currentTimeMillis())
                .build();

        String messageJson = toJson(message);

        assertDoesNotThrow(() -> propertySyncConsumer.handlePropertySync(messageJson));

        verify(propertySearchService, never()).syncProperty(anyLong(), anyString());
        verify(propertySearchService, never()).deleteProperty(anyLong(), anyString());
    }

    @Test
    void handlePropertySync_空消息不抛出异常() {
        assertDoesNotThrow(() -> propertySyncConsumer.handlePropertySync(""));

        verify(propertySearchService, never()).syncProperty(anyLong(), anyString());
        verify(propertySearchService, never()).deleteProperty(anyLong(), anyString());
    }

    @Test
    void handlePropertySync_null消息不抛出异常() {
        assertDoesNotThrow(() -> propertySyncConsumer.handlePropertySync(null));

        verify(propertySearchService, never()).syncProperty(anyLong(), anyString());
        verify(propertySearchService, never()).deleteProperty(anyLong(), anyString());
    }

    @Test
    void handlePropertySync_syncProperty抛出异常不影响消费() {
        PropertySyncMessage message = PropertySyncMessage.builder()
                .operation("CREATE")
                .propertyId(1L)
                .appId("test-app")
                .timestamp(System.currentTimeMillis())
                .build();

        String messageJson = toJson(message);

        doThrow(new RuntimeException("Sync failed")).when(propertySearchService)
                .syncProperty(anyLong(), anyString());

        assertDoesNotThrow(() -> propertySyncConsumer.handlePropertySync(messageJson));
    }

    @Test
    void handlePropertySync_deleteProperty抛出异常不影响消费() {
        PropertySyncMessage message = PropertySyncMessage.builder()
                .operation("DELETE")
                .propertyId(1L)
                .appId("test-app")
                .timestamp(System.currentTimeMillis())
                .build();

        String messageJson = toJson(message);

        doThrow(new RuntimeException("Delete failed")).when(propertySearchService)
                .deleteProperty(anyLong(), anyString());

        assertDoesNotThrow(() -> propertySyncConsumer.handlePropertySync(messageJson));
    }

    private String toJson(PropertySyncMessage message) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
