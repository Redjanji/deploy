package com.xss.gatewayservice.filter;

import com.xss.gatewayservice.config.IpWhitelistProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component
public class IpWhitelistGlobalFilter implements WebFilter, Ordered {

    private final IpWhitelistProperties ipWhitelistProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IpWhitelistGlobalFilter(IpWhitelistProperties ipWhitelistProperties) {
        this.ipWhitelistProperties = ipWhitelistProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!ipWhitelistProperties.isEnabled()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String remoteIp = getClientIp(request);

        boolean allowed = false;
        if (ipWhitelistProperties.getAllowedIps() != null) {
            for (String allowedIp : ipWhitelistProperties.getAllowedIps()) {
                if (matchIp(remoteIp, allowedIp)) {
                    allowed = true;
                    break;
                }
            }
        }

        if (!allowed) {
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.FORBIDDEN);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> error = new HashMap<>();
            error.put("code", 403);
            error.put("message", "IP address not allowed: " + remoteIp);
            error.put("data", null);
            try {
                byte[] bytes = objectMapper.writeValueAsString(error).getBytes(StandardCharsets.UTF_8);
                DataBuffer buffer = response.bufferFactory().wrap(bytes);
                return response.writeWith(Mono.just(buffer));
            } catch (Exception e) {
                return response.setComplete();
            }
        }

        return chain.filter(exchange);
    }

    private String getClientIp(ServerHttpRequest request) {
        String xff = request.getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            int comma = xff.indexOf(',');
            if (comma > 0) {
                return xff.substring(0, comma).trim();
            }
            return xff.trim();
        }
        return request.getRemoteAddress() != null ? request.getRemoteAddress().getAddress().getHostAddress() : "unknown";
    }

    private boolean matchIp(String remoteIp, String allowed) {
        try {
            if (allowed.contains("/")) {
                String[] parts = allowed.split("/");
                InetAddress remote = InetAddress.getByName(remoteIp);
                InetAddress subnet = InetAddress.getByName(parts[0]);
                int prefixLength = Integer.parseInt(parts[1]);

                byte[] remoteBytes = remote.getAddress();
                byte[] subnetBytes = subnet.getAddress();

                if (remoteBytes.length != subnetBytes.length) {
                    return false;
                }

                return isInSubnet(remoteBytes, subnetBytes, prefixLength);
            } else {
                InetAddress remote = InetAddress.getByName(remoteIp);
                InetAddress allowedAddr = InetAddress.getByName(allowed);
                return remote.equals(allowedAddr);
            }
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private boolean isInSubnet(byte[] ip, byte[] subnet, int prefixLength) {
        int fullBytes = prefixLength / 8;
        int remainderBits = prefixLength % 8;

        for (int i = 0; i < fullBytes; i++) {
            if (ip[i] != subnet[i]) {
                return false;
            }
        }

        if (remainderBits > 0 && fullBytes < ip.length) {
            int mask = 0xFF << (8 - remainderBits);
            if ((ip[fullBytes] & mask) != (subnet[fullBytes] & mask)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public int getOrder() {
        return -100;
    }
}