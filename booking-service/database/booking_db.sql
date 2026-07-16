/*
SQLyog Community v13.3.1 (64 bit)
MySQL - 8.4.10-0ubuntu0.26.04.1 : Database - booking_db
*********************************************************************
*/

/*!40101 SET NAMES utf8 */;

/*!40101 SET SQL_MODE=''*/;

/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
CREATE DATABASE /*!32312 IF NOT EXISTS*/`booking_db` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `booking_db`;

/*Table structure for table `bookings` */

DROP TABLE IF EXISTS `bookings`;

CREATE TABLE `bookings` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `property_id` bigint NOT NULL COMMENT '房源ID',
  `user_id` bigint NOT NULL COMMENT '预约用户ID',
  `agent_id` bigint DEFAULT NULL COMMENT '经纪人/房东ID',
  `appointment_time` datetime NOT NULL COMMENT '预约看房时间',
  `status` tinyint DEFAULT '0' COMMENT '0待确认 1已确认 2已完成 3已取消 4拒绝',
  `remark` varchar(500) DEFAULT NULL COMMENT '用户留言',
  `cancel_reason` varchar(500) DEFAULT NULL COMMENT '取消原因',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_property` (`property_id`),
  KEY `idx_agent` (`agent_id`),
  KEY `idx_status_time` (`status`,`appointment_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='看房预约';

/*Data for the table `bookings` */

LOCK TABLES `bookings` WRITE;

UNLOCK TABLES;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
