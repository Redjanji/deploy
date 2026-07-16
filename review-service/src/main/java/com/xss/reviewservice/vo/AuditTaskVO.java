package com.xss.reviewservice.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AuditTaskVO {
    private Long id;
    private Long propertyId;
    private String appId;
    private String taskType;
    private Integer status;
    private String resultDetail;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<AuditRecordVO> auditRecords;
}
