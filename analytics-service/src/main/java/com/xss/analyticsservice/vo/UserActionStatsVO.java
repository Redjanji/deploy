package com.xss.analyticsservice.vo;

import lombok.Data;

import java.time.LocalDate;

@Data
public class UserActionStatsVO {
    private String appId;
    private String eventType;
    private LocalDate statsDate;
    private Long actionCount;
}
