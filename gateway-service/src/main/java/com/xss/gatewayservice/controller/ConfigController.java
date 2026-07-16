package com.xss.gatewayservice.controller;

import com.xss.gatewayservice.config.AppCredentialsProperties;
import com.xss.gatewayservice.config.IpWhitelistProperties;
import com.xss.gatewayservice.config.JwtProperties;
import com.xss.gatewayservice.config.ResourceRoutesProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/config")
@Slf4j
@RefreshScope
public class ConfigController {

    private final JwtProperties jwtProperties;
    private final AppCredentialsProperties appCredentialsProperties;
    private final IpWhitelistProperties ipWhitelistProperties;
    private final ResourceRoutesProperties resourceRoutesProperties;

    @Value("${spring.application.name}")
    private String appName;

    @Value("${server.port}")
    private String serverPort;

    @Value("${config-test.message:N/A}")
    private String configTestMessage;

    @Value("${config-test.refresh-count:0}")
    private Integer configTestRefreshCount;

    public ConfigController(JwtProperties jwtProperties,
                           AppCredentialsProperties appCredentialsProperties,
                           IpWhitelistProperties ipWhitelistProperties,
                           ResourceRoutesProperties resourceRoutesProperties) {
        this.jwtProperties = jwtProperties;
        this.appCredentialsProperties = appCredentialsProperties;
        this.ipWhitelistProperties = ipWhitelistProperties;
        this.resourceRoutesProperties = resourceRoutesProperties;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getConfigStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", LocalDateTime.now().toString());
        result.put("appName", appName);
        result.put("serverPort", serverPort);
        result.put("status", "config-loaded");

        Map<String, Object> jwt = new HashMap<>();
        jwt.put("secretBase64", jwtProperties.getSecretBase64() != null ? "******" : null);
        jwt.put("userPublicKey", jwtProperties.getUserPublicKey());
        result.put("jwt", jwt);

        Map<String, Object> ipWhitelist = new HashMap<>();
        ipWhitelist.put("enabled", ipWhitelistProperties.isEnabled());
        ipWhitelist.put("allowedIps", ipWhitelistProperties.getAllowedIps());
        result.put("ipWhitelist", ipWhitelist);

        Map<String, Object> routes = new HashMap<>();
        routes.put("count", resourceRoutesProperties.getRoutes().size());
        List<Map<String, String>> routeList = resourceRoutesProperties.getRoutes().stream()
                .map(r -> {
                    Map<String, String> map = new HashMap<>();
                    map.put("prefix", r.getPrefix());
                    map.put("target", r.getTarget());
                    return map;
                }).toList();
        routes.put("routes", routeList);
        result.put("routes", routes);

        Map<String, Object> configTest = new HashMap<>();
        configTest.put("message", configTestMessage);
        configTest.put("refreshCount", configTestRefreshCount);
        result.put("configTest", configTest);

        log.info("Config status accessed at {}", LocalDateTime.now());
        return ResponseEntity.ok(result);
    }
}
