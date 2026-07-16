package com.xss.propertyservice.config;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class RabbitMQConfig {

    @Value("${sync.property-exchange}")
    private String propertyExchange;

    @Value("${message.send.exchange}")
    private String messageSendExchange;

    @Bean
    public DirectExchange propertyExchange() {
        return new DirectExchange(propertyExchange, true, false);
    }

    @Bean
    public DirectExchange messageSendExchange() {
        return new DirectExchange(messageSendExchange, true, false);
    }
}
