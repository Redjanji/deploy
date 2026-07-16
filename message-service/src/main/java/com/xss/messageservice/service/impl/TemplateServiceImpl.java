package com.xss.messageservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xss.messageservice.entity.MessageTemplate;
import com.xss.messageservice.mapper.MessageTemplateMapper;
import com.xss.messageservice.service.TemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateServiceImpl implements TemplateService {

    private final MessageTemplateMapper templateMapper;

    @Override
    public MessageTemplate getTemplate(String templateCode) {
        return templateMapper.selectOne(new LambdaQueryWrapper<MessageTemplate>()
                .eq(MessageTemplate::getTemplateCode, templateCode)
                .eq(MessageTemplate::getIsEnabled, true));
    }

    @Override
    public String render(MessageTemplate template, Map<String, Object> params) {
        String content = template.getContent();
        if (params == null || params.isEmpty()) {
            return content;
        }
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            content = content.replace(placeholder, value);
        }
        return content;
    }
}
