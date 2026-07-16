package com.xss.analyticsservice.vo;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ImageUploadSummaryVO {
    private String appId;
    private LocalDate statsDate;
    private Long uploadCount;
    private Long totalSize;
    private String totalSizeFormatted;
}
