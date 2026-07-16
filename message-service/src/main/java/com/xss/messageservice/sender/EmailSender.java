package com.xss.messageservice.sender;

import com.xss.messageservice.enums.MessageChannel;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailSender implements MessageSender {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String from;

    @Override
    public boolean send(String receiver, String subject, String content) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(receiver);
            helper.setSubject(subject != null ? subject : "系统通知");
            helper.setText(content, true);
            mailSender.send(message);
            log.info("Email sent successfully to {}", receiver);
            return true;
        } catch (Exception e) {
            log.error("Email send failed to {}", receiver, e);
            return false;
        }
    }

    @Override
    public MessageChannel getChannel() {
        return MessageChannel.EMAIL;
    }
}
