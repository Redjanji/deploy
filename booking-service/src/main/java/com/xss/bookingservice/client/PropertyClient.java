package com.xss.bookingservice.client;

import com.xss.bookingservice.common.Result;
import com.xss.bookingservice.vo.PropertyBriefVO;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PropertyClient {
    private final RestTemplate restTemplate;

    public boolean exists(Long propertyId) {
        try {
            String url = "http://property-service/api/properties/" + propertyId + "/exists";
            Result<Boolean> result = restTemplate.getForObject(url, Result.class);
            return result != null && result.getCode() == 200 && Boolean.TRUE.equals(result.getData());
        } catch (Exception e) {
            return false;
        }
    }

    public PropertyBriefVO getBrief(Long propertyId) {
        try {
            String url = "http://property-service/api/properties/brief?ids=" + propertyId;
            ResponseEntity<Result<List<PropertyBriefVO>>> response = restTemplate.exchange(
                    url, HttpMethod.GET, null,
                    new ParameterizedTypeReference<Result<List<PropertyBriefVO>>>() {});
            Result<List<PropertyBriefVO>> result = response.getBody();
            if (result != null && result.getCode() == 200 && result.getData() != null && !result.getData().isEmpty()) {
                return result.getData().get(0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
