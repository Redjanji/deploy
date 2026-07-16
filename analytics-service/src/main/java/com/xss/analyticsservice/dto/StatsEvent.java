package com.xss.analyticsservice.dto;

import lombok.Data;

import java.util.Map;

@Data
public class StatsEvent {
    private String eventType;
    private String appId;
    private Long userId;
    private Long targetId;
    private Long timestamp;
    private Map<String, Object> extra;
}
