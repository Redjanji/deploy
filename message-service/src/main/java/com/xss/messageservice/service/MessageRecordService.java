package com.xss.messageservice.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xss.messageservice.dto.NotificationEvent;
import com.xss.messageservice.entity.MessageRecord;
import com.xss.messageservice.enums.MessageStatus;

public interface MessageRecordService {
    void saveRecord(NotificationEvent event, String content, MessageStatus status, String errorMessage);
    Page<MessageRecord> listByApp(String appId, Integer status, int page, int size);
    MessageRecord getById(Long id);
    boolean retry(Long id);
}
