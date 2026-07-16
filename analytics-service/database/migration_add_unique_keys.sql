-- =====================================================
-- analytics_db 增量迁移：添加唯一键
-- 用于已部署的数据库，使 ON DUPLICATE KEY UPDATE 生效
-- =====================================================

USE `analytics_db`;

-- stats_image_uploads: 添加唯一键 (app_id, stats_date, stats_hour)
-- 先删除可能存在的重复数据，保留每组最新一条
DELETE e1 FROM stats_image_uploads e1
INNER JOIN stats_image_uploads e2
ON e1.app_id = e2.app_id
   AND e1.stats_date = e2.stats_date
   AND e1.stats_hour = e2.stats_hour
   AND e1.id < e2.id;

ALTER TABLE stats_image_uploads
    DROP INDEX IF EXISTS idx_app_date,
    ADD UNIQUE KEY uk_app_date_hour (app_id, stats_date, stats_hour),
    ADD KEY idx_app_date (app_id, stats_date);

-- stats_user_actions: 添加唯一键 (app_id, event_type, stats_date, stats_hour)
DELETE e1 FROM stats_user_actions e1
INNER JOIN stats_user_actions e2
ON e1.app_id = e2.app_id
   AND e1.event_type = e2.event_type
   AND e1.stats_date = e2.stats_date
   AND e1.stats_hour = e2.stats_hour
   AND e1.id < e2.id;

ALTER TABLE stats_user_actions
    DROP INDEX IF EXISTS idx_app_event_date,
    ADD UNIQUE KEY uk_app_event_date_hour (app_id, event_type, stats_date, stats_hour),
    ADD KEY idx_app_event_date (app_id, event_type, stats_date);

-- stats_property_views 已有唯一键 uk_app_prop_date_hour，无需修改
