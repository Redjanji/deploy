package com.xss.reviewservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xss.reviewservice.common.ResourceNotFoundException;
import com.xss.reviewservice.entity.AuditRecordEntity;
import com.xss.reviewservice.entity.AuditTaskEntity;
import com.xss.reviewservice.mapper.AuditRecordMapper;
import com.xss.reviewservice.mapper.AuditTaskMapper;
import com.xss.reviewservice.service.ReviewService;
import com.xss.reviewservice.vo.AuditRecordVO;
import com.xss.reviewservice.vo.AuditTaskVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl extends ServiceImpl<AuditTaskMapper, AuditTaskEntity> implements ReviewService {

    private final AuditRecordMapper auditRecordMapper;

    @Override
    public Map<String, Object> listTasks(Integer status, int page, int size) {
        Page<AuditTaskEntity> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<AuditTaskEntity> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(AuditTaskEntity::getStatus, status);
        }
        wrapper.orderByDesc(AuditTaskEntity::getCreatedAt);
        Page<AuditTaskEntity> result = page(pageObj, wrapper);
        List<AuditTaskVO> records = result.getRecords().stream()
                .map(this::toTaskVO)
                .collect(Collectors.toList());
        Map<String, Object> data = new HashMap<>();
        data.put("total", result.getTotal());
        data.put("records", records);
        return data;
    }

    @Override
    public AuditTaskVO getTaskDetail(Long taskId) {
        AuditTaskEntity task = getById(taskId);
        if (task == null) {
            throw new ResourceNotFoundException("审核任务不存在: " + taskId);
        }
        AuditTaskVO vo = toTaskVO(task);
        List<AuditRecordEntity> records = auditRecordMapper.selectList(
                new LambdaQueryWrapper<AuditRecordEntity>()
                        .eq(AuditRecordEntity::getTaskId, taskId)
                        .orderByAsc(AuditRecordEntity::getAuditAt));
        vo.setAuditRecords(records.stream().map(this::toRecordVO).collect(Collectors.toList()));
        return vo;
    }

    @Override
    public void manualAudit(Long taskId, Integer result, String reason, Long auditorId) {
        if (result == null || (result != 1 && result != 2)) {
            throw new IllegalArgumentException("审核结果必须为1(通过)或2(拒绝)");
        }
        AuditTaskEntity task = getById(taskId);
        if (task == null) {
            throw new ResourceNotFoundException("审核任务不存在: " + taskId);
        }
        AuditRecordEntity record = new AuditRecordEntity();
        record.setTaskId(taskId);
        record.setAuditType("MANUAL");
        record.setResult(result);
        record.setReason(reason);
        record.setAuditorId(auditorId);
        record.setAuditAt(LocalDateTime.now());
        auditRecordMapper.insert(record);

        task.setStatus(result == 1 ? 2 : 3);
        updateById(task);
    }

    @Override
    public Long createTask(Long propertyId, String appId, String taskType) {
        AuditTaskEntity task = new AuditTaskEntity();
        task.setPropertyId(propertyId);
        task.setAppId(appId);
        task.setTaskType(taskType != null ? taskType : "CONTENT_AUDIT");
        task.setStatus(1);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        save(task);
        return task.getId();
    }

    private AuditTaskVO toTaskVO(AuditTaskEntity task) {
        AuditTaskVO vo = new AuditTaskVO();
        vo.setId(task.getId());
        vo.setPropertyId(task.getPropertyId());
        vo.setAppId(task.getAppId());
        vo.setTaskType(task.getTaskType());
        vo.setStatus(task.getStatus());
        vo.setResultDetail(task.getResultDetail());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setUpdatedAt(task.getUpdatedAt());
        return vo;
    }

    private AuditRecordVO toRecordVO(AuditRecordEntity record) {
        AuditRecordVO vo = new AuditRecordVO();
        vo.setId(record.getId());
        vo.setTaskId(record.getTaskId());
        vo.setAuditType(record.getAuditType());
        vo.setResult(record.getResult());
        vo.setReason(record.getReason());
        vo.setAuditorId(record.getAuditorId());
        vo.setAuditAt(record.getAuditAt());
        return vo;
    }
}
