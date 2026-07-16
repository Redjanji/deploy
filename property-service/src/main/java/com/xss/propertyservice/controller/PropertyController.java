package com.xss.propertyservice.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xss.propertyservice.common.Result;
import com.xss.propertyservice.dto.PropertyCreateRequest;
import com.xss.propertyservice.dto.PropertySearchRequest;
import com.xss.propertyservice.dto.PropertyUpdateRequest;
import com.xss.propertyservice.service.PropertyService;
import com.xss.propertyservice.vo.PropertyDetailVO;
import com.xss.propertyservice.vo.PropertyVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/properties")
@RequiredArgsConstructor
public class PropertyController {

    private final PropertyService propertyService;

    @PostMapping
    public Result<PropertyDetailVO> create(@Valid @RequestBody PropertyCreateRequest req,
                                           @RequestHeader(value = "X-App-Id", required = false, defaultValue = "default") String appId,
                                           @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return Result.success(propertyService.create(req, appId, userId));
    }

    @PutMapping("/{id}")
    public Result<PropertyDetailVO> update(@PathVariable Long id,
                                           @Valid @RequestBody PropertyUpdateRequest req,
                                           @RequestHeader(value = "X-App-Id", required = false, defaultValue = "default") String appId,
                                           @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return Result.success(propertyService.update(id, req, appId, userId));
    }

    @GetMapping("/{id}")
    public Result<PropertyDetailVO> detail(@PathVariable Long id,
                                           @RequestHeader(value = "X-App-Id", required = false, defaultValue = "default") String appId,
                                           @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return Result.success(propertyService.viewDetail(id, appId, userId));
    }

    @GetMapping
    public Result<IPage<PropertyVO>> search(PropertySearchRequest req,
                                           @RequestHeader(value = "X-App-Id", required = false, defaultValue = "default") String appId) {
        return Result.success(propertyService.search(req, appId));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id,
                               @RequestHeader(value = "X-App-Id", required = false, defaultValue = "default") String appId,
                               @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        propertyService.delete(id, appId, userId);
        return Result.success();
    }

    @PutMapping("/{id}/publish-status")
    public Result<Void> updatePublishStatus(@PathVariable Long id,
                                            @RequestParam Integer status,
                                            @RequestHeader(value = "X-App-Id", required = false, defaultValue = "default") String appId,
                                            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        propertyService.updatePublishStatus(id, status, appId, userId);
        return Result.success();
    }

    @PutMapping("/{id}/audit-status")
    public Result<Void> updateAuditStatus(@PathVariable Long id,
                                          @RequestParam Integer status,
                                          @RequestHeader(value = "X-App-Id", required = false, defaultValue = "default") String appId) {
        propertyService.updateAuditStatus(id, status, appId);
        return Result.success();
    }
}
