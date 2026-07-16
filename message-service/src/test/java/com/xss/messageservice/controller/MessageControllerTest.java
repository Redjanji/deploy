package com.xss.messageservice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xss.messageservice.dto.NotificationEvent;
import com.xss.messageservice.entity.MessageRecord;
import com.xss.messageservice.entity.MessageTemplate;
import com.xss.messageservice.enums.MessageChannel;
import com.xss.messageservice.enums.MessageStatus;
import com.xss.messageservice.sender.EmailSender;
import com.xss.messageservice.service.MessageRecordService;
import com.xss.messageservice.service.TemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class MessageControllerTest {

    @Mock
    private MessageRecordService messageRecordService;

    @Mock
    private TemplateService templateService;

    @Mock
    private EmailSender emailSender;

    @InjectMocks
    private MessageController messageController;

    private MockMvc mockMvc;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(messageController).build();
    }

    @Test
    void send_shouldReturnSuccess_whenEmailSentSuccessfully() throws Exception {
        NotificationEvent event = new NotificationEvent();
        event.setAppId("test_app");
        event.setTemplateCode("test_template");
        event.setReceiver("test@example.com");
        event.setSubject("Test Subject");
        Map<String, Object> params = new HashMap<>();
        params.put("name", "John");
        event.setParams(params);

        MessageTemplate template = new MessageTemplate();
        template.setTemplateCode("test_template");
        template.setSubject("Template Subject");
        template.setContent("Hello ${name}");
        template.setIsEnabled(true);

        when(templateService.getTemplate("test_template")).thenReturn(template);
        when(templateService.render(eq(template), any(Map.class))).thenReturn("Hello John");
        when(emailSender.send(eq("test@example.com"), eq("Test Subject"), eq("Hello John"))).thenReturn(true);

        mockMvc.perform(post("/api/messages/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(true));

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
    void send_shouldReturnFail_whenTemplateNotFound() throws Exception {
        NotificationEvent event = new NotificationEvent();
        event.setAppId("test_app");
        event.setTemplateCode("non_existent_template");
        event.setReceiver("test@example.com");

        when(templateService.getTemplate("non_existent_template")).thenReturn(null);

        mockMvc.perform(post("/api/messages/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("模板不存在"));

        verify(templateService, times(1)).getTemplate("non_existent_template");
        verify(emailSender, never()).send(anyString(), any(), anyString());
    }

    @Test
    void getRecord_shouldReturnRecord_whenRecordExists() throws Exception {
        Long id = 1L;
        MessageRecord record = new MessageRecord();
        record.setId(id);
        record.setAppId("test_app");
        record.setReceiver("test@example.com");
        record.setStatus(MessageStatus.SUCCESS);

        when(messageRecordService.getById(id)).thenReturn(record);

        mockMvc.perform(get("/api/messages/records/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.appId").value("test_app"))
                .andExpect(jsonPath("$.data.receiver").value("test@example.com"));

        verify(messageRecordService, times(1)).getById(id);
    }

    @Test
    void getRecord_shouldReturnNull_whenRecordNotFound() throws Exception {
        Long id = 999L;

        when(messageRecordService.getById(id)).thenReturn(null);

        mockMvc.perform(get("/api/messages/records/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isEmpty());

        verify(messageRecordService, times(1)).getById(id);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listRecords_shouldReturnPage_whenValidRequest() throws Exception {
        String appId = "test_app";
        int page = 1;
        int size = 20;

        Page<MessageRecord> expectedPage = new Page<>(page, size);
        expectedPage.setTotal(100);
        expectedPage.setPages(5);

        when(messageRecordService.listByApp(eq(appId), isNull(), eq(page), eq(size))).thenReturn(expectedPage);

        mockMvc.perform(get("/api/messages/records")
                        .header("X-App-Id", appId)
                        .param("page", String.valueOf(page))
                        .param("size", String.valueOf(size)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(100))
                .andExpect(jsonPath("$.data.pages").value(5))
                .andExpect(jsonPath("$.data.current").value(1))
                .andExpect(jsonPath("$.data.size").value(20));

        verify(messageRecordService, times(1)).listByApp(eq(appId), isNull(), eq(page), eq(size));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listRecords_shouldUseDefaultPagination_whenParamsNotProvided() throws Exception {
        Page<MessageRecord> expectedPage = new Page<>(1, 20);

        when(messageRecordService.listByApp(isNull(), isNull(), eq(1), eq(20))).thenReturn(expectedPage);

        mockMvc.perform(get("/api/messages/records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(messageRecordService, times(1)).listByApp(isNull(), isNull(), eq(1), eq(20));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listRecords_shouldFilterByStatus_whenStatusProvided() throws Exception {
        Integer status = 1;

        Page<MessageRecord> expectedPage = new Page<>(1, 20);

        when(messageRecordService.listByApp(isNull(), eq(status), eq(1), eq(20))).thenReturn(expectedPage);

        mockMvc.perform(get("/api/messages/records")
                        .param("status", String.valueOf(status)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(messageRecordService, times(1)).listByApp(isNull(), eq(status), eq(1), eq(20));
    }

    @Test
    void retry_shouldReturnSuccess_whenRetrySucceeds() throws Exception {
        Long id = 1L;

        when(messageRecordService.retry(id)).thenReturn(true);

        mockMvc.perform(post("/api/messages/retry/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(true));

        verify(messageRecordService, times(1)).retry(id);
    }

    @Test
    void sendTest_shouldSendTestEmailSuccessfully() throws Exception {
        String email = "test@example.com";

        when(emailSender.send(eq(email), anyString(), anyString())).thenReturn(true);

        mockMvc.perform(post("/api/messages/send-test")
                        .param("email", email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(true));

        verify(emailSender, times(1)).send(eq(email), anyString(), anyString());
        verify(messageRecordService, times(1)).saveRecord(
                any(NotificationEvent.class),
                anyString(),
                eq(MessageStatus.SUCCESS),
                isNull()
        );
    }
}
