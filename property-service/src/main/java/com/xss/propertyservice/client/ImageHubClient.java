package com.xss.propertyservice.client;

import com.xss.propertyservice.common.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageHubClient {

    private final RestTemplate restTemplate;

    @Value("${image-hub.cdn-prefix}")
    private String cdnPrefix;

    private static final String IMAGE_SERVICE_URL = "http://image-service";

    /**
     * 根据图片ID和尺寸获取访问URL
     * 优先调用 image-service 接口获取；失败时降级为 CDN 前缀拼接
     */
    public String getImageUrl(Long imageId, String sizeType) {
        try {
            Result<Map<String, String>> resp = restTemplate.exchange(
                    IMAGE_SERVICE_URL + "/api/images/" + imageId + "/url?sizeType=" + sizeType,
                    HttpMethod.GET, null,
                    new ParameterizedTypeReference<Result<Map<String, String>>>() {}).getBody();
            if (resp != null && resp.getCode() == 200 && resp.getData() != null) {
                return resp.getData().get("url");
            }
        } catch (Exception e) {
            log.debug("调用 image-service 获取图片URL失败，使用降级方案: imageId={}, sizeType={}",
                    imageId, sizeType);
        }
        return cdnPrefix + imageId + "/" + sizeType + ".webp";
    }
}
