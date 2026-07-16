package com.xss.messageservice.sender;

import com.xss.messageservice.enums.MessageChannel;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailSenderTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailSender emailSender;

    @BeforeEach
    void setUp() throws Exception {
        Field fromField = EmailSender.class.getDeclaredField("from");
        fromField.setAccessible(true);
        fromField.set(emailSender, "no-reply@example.com");
    }

    @Test
    void send_shouldSendEmailSuccessfully_whenAllParamsAreValid() throws Exception {
        String receiver = "test@example.com";
        String subject = "Test Subject";
        String content = "<h1>Test Content</h1>";

        MimeMessage mimeMessage = new MimeMessage((jakarta.mail.Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        boolean result = emailSender.send(receiver, subject, content);

        assertTrue(result);
        verify(mailSender, times(1)).send(mimeMessage);
    }

    @Test
    void send_shouldUseDefaultSubject_whenSubjectIsNull() throws Exception {
        String receiver = "test@example.com";
        String content = "Test Content";

        MimeMessage mimeMessage = new MimeMessage((jakarta.mail.Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        boolean result = emailSender.send(receiver, null, content);

        assertTrue(result);
        verify(mailSender, times(1)).send(mimeMessage);
    }

    @Test
    void send_shouldReturnFalse_whenSendFails() throws Exception {
        String receiver = "test@example.com";
        String subject = "Test Subject";
        String content = "Test Content";

        MimeMessage mimeMessage = new MimeMessage((jakarta.mail.Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new RuntimeException("Mail server unavailable")).when(mailSender).send(any(MimeMessage.class));

        boolean result = emailSender.send(receiver, subject, content);

        assertFalse(result);
    }

    @Test
    void getChannel_shouldReturnEmail() {
        MessageChannel channel = emailSender.getChannel();

        assertEquals(MessageChannel.EMAIL, channel);
    }
}
