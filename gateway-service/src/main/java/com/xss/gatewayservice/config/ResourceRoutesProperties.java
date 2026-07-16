package com.xss.gatewayservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "resource")
@Data
public class ResourceRoutesProperties {

    private List<Route> routes;

    @Data
    public static class Route {
        private String prefix;
        private String target;
    }
}
