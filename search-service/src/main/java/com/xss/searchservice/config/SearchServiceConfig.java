package com.xss.searchservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

@Slf4j
@Configuration
public class SearchServiceConfig {

    @PostConstruct
    public void init() {
        log.info("SearchService initialized - Elasticsearch connection will be attempted on first use");
    }
}
