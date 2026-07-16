package com.xss.messageservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xss.messageservice.enums.MessageChannel;
import com.xss.messageservice.enums.MessageStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("message_records")
public class MessageRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("app_id")
    private String appId;

    @TableField("message_type")
    private MessageChannel messageType;

    @TableField("receiver")
    private String receiver;

    @TableField("content")
    private String content;

    @TableField("status")
    private MessageStatus status;

    @TableField("retry_count")
    private Integer retryCount;

    @TableField("error_message")
    private String errorMessage;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
