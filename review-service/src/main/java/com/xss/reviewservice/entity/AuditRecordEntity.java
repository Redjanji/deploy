package com.xss.reviewservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audit_records")
public class AuditRecordEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private String auditType;
    private Integer result;
    private String reason;
    private Long auditorId;
    private LocalDateTime auditAt;
}
