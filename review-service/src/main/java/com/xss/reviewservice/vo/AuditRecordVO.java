package com.xss.reviewservice.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AuditRecordVO {
    private Long id;
    private Long taskId;
    private String auditType;
    private Integer result;
    private String reason;
    private Long auditorId;
    private LocalDateTime auditAt;
}
