package com.xss.searchservice.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String PROPERTY_SYNC_EXCHANGE = "property.sync.exchange";
    public static final String PROPERTY_SYNC_QUEUE = "property.sync.queue";
    public static final String PROPERTY_SYNC_ROUTING_KEY = "property.sync";

    @Bean
    public DirectExchange propertySyncExchange() {
        return new DirectExchange(PROPERTY_SYNC_EXCHANGE, true, false);
    }

    @Bean
    public Queue propertySyncQueue() {
        return QueueBuilder.durable(PROPERTY_SYNC_QUEUE)
                .build();
    }

    @Bean
    public Binding propertySyncBinding(DirectExchange propertySyncExchange, Queue propertySyncQueue) {
        return BindingBuilder.bind(propertySyncQueue)
                .to(propertySyncExchange)
                .with(PROPERTY_SYNC_ROUTING_KEY);
    }
}
