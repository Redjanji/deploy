package com.xss.messageservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xss.messageservice.entity.MessageTemplate;
import com.xss.messageservice.enums.MessageChannel;
import com.xss.messageservice.mapper.MessageTemplateMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemplateServiceImplTest {

    @Mock
    private MessageTemplateMapper templateMapper;

    @InjectMocks
    private TemplateServiceImpl templateService;

    @Test
    void getTemplate_shouldReturnTemplate_whenTemplateExistsAndEnabled() {
        String templateCode = "test_template";
        MessageTemplate template = new MessageTemplate();
        template.setId(1L);
        template.setTemplateCode(templateCode);
        template.setType(MessageChannel.EMAIL);
        template.setSubject("Test Subject");
        template.setContent("Test Content");
        template.setIsEnabled(true);

        when(templateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(template);

        MessageTemplate result = templateService.getTemplate(templateCode);

        assertNotNull(result);
        assertEquals(templateCode, result.getTemplateCode());
        assertTrue(result.getIsEnabled());
    }

    @Test
    void getTemplate_shouldReturnNull_whenTemplateNotFound() {
        String templateCode = "non_existent_template";

        when(templateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        MessageTemplate result = templateService.getTemplate(templateCode);

        assertNull(result);
    }

    @Test
    void render_shouldReplacePlaceholders_whenParamsAreValid() {
        MessageTemplate template = new MessageTemplate();
        template.setContent("Hello ${name}, your order ${orderId} has been shipped.");

        Map<String, Object> params = new HashMap<>();
        params.put("name", "John");
        params.put("orderId", "ORD12345");

        String result = templateService.render(template, params);

        assertEquals("Hello John, your order ORD12345 has been shipped.", result);
    }

    @Test
    void render_shouldReturnOriginalContent_whenParamsIsNull() {
        MessageTemplate template = new MessageTemplate();
        template.setContent("Hello ${name}");

        String result = templateService.render(template, null);

        assertEquals("Hello ${name}", result);
    }

    @Test
    void render_shouldReturnOriginalContent_whenParamsIsEmpty() {
        MessageTemplate template = new MessageTemplate();
        template.setContent("Hello ${name}");

        Map<String, Object> params = new HashMap<>();

        String result = templateService.render(template, params);

        assertEquals("Hello ${name}", result);
    }

    @Test
    void render_shouldReplaceWithEmptyString_whenValueIsNull() {
        MessageTemplate template = new MessageTemplate();
        template.setContent("Hello ${name}, welcome!");

        Map<String, Object> params = new HashMap<>();
        params.put("name", null);

        String result = templateService.render(template, params);

        assertEquals("Hello , welcome!", result);
    }

    @Test
    void render_shouldReplaceMultiplePlaceholdersCorrectly() {
        MessageTemplate template = new MessageTemplate();
        template.setContent("User: ${username}, Email: ${email}, Age: ${age}");

        Map<String, Object> params = new HashMap<>();
        params.put("username", "testuser");
        params.put("email", "test@example.com");
        params.put("age", 25);

        String result = templateService.render(template, params);

        assertEquals("User: testuser, Email: test@example.com, Age: 25", result);
    }
}
