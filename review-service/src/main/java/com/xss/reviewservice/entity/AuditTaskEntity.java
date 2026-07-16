package com.xss.reviewservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audit_tasks")
public class AuditTaskEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long propertyId;
    private String appId;
    private String taskType;
    private Integer status;
    private String resultDetail;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
