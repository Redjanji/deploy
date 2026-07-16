package com.xss.bookingservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    public static final String EXCHANGE = "xss.booking.exchange";
    public static final String ROUTING_KEY = "booking.event";
    public static final String QUEUE = "xss.booking.event";

    @Bean
    public DirectExchange bookingExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue bookingQueue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    public Binding bookingBinding(DirectExchange exchange, Queue queue) {
        return BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY);
    }
}
