package com.xss.propertyservice.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xss.propertyservice.dto.PropertyCreateRequest;
import com.xss.propertyservice.dto.PropertySearchRequest;
import com.xss.propertyservice.dto.PropertyUpdateRequest;
import com.xss.propertyservice.vo.PropertyDetailVO;
import com.xss.propertyservice.vo.PropertyVO;

public interface PropertyService {
    PropertyDetailVO create(PropertyCreateRequest req, String appId, Long ownerId);

    PropertyDetailVO update(Long id, PropertyUpdateRequest req, String appId, Long ownerId);

    PropertyDetailVO getDetail(Long id, String appId);

    PropertyDetailVO viewDetail(Long id, String appId, Long userId);

    IPage<PropertyVO> search(PropertySearchRequest req, String appId);

    void delete(Long id, String appId, Long ownerId);

    void updatePublishStatus(Long id, Integer publishStatus, String appId, Long ownerId);

    void updateAuditStatus(Long id, Integer status, String appId);
}
