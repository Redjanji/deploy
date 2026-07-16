package com.xss.analyticsservice.service;

import com.xss.analyticsservice.mapper.StatsImageUploadMapper;
import com.xss.analyticsservice.mapper.StatsPropertyViewMapper;
import com.xss.analyticsservice.mapper.StatsUserActionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatsFlushService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final StatsPropertyViewMapper propertyViewMapper;
    private final StatsImageUploadMapper imageUploadMapper;
    private final StatsUserActionMapper userActionMapper;

    /**
     * 定时刷新 Redis 统计数据到 MySQL。
     * 使用 ShedLock 分布式锁，保证多实例环境下只有一个实例执行。
     * 同时处理今天和昨天的数据，防止午夜跨日数据丢失。
     */
    @Scheduled(cron = "${analytics.flush.cron:0 */5 * * * ?}")
    @SchedulerLock(name = "flushStatsToDatabase", lockAtMostFor = "PT9M", lockAtLeastFor = "PT1M")
    public void flushToDatabase() {
        String today = LocalDate.now().toString();
        String yesterday = LocalDate.now().minusDays(1).toString();

        log.info("Flushing stats to database for today: {}, yesterday: {}", today, yesterday);

        // 先刷新昨天的数据（防止午夜跨日残留）
        try {
            flushPropertyViews(yesterday);
            flushImageUploads(yesterday);
            flushUserActions(yesterday);
        } catch (Exception e) {
            log.error("Failed to flush yesterday stats (date={})", yesterday, e);
        }

        // 再刷新今天的数据
        try {
            flushPropertyViews(today);
        } catch (Exception e) {
            log.error("Failed to flush property views for date: {}", today, e);
        }

        try {
            flushImageUploads(today);
        } catch (Exception e) {
            log.error("Failed to flush image uploads for date: {}", today, e);
        }

        try {
            flushUserActions(today);
        } catch (Exception e) {
            log.error("Failed to flush user actions for date: {}", today, e);
        }

        // 清理 7 天前的 Redis 旧数据，防止内存泄漏
        try {
            cleanupOldRedisKeys();
        } catch (Exception e) {
            log.error("Failed to cleanup old Redis keys", e);
        }
    }

    // ==================== 房源浏览统计 ====================

    private void flushPropertyViews(String date) {
        Set<String> keys = scanKeys("stats:property:view:*:" + date);
        if (keys.isEmpty()) return;

        int count = 0;
        for (String key : keys) {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
            String[] parts = key.split(":");
            String appId = parts[3];

            for (Map.Entry<Object, Object> entry : entries.entrySet()) {
                String field = (String) entry.getKey();
                if (!field.endsWith(":count")) continue;

                Long propertyId = Long.parseLong(field.replace(":count", ""));
                Long viewCount = Long.parseLong(entry.getValue().toString());

                String uvKey = String.format("stats:property:uv:%s:%s:%s", appId, propertyId, date);
                Long uv = redisTemplate.opsForHyperLogLog().size(uvKey);

                propertyViewMapper.upsert(appId, propertyId, viewCount, uv != null ? uv : 0L, date, -1);
                count++;
            }
        }
        log.info("Flushed {} property view records for date: {}", count, date);
    }

    // ==================== 图片上传统计 ====================

    private void flushImageUploads(String date) {
        Set<String> countKeys = scanKeys("stats:image:upload:*:" + date + ":count");
        if (countKeys.isEmpty()) return;

        int count = 0;
        for (String countKey : countKeys) {
            String baseKey = countKey.replace(":count", "");
            String sizeKey = baseKey + ":size";
            String[] parts = countKey.split(":");
            String appId = parts[3];

            Object countObj = redisTemplate.opsForValue().get(countKey);
            Object sizeObj = redisTemplate.opsForValue().get(sizeKey);
            Long uploadCount = countObj != null ? Long.parseLong(countObj.toString()) : 0L;
            Long totalSize = sizeObj != null ? Long.parseLong(sizeObj.toString()) : 0L;

            imageUploadMapper.upsert(appId, uploadCount, totalSize, date, -1);
            count++;
        }
        log.info("Flushed {} image upload records for date: {}", count, date);
    }

    // ==================== 用户行为统计 ====================

    private void flushUserActions(String date) {
        Set<String> keys = scanKeys("stats:user:*:*:" + date);
        if (keys.isEmpty()) return;

        int count = 0;
        for (String key : keys) {
            String[] parts = key.split(":");
            String eventType = parts[2];
            String appId = parts[3];

            Object countObj = redisTemplate.opsForValue().get(key);
            Long actionCount = countObj != null ? Long.parseLong(countObj.toString()) : 0L;

            userActionMapper.upsert(appId, eventType, actionCount, date, -1);
            count++;
        }
        log.info("Flushed {} user action records for date: {}", count, date);
    }

    // ==================== Redis 旧数据清理 ====================

    /**
     * 清理 7 天前的 Redis 统计 key，防止内存无限增长。
     * 只清理 stats: 前缀的 key，不影响其他业务数据。
     */
    private void cleanupOldRedisKeys() {
        LocalDate cutoff = LocalDate.now().minusDays(7);
        Set<String> allKeys = scanKeys("stats:*");
        int deleted = 0;

        for (String key : allKeys) {
            // 从 key 中提取日期部分（格式: stats:xxx:...:yyyy-MM-dd[:suffix]）
            String[] parts = key.split(":");
            for (String part : parts) {
                if (part.length() == 10 && part.charAt(4) == '-') {
                    try {
                        LocalDate keyDate = LocalDate.parse(part);
                        if (keyDate.isBefore(cutoff)) {
                            redisTemplate.delete(key);
                            deleted++;
                        }
                    } catch (Exception ignored) {
                        // 不是日期格式，跳过
                    }
                }
            }
        }

        if (deleted > 0) {
            log.info("Cleaned up {} old Redis keys (older than {})", deleted, cutoff);
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 使用 SCAN 命令迭代 Redis key，替代阻塞式的 KEYS 命令。
     * SCAN 是非阻塞的游标迭代，不会影响 Redis 性能。
     */
    private Set<String> scanKeys(String pattern) {
        Set<String> keys = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();

        redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
            try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                while (cursor.hasNext()) {
                    keys.add(new String(cursor.next()));
                }
            }
            return null;
        });

        return keys;
    }
}
