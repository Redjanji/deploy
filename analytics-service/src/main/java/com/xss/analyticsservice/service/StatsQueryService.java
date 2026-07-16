package com.xss.analyticsservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xss.analyticsservice.entity.StatsImageUpload;
import com.xss.analyticsservice.entity.StatsPropertyView;
import com.xss.analyticsservice.entity.StatsUserAction;
import com.xss.analyticsservice.mapper.StatsImageUploadMapper;
import com.xss.analyticsservice.mapper.StatsPropertyViewMapper;
import com.xss.analyticsservice.mapper.StatsUserActionMapper;
import com.xss.analyticsservice.vo.DashboardSummaryVO;
import com.xss.analyticsservice.vo.ImageUploadSummaryVO;
import com.xss.analyticsservice.vo.PropertyViewStatsVO;
import com.xss.analyticsservice.vo.UserActionStatsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StatsQueryService {

    private final StatsPropertyViewMapper propertyViewMapper;
    private final StatsImageUploadMapper imageUploadMapper;
    private final StatsUserActionMapper userActionMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    public List<PropertyViewStatsVO> getPropertyViews(String appId, String startDate, String endDate) {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = endDate != null ? LocalDate.parse(endDate) : LocalDate.now();

        List<StatsPropertyView> records = propertyViewMapper.selectList(
                new LambdaQueryWrapper<StatsPropertyView>()
                        .eq(StatsPropertyView::getAppId, appId)
                        .between(StatsPropertyView::getStatsDate, start, end)
                        .orderByDesc(StatsPropertyView::getStatsDate)
        );

        List<PropertyViewStatsVO> result = new ArrayList<>();
        for (StatsPropertyView r : records) {
            PropertyViewStatsVO vo = new PropertyViewStatsVO();
            vo.setAppId(r.getAppId());
            vo.setPropertyId(r.getPropertyId());
            vo.setStatsDate(r.getStatsDate());
            vo.setViewCount(r.getViewCount());
            vo.setUniqueVisitors(r.getUniqueVisitors());
            result.add(vo);
        }
        return result;
    }

    public List<ImageUploadSummaryVO> getImageUploadSummary(String appId) {
        LocalDate today = LocalDate.now();
        List<StatsImageUpload> records = imageUploadMapper.selectList(
                new LambdaQueryWrapper<StatsImageUpload>()
                        .eq(appId != null, StatsImageUpload::getAppId, appId)
                        .eq(StatsImageUpload::getStatsDate, today)
        );

        List<ImageUploadSummaryVO> result = new ArrayList<>();
        for (StatsImageUpload r : records) {
            ImageUploadSummaryVO vo = new ImageUploadSummaryVO();
            vo.setAppId(r.getAppId());
            vo.setStatsDate(r.getStatsDate());
            vo.setUploadCount(r.getUploadCount());
            vo.setTotalSize(r.getTotalSize());
            vo.setTotalSizeFormatted(formatSize(r.getTotalSize()));
            result.add(vo);
        }

        if (result.isEmpty()) {
            String date = today.toString();
            Set<String> countKeys = redisTemplate.keys("stats:image:upload:*:" + date + ":count");
            if (countKeys != null) {
                for (String countKey : countKeys) {
                    String[] parts = countKey.split(":");
                    String app = parts[3];
                    String sizeKey = "stats:image:upload:" + app + ":" + date + ":size";
                    Object countObj = redisTemplate.opsForValue().get(countKey);
                    Object sizeObj = redisTemplate.opsForValue().get(sizeKey);
                    ImageUploadSummaryVO vo = new ImageUploadSummaryVO();
                    vo.setAppId(app);
                    vo.setStatsDate(today);
                    vo.setUploadCount(countObj != null ? Long.parseLong(countObj.toString()) : 0L);
                    vo.setTotalSize(sizeObj != null ? Long.parseLong(sizeObj.toString()) : 0L);
                    vo.setTotalSizeFormatted(formatSize(vo.getTotalSize()));
                    result.add(vo);
                }
            }
        }
        return result;
    }

    public List<UserActionStatsVO> getUserActions(String appId, String eventType, String startDate, String endDate) {
        LocalDate start = startDate != null ? LocalDate.parse(startDate) : LocalDate.now();
        LocalDate end = endDate != null ? LocalDate.parse(endDate) : LocalDate.now();

        List<StatsUserAction> records = userActionMapper.selectList(
                new LambdaQueryWrapper<StatsUserAction>()
                        .eq(appId != null, StatsUserAction::getAppId, appId)
                        .eq(eventType != null, StatsUserAction::getEventType, eventType)
                        .between(StatsUserAction::getStatsDate, start, end)
                        .orderByDesc(StatsUserAction::getStatsDate)
        );

        List<UserActionStatsVO> result = new ArrayList<>();
        for (StatsUserAction r : records) {
            UserActionStatsVO vo = new UserActionStatsVO();
            vo.setAppId(r.getAppId());
            vo.setEventType(r.getEventType());
            vo.setStatsDate(r.getStatsDate());
            vo.setActionCount(r.getActionCount());
            result.add(vo);
        }
        return result;
    }

    public DashboardSummaryVO getDashboard() {
        LocalDate today = LocalDate.now();
        String date = today.toString();
        DashboardSummaryVO vo = new DashboardSummaryVO();
        vo.setToday(today);

        vo.setTodayPropertyViews(getRedisSum("stats:property:view:*:" + date));
        vo.setTodayImageUploads(getRedisSum("stats:image:upload:*:" + date + ":count"));
        vo.setTodayUserRegisters(getRedisSingle("stats:user:USER_REGISTER:*:" + date));
        vo.setTodayUserLogins(getRedisSingle("stats:user:USER_LOGIN:*:" + date));
        vo.setTodayPropertyCreates(getRedisSingle("stats:user:PROPERTY_CREATE:*:" + date));

        List<StatsPropertyView> topRecords = propertyViewMapper.selectList(
                new LambdaQueryWrapper<StatsPropertyView>()
                        .eq(StatsPropertyView::getStatsDate, today)
                        .orderByDesc(StatsPropertyView::getViewCount)
                        .last("LIMIT 10")
        );
        List<PropertyViewStatsVO> topProperties = new ArrayList<>();
        for (StatsPropertyView r : topRecords) {
            PropertyViewStatsVO pvo = new PropertyViewStatsVO();
            pvo.setAppId(r.getAppId());
            pvo.setPropertyId(r.getPropertyId());
            pvo.setStatsDate(r.getStatsDate());
            pvo.setViewCount(r.getViewCount());
            pvo.setUniqueVisitors(r.getUniqueVisitors());
            topProperties.add(pvo);
        }
        vo.setTopProperties(topProperties);

        List<ImageUploadSummaryVO> appImageSummary = getImageUploadSummary(null);
        appImageSummary.sort(Comparator.comparingLong(ImageUploadSummaryVO::getTotalSize).reversed());
        vo.setAppImageSummary(appImageSummary);

        return vo;
    }

    private Long getRedisSum(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys == null || keys.isEmpty()) return 0L;
        long sum = 0;
        for (String key : keys) {
            if (key.contains(":count") || key.startsWith("stats:image:upload:")) {
                Object val = redisTemplate.opsForValue().get(key);
                if (val != null) sum += Long.parseLong(val.toString());
            } else {
                Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
                for (Object v : entries.values()) {
                    if (v != null) sum += Long.parseLong(v.toString());
                }
            }
        }
        return sum;
    }

    private Long getRedisSingle(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys == null || keys.isEmpty()) return 0L;
        long sum = 0;
        for (String key : keys) {
            Object val = redisTemplate.opsForValue().get(key);
            if (val != null) sum += Long.parseLong(val.toString());
        }
        return sum;
    }

    private String formatSize(Long bytes) {
        if (bytes == null || bytes == 0) return "0 B";
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        double size = bytes;
        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }
        return String.format("%.2f %s", size, units[unitIndex]);
    }
}
