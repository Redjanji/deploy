package com.xss.analyticsservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${analytics.exchange:analytics.event.exchange}")
    private String exchange;

    @Value("${analytics.queue:analytics.event.queue}")
    private String queue;

    @Value("${analytics.routing-key:analytics.event}")
    private String routingKey;

    @Bean
    public DirectExchange analyticsEventExchange() {
        return new DirectExchange(exchange, true, false);
    }

    @Bean
    public Queue analyticsEventQueue() {
        return new Queue(queue, true);
    }

    @Bean
    public Binding analyticsEventBinding() {
        return BindingBuilder.bind(analyticsEventQueue())
                .to(analyticsEventExchange())
                .with(routingKey);
    }
}
