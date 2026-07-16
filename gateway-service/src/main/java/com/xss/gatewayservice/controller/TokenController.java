package com.xss.gatewayservice.controller;

import com.xss.gatewayservice.config.AppCredentialsProperties;
import com.xss.gatewayservice.util.HmacUtil;
import com.xss.gatewayservice.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
public class TokenController {

    private static final Logger logger = LoggerFactory.getLogger(TokenController.class);

    private final JwtUtil jwtUtil;
    private final Map<String, String> appCredentials;
    private final long timestampTolerance;
    private final Cache nonceCache;

    public TokenController(JwtUtil jwtUtil,
                           AppCredentialsProperties appCredentialsProperties,
                           @Value("${hmac.timestamp-tolerance}") long timestampTolerance,
                           CacheManager cacheManager) {
        this.jwtUtil = jwtUtil;
        this.appCredentials = appCredentialsProperties.getCredentials();
        this.timestampTolerance = timestampTolerance;
        this.nonceCache = cacheManager.getCache("nonceCache");
    }

    @PostMapping("/token")
    public ResponseEntity<Map<String, Object>> getToken(@RequestParam String appId,
                                                        @RequestParam long timestamp,
                                                        @RequestParam String nonce,
                                                        @RequestParam String sign) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", "success");
        response.put("data", null);

        long now = System.currentTimeMillis() / 1000;
        logger.info("Token request: appId={}, timestamp={}, now={}, diff={}, tolerance={}", 
                    appId, timestamp, now, Math.abs(now - timestamp), timestampTolerance);
        if (Math.abs(now - timestamp) > timestampTolerance) {
            response.put("code", 401);
            response.put("message", "Timestamp expired");
            return ResponseEntity.status(401).body(response);
        }

        String appSecret = appCredentials.get(appId);
        if (appSecret == null) {
            response.put("code", 401);
            response.put("message", "Unknown appId");
            return ResponseEntity.status(401).body(response);
        }

        String payload = appId + ":" + timestamp + ":" + nonce;
        String expectedSign = HmacUtil.hmacSha256(payload, appSecret);
        if (!HmacUtil.constantTimeEquals(expectedSign, sign)) {
            response.put("code", 401);
            response.put("message", "Invalid signature");
            return ResponseEntity.status(401).body(response);
        }

        String nonceKey = appId + ":" + nonce;
        if (nonceCache != null && nonceCache.get(nonceKey) != null) {
            response.put("code", 401);
            response.put("message", "Nonce reused");
            return ResponseEntity.status(401).body(response);
        }

        if (nonceCache != null) {
            nonceCache.put(nonceKey, Boolean.TRUE);
        }

        String token = jwtUtil.generateToken(appId);
        if (token == null) {
            response.put("code", 500);
            response.put("message", "Token generation failed (null)");
            return ResponseEntity.status(500).body(response);
        }

        Map<String, String> data = new HashMap<>();
        data.put("token", token);
        response.put("data", data);
        response.put("code", 200);
        response.put("message", "success");
        return ResponseEntity.ok(response);
    }
}
