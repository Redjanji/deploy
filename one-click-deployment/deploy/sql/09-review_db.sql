SET FOREIGN_KEY_CHECKS = 0;

/*
SQLyog Community v13.3.1 (64 bit)
MySQL - 8.4.10-0ubuntu0.26.04.1 : Database - review_db
*********************************************************************
*/

/*!40101 SET NAMES utf8 */;

/*!40101 SET SQL_MODE=''*/;

/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
CREATE DATABASE /*!32312 IF NOT EXISTS*/`review_db` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `review_db`;

/*Table structure for table `audit_records` */

DROP TABLE IF EXISTS `audit_records`;

CREATE TABLE `audit_records` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` bigint NOT NULL,
  `audit_type` varchar(20) DEFAULT NULL COMMENT 'MACHINE / MANUAL',
  `result` tinyint DEFAULT NULL COMMENT '1通过 2拒绝',
  `reason` varchar(500) DEFAULT NULL,
  `auditor_id` bigint DEFAULT NULL COMMENT '审核员ID（人审时）',
  `audit_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `task_id` (`task_id`),
  CONSTRAINT `audit_records_ibfk_1` FOREIGN KEY (`task_id`) REFERENCES `audit_tasks` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审核记录';

/*Data for the table `audit_records` */

LOCK TABLES `audit_records` WRITE;

UNLOCK TABLES;

/*Table structure for table `audit_tasks` */

DROP TABLE IF EXISTS `audit_tasks`;

CREATE TABLE `audit_tasks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `property_id` bigint NOT NULL COMMENT '房源ID',
  `app_id` varchar(32) DEFAULT NULL COMMENT '应用ID',
  `task_type` varchar(20) DEFAULT 'MACHINE' COMMENT 'MACHINE / MANUAL',
  `status` tinyint DEFAULT '0' COMMENT '0待处理 1处理中 2通过 3拒绝 4异常',
  `result_detail` text COMMENT '审核结果详情JSON',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_property_id` (`property_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审核任务';

/*Data for the table `audit_tasks` */

LOCK TABLES `audit_tasks` WRITE;

UNLOCK TABLES;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

SET FOREIGN_KEY_CHECKS = 1;
