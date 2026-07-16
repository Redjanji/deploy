package com.xss.messageservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xss.messageservice.dto.NotificationEvent;
import com.xss.messageservice.entity.MessageTemplate;
import com.xss.messageservice.enums.MessageChannel;
import com.xss.messageservice.enums.MessageStatus;
import com.xss.messageservice.sender.EmailSender;
import com.xss.messageservice.sender.MessageSender;
import com.xss.messageservice.service.MessageRecordService;
import com.xss.messageservice.service.TemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationConsumerTest {

    @Mock
    private TemplateService templateService;

    @Mock
    private MessageRecordService messageRecordService;

    @Mock
    private EmailSender emailSender;

    private NotificationConsumer notificationConsumer;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        List<MessageSender> senders = Arrays.asList(emailSender);
        when(emailSender.getChannel()).thenReturn(MessageChannel.EMAIL);

        notificationConsumer = new NotificationConsumer(templateService, messageRecordService, senders);
    }

    @Test
    void handle_shouldProcessMessageSuccessfully_whenValidJson() throws Exception {
        NotificationEvent event = new NotificationEvent();
        event.setAppId("test_app");
        event.setTemplateCode("test_template");
        event.setReceiver("test@example.com");
        event.setChannel(MessageChannel.EMAIL);
        event.setSubject("Test Subject");
        Map<String, Object> params = new HashMap<>();
        params.put("name", "John");
        event.setParams(params);

        String messageJson = objectMapper.writeValueAsString(event);

        MessageTemplate template = new MessageTemplate();
        template.setTemplateCode("test_template");
        template.setType(MessageChannel.EMAIL);
        template.setSubject("Template Subject");
        template.setContent("Hello ${name}");
        template.setIsEnabled(true);

        when(templateService.getTemplate("test_template")).thenReturn(template);
        when(templateService.render(eq(template), any(Map.class))).thenReturn("Hello John");
        when(emailSender.send(eq("test@example.com"), eq("Test Subject"), eq("Hello John"))).thenReturn(true);

        notificationConsumer.handle(messageJson);

        verify(templateService, times(1)).getTemplate("test_template");
        verify(emailSender, times(1)).send(eq("test@example.com"), eq("Test Subject"), eq("Hello John"));
        verify(messageRecordService, times(1)).saveRecord(
                any(NotificationEvent.class),
                eq("Hello John"),
                eq(MessageStatus.SUCCESS),
                isNull()
        );
    }

    @Test
    void handle_shouldNotThrowException_whenJsonParseFails() {
        String invalidJson = "invalid json content";

        notificationConsumer.handle(invalidJson);

        verify(templateService, never()).getTemplate(anyString());
        verify(messageRecordService, never()).saveRecord(any(), any(), any(), any());
    }

    @Test
    void handle_shouldNotThrowException_whenMessageJsonIsNull() {
        notificationConsumer.handle(null);

        verify(templateService, never()).getTemplate(anyString());
        verify(messageRecordService, never()).saveRecord(any(), any(), any(), any());
    }

    @Test
    void handle_shouldReturn_whenTemplateNotFound() throws Exception {
        NotificationEvent event = new NotificationEvent();
        event.setAppId("test_app");
        event.setTemplateCode("non_existent_template");
        event.setReceiver("test@example.com");
        event.setChannel(MessageChannel.EMAIL);

        String messageJson = objectMapper.writeValueAsString(event);

        when(templateService.getTemplate("non_existent_template")).thenReturn(null);

        notificationConsumer.handle(messageJson);

        verify(templateService, times(1)).getTemplate("non_existent_template");
        verify(emailSender, never()).send(anyString(), any(), anyString());
        verify(messageRecordService, never()).saveRecord(any(), any(), any(), any());
    }

    @Test
    void handle_shouldUseTemplateSubject_whenEventSubjectIsNull() throws Exception {
        NotificationEvent event = new NotificationEvent();
        event.setAppId("test_app");
        event.setTemplateCode("test_template");
        event.setReceiver("test@example.com");
        event.setChannel(MessageChannel.EMAIL);
        event.setSubject(null);

        String messageJson = objectMapper.writeValueAsString(event);

        MessageTemplate template = new MessageTemplate();
        template.setTemplateCode("test_template");
        template.setType(MessageChannel.EMAIL);
        template.setSubject("Template Subject");
        template.setContent("Test Content");
        template.setIsEnabled(true);

        when(templateService.getTemplate("test_template")).thenReturn(template);
        when(templateService.render(eq(template), any())).thenReturn("Test Content");
        when(emailSender.send(anyString(), anyString(), anyString())).thenReturn(true);

        notificationConsumer.handle(messageJson);

        verify(emailSender, times(1)).send(eq("test@example.com"), eq("Template Subject"), eq("Test Content"));
    }

    @Test
    void handle_shouldUseTemplateType_whenEventChannelIsNull() throws Exception {
        NotificationEvent event = new NotificationEvent();
        event.setAppId("test_app");
        event.setTemplateCode("test_template");
        event.setReceiver("test@example.com");
        event.setChannel(null);

        String messageJson = objectMapper.writeValueAsString(event);

        MessageTemplate template = new MessageTemplate();
        template.setTemplateCode("test_template");
        template.setType(MessageChannel.EMAIL);
        template.setSubject("Test Subject");
        template.setContent("Test Content");
        template.setIsEnabled(true);

        when(templateService.getTemplate("test_template")).thenReturn(template);
        when(templateService.render(eq(template), any())).thenReturn("Test Content");
        when(emailSender.send(anyString(), anyString(), anyString())).thenReturn(true);

        notificationConsumer.handle(messageJson);

        verify(emailSender, times(1)).send(anyString(), anyString(), anyString());
        verify(messageRecordService, times(1)).saveRecord(any(), any(), eq(MessageStatus.SUCCESS), isNull());
    }

    @Test
    void handle_shouldSaveFailedRecord_whenSendFails() throws Exception {
        NotificationEvent event = new NotificationEvent();
        event.setAppId("test_app");
        event.setTemplateCode("test_template");
        event.setReceiver("test@example.com");
        event.setChannel(MessageChannel.EMAIL);

        String messageJson = objectMapper.writeValueAsString(event);

        MessageTemplate template = new MessageTemplate();
        template.setTemplateCode("test_template");
        template.setType(MessageChannel.EMAIL);
        template.setSubject("Test Subject");
        template.setContent("Test Content");
        template.setIsEnabled(true);

        when(templateService.getTemplate("test_template")).thenReturn(template);
        when(templateService.render(eq(template), any())).thenReturn("Test Content");
        when(emailSender.send(anyString(), anyString(), anyString())).thenReturn(false);

        notificationConsumer.handle(messageJson);

        verify(messageRecordService, times(1)).saveRecord(
                any(NotificationEvent.class),
                eq("Test Content"),
                eq(MessageStatus.FAILED),
                eq("发送失败")
        );
    }

    @Test
    void handle_shouldSaveErrorRecord_whenExceptionOccurs() throws Exception {
        NotificationEvent event = new NotificationEvent();
        event.setAppId("test_app");
        event.setTemplateCode("test_template");
        event.setReceiver("test@example.com");
        event.setChannel(MessageChannel.EMAIL);

        String messageJson = objectMapper.writeValueAsString(event);

        when(templateService.getTemplate("test_template")).thenThrow(new RuntimeException("Unexpected error"));

        notificationConsumer.handle(messageJson);

        verify(messageRecordService, times(1)).saveRecord(
                any(NotificationEvent.class),
                eq(""),
                eq(MessageStatus.FAILED),
                eq("Unexpected error")
        );
    }
}
