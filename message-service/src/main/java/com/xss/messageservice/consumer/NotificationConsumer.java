package com.xss.messageservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xss.messageservice.dto.NotificationEvent;
import com.xss.messageservice.entity.MessageTemplate;
import com.xss.messageservice.enums.MessageChannel;
import com.xss.messageservice.enums.MessageStatus;
import com.xss.messageservice.sender.MessageSender;
import com.xss.messageservice.service.MessageRecordService;
import com.xss.messageservice.service.TemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class NotificationConsumer {

    private final TemplateService templateService;
    private final MessageRecordService messageRecordService;
    private final Map<MessageChannel, MessageSender> senderMap;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NotificationConsumer(TemplateService templateService,
                                MessageRecordService messageRecordService,
                                List<MessageSender> senders) {
        this.templateService = templateService;
        this.messageRecordService = messageRecordService;
        this.senderMap = senders.stream()
                .collect(Collectors.toMap(MessageSender::getChannel, Function.identity()));
    }

    @RabbitListener(queues = "message.send.queue")
    public void handle(String messageJson) {
        NotificationEvent event;
        try {
            event = objectMapper.readValue(messageJson, NotificationEvent.class);
        } catch (Exception e) {
            log.error("Failed to parse message: {}", e.getMessage(), e);
            return;
        }

        log.info("Received notification event: appId={}, templateCode={}, channel={}, receiver={}",
                event.getAppId(), event.getTemplateCode(), event.getChannel(), event.getReceiver());

        try {
            MessageTemplate template = templateService.getTemplate(event.getTemplateCode());
            if (template == null) {
                log.error("Template not found: {}", event.getTemplateCode());
                return;
            }

            String content = templateService.render(template, event.getParams());
            String subject = event.getSubject() != null ? event.getSubject() : template.getSubject();

            MessageChannel channel = event.getChannel() != null ? event.getChannel() : template.getType();
            MessageSender sender = senderMap.get(channel);
            if (sender == null) {
                log.error("No sender for channel: {}", channel);
                return;
            }

            boolean success = sender.send(event.getReceiver(), subject, content);
            messageRecordService.saveRecord(event, content,
                    success ? MessageStatus.SUCCESS : MessageStatus.FAILED,
                    success ? null : "发送失败");

            if (!success) {
                log.warn("Message send failed, receiver={}, template={}", event.getReceiver(), event.getTemplateCode());
            }
        } catch (Exception e) {
            log.error("Failed to handle notification: {}", e.getMessage(), e);
            try {
                messageRecordService.saveRecord(event, "", MessageStatus.FAILED, e.getMessage());
            } catch (Exception ex) {
                log.error("Failed to save error record: {}", ex.getMessage());
            }
        }
    }
}
