package com.xss.analyticsservice.vo;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class DashboardSummaryVO {
    private LocalDate today;
    private Long todayPropertyViews;
    private Long todayImageUploads;
    private Long todayUserRegisters;
    private Long todayUserLogins;
    private Long todayPropertyCreates;
    private List<PropertyViewStatsVO> topProperties;
    private List<ImageUploadSummaryVO> appImageSummary;
}
