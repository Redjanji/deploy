package com.xss.gatewayservice.filter;

import com.xss.gatewayservice.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class JwtAuthenticationFilter implements Filter {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String token = httpRequest.getHeader("Authorization");
        
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
            try {
                String subject = jwtUtil.parseClaims(token).getSubject();
                if (subject != null && subject.matches("\\d+")) {
                    httpRequest.setAttribute("X-User-Id", subject);
                } else {
                    httpRequest.setAttribute("X-App-Id", subject);
                }
            } catch (Exception e) {
                httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                httpResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
                httpResponse.setCharacterEncoding("UTF-8");
                Map<String, Object> error = new HashMap<>();
                error.put("code", 401);
                error.put("message", "Invalid token");
                error.put("data", null);
                httpResponse.getWriter().write(objectMapper.writeValueAsString(error));
                return;
            }
        }

        chain.doFilter(request, response);
    }
}
