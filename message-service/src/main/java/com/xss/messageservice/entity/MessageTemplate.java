package com.xss.messageservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xss.messageservice.enums.MessageChannel;
import lombok.Data;

@Data
@TableName("message_templates")
public class MessageTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("template_code")
    private String templateCode;

    @TableField("type")
    private MessageChannel type;

    @TableField("subject")
    private String subject;

    @TableField("content")
    private String content;

    @TableField("is_enabled")
    private Boolean isEnabled;
}
