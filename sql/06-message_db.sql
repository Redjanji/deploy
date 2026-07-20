SET FOREIGN_KEY_CHECKS = 0;

/*
SQLyog Community v13.3.1 (64 bit)
MySQL - 8.4.10-0ubuntu0.26.04.1 : Database - message_db
*********************************************************************
*/

/*!40101 SET NAMES utf8mb4 */;

/*!40101 SET SQL_MODE=''*/;

/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
CREATE DATABASE /*!32312 IF NOT EXISTS*/`message_db` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `message_db`;

/*Table structure for table `message_records` */

DROP TABLE IF EXISTS `message_records`;

CREATE TABLE `message_records` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `app_id` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '来源应用ID',
  `message_type` enum('EMAIL','SMS') COLLATE utf8mb4_general_ci NOT NULL,
  `receiver` varchar(256) COLLATE utf8mb4_general_ci NOT NULL COMMENT '接收地址(邮箱/手机号)',
  `content` text COLLATE utf8mb4_general_ci NOT NULL COMMENT '最终发送内容',
  `status` tinyint DEFAULT '0' COMMENT '0待发送,1发送成功,2发送失败',
  `retry_count` int DEFAULT '0',
  `error_message` text COLLATE utf8mb4_general_ci,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

/*Data for the table `message_records` */

LOCK TABLES `message_records` WRITE;

UNLOCK TABLES;

/*Table structure for table `message_templates` */

DROP TABLE IF EXISTS `message_templates`;

CREATE TABLE `message_templates` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `template_code` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '模板编码',
  `type` enum('EMAIL','SMS') COLLATE utf8mb4_general_ci NOT NULL,
  `subject` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '邮件主题',
  `content` text COLLATE utf8mb4_general_ci NOT NULL COMMENT '模板内容(含占位符)',
  `is_enabled` tinyint(1) DEFAULT '1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `template_code` (`template_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

/*Data for the table `message_templates` */

LOCK TABLES `message_templates` WRITE;

UNLOCK TABLES;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

SET FOREIGN_KEY_CHECKS = 1;

