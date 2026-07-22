-- =====================================================
-- XSS 微服务集群 - 数据库初始化脚本
-- =====================================================
-- MySQL 容器启动时会自动执行 /docker-entrypoint-initdb.d/ 下的所有 .sql 文件
-- 本文件创建所有数据库，各服务的建表脚本由对应服务启动时自动执行或手动导入
-- =====================================================

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 认证服务数据库
CREATE DATABASE IF NOT EXISTS `auth_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 字典服务数据库
CREATE DATABASE IF NOT EXISTS `dict_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 图片服务数据库
CREATE DATABASE IF NOT EXISTS `image_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 房源服务数据库
CREATE DATABASE IF NOT EXISTS `property_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 搜索服务数据库（仅存储索引元数据，主要数据在 ES）
CREATE DATABASE IF NOT EXISTS `search_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 统计分析服务数据库
CREATE DATABASE IF NOT EXISTS `analytics_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 消息服务数据库
CREATE DATABASE IF NOT EXISTS `message_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 收藏服务数据库
CREATE DATABASE IF NOT EXISTS `favorite_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 审核服务数据库
CREATE DATABASE IF NOT EXISTS `review_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 预约服务数据库
CREATE DATABASE IF NOT EXISTS `booking_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 授权访问（MySQL 8.0 兼容写法）
CREATE USER IF NOT EXISTS 'root'@'%' IDENTIFIED BY 'root';
GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION;
FLUSH PRIVILEGES;
