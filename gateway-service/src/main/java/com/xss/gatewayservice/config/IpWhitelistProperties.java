package com.xss.gatewayservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "ip-whitelist")
@RefreshScope
public class IpWhitelistProperties {
    private boolean enabled = true;
    private List<String> allowedIps = new ArrayList<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public List<String> getAllowedIps() {
        return allowedIps != null ? allowedIps : new ArrayList<>();
    }

    public void setAllowedIps(List<String> allowedIps) {
        this.allowedIps = allowedIps != null ? allowedIps : new ArrayList<>();
    }
}
