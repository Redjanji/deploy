package com.xss.propertyservice.client;

import com.xss.propertyservice.common.Result;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DictClient {

    private final RestTemplate restTemplate;

    private static final String DICT_SERVICE_URL = "http://dict-service";

    /**
     * 获取某个字典类型的所有条目（key → value 映射）
     * 调用 dict-service 的 /api/dict/items?type={typeCode} 接口
     */
    public Map<String, String> getDictMap(String typeCode) {
        try {
            Result<List<DictItem>> resp = restTemplate.exchange(
                    DICT_SERVICE_URL + "/api/dict/items?type=" + typeCode,
                    HttpMethod.GET, null,
                    new ParameterizedTypeReference<Result<List<DictItem>>>() {}).getBody();
            if (resp != null && resp.getCode() == 200 && resp.getData() != null) {
                return resp.getData().stream()
                        .collect(Collectors.toMap(DictItem::getCode, DictItem::getName, (a, b) -> a));
            }
        } catch (Exception e) {
            log.warn("调用 dict-service 获取字典失败: typeCode={}", typeCode);
        }
        return Collections.emptyMap();
    }

    @Data
    public static class DictItem {
        private String code;
        private String name;
    }
}
