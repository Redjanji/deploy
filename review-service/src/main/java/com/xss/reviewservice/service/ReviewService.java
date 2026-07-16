package com.xss.reviewservice.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xss.reviewservice.entity.AuditTaskEntity;
import com.xss.reviewservice.vo.AuditTaskVO;

import java.util.Map;

public interface ReviewService extends IService<AuditTaskEntity> {

    Map<String, Object> listTasks(Integer status, int page, int size);

    AuditTaskVO getTaskDetail(Long taskId);

    void manualAudit(Long taskId, Integer result, String reason, Long auditorId);

    Long createTask(Long propertyId, String appId, String taskType);
}
