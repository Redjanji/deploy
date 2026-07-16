package com.xss.reviewservice.vo;

import lombok.Data;

@Data
public class ManualAuditRequest {
    private Long taskId;
    private Integer result;
    private String reason;
    private Long auditorId;
}
