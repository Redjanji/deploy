SET FOREIGN_KEY_CHECKS = 0;

/*
SQLyog Community v13.3.1 (64 bit)
MySQL - 8.4.10-0ubuntu0.26.04.1 : Database - image_db
*********************************************************************
*/

/*!40101 SET NAMES utf8mb4 */;

/*!40101 SET SQL_MODE=''*/;

/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
CREATE DATABASE /*!32312 IF NOT EXISTS*/`image_db` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `image_db`;

/*Table structure for table `image_group_items` */

DROP TABLE IF EXISTS `image_group_items`;

CREATE TABLE `image_group_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `group_id` bigint NOT NULL,
  `image_id` bigint NOT NULL,
  `sort_order` int DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_image` (`group_id`,`image_id`),
  KEY `image_id` (`image_id`),
  CONSTRAINT `image_group_items_ibfk_1` FOREIGN KEY (`group_id`) REFERENCES `image_groups` (`id`) ON DELETE CASCADE,
  CONSTRAINT `image_group_items_ibfk_2` FOREIGN KEY (`image_id`) REFERENCES `images` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

/*Data for the table `image_group_items` */

LOCK TABLES `image_group_items` WRITE;

insert  into `image_group_items`(`id`,`group_id`,`image_id`,`sort_order`) values 
(1,3,3,1),
(2,4,4,1),
(3,5,5,1),
(4,6,7,1);

UNLOCK TABLES;

/*Table structure for table `image_groups` */

DROP TABLE IF EXISTS `image_groups`;

CREATE TABLE `image_groups` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `app_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL,
  `NAME` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '分组名称（如“首页轮播”）',
  `DESCRIPTION` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `sort_order` int DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_name` (`app_id`,`NAME`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

/*Data for the table `image_groups` */

LOCK TABLES `image_groups` WRITE;

insert  into `image_groups`(`id`,`app_id`,`NAME`,`DESCRIPTION`,`sort_order`,`created_at`) values 
(1,'test-app','????','?????',0,'2026-07-07 02:19:10'),
(3,'test-app','????_20260707022212','?????',0,'2026-07-07 02:22:12'),
(4,'test-app','????_20260707022514','?????',0,'2026-07-07 02:25:14'),
(5,'test-app','????_20260707024626','?????',0,'2026-07-07 02:46:26'),
(6,'test-app','????_20260707024707','?????',0,'2026-07-07 02:47:07');

UNLOCK TABLES;

/*Table structure for table `images` */

DROP TABLE IF EXISTS `images`;

CREATE TABLE `images` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `app_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '应用标识',
  `origin_key` varchar(256) COLLATE utf8mb4_general_ci NOT NULL COMMENT '原图存储key',
  `large_key` varchar(256) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '大图key(1280px)',
  `medium_key` varchar(256) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '中图key(640px)',
  `small_key` varchar(256) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '小图key(200px)',
  `width` int DEFAULT NULL COMMENT '原图宽',
  `height` int DEFAULT NULL COMMENT '原图高',
  `file_size` bigint DEFAULT NULL COMMENT '原图大小(字节)',
  `mime_type` varchar(32) COLLATE utf8mb4_general_ci DEFAULT 'image/webp',
  `STATUS` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'READY' COMMENT 'READY,DELETED',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `owner_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_app_id` (`app_id`),
  KEY `idx_status` (`app_id`,`STATUS`),
  KEY `idx_owner_id` (`owner_id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

/*Data for the table `images` */

LOCK TABLES `images` WRITE;

insert  into `images`(`id`,`app_id`,`origin_key`,`large_key`,`medium_key`,`small_key`,`width`,`height`,`file_size`,`mime_type`,`STATUS`,`created_at`,`owner_id`) values 
(1,'test-app','test-app/20260707/original/c98e0307-bdff-48b5-bd67-9665cf7cab78.webp','test-app/20260707/large/a8ea40e2-c971-4a2a-9233-7d400fbb7776.webp','test-app/20260707/medium/81f6918c-7cd1-4800-8b31-92d72c222e54.webp','test-app/20260707/small/93bad655-de92-4dee-b89b-866b0ed3749e.webp',100,100,110,'image/webp','READY','2026-07-07 02:18:04',NULL),
(2,'test-app','test-app/20260707/original/c668a309-ccff-41c3-995e-23bea6533db6.webp','test-app/20260707/large/d4f4c3b7-3b4d-4b63-9508-f8656adbd921.webp','test-app/20260707/medium/6dee518b-65f2-49d2-8981-2bc2d709f7db.webp','test-app/20260707/small/05dc42c9-2db2-478d-bd4b-2bf5597e191f.webp',1,1,68,'image/webp','DELETED','2026-07-07 02:21:10',NULL),
(3,'test-app','test-app/20260707/original/cb55b008-3673-4931-9db4-ff3fb45744ce.webp','test-app/20260707/large/af93831b-715d-4d08-be58-efbd81b64f49.webp','test-app/20260707/medium/644c2f91-a979-4f9b-b33c-ea8f9729d84a.webp','test-app/20260707/small/2ec9725b-b44b-4bd2-b77e-881906d786ad.webp',1,1,68,'image/webp','DELETED','2026-07-07 02:22:13',NULL),
(4,'test-app','test-app/20260707/original/4042c1fd-44d2-4553-8bc4-c17b728f5084.webp','test-app/20260707/large/6d403e2b-5ef6-403f-a4b2-55885823bd2e.webp','test-app/20260707/medium/72f07c16-7b3e-4749-b6b3-4566f74f5606.webp','test-app/20260707/small/b906018b-04ad-4d3e-8d70-44aa8220712e.webp',1,1,68,'image/webp','DELETED','2026-07-07 02:25:15',NULL),
(5,'test-app','test-app/20260707/original/5fcad809-4a3e-4f30-abef-61aad807ad92.webp','test-app/20260707/large/30361a7c-578c-4fbf-8b81-264d51b95777.webp','test-app/20260707/medium/a6333d9f-2bce-4cce-a412-9bc37ff91ee3.webp','test-app/20260707/small/fa532de8-8bd3-4a51-81f6-a3a1daaed221.webp',1,1,68,'image/webp','DELETED','2026-07-07 02:46:27',NULL),
(6,'test-app','test-app/20260707/original/470d3e32-2466-49ad-bc57-fc91218e7f6c.webp','test-app/20260707/large/5fee5d95-85f6-4999-a482-941c8d4965c9.webp','test-app/20260707/medium/9154de79-ff8b-46a1-873a-624e8af01ab1.webp','test-app/20260707/small/f419ea2b-fe30-4be2-a6db-f0bba7d62b72.webp',1,1,68,'image/webp','DELETED','2026-07-07 02:46:27',1001),
(7,'test-app','test-app/20260707/original/598a6218-6519-4911-99cf-6f7e5fad8fda.webp','test-app/20260707/large/da4685b7-3bbd-48a3-b509-4a42cbe99e1d.webp','test-app/20260707/medium/f1328721-9f7a-41e6-bb63-2d9737f8329c.webp','test-app/20260707/small/3d463113-6146-4583-ae0e-852618e0abc6.webp',1,1,68,'image/webp','DELETED','2026-07-07 02:47:08',NULL),
(8,'test-app','test-app/20260707/original/89650676-48e2-4524-8f7c-05ccb10ea5f2.webp','test-app/20260707/large/1dac23fb-5d5b-4ea2-b08a-b518e6a1d021.webp','test-app/20260707/medium/74505e43-d76e-46ff-a09e-ef32ace1dc85.webp','test-app/20260707/small/05e7d895-39a2-49a0-8470-abb1f4f1926e.webp',1,1,68,'image/webp','DELETED','2026-07-07 02:47:08',1001);

UNLOCK TABLES;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

SET FOREIGN_KEY_CHECKS = 1;

