-- ============================================
-- 预约看房消息模板初始化数据
-- 表结构: message_templates
-- ============================================

USE `message_db`;

-- ============================================
-- 邮件模板（EMAIL）
-- ============================================

-- 1. BOOKING_CREATE_NOTIFY - 新预约创建通知（通知经纪人/管理员）
INSERT INTO `message_templates` (`template_code`, `type`, `subject`, `content`, `is_enabled`)
VALUES
    ('BOOKING_CREATE_NOTIFY', 'EMAIL',
     '【XX房产】新预约通知',
     '<html><body>'
     '<h2>新看房预约</h2>'
     '<p>您好，有用户预约看房，请及时处理：</p>'
     '<table border="1" cellpadding="8" cellspacing="0" style="border-collapse:collapse;">'
     '<tr><td>预约ID</td><td>${bookingId}</td></tr>'
     '<tr><td>房源ID</td><td>${propertyId}</td></tr>'
     '<tr><td>用户ID</td><td>${userId}</td></tr>'
     '<tr><td>预约时间</td><td>${appointmentTime}</td></tr>'
     '<tr><td>备注</td><td>${remark}</td></tr>'
     '</table>'
     '<p>请登录管理后台或App进行确认。</p>'
     '</body></html>',
     1);

-- 2. BOOKING_CONFIRM_NOTIFY - 预约确认通知（通知用户）
INSERT INTO `message_templates` (`template_code`, `type`, `subject`, `content`, `is_enabled`)
VALUES
    ('BOOKING_CONFIRM_NOTIFY', 'EMAIL',
     '【XX房产】您的预约已确认',
     '<html><body>'
     '<h2>预约已确认</h2>'
     '<p>您好，您的看房预约已被经纪人确认：</p>'
     '<table border="1" cellpadding="8" cellspacing="0" style="border-collapse:collapse;">'
     '<tr><td>预约ID</td><td>${bookingId}</td></tr>'
     '<tr><td>房源ID</td><td>${propertyId}</td></tr>'
     '<tr><td>预约时间</td><td>${appointmentTime}</td></tr>'
     '<tr><td>经纪人ID</td><td>${agentId}</td></tr>'
     '</table>'
     '<p>请按时到达约定地点，如需修改请提前联系。</p>'
     '</body></html>',
     1);

-- 3. BOOKING_CANCEL_NOTIFY - 预约取消通知（通知经纪人/管理员）
INSERT INTO `message_templates` (`template_code`, `type`, `subject`, `content`, `is_enabled`)
VALUES
    ('BOOKING_CANCEL_NOTIFY', 'EMAIL',
     '【XX房产】预约已取消',
     '<html><body>'
     '<h2>预约已取消</h2>'
     '<p>您好，用户已取消看房预约：</p>'
     '<table border="1" cellpadding="8" cellspacing="0" style="border-collapse:collapse;">'
     '<tr><td>预约ID</td><td>${bookingId}</td></tr>'
     '<tr><td>房源ID</td><td>${propertyId}</td></tr>'
     '<tr><td>用户ID</td><td>${userId}</td></tr>'
     '<tr><td>预约时间</td><td>${appointmentTime}</td></tr>'
     '<tr><td>取消原因</td><td>${cancelReason}</td></tr>'
     '</table>'
     '</body></html>',
     1);

-- 4. BOOKING_REJECT_NOTIFY - 预约拒绝通知（通知用户）
INSERT INTO `message_templates` (`template_code`, `type`, `subject`, `content`, `is_enabled`)
VALUES
    ('BOOKING_REJECT_NOTIFY', 'EMAIL',
     '【XX房产】预约未被接受',
     '<html><body>'
     '<h2>预约通知</h2>'
     '<p>您好，很抱歉您的看房预约未被接受：</p>'
     '<table border="1" cellpadding="8" cellspacing="0" style="border-collapse:collapse;">'
     '<tr><td>预约ID</td><td>${bookingId}</td></tr>'
     '<tr><td>房源ID</td><td>${propertyId}</td></tr>'
     '<tr><td>预约时间</td><td>${appointmentTime}</td></tr>'
     '<tr><td>拒绝原因</td><td>${rejectReason}</td></tr>'
     '</table>'
     '<p>您可以选择其他时间重新预约，或浏览其他房源。</p>'
     '</body></html>',
     1);

-- 5. BOOKING_COMPLETE_NOTIFY - 看房完成通知（通知用户）
INSERT INTO `message_templates` (`template_code`, `type`, `subject`, `content`, `is_enabled`)
VALUES
    ('BOOKING_COMPLETE_NOTIFY', 'EMAIL',
     '【XX房产】看房已完成',
     '<html><body>'
     '<h2>看房已完成</h2>'
     '<p>您好，您的看房预约已标记为完成：</p>'
     '<table border="1" cellpadding="8" cellspacing="0" style="border-collapse:collapse;">'
     '<tr><td>预约ID</td><td>${bookingId}</td></tr>'
     '<tr><td>房源ID</td><td>${propertyId}</td></tr>'
     '<tr><td>预约时间</td><td>${appointmentTime}</td></tr>'
     '</table>'
     '<p>感谢您使用我们的服务，如有反馈请随时联系我们。</p>'
     '</body></html>',
     1);

-- ============================================
-- 验证查询
-- ============================================
-- SELECT id, template_code, type, subject, is_enabled FROM message_templates WHERE template_code LIKE 'BOOKING%' ORDER BY id;
