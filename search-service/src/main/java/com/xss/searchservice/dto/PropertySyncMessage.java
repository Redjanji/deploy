package com.xss.searchservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertySyncMessage {

    private String operation;
    private Long propertyId;
    private String appId;
    private Long timestamp;
}
