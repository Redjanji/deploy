package com.xss.analyticsservice.vo;

import lombok.Data;

import java.time.LocalDate;

@Data
public class PropertyViewStatsVO {
    private String appId;
    private Long propertyId;
    private LocalDate statsDate;
    private Long viewCount;
    private Long uniqueVisitors;
}
