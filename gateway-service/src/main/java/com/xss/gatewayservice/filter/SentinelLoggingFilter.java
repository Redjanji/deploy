package com.xss.gatewayservice.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
public class SentinelLoggingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String uri = httpRequest.getRequestURI();
        String method = httpRequest.getMethod();
        String clientIp = getClientIp(httpRequest);
        String requestId = java.util.UUID.randomUUID().toString().substring(0, 8);
        
        long startTime = System.currentTimeMillis();
        
        log.info("[GATEWAY] requestId={}, clientIp={}, method={}, uri={}, thread={}",
                requestId, clientIp, method, uri, Thread.currentThread().getName());
        
        try {
            chain.doFilter(request, response);
            long duration = System.currentTimeMillis() - startTime;
            int status = httpResponse.getStatus();
            log.info("[GATEWAY] requestId={}, method={}, uri={}, duration={}ms, status={}",
                    requestId, method, uri, duration, status);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.warn("[GATEWAY] requestId={}, method={}, uri={}, duration={}ms, error={}",
                    requestId, method, uri, duration, e.getMessage());
            throw e;
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
