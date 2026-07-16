package com.xss.messageservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xss.messageservice.common.BusinessException;
import com.xss.messageservice.dto.NotificationEvent;
import com.xss.messageservice.entity.MessageRecord;
import com.xss.messageservice.entity.MessageTemplate;
import com.xss.messageservice.enums.MessageChannel;
import com.xss.messageservice.enums.MessageStatus;
import com.xss.messageservice.mapper.MessageRecordMapper;
import com.xss.messageservice.sender.EmailSender;
import com.xss.messageservice.sender.MessageSender;
import com.xss.messageservice.service.TemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageRecordServiceImplTest {

    @Mock
    private MessageRecordMapper messageRecordMapper;

    @Mock
    private TemplateService templateService;

    @Mock
    private EmailSender emailSender;

    private MessageRecordServiceImpl messageRecordService;

    @BeforeEach
    void setUp() throws Exception {
        List<MessageSender> senders = Arrays.asList(emailSender);
        when(emailSender.getChannel()).thenReturn(MessageChannel.EMAIL);

        messageRecordService = new MessageRecordServiceImpl(messageRecordMapper, templateService, senders);

        Field maxRetryField = MessageRecordServiceImpl.class.getDeclaredField("maxRetry");
        maxRetryField.setAccessible(true);
        maxRetryField.set(messageRecordService, 3);
    }

    @Test
    void saveRecord_shouldInsertRecordSuccessfully() {
        NotificationEvent event = new NotificationEvent();
        event.setAppId("test_app");
        event.setChannel(MessageChannel.EMAIL);
        event.setReceiver("test@example.com");

        String content = "Test content";
        MessageStatus status = MessageStatus.SUCCESS;
        String errorMessage = null;

        when(messageRecordMapper.insert(any(MessageRecord.class))).thenReturn(1);

        messageRecordService.saveRecord(event, content, status, errorMessage);

        verify(messageRecordMapper, times(1)).insert(any(MessageRecord.class));
    }

    @Test
    void saveRecord_shouldSetCorrectFields() {
        NotificationEvent event = new NotificationEvent();
        event.setAppId("test_app");
        event.setChannel(MessageChannel.EMAIL);
        event.setReceiver("test@example.com");

        String content = "Test content";
        MessageStatus status = MessageStatus.FAILED;
        String errorMessage = "Send failed";

        messageRecordService.saveRecord(event, content, status, errorMessage);

        verify(messageRecordMapper).insert(org.mockito.ArgumentMatchers.<MessageRecord>argThat(record -> {
            assertEquals("test_app", record.getAppId());
            assertEquals(MessageChannel.EMAIL, record.getMessageType());
            assertEquals("test@example.com", record.getReceiver());
            assertEquals("Test content", record.getContent());
            assertEquals(MessageStatus.FAILED, record.getStatus());
            assertEquals(0, record.getRetryCount());
            assertEquals("Send failed", record.getErrorMessage());
            assertNotNull(record.getCreatedAt());
            assertNotNull(record.getUpdatedAt());
            return true;
        }));
    }

    @Test
    void getById_shouldReturnRecord_whenRecordExists() {
        Long id = 1L;
        MessageRecord record = new MessageRecord();
        record.setId(id);
        record.setAppId("test_app");
        record.setReceiver("test@example.com");

        when(messageRecordMapper.selectById(id)).thenReturn(record);

        MessageRecord result = messageRecordService.getById(id);

        assertNotNull(result);
        assertEquals(id, result.getId());
        assertEquals("test_app", result.getAppId());
    }

    @Test
    void getById_shouldReturnNull_whenRecordNotFound() {
        Long id = 999L;

        when(messageRecordMapper.selectById(id)).thenReturn(null);

        MessageRecord result = messageRecordService.getById(id);

        assertNull(result);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listByApp_shouldReturnPageSuccessfully() {
        String appId = "test_app";
        Integer status = 1;
        int page = 1;
        int size = 10;

        Page<MessageRecord> expectedPage = new Page<>(page, size);
        expectedPage.setTotal(100);

        when(messageRecordMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(expectedPage);

        Page<MessageRecord> result = messageRecordService.listByApp(appId, status, page, size);

        assertNotNull(result);
        assertEquals(100, result.getTotal());
        verify(messageRecordMapper, times(1)).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listByApp_shouldHandleNullAppIdAndStatus() {
        int page = 1;
        int size = 20;

        Page<MessageRecord> expectedPage = new Page<>(page, size);

        when(messageRecordMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(expectedPage);

        Page<MessageRecord> result = messageRecordService.listByApp(null, null, page, size);

        assertNotNull(result);
        verify(messageRecordMapper, times(1)).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    void retry_shouldThrowException_whenRecordNotFound() {
        Long id = 999L;

        when(messageRecordMapper.selectById(id)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            messageRecordService.retry(id);
        });

        assertEquals("消息记录不存在", exception.getMessage());
    }

    @Test
    void retry_shouldThrowException_whenRecordAlreadySuccess() {
        Long id = 1L;
        MessageRecord record = new MessageRecord();
        record.setId(id);
        record.setStatus(MessageStatus.SUCCESS);

        when(messageRecordMapper.selectById(id)).thenReturn(record);

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            messageRecordService.retry(id);
        });

        assertEquals("消息已发送成功，无需重试", exception.getMessage());
    }

    @Test
    void retry_shouldThrowException_whenMaxRetryReached() throws Exception {
        Long id = 1L;
        MessageRecord record = new MessageRecord();
        record.setId(id);
        record.setStatus(MessageStatus.FAILED);
        record.setRetryCount(3);

        when(messageRecordMapper.selectById(id)).thenReturn(record);

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            messageRecordService.retry(id);
        });

        assertEquals("已达最大重试次数", exception.getMessage());
    }

    @Test
    void retry_shouldSendEmailSuccessfully_whenAllConditionsMet() {
        Long id = 1L;
        MessageRecord record = new MessageRecord();
        record.setId(id);
        record.setStatus(MessageStatus.FAILED);
        record.setRetryCount(1);
        record.setMessageType(MessageChannel.EMAIL);
        record.setReceiver("test@example.com");
        record.setContent("Test content");

        when(messageRecordMapper.selectById(id)).thenReturn(record);
        when(emailSender.send(anyString(), any(), anyString())).thenReturn(true);
        when(messageRecordMapper.updateById(any(MessageRecord.class))).thenReturn(1);

        boolean result = messageRecordService.retry(id);

        assertTrue(result);
        verify(emailSender, times(1)).send(eq("test@example.com"), any(), eq("Test content"));
        verify(messageRecordMapper, times(1)).updateById(any(MessageRecord.class));
    }

    @Test
    void retry_shouldReturnFalse_whenSendFails() {
        Long id = 1L;
        MessageRecord record = new MessageRecord();
        record.setId(id);
        record.setStatus(MessageStatus.FAILED);
        record.setRetryCount(0);
        record.setMessageType(MessageChannel.EMAIL);
        record.setReceiver("test@example.com");
        record.setContent("Test content");

        when(messageRecordMapper.selectById(id)).thenReturn(record);
        when(emailSender.send(anyString(), any(), anyString())).thenReturn(false);
        when(messageRecordMapper.updateById(any(MessageRecord.class))).thenReturn(1);

        boolean result = messageRecordService.retry(id);

        assertFalse(result);
        verify(messageRecordMapper, times(1)).updateById(any(MessageRecord.class));
    }
}
