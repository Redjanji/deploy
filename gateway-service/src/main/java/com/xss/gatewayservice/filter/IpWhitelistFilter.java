package com.xss.gatewayservice.filter;

import com.xss.gatewayservice.config.IpWhitelistProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class IpWhitelistFilter implements Filter {

    private final IpWhitelistProperties ipWhitelistProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IpWhitelistFilter(IpWhitelistProperties ipWhitelistProperties) {
        this.ipWhitelistProperties = ipWhitelistProperties;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
            throws IOException, ServletException {
        
        if (!ipWhitelistProperties.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String remoteIp = getClientIp(httpRequest);

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
            httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
            httpResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
            httpResponse.setCharacterEncoding("UTF-8");
            Map<String, Object> error = new HashMap<>();
            error.put("code", 403);
            error.put("message", "IP address not allowed: " + remoteIp);
            error.put("data", null);
            httpResponse.getWriter().write(objectMapper.writeValueAsString(error));
            return;
        }

        chain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            int comma = xff.indexOf(',');
            if (comma > 0) {
                return xff.substring(0, comma).trim();
            }
            return xff.trim();
        }
        return request.getRemoteAddr();
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
}
