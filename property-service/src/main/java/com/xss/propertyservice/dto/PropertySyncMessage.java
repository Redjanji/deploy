package com.xss.propertyservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PropertySyncMessage {
    private String operation;
    private Long propertyId;
    private String appId;
    private Long timestamp;

    public static PropertySyncMessage create(Long propertyId, String appId) {
        return new PropertySyncMessage("CREATE", propertyId, appId, System.currentTimeMillis());
    }

    public static PropertySyncMessage update(Long propertyId, String appId) {
        return new PropertySyncMessage("UPDATE", propertyId, appId, System.currentTimeMillis());
    }

    public static PropertySyncMessage delete(Long propertyId, String appId) {
        return new PropertySyncMessage("DELETE", propertyId, appId, System.currentTimeMillis());
    }
}
