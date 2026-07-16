package com.xss.imageservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Data
@RefreshScope
@ConfigurationProperties(prefix = "image")
@Component
public class ImageConfigProperties {
    private long maxSize = 10_485_760;
    private Set<String> allowedExtensions = Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp");
    private Set<String> allowedMimeTypes = Set.of("image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp");
    private List<Integer> thumbnailWidths = List.of(1280, 640, 200);
    private float webpQuality = 0.8f;
    private String cdnUrl = "";
    private String localUrl = "/api/images/";
}
