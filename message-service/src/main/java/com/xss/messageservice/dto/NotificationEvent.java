package com.xss.messageservice.dto;

import com.xss.messageservice.enums.MessageChannel;
import lombok.Data;

import java.util.Map;

@Data
public class NotificationEvent {
    private String appId;
    private String templateCode;
    private String receiver;
    private MessageChannel channel;
    private String subject;
    private Map<String, Object> params;
    private String traceId;
}
