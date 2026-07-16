# P0 级问题实施计划：配置外部化 + analytics 事件对接

## Context（背景）

根据《微服务集群问题盘点与待实现清单.md》，P0 级问题有两项：
1. **配置外部化**：8 个服务存在密码/密钥/凭证/WSL IP 硬编码（🔴 高优先级），目标是统一改成 `${VAR:default}` 形式。弱凭证（JWT secret、appSecret）默认值置空（用户已确认），WSL IP 保留为开发默认值。
2. **analytics 事件对接完全缺失**：analytics-service 已实现 8 种事件消费，但 property/image/auth 三个业务服务没有任何发送统计事件的代码，导致统计链路形同虚设。

用户额外要求：生成 `.env.example` 环境变量清单 + 一份"开发/生产环境切换说明书"（用户表示不会切换环境，需详细说明）。

## Part 1：配置外部化（8 服务）

### 统一环境变量命名（沿用 analytics/dict 现有风格）

| 类别 | 变量名 | 默认值（开发） | 说明 |
| --- | --- | --- | --- |
| MySQL | MYSQL_HOST | 172.24.35.23 | 嵌入 URL: `jdbc:mysql://${MYSQL_HOST:172.24.35.23}:3306/xxx_db?...` |
| MySQL | MYSQL_USER / MYSQL_PASS | root / root | |
| Redis | REDIS_HOST / REDIS_PASSWORD | 172.24.35.23 / redisroot | |
| RabbitMQ | RABBITMQ_HOST / RABBITMQ_USER / RABBITMQ_PASS | 172.24.35.23 / guest / guest | |
| MinIO | MINIO_ENDPOINT / MINIO_ACCESS_KEY / MINIO_SECRET_KEY | http://172.24.35.23:9000 / minioadmin / minioadmin | |
| SMTP | MAIL_USERNAME / MAIL_PASSWORD | 753280878@qq.com / ufhravxbjladbeig | |
| ES | ES_URIS | http://172.24.35.23:9200 | |
| JWT | JWT_SECRET | **空**（置空） | gateway + auth 必须一致 |
| App 凭证 | APP_SECRET_BACKEND / APP_SECRET_DASHBOARD | **空**（置空） | |

### 各服务修改清单

**auth-service/src/main/resources/application.yaml**（改动最密集）
- 数据库 URL 的 host → `${MYSQL_HOST:172.24.35.23}`；username/password → `${MYSQL_USER:root}` / `${MYSQL_PASS:root}`
- Redis host/password → `${REDIS_HOST:172.24.35.23}` / `${REDIS_PASSWORD:redisroot}`
- JWT secret-base64 → `${JWT_SECRET:}`（置空）
- 新增 `server.address: 127.0.0.1`（当前只有 port，文档指出未绑定本机）
- 新增 RabbitMQ 配置块（host/port/username/password 用 `${}`）— 为发 stats 事件
- 新增 analytics 配置块：`analytics.exchange: analytics.event.exchange` + `analytics.routing-key: analytics.event`

**image-service/src/main/resources/application.yaml**
- 数据库 URL/username/password → `${}`
- minio.endpoint/access-key/secret-key → `${MINIO_ENDPOINT:...}` 等
- 新增 RabbitMQ 配置块 + analytics 配置块

**property-service/src/main/resources/application.yaml**
- 数据库 URL host → `${MYSQL_HOST:172.24.35.23}`（username/password 已 ${}）
- RabbitMQ host/username/password → `${}`
- 新增 analytics 配置块

**search-service/src/main/resources/application.yaml**
- ES uris → `${ES_URIS:http://172.24.35.23:9200}`
- RabbitMQ host/username/password → `${}`

**message-service/src/main/resources/application.yaml**
- RabbitMQ host/username/password → `${}`
- mail username/password → `${MAIL_USERNAME:...}` / `${MAIL_PASSWORD:...}`
- 数据库 URL/username/password → `${}`

**gateway-service/src/main/resources/application.yaml**
- JWT secret-base64 → `${JWT_SECRET:}`（置空）
- appSecret → `${APP_SECRET_BACKEND:}` / `${APP_SECRET_DASHBOARD:}`（置空）

**analytics-service/src/main/resources/application.yaml**
- 数据库 URL host → `${MYSQL_HOST:172.24.35.23}`（其余已 ${}）

**dict-service/src/main/resources/application.yaml**
- 数据库 URL host → `${MYSQL_HOST:172.24.35.23}`（其余已 ${}）

## Part 2：analytics 事件对接（3 服务）

### 事件协议（来自 analytics-service，已确认）
- exchange=`analytics.event.exchange`（DirectExchange），routing-key=`analytics.event`
- 消息体=**JSON 字符串**（StatsEventConsumer 用 ObjectMapper.readValue 反序列化）
- StatsEvent 字段：eventType, appId, userId(Long), targetId(Long), timestamp(Long), extra(Map)
- **项目经验**：跨服务 MQ 必须手动 JSON 序列化，避免 `__TypeId__` 类名不匹配

### 新建组件：StatsEventPublisher（3 服务各一份，代码相同仅 package 不同）

位置：`<service>/src/main/java/com/xss/<service>/mq/StatsEventPublisher.java`

设计要点（复用 property-service 现有 `publishSyncMessage` 范式 PropertyServiceImpl.java:272-283）：
- 注入 `RabbitTemplate` + `ObjectMapper`（Spring 自动配置的 bean，不 new）
- `@Value` 注入 exchange/routing-key
- 用 `ObjectNode` 直接构造 JSON（**不创建 StatsEvent DTO 类**，避免类名不匹配）
- 两个重载：`publish(eventType, appId, userId, targetId)` 和 `publish(..., extra)` 
- try-catch 兜底，失败只 log，不阻断主业务（与 publishSyncMessage 一致）
- publisher 端不声明 exchange bean，依赖 analytics-service 启动时创建

### pom.xml 依赖新增
- auth-service/pom.xml：新增 `spring-boot-starter-amqp`
- image-service/pom.xml：新增 `spring-boot-starter-amqp`
- property-service：已有 ✅

### 注入点改动

**property-service（已有 amqp）**
- 新建 `StatsEventPublisher`（package `com.xss.propertyservice.mq`）
- `PropertyService` 接口新增 `viewDetail(Long id, String appId, Long userId)` 方法
- `PropertyServiceImpl`：
  - 注入 `StatsEventPublisher`
  - `create`（L89 后）发 `PROPERTY_CREATE`：`publish("PROPERTY_CREATE", appId, ownerId, entity.getId())`
  - 新增 `viewDetail` 实现：调 `getDetail(id, appId)` 后发 `PROPERTY_VIEW`：`publish("PROPERTY_VIEW", appId, userId, id)`
  - **关键**：`getDetail` 不改（被 create L92/update L119 复用，避免误触发 view）
- `PropertyController.detail()`（L37-41）：新增 `@RequestHeader X-User-Id`，改调 `viewDetail`

**image-service（需加 amqp）**
- 新建 `StatsEventPublisher`（package `com.xss.imageservice.mq`）
- `ImageService` 注入 `StatsEventPublisher`
- `upload`（L78 return 前）发 `IMAGE_UPLOAD`：`publish("IMAGE_UPLOAD", appId, ownerId, entity.getId(), Map.of("fileSize", entity.getFileSize()))`（consumer 用 fileSize 统计上传字节）
- `delete`（L107 后）发 `IMAGE_DELETE`：`publish("IMAGE_DELETE", appId, ownerId, imageId)`

**auth-service（需加 amqp）**
- 新建 `StatsEventPublisher`（package `com.xss.authservice.mq`）
- `AuthServiceImpl` 注入 `StatsEventPublisher`（@RequiredArgsConstructor 自动注入）
- `register`（L36 insert 后）发 `USER_REGISTER`：`publish("USER_REGISTER", "default", user.getId(), user.getId())`（auth 无 appId 概念，用 "default"；MyBatis-Plus insert 后 id 回填）
- `login`（L46 generateToken 后）发 `USER_LOGIN`：`publish("USER_LOGIN", "default", user.getId(), user.getId())`

## Part 3：文档与配置示例

### 新建 `.env.example`（项目根目录）
列出所有环境变量，按类别分组（MySQL/Redis/RabbitMQ/MinIO/SMTP/ES/JWT/App凭证/可选），标注 `[必须]` 项（JWT_SECRET、APP_SECRET_*），附生成方法。

### 新建 `docs/配置外部化与环境切换说明.md`
大纲：
1. 概述与原则
2. 环境变量完整清单（表格）
3. **开发环境配置**：无需设任何变量（WSL 默认值），唯一例外是 JWT_SECRET 必须设置（否则登录/Token 失败）
4. **生产环境配置**：三种注入方式（系统环境变量 / JVM 启动参数 -D / SPRING_APPLICATION_JSON）
5. **开发→生产切换步骤**：6 步清单
6. 弱凭证生成方法：`openssl rand -base64 32`（JWT）、`openssl rand -hex 32`（AppSecret）；Windows 替代方案
7. 配置项与服务映射关系（排查"某服务启动失败查哪些变量"）
8. 常见问题排查

## 验证方案

### P0-1 配置外部化
- 不设环境变量、仅设 `JWT_SECRET`，启动全部 8 服务 → 全部正常连接 WSL 基础设施
- 设 `MYSQL_HOST=127.0.0.1` 启动某服务 → 连接失败但证明变量被读取
- 不设 `JWT_SECRET` 启动 gateway → 服务能启动（安全失败），但 /token 签发时报 WeakKeyException

### P0-2 analytics 事件对接
前提：analytics-service 已启动（创建 exchange/queue）。
- 创建房源 → analytics 日志输出 `Stats event processed: type=PROPERTY_CREATE`
- GET /api/properties/{id} → 日志输出 PROPERTY_VIEW；Redis `stats:property:view:default:{date}` 有计数
- POST 创建房源 → 日志只有 PROPERTY_CREATE 无 PROPERTY_VIEW（验证 viewDetail 不误触发）
- 上传图片 → IMAGE_UPLOAD；Redis `stats:image:upload:...:count` 递增、`...:size` 累加
- 删除图片 → IMAGE_DELETE
- 注册/登录 → USER_REGISTER / USER_LOGIN
- 等待 5 分钟（cron 刷新）→ analytics_db 有当日数据；GET /api/stats/dashboard 返回统计

### 容错验证
- 停止 analytics-service，创建房源/上传图片/注册登录 → 业务正常完成，publisher 日志报 "Failed to publish stats event" 但不抛异常
- 重启 analytics-service 后再次操作 → 事件正常接收

## 执行顺序

1. **阶段 1**：修改 8 个服务 application.yaml（配置外部化）
2. **阶段 2**：auth-service/pom.xml + image-service/pom.xml 加 amqp 依赖
3. **阶段 3**：3 个服务新建 StatsEventPublisher（代码相同仅 package 不同）
4. **阶段 4**：集成到业务代码（property: 接口+impl+controller；image: ImageService；auth: AuthServiceImpl）
5. **阶段 5**：创建 .env.example + docs/配置外部化与环境切换说明.md
6. **阶段 6**：启动服务验证（设 JWT_SECRET）+ 触发业务验证事件接收 + 容错验证

## 关键风险与注意事项

1. **JWT_SECRET 置空后**：服务能启动（构造器不报错），但运行时签发/验签失败（WeakKeyException）——这是预期的"安全失败"，强制运维设置密钥。开发环境必须设 `JWT_SECRET`。
2. **auth/image 新增 amqp 后**：TCP 连接懒加载（首次 convertAndSend 时才建连），RabbitMQ 未运行不影响服务启动。但 application.yaml 必须配 RabbitMQ host（默认 localhost 会连不上 WSL 的 MQ）。
3. **analytics-service 未启动时**：exchange 不存在，消息丢弃，try-catch 兜底。主业务不受影响。统计是非关键路径，可接受。
4. **@Transactional 与 MQ 时序**：PropertyServiceImpl 类级 @Transactional，stats 事件在事务内发送，事务回滚时消息无法撤回——这是现有 sync/notification 消息已有的行为，stats 遵循相同模式。
5. **getDetail 不改**：避免 create/update 复用时误触发 PROPERTY_VIEW。只有新增的 viewDetail 发事件。
