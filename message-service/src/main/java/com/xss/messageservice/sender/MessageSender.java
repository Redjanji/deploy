package com.xss.messageservice.sender;

import com.xss.messageservice.enums.MessageChannel;

public interface MessageSender {
    boolean send(String receiver, String subject, String content);
    MessageChannel getChannel();
}
