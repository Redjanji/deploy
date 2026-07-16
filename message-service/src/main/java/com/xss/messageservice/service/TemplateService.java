package com.xss.messageservice.service;

import com.xss.messageservice.entity.MessageTemplate;

import java.util.Map;

public interface TemplateService {
    MessageTemplate getTemplate(String templateCode);
    String render(MessageTemplate template, Map<String, Object> params);
}
