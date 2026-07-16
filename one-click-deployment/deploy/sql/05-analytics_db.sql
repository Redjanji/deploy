SET FOREIGN_KEY_CHECKS = 0;

/*
SQLyog Community v13.3.1 (64 bit)
MySQL - 8.4.10-0ubuntu0.26.04.1 : Database - analytics_db
*********************************************************************
*/

/*!40101 SET NAMES utf8 */;

/*!40101 SET SQL_MODE=''*/;

/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
CREATE DATABASE /*!32312 IF NOT EXISTS*/`analytics_db` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `analytics_db`;

/*Table structure for table `stats_image_uploads` */

DROP TABLE IF EXISTS `stats_image_uploads`;

CREATE TABLE `stats_image_uploads` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `app_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL,
  `upload_count` bigint DEFAULT '0',
  `total_size` bigint DEFAULT '0',
  `stats_date` date NOT NULL,
  `stats_hour` tinyint DEFAULT '-1',
  PRIMARY KEY (`id`),
  KEY `idx_app_date` (`app_id`,`stats_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

/*Data for the table `stats_image_uploads` */

LOCK TABLES `stats_image_uploads` WRITE;

UNLOCK TABLES;

/*Table structure for table `stats_property_views` */

DROP TABLE IF EXISTS `stats_property_views`;

CREATE TABLE `stats_property_views` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `app_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL,
  `property_id` bigint NOT NULL,
  `view_count` bigint DEFAULT '0',
  `unique_visitors` bigint DEFAULT '0',
  `stats_date` date NOT NULL,
  `stats_hour` tinyint DEFAULT '-1',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_prop_date_hour` (`app_id`,`property_id`,`stats_date`,`stats_hour`),
  KEY `idx_app_date` (`app_id`,`stats_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

/*Data for the table `stats_property_views` */

LOCK TABLES `stats_property_views` WRITE;

UNLOCK TABLES;

/*Table structure for table `stats_user_actions` */

DROP TABLE IF EXISTS `stats_user_actions`;

CREATE TABLE `stats_user_actions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `app_id` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `event_type` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `action_count` bigint DEFAULT '0',
  `stats_date` date NOT NULL,
  `stats_hour` tinyint DEFAULT '-1',
  PRIMARY KEY (`id`),
  KEY `idx_app_event_date` (`app_id`,`event_type`,`stats_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

/*Data for the table `stats_user_actions` */

LOCK TABLES `stats_user_actions` WRITE;

UNLOCK TABLES;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

SET FOREIGN_KEY_CHECKS = 1;
