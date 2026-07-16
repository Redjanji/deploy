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
import com.xss.messageservice.sender.MessageSender;
import com.xss.messageservice.service.MessageRecordService;
import com.xss.messageservice.service.TemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MessageRecordServiceImpl implements MessageRecordService {

    private final MessageRecordMapper messageRecordMapper;
    private final TemplateService templateService;
    private final Map<MessageChannel, MessageSender> senderMap;

    @Value("${message.max-retry:3}")
    private int maxRetry;

    public MessageRecordServiceImpl(MessageRecordMapper messageRecordMapper,
                                    TemplateService templateService,
                                    List<MessageSender> senders) {
        this.messageRecordMapper = messageRecordMapper;
        this.templateService = templateService;
        this.senderMap = senders.stream()
                .collect(Collectors.toMap(MessageSender::getChannel, Function.identity()));
    }

    @Override
    public void saveRecord(NotificationEvent event, String content, MessageStatus status, String errorMessage) {
        MessageRecord record = new MessageRecord();
        record.setAppId(event.getAppId());
        record.setMessageType(event.getChannel());
        record.setReceiver(event.getReceiver());
        record.setContent(content);
        record.setStatus(status);
        record.setRetryCount(0);
        record.setErrorMessage(errorMessage);
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        messageRecordMapper.insert(record);
    }

    @Override
    public Page<MessageRecord> listByApp(String appId, Integer status, int page, int size) {
        LambdaQueryWrapper<MessageRecord> wrapper = new LambdaQueryWrapper<>();
        if (appId != null) {
            wrapper.eq(MessageRecord::getAppId, appId);
        }
        if (status != null) {
            wrapper.eq(MessageRecord::getStatus, status);
        }
        wrapper.orderByDesc(MessageRecord::getCreatedAt);
        return messageRecordMapper.selectPage(Page.of(page, size), wrapper);
    }

    @Override
    public MessageRecord getById(Long id) {
        return messageRecordMapper.selectById(id);
    }

    @Override
    public boolean retry(Long id) {
        MessageRecord record = messageRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("消息记录不存在");
        }
        if (record.getStatus() == MessageStatus.SUCCESS) {
            throw new BusinessException("消息已发送成功，无需重试");
        }
        if (record.getRetryCount() >= maxRetry) {
            throw new BusinessException("已达最大重试次数");
        }

        MessageSender sender = senderMap.get(record.getMessageType());
        if (sender == null) {
            throw new BusinessException("不支持的发送渠道: " + record.getMessageType());
        }

        boolean success = sender.send(record.getReceiver(), extractSubject(record), record.getContent());
        record.setRetryCount(record.getRetryCount() + 1);
        record.setStatus(success ? MessageStatus.SUCCESS : MessageStatus.FAILED);
        record.setErrorMessage(success ? null : "重试失败");
        record.setUpdatedAt(LocalDateTime.now());
        messageRecordMapper.updateById(record);

        return success;
    }

    private String extractSubject(MessageRecord record) {
        if (record.getMessageType() == MessageChannel.SMS) {
            return null;
        }
        MessageTemplate template = templateService.getTemplate("SUBJECT_PLACEHOLDER");
        return null;
    }
}
