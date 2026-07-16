package com.xss.bookingservice.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.xss.bookingservice.mapper")
public class MyBatisPlusConfig {
}
