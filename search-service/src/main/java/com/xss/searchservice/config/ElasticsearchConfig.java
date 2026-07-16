package com.xss.searchservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchConfig {

    @Value("${elasticsearch.index-name}")
    private String indexName;

    public String getIndexName() {
        return indexName;
    }
}
