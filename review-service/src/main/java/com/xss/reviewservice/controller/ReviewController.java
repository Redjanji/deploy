package com.xss.reviewservice.controller;

import com.xss.reviewservice.common.Result;
import com.xss.reviewservice.service.ReviewService;
import com.xss.reviewservice.vo.AuditTaskVO;
import com.xss.reviewservice.vo.ManualAuditRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping("/tasks")
    public Result<Map<String, Object>> listTasks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer status) {
        return Result.ok(reviewService.listTasks(status, page, size));
    }

    @GetMapping("/tasks/{taskId}")
    public Result<AuditTaskVO> getTask(@PathVariable Long taskId) {
        return Result.ok(reviewService.getTaskDetail(taskId));
    }

    @PostMapping("/manual")
    public Result<Void> manualAudit(@RequestBody ManualAuditRequest request) {
        reviewService.manualAudit(request.getTaskId(), request.getResult(),
                request.getReason(), request.getAuditorId());
        return Result.ok(null, "审核完成");
    }

    @PostMapping("/tasks")
    public Result<Long> createTask(
            @RequestParam Long propertyId,
            @RequestHeader(value = "X-App-Id", required = false, defaultValue = "default") String appId,
            @RequestParam(required = false) String taskType) {
        return Result.ok(reviewService.createTask(propertyId, appId, taskType));
    }
}
