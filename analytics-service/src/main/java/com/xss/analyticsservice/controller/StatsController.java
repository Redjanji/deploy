package com.xss.analyticsservice.controller;

import com.xss.analyticsservice.common.Result;
import com.xss.analyticsservice.service.StatsQueryService;
import com.xss.analyticsservice.vo.DashboardSummaryVO;
import com.xss.analyticsservice.vo.ImageUploadSummaryVO;
import com.xss.analyticsservice.vo.PropertyViewStatsVO;
import com.xss.analyticsservice.vo.UserActionStatsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsQueryService statsQueryService;

    @GetMapping("/property/views")
    public Result<List<PropertyViewStatsVO>> getPropertyViews(
            @RequestParam String appId,
            @RequestParam String startDate,
            @RequestParam(required = false) String endDate,
            @RequestHeader(value = "X-App-Id", required = false) String headerAppId) {
        if (endDate == null) endDate = LocalDate.now().toString();
        return Result.success(statsQueryService.getPropertyViews(appId, startDate, endDate));
    }

    @GetMapping("/image/upload-summary")
    public Result<List<ImageUploadSummaryVO>> getImageUploadSummary(
            @RequestParam(required = false) String appId,
            @RequestHeader(value = "X-App-Id", required = false) String headerAppId) {
        return Result.success(statsQueryService.getImageUploadSummary(appId));
    }

    @GetMapping("/user/actions")
    public Result<List<UserActionStatsVO>> getUserActions(
            @RequestParam(required = false) String appId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestHeader(value = "X-App-Id", required = false) String headerAppId) {
        if (startDate == null) startDate = LocalDate.now().toString();
        if (endDate == null) endDate = LocalDate.now().toString();
        return Result.success(statsQueryService.getUserActions(appId, eventType, startDate, endDate));
    }

    @GetMapping("/dashboard")
    public Result<DashboardSummaryVO> getDashboard(
            @RequestHeader(value = "X-App-Id", required = false) String appId) {
        return Result.success(statsQueryService.getDashboard());
    }
}
