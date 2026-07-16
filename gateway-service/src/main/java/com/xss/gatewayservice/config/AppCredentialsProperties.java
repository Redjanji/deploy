package com.xss.gatewayservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

@Component
@RefreshScope
@ConfigurationProperties(prefix = "apps")
public class AppCredentialsProperties {
    private Map<String, String> credentials = new HashMap<>();

    public Map<String, String> getCredentials() { return credentials; }
    public void setCredentials(Map<String, String> credentials) { this.credentials = credentials; }
}
