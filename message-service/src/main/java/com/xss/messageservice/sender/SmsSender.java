package com.xss.messageservice.sender;

import com.xss.messageservice.enums.MessageChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SmsSender implements MessageSender {

    @Value("${sms.provider:mock}")
    private String provider;

    @Value("${sms.mock-enabled:true}")
    private boolean mockEnabled;

    @Override
    public boolean send(String receiver, String subject, String content) {
        if (mockEnabled) {
            log.info("[Mock SMS] Sent to {}: {}", receiver, content);
            return true;
        }

        switch (provider.toLowerCase()) {
            case "aliyun":
                return sendViaAliyun(receiver, content);
            case "tencent":
                return sendViaTencent(receiver, content);
            default:
                log.warn("Unknown SMS provider: {}, using mock", provider);
                log.info("[Mock SMS] Sent to {}: {}", receiver, content);
                return true;
        }
    }

    private boolean sendViaAliyun(String receiver, String content) {
        log.warn("Aliyun SMS provider not implemented yet. Receiver: {}, Content: {}", receiver, content);
        return false;
    }

    private boolean sendViaTencent(String receiver, String content) {
        log.warn("Tencent SMS provider not implemented yet. Receiver: {}, Content: {}", receiver, content);
        return false;
    }

    @Override
    public MessageChannel getChannel() {
        return MessageChannel.SMS;
    }
}
