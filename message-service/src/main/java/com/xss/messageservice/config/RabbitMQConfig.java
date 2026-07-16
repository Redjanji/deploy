package com.xss.messageservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String MESSAGE_SEND_EXCHANGE = "message.send.exchange";
    public static final String MESSAGE_SEND_QUEUE = "message.send.queue";
    public static final String MESSAGE_SEND_ROUTING_KEY = "message.send";

    @Value("${message.send.exchange:message.send.exchange}")
    private String messageSendExchange;

    @Value("${message.send.queue:message.send.queue}")
    private String messageSendQueue;

    @Value("${message.send.routing-key:message.send}")
    private String messageSendRoutingKey;

    @Bean
    public DirectExchange messageSendExchange() {
        return new DirectExchange(messageSendExchange, true, false);
    }

    @Bean
    public Queue messageSendQueue() {
        return new Queue(messageSendQueue, true);
    }

    @Bean
    public Binding messageSendBinding() {
        return BindingBuilder.bind(messageSendQueue())
                .to(messageSendExchange())
                .with(messageSendRoutingKey);
    }
}
