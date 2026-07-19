SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- property-service 数据库脚本
-- 房源服务数据库：property_db
-- ============================================================

CREATE DATABASE IF NOT EXISTS `property_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `property_db`;

-- ============================================================
-- 房源主表
-- ============================================================
DROP TABLE IF EXISTS `properties`;
CREATE TABLE `properties` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `app_id` varchar(32) NOT NULL DEFAULT 'default' COMMENT '应用标识',
  `title` varchar(200) NOT NULL COMMENT '房源标题',
  `type` varchar(50) NOT NULL COMMENT '房源类型字典key',
  `price` bigint DEFAULT '0' COMMENT '价格（元/月 或 总价，单位分）',
  `rental_area` int DEFAULT '0' COMMENT '面积(㎡)',
  `rooms` varchar(50) DEFAULT NULL COMMENT '户型字典key',
  `orientation` varchar(50) DEFAULT NULL COMMENT '朝向字典key',
  `floor` varchar(50) DEFAULT NULL COMMENT '楼层描述',
  `total_floors` int DEFAULT '0' COMMENT '总楼层',
  `address` varchar(500) DEFAULT NULL COMMENT '详细地址',
  `lat` decimal(10,6) DEFAULT NULL COMMENT '纬度',
  `lng` decimal(10,6) DEFAULT NULL COMMENT '经度',
  `geohash` varchar(12) DEFAULT NULL COMMENT 'GeoHash 编码（附近搜索）',
  `province_code` varchar(20) DEFAULT NULL COMMENT '省份编码',
  `province_name` varchar(50) DEFAULT NULL COMMENT '省份名称',
  `city_code` varchar(20) DEFAULT NULL COMMENT '城市编码',
  `city_name` varchar(50) DEFAULT NULL COMMENT '城市名称',
  `district_code` varchar(20) DEFAULT NULL COMMENT '区县编码',
  `district_name` varchar(50) DEFAULT NULL COMMENT '区县名称',
  `decoration` varchar(50) DEFAULT NULL COMMENT '装修字典key',
  `heating_method` varchar(50) DEFAULT NULL COMMENT '供暖方式字典key',
  `water_supply` varchar(50) DEFAULT NULL COMMENT '供水字典key',
  `power_supply` varchar(50) DEFAULT NULL COMMENT '供电字典key',
  `gas_supply` varchar(50) DEFAULT NULL COMMENT '供气字典key',
  `internet` varchar(50) DEFAULT NULL COMMENT '网络接入字典key',
  `tv_service` varchar(50) DEFAULT NULL COMMENT '电视服务字典key',
  `description` text COMMENT '房源描述',
  `contact_phone` varchar(50) DEFAULT NULL COMMENT '联系电话',
  `agent_name` varchar(100) DEFAULT NULL COMMENT '经纪人姓名',
  `agent_title` varchar(50) DEFAULT NULL COMMENT '经纪人职务',
  `agent_phone` varchar(50) DEFAULT NULL COMMENT '经纪人电话',
  `publish_status` tinyint(1) DEFAULT '0' COMMENT '发布状态：0草稿 1已发布 2已下架',
  `status` tinyint(1) DEFAULT '0' COMMENT '审核状态：0草稿 1通过 2待审核 3驳回',
  `hot` tinyint(1) DEFAULT '0' COMMENT '是否热门',
  `featured` tinyint(1) DEFAULT '0' COMMENT '是否精选',
  `branch_id` bigint DEFAULT NULL COMMENT '门店ID',
  `building_id` bigint DEFAULT NULL COMMENT '楼栋ID',
  `owner_id` bigint DEFAULT NULL COMMENT '发布者用户ID（私有房源）',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_app_id` (`app_id`),
  KEY `idx_app_status` (`app_id`, `publish_status`),
  KEY `idx_app_audit` (`app_id`, `status`),
  KEY `idx_geohash` (`geohash`),
  KEY `idx_city_code` (`city_code`),
  KEY `idx_owner_id` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='房源主表';

-- ============================================================
-- 房源图片关联表
-- ============================================================
DROP TABLE IF EXISTS `property_images`;
CREATE TABLE `property_images` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `property_id` bigint unsigned NOT NULL COMMENT '房源ID',
  `image_id` bigint NOT NULL COMMENT 'image-hub 中的图片ID',
  `is_cover` tinyint(1) DEFAULT '0' COMMENT '是否封面',
  `sort_order` int DEFAULT '0' COMMENT '排序值',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_property_id` (`property_id`),
  KEY `idx_image_id` (`image_id`),
  CONSTRAINT `fk_property_images_property` FOREIGN KEY (`property_id`) REFERENCES `properties`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='房源图片关联';


SET FOREIGN_KEY_CHECKS = 1;