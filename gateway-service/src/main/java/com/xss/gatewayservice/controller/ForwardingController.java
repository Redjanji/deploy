package com.xss.gatewayservice.controller;

import com.xss.gatewayservice.config.ResourceRoutesProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Enumeration;

@RestController
public class ForwardingController {

    private final RestTemplate restTemplate;
    private final ResourceRoutesProperties routesProperties;

    public ForwardingController(RestTemplate restTemplate,
                                ResourceRoutesProperties routesProperties) {
        this.restTemplate = restTemplate;
        this.routesProperties = routesProperties;
    }

    @RequestMapping("/**")
    public ResponseEntity<byte[]> forward(HttpServletRequest request) {
        String requestUri = request.getRequestURI();

        // Skip /error, /token, /config, /actuator — handled by other controllers
        if (requestUri.equals("/error") || requestUri.equals("/token")
                || requestUri.startsWith("/config") || requestUri.startsWith("/actuator")) {
            return ResponseEntity.notFound().build();
        }

        // Find matching route
        ResourceRoutesProperties.Route matchedRoute = null;
        if (routesProperties.getRoutes() != null) {
            for (ResourceRoutesProperties.Route route : routesProperties.getRoutes()) {
                if (requestUri.startsWith(route.getPrefix())) {
                    matchedRoute = route;
                    break;
                }
            }
        }

        if (matchedRoute == null) {
            return ResponseEntity.notFound().build();
        }

        // Build target URL
        String targetUrl = matchedRoute.getTarget() + requestUri;
        String queryString = request.getQueryString();
        if (queryString != null && !queryString.isEmpty()) {
            targetUrl += "?" + queryString;
        }

        // Build headers
        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            // Skip hop-by-hop headers
            if (name.equalsIgnoreCase("host") || name.equalsIgnoreCase("content-length")
                    || name.equalsIgnoreCase("transfer-encoding")) {
                continue;
            }
            Enumeration<String> values = request.getHeaders(name);
            while (values.hasMoreElements()) {
                headers.add(name, values.nextElement());
            }
        }

        // Inject X-User-Id from JWT attribute (set by JwtAuthenticationFilter)
        // Only forward if it's a numeric value (user JWT), not app IDs (app JWT)
        Object userIdAttr = request.getAttribute("X-User-Id");
        if (userIdAttr != null && !headers.containsKey("X-User-Id")) {
            String userIdStr = userIdAttr.toString();
            if (userIdStr.matches("\\d+")) {
                headers.set("X-User-Id", userIdStr);
            }
        }

        // Read body
        byte[] body = null;
        try {
            body = StreamUtils.copyToByteArray(request.getInputStream());
        } catch (Exception e) {
            body = new byte[0];
        }

        // Determine content type
        MediaType contentType = MediaType.APPLICATION_OCTET_STREAM;
        if (request.getContentType() != null && !request.getContentType().isEmpty()) {
            try {
                contentType = MediaType.parseMediaType(request.getContentType());
            } catch (Exception ignored) {}
        }

        HttpEntity<byte[]> entity = new HttpEntity<>(body, headers);

        // Forward request
        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    URI.create(targetUrl),
                    HttpMethod.valueOf(request.getMethod()),
                    entity,
                    byte[].class
            );
            // Strip hop-by-hop headers from backend response to prevent duplicates
            HttpHeaders cleanedHeaders = new HttpHeaders();
            HttpHeaders backendHeaders = response.getHeaders();
            for (String headerName : backendHeaders.keySet()) {
                if (headerName.equalsIgnoreCase("transfer-encoding")
                        || headerName.equalsIgnoreCase("content-length")
                        || headerName.equalsIgnoreCase("connection")) {
                    continue;
                }
                cleanedHeaders.put(headerName, backendHeaders.get(headerName));
            }
            return ResponseEntity.status(response.getStatusCode())
                    .headers(cleanedHeaders)
                    .body(response.getBody());
        } catch (HttpStatusCodeException e) {
            // Strip hop-by-hop headers from error response as well
            HttpHeaders errorHeaders = new HttpHeaders();
            if (e.getResponseHeaders() != null) {
                for (String headerName : e.getResponseHeaders().keySet()) {
                    if (headerName.equalsIgnoreCase("transfer-encoding")
                            || headerName.equalsIgnoreCase("content-length")
                            || headerName.equalsIgnoreCase("connection")) {
                        continue;
                    }
                    errorHeaders.put(headerName, e.getResponseHeaders().get(headerName));
                }
            }
            if (errorHeaders.getContentType() == null) {
                errorHeaders.setContentType(MediaType.APPLICATION_JSON);
            }
            return ResponseEntity.status(e.getStatusCode())
                    .headers(errorHeaders)
                    .body(e.getResponseBodyAsByteArray());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(("{\"code\":502,\"message\":\"Bad Gateway: " + e.getMessage() + "\",\"data\":null}").getBytes());
        }
    }
}
