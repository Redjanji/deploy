-- ============================================
-- 消息模板初始化数据
-- MySQL 8.0 兼容版本
-- ============================================

USE `message_db`;

-- 字段扩展（使用存储过程实现 IF NOT EXISTS，兼容 MySQL 8.0）
DROP PROCEDURE IF EXISTS add_col_if_not_exists;
DELIMITER //
CREATE PROCEDURE add_col_if_not_exists(
    IN p_table VARCHAR(64),
    IN p_col VARCHAR(64),
    IN p_def TEXT
)
BEGIN
    DECLARE col_exists INT DEFAULT 0;
    SELECT COUNT(*) INTO col_exists
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table
      AND COLUMN_NAME = p_col;
    IF col_exists = 0 THEN
        SET @sql = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_col, '` ', p_def);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

CALL add_col_if_not_exists('message_templates', 'description', 'VARCHAR(255) DEFAULT NULL COMMENT ''模板描述（运营可见）'' AFTER `template_code`');
CALL add_col_if_not_exists('message_templates', 'scene', 'VARCHAR(32) DEFAULT NULL COMMENT ''业务场景: LOGIN/REGISTER/AUDIT/ORDER/RESET'' AFTER `type`');
CALL add_col_if_not_exists('message_templates', 'content_type', 'VARCHAR(16) DEFAULT ''TEXT'' COMMENT ''内容类型: TEXT/HTML（仅邮件有效）'' AFTER `content`');
CALL add_col_if_not_exists('message_templates', 'provider_template_id', 'VARCHAR(64) DEFAULT NULL COMMENT ''第三方平台模板ID'' AFTER `content`');
CALL add_col_if_not_exists('message_templates', 'created_at', 'TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间'' AFTER `is_enabled`');
CALL add_col_if_not_exists('message_templates', 'updated_at', 'TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'' AFTER `created_at`');

DROP PROCEDURE IF EXISTS add_col_if_not_exists;

-- ============================================
-- 邮件模板（EMAIL）
-- ============================================

-- 1. PROPERTY_CREATE_NOTIFY
INSERT INTO `message_templates` (`template_code`, `description`, `type`, `scene`, `subject`, `content`, `content_type`, `is_enabled`)
VALUES ('PROPERTY_CREATE_NOTIFY', '新房源创建时通知管理员', 'EMAIL', 'AUDIT', '【XX房产】新房源创建通知 - ${title}', '<html><body><h2>新房源创建通知</h2><p>您好，有新房源已创建，请及时审核：</p><table border="1" cellpadding="8" cellspacing="0" style="border-collapse:collapse;"><tr><td>房源ID</td><td>${propertyId}</td></tr><tr><td>房源标题</td><td>${title}</td></tr><tr><td>价格</td><td>￥${price}</td></tr><tr><td>创建时间</td><td>${createTime}</td></tr></table><p>请登录管理后台进行审核。</p></body></html>', 'HTML', 1)
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`), `scene` = VALUES(`scene`), `subject` = VALUES(`subject`), `content` = VALUES(`content`), `content_type` = VALUES(`content_type`), `updated_at` = CURRENT_TIMESTAMP;

-- 2. PROPERTY_AUDIT_NOTIFY
INSERT INTO `message_templates` (`template_code`, `description`, `type`, `scene`, `subject`, `content`, `content_type`, `is_enabled`)
VALUES ('PROPERTY_AUDIT_NOTIFY', '房源审核结果通知', 'EMAIL', 'AUDIT', '【XX房产】房源审核结果 - ${title}', '<html><body><h2>房源审核结果通知</h2><p>您的房源审核结果如下：</p><table border="1" cellpadding="8" cellspacing="0" style="border-collapse:collapse;"><tr><td>房源ID</td><td>${propertyId}</td></tr><tr><td>房源标题</td><td>${title}</td></tr><tr><td>价格</td><td>￥${price}</td></tr><tr><td>审核结果</td><td style="color:${resultColor};font-weight:bold;">${auditResult}</td></tr></table><p>如有疑问，请联系客服。</p></body></html>', 'HTML', 1)
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`), `scene` = VALUES(`scene`), `subject` = VALUES(`subject`), `content` = VALUES(`content`), `content_type` = VALUES(`content_type`), `updated_at` = CURRENT_TIMESTAMP;

-- 3. USER_REGISTER_WELCOME
INSERT IGNORE INTO `message_templates` (`template_code`, `description`, `type`, `scene`, `subject`, `content`, `content_type`, `is_enabled`)
VALUES ('USER_REGISTER_WELCOME', '用户注册成功后的欢迎邮件', 'EMAIL', 'REGISTER', '【XX房产】欢迎注册，${username}！', '<html><body><h2>欢迎加入 XX房产！</h2><p>亲爱的 <strong>${username}</strong>：</p><p>感谢您注册 XX房产，您的账号已创建成功！</p><p>您可以立即开始：</p><ul><li>浏览海量真实房源</li><li>发布您的房源信息</li><li>收藏心仪房源</li></ul><p>如有任何问题，请随时联系客服。</p><p style="color:#999;font-size:12px;">此邮件由系统自动发送，请勿回复。</p></body></html>', 'HTML', 1);

-- 4. USER_PASSWORD_RESET
INSERT IGNORE INTO `message_templates` (`template_code`, `description`, `type`, `scene`, `subject`, `content`, `content_type`, `is_enabled`)
VALUES ('USER_PASSWORD_RESET', '密码重置通知邮件', 'EMAIL', 'RESET', '【XX房产】您的密码已重置', '<html><body><h2>密码重置通知</h2><p>亲爱的 <strong>${username}</strong>：</p><p>您的账号密码已于 ${resetTime} 重置。</p><p>如果此操作不是您本人发起，请立即联系客服处理。</p><p style="color:#999;font-size:12px;">此邮件由系统自动发送，请勿回复。</p></body></html>', 'HTML', 1);

-- ============================================
-- 短信模板（SMS）
-- ============================================

-- 5. USER_VERIFY_CODE
INSERT IGNORE INTO `message_templates` (`template_code`, `description`, `type`, `scene`, `subject`, `content`, `content_type`, `provider_template_id`, `is_enabled`)
VALUES ('USER_VERIFY_CODE', '用户登录/注册验证码', 'SMS', 'LOGIN', NULL, '【XX房产】您的验证码为${code}，${expireMinutes}分钟内有效，请勿告知他人。', 'TEXT', 'SMS_123456789', 1);

-- 6. PROPERTY_AUDIT_NOTIFY_SMS
INSERT IGNORE INTO `message_templates` (`template_code`, `description`, `type`, `scene`, `subject`, `content`, `content_type`, `provider_template_id`, `is_enabled`)
VALUES ('PROPERTY_AUDIT_NOTIFY_SMS', '房源审核结果短信通知', 'SMS', 'AUDIT', NULL, '【XX房产】您的房源${title}审核${auditResult}。详见 App。', 'TEXT', 'SMS_234567890', 1);

-- 7. ORDER_CONFIRM
INSERT IGNORE INTO `message_templates` (`template_code`, `description`, `type`, `scene`, `subject`, `content`, `content_type`, `provider_template_id`, `is_enabled`)
VALUES ('ORDER_CONFIRM', '订单确认通知', 'SMS', 'ORDER', NULL, '【XX房产】您的订单${orderId}已确认，金额${amount}元。详见 App。', 'TEXT', 'SMS_345678901', 1);
