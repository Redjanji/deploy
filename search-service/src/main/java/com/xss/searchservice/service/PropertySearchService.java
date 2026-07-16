package com.xss.searchservice.service;

import com.xss.searchservice.dto.PropertySearchRequest;
import com.xss.searchservice.vo.SearchResultVO;

public interface PropertySearchService {
    SearchResultVO search(PropertySearchRequest request, String appId);
    void indexProperty(Long propertyId);
    void deleteProperty(Long propertyId);
    void syncProperty(Long propertyId, String appId);
    void deleteProperty(Long propertyId, String appId);
    void reindexAll(String appId);
}
