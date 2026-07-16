package com.xss.messageservice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xss.messageservice.common.Result;
import com.xss.messageservice.dto.NotificationEvent;
import com.xss.messageservice.entity.MessageRecord;
import com.xss.messageservice.enums.MessageChannel;
import com.xss.messageservice.enums.MessageStatus;
import com.xss.messageservice.sender.EmailSender;
import com.xss.messageservice.service.MessageRecordService;
import com.xss.messageservice.service.TemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageRecordService messageRecordService;
    private final TemplateService templateService;
    private final EmailSender emailSender;

    @GetMapping("/records")
    public Result<Page<MessageRecord>> listRecords(
            @RequestHeader(value = "X-App-Id", required = false) String appId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(messageRecordService.listByApp(appId, status, page, size));
    }

    @GetMapping("/records/{id}")
    public Result<MessageRecord> getRecord(@PathVariable Long id) {
        return Result.success(messageRecordService.getById(id));
    }

    @PostMapping("/retry/{id}")
    public Result<Boolean> retry(@PathVariable Long id) {
        return Result.success(messageRecordService.retry(id));
    }

    @PostMapping("/send")
    public Result<Boolean> send(@RequestBody NotificationEvent event) {
        log.info("Direct send request: receiver={}, templateCode={}", event.getReceiver(), event.getTemplateCode());
        
        var template = templateService.getTemplate(event.getTemplateCode());
        if (template == null) {
            log.warn("Template not found: {}", event.getTemplateCode());
            return Result.fail(400, "模板不存在");
        }
        
        String content = templateService.render(template, event.getParams());
        String subject = event.getSubject() != null ? event.getSubject() : template.getSubject();
        
        boolean success = emailSender.send(event.getReceiver(), subject, content);
        
        NotificationEvent recordEvent = new NotificationEvent();
        recordEvent.setAppId(event.getAppId());
        recordEvent.setReceiver(event.getReceiver());
        recordEvent.setChannel(MessageChannel.EMAIL);
        messageRecordService.saveRecord(recordEvent, content,
                success ? MessageStatus.SUCCESS : MessageStatus.FAILED,
                success ? null : "邮件发送失败");
        
        return Result.success(success);
    }

    @PostMapping("/send-test")
    public Result<Boolean> sendTest(@RequestParam String email) {
        log.info("Send test email to: {}", email);
        
        Map<String, Object> params = new HashMap<>();
        params.put("appName", "message-service");
        params.put("time", java.time.LocalDateTime.now().toString());
        
        String subject = "message-service 测试邮件";
        String content = "<h1>message-service 测试邮件</h1>" +
                "<p>这是一封来自 message-service 的测试邮件。</p>" +
                "<p>应用名称: ${appName}</p>" +
                "<p>发送时间: ${time}</p>";
        
        content = content.replace("${appName}", params.get("appName").toString())
                .replace("${time}", params.get("time").toString());
        
        boolean success = emailSender.send(email, subject, content);
        
        NotificationEvent event = new NotificationEvent();
        event.setAppId("test");
        event.setReceiver(email);
        event.setChannel(MessageChannel.EMAIL);
        messageRecordService.saveRecord(event, content,
                success ? MessageStatus.SUCCESS : MessageStatus.FAILED,
                success ? null : "测试邮件发送失败");
        
        return Result.success(success);
    }
}
