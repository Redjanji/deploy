# XSS 微服务系统 - Code Wiki

> 基于 Spring Boot 3.x + Spring Cloud Alibaba 的房地产微服务架构系统完整技术文档

---

## 一、项目概述

XSS（Xiao Sheng Shi）是一个面向房地产行业的微服务系统，提供房源管理、用户认证、图片存储、搜索推荐、预约看房、消息通知、数据统计等完整业务闭环。系统采用 Spring Cloud Alibaba 微服务架构，支持服务发现、配置中心、限流熔断和分布式链路追踪。

### 1.1 核心特性

| 特性 | 说明 |
|------|------|
| 统一网关入口 | Gateway 统一鉴权、路由、IP 白名单、限流熔断 |
| 双 Token 体系 | 应用 Token（HMAC 签名）+ 用户 Token（JWT） |
| 服务自动发现 | 基于 Nacos 的服务注册与发现 |
| 配置中心化 | 基于 Nacos Config 的远程配置管理 |
| 事件驱动 | 基于 RabbitMQ 的跨服务异步通信 |
| 对象存储 | 基于 MinIO 的分布式图片存储 |
| 全文检索 | 基于 Elasticsearch 的房源搜索 |
| 链路追踪 | 基于 Micrometer + Zipkin 的分布式追踪 |

---

## 二、整体架构

### 2.1 系统架构图

```
┌─────────────────────────────────────────────────────────────┐
│                        客户端层                              │
│         (Web App / 小程序 / 管理后台 / 第三方系统)              │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│                      Nginx 反向代理                           │
│              (静态资源 / 负载均衡 / HTTPS 终结)                │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│                     Gateway-Service                         │
│  ┌─────────────┐ ┌─────────────┐ ┌───────────────────────┐  │
│  │ IP白名单过滤 │ │ JWT认证过滤  │ │    Sentinel 限流熔断   │  │
│  └─────────────┘ └─────────────┘ └───────────────────────┘  │
│  ┌────────────────────────────────────────────────────────┐  │
│  │              请求转发 (RestTemplate)                    │  │
│  │  透明转发原始字节流 / 保留 Content-Type / 注入用户头      │  │
│  └────────────────────────────────────────────────────────┘  │
└──────────┬──────────┬──────────┬──────────┬─────────────────┘
           │          │          │          │
    ┌──────▼───┐ ┌────▼─────┐ ┌──▼─────┐ ┌─▼────────┐
    │  Auth    │ │  Dict    │ │Property│ │  Image   │
    │ -Service │ │ -Service │ │-Service│ │ -Service │
    └────┬─────┘ └────┬─────┘ └───┬────┘ └────┬─────┘
         │            │           │           │
    ┌────▼─────┐ ┌────▼─────┐ ┌──▼─────┐ ┌──▼─────┐
    │  Search  │ │Analytics │ │ Message│ │Booking │
    │ -Service │ │ -Service │ │-Service│ │-Service│
    └────┬─────┘ └────┬─────┘ └───┬────┘ └──┬─────┘
         │            │           │         │
    ┌────▼─────┐ ┌────▼─────┐ ┌──▼─────┐
    │ Favorite │ │  Review  │ │  MQ    │
    │ -Service │ │ -Service │ │RabbitMQ│
    └──────────┘ └──────────┘ └───┬────┘
                                  │
┌─────────────────────────────────▼───────────────────────────┐
│                      基础设施层                              │
│  ┌────────┐ ┌────────┐ ┌──────────┐ ┌──────────────┐       │
│  │ MySQL  │ │ Redis  │ │Elasticsearch│ │   MinIO    │       │
│  │(9 DB)  │ │(缓存)  │ │(全文检索)  │ │(对象存储)  │       │
│  └────────┘ └────────┘ └──────────┘ └──────────────┘       │
│  ┌─────────────────────────────────────────────────────┐    │
│  │              Nacos (服务发现 + 配置中心)               │    │
│  └─────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 服务清单

| 服务名 | 端口 | 职责 | 数据库 | 中间件依赖 |
|--------|------|------|--------|-----------|
| **gateway-service** | 8080 | 统一入口、鉴权、路由、限流 | 无 | Nacos, Redis |
| **dict-service** | 8081 | 字典数据（行政区划/国家/货币/语言/时区） | dict_db | MySQL, Redis, Nacos |
| **image-service** | 8082 | 图片上传、存储、处理、分组 | image_db | MySQL, MinIO, RabbitMQ, Nacos |
| **auth-service** | 8083 | 用户注册、登录、JWT签发、Token黑名单 | auth_db | MySQL, Redis, RabbitMQ, Nacos |
| **property-service** | 8085 | 房源CRUD、搜索、上下架、审核 | property_db | MySQL, RabbitMQ, Nacos |
| **search-service** | 8086 | 房源全文检索、地图找房、聚合统计 | 无 | Elasticsearch, RabbitMQ, Nacos |
| **analytics-service** | 8087 | 业务统计、数据报表、趋势分析 | analytics_db | MySQL, Redis, RabbitMQ, Nacos |
| **message-service** | 8088 | 消息通知（邮件/短信）、模板管理 | message_db | MySQL, RabbitMQ, Nacos |
| **favorite-service** | 8089 | 用户收藏、收藏列表管理 | favorite_db | MySQL, Nacos |
| **review-service** | 8091 | 房源审核、审核任务管理 | review_db | MySQL, RabbitMQ, Nacos |
| **booking-service** | 8092 | 预约看房、预约记录管理 | booking_db | MySQL, RabbitMQ, Nacos |

---

## 三、技术栈

### 3.1 核心技术

| 分类 | 技术 | 版本 | 用途 |
|------|------|------|------|
| 语言 | Java | 21 | 运行时 |
| 框架 | Spring Boot | 3.3.1 | 基础框架 |
| 微服务 | Spring Cloud | 2023.0.0 | 微服务基础设施 |
| 微服务 | Spring Cloud Alibaba | 2023.0.1.0 | 阿里生态集成 |
| 服务发现 | Nacos | 2.3.0 | 注册中心 + 配置中心 |
| 限流熔断 | Sentinel | 1.8.7 | 流量控制 |
| ORM | MyBatis-Plus | 3.5.7 | 数据访问层 |
| 搜索引擎 | Elasticsearch | 8.13.x | 全文检索 |
| 消息队列 | RabbitMQ | 3.13.x | 异步消息 |
| 对象存储 | MinIO | 9.x | 图片存储 |
| 缓存 | Redis | 7.x | 分布式缓存 |
| 认证 | JWT (jjwt) | 0.12.6 | Token 签发与验证 |
| 链路追踪 | Micrometer + Zipkin | - | 分布式追踪 |
| 定时任务 | ShedLock | 5.10.0 | 分布式锁 |

### 3.2 构建与部署

| 工具 | 用途 |
|------|------|
| Maven | 项目构建 |
| Docker | 容器化 |
| Docker Compose | 单机编排（profiles 分批启动） |
| Nginx | 反向代理 |

---

## 四、模块详细说明

### 4.1 Gateway-Service（网关服务）

**定位**：系统统一入口，所有外部请求必须经过网关。

**核心功能**：
- **Token 签发**：`/token` 接口基于 HMAC-SHA256 签名的应用认证
- **JWT 认证**：解析 `Authorization: Bearer <token>` 头部，注入 `X-User-Id` / `X-App-Id`
- **IP 白名单**：支持 IPv4/IPv6 + CIDR 段（生产环境前置过滤）
- **请求转发**：透明转发原始字节流，保留 Content-Type（含 multipart boundary）
- **Sentinel 限流熔断**：基于 `METHOD:URI` 资源名的精确限流

**关键类**：

| 类 | 包路径 | 职责 |
|----|--------|------|
| `TokenController` | `controller` | 应用 Token 签发，HMAC 签名验证 |
| `ForwardingController` | `controller` | 通用请求转发，匹配 `resource.routes` 配置 |
| `JwtAuthenticationFilter` | `filter` | JWT 解析，注入用户/应用头 |
| `IpWhitelistFilter` | `filter` | IP 白名单校验（支持 CIDR） |
| `SentinelLoggingFilter` | `filter` | Sentinel Span 生命周期日志 |
| `JwtUtil` | `util` | JWT 生成与解析（支持 HMAC + RSA 双算法） |
| `HmacUtil` | `util` | HMAC-SHA256 签名与常量时间比较 |
| `SentinelFlowRuleConfig` | `config` | 限流/熔断规则初始化（@PostConstruct） |
| `SecurityConfig` | `config` | Spring Security 过滤器链配置 |

**路由配置示例**（application.yaml）：
```yaml
resource:
  routes:
    - prefix: /api/dict
      target: http://dict-service
    - prefix: /api/properties
      target: http://property-service
    - prefix: /api/images
      target: http://image-service
```

---

### 4.2 Auth-Service（认证服务）

**定位**：用户身份中心，管理用户生命周期和 Token。

**核心功能**：
- 用户注册（密码 Bcrypt 加密）
- 用户登录（签发用户 JWT）
- Token 刷新与黑名单（Redis 存储）
- 用户信息查询

**关键类**：

| 类 | 包路径 | 职责 |
|----|--------|------|
| `AuthController` | `controller` | `/auth/register`, `/auth/login`, `/auth/logout`, `/auth/refresh` |
| `AuthServiceImpl` | `service.impl` | 注册、登录、用户信息业务逻辑 |
| `JwtTokenProvider` | `security` | 用户 JWT 生成与解析（jjwt 0.12.6） |
| `TokenBlacklistServiceImpl` | `service.impl` | Redis 黑名单管理 |
| `UserEntity` | `entity` | 用户表实体 |

**数据库**：`auth_db` — `users` 表

---

### 4.3 Property-Service（房源服务）

**定位**：核心业务服务，管理房源全生命周期。

**核心功能**：
- 房源 CRUD（创建、查询、更新、删除）
- 房源搜索（MyBatis-Plus 动态 SQL + GeoHash 附近搜索）
- 上下架管理（`publish_status`）
- 审核状态管理（`status`）
- 图片关联（通过 `ImageHubClient` 调用 image-service）
- MQ 事件发布（房源变更同步到 search-service，统计事件到 analytics-service）

**关键类**：

| 类 | 包路径 | 职责 |
|----|--------|------|
| `PropertyController` | `controller` | REST API：房源 CRUD、搜索、状态变更 |
| `PropertyServiceImpl` | `service.impl` | 核心业务逻辑，包含 GeoHash 编码、图片保存、MQ 发布 |
| `PropertyMapper` | `mapper` | MyBatis-Plus 数据访问 |
| `ImageHubClient` | `client` | 通过 RestTemplate 调用 image-service |
| `GeoHashUtil` | `util` | GeoHash 编码与附近网格计算 |
| `StatsEventPublisher` | `mq` | 发送统计事件到 RabbitMQ |

**数据库**：`property_db` — `properties`, `property_images`

---

### 4.4 Dict-Service（字典服务）

**定位**：基础数据服务，提供各类字典和行政区划数据。

**核心功能**：
- 中国行政区划（省/市/区/街道/村五级）
- 国家、货币、语言、时区字典
- 房产类型/装修类型等业务字典（`sys_property_dict_type/item`）
- Redis 缓存加速

**关键类**：

| 类 | 包路径 | 职责 |
|----|--------|------|
| `DictController` | `controller` | 字典项查询接口 |
| `ChinaRegionController` | `controller` | 行政区划接口 |
| `DictService` | `service` | 字典业务逻辑 |
| `ChinaRegionService` | `service` | 行政区划查询 |

**数据库**：`dict_db` — `china_region`, `countries`, `currencies`, `languages`, `timezones`, `sys_property_dict_type`, `sys_property_dict_item`

---

### 4.5 Image-Service（图片服务）

**定位**：图片资源中心，支持上传、存储、格式转换和分组管理。

**核心功能**：
- 图片上传（缩略图生成、WebP 转换）
- MinIO 对象存储
- 图片分组管理
- 本地/ CDN 双 URL 支持
- MIME 类型安全检测（Magic Number）

**关键类**：

| 类 | 包路径 | 职责 |
|----|--------|------|
| `ImageController` | `controller` | 图片上传、获取、删除接口 |
| `ImageGroupController` | `controller` | 图片分组管理 |
| `MinioStorageService` | `service.impl` | MinIO 上传/删除实现 |
| `ImageSecurityChecker` | `security` | Magic Number 文件头校验 |
| `ImageConverter` | `util` | 缩略图/WebP 转换 |

**数据库**：`image_db` — `images`, `image_groups`, `image_group_items`

---

### 4.6 Search-Service（搜索服务）

**定位**：房源搜索引擎，提供全文检索和地图找房。

**核心功能**：
- 房源全文检索（Elasticsearch）
- 地图找房（GeoHash 聚合）
- 监听 RabbitMQ 同步 property-service 的房源变更

**依赖**：Elasticsearch, RabbitMQ, Nacos

---

### 4.7 Analytics-Service（统计服务）

**定位**：业务数据统计中心，消费事件生成报表。

**核心功能**：
- 消费 RabbitMQ 统计事件（`StatsEventConsumer`）
- Redis 实时计数（HyperLogLog UV 计算）
- 定时刷盘（ShedLock 分布式锁保证单实例执行）
- Dashboard 汇总查询

**关键类**：

| 类 | 包路径 | 职责 |
|----|--------|------|
| `StatsEventConsumer` | `consumer` | RabbitMQ 事件消费与 Redis 计数 |
| `StatsFlushService` | `service` | 定时将 Redis 数据刷入 MySQL |
| `StatsQueryService` | `service` | Dashboard 数据查询 |
| `ShedLockConfig` | `config` | 分布式定时任务锁配置 |

**数据库**：`analytics_db` — `stats_property_view`, `stats_user_action`, `stats_image_upload`

---

### 4.8 Message-Service（消息服务）

**定位**：消息通知中心，支持多渠道发送。

**核心功能**：
- 邮件发送（Spring Mail + Thymeleaf 模板）
- 短信发送（预留接口）
- RabbitMQ 消费通知事件
- 消息模板管理
- 消息记录持久化

**关键类**：

| 类 | 包路径 | 职责 |
|----|--------|------|
| `MessageController` | `controller` | 消息记录、模板管理接口 |
| `NotificationConsumer` | `consumer` | RabbitMQ 通知事件消费 |
| `EmailSender` | `sender` | 邮件发送实现 |
| `SmsSender` | `sender` | 短信发送实现 |
| `MessageSender` | `sender` | 发送器接口（策略模式） |

**数据库**：`message_db` — `message_records`, `message_templates`

---

### 4.9 Favorite-Service（收藏服务）

**定位**：用户收藏管理。

**核心功能**：
- 添加/取消收藏（幂等设计）
- 收藏列表查询
- 检查是否已收藏

**关键类**：

| 类 | 包路径 | 职责 |
|----|--------|------|
| `FavoriteController` | `controller` | 收藏 CRUD 接口 |
| `FavoriteServiceImpl` | `service.impl` | 幂等收藏逻辑 |

**数据库**：`favorite_db` — `user_favorites`

---

### 4.10 Review-Service（审核服务）

**定位**：房源内容审核中心。

**核心功能**：
- 审核任务创建与分配
- 人工审核接口
- 审核记录管理

**关键类**：

| 类 | 包路径 | 职责 |
|----|--------|------|
| `ReviewController` | `controller` | `/api/audit/tasks`, `/api/audit/manual` |
| `ReviewService` | `service` | 审核业务逻辑 |

**数据库**：`review_db` — `audit_tasks`, `audit_records`

---

### 4.11 Booking-Service（预约服务）

**定位**：预约看房管理。

**核心功能**：
- 创建预约（校验房源存在性、预约时间）
- 预约状态流转：待确认 → 已确认 → 已完成 / 已取消 / 已拒绝
- 用户/经纪人双视角查询
- MQ 事件发布

**关键类**：

| 类 | 包路径 | 职责 |
|----|--------|------|
| `BookingController` | `controller` | 预约 CRUD、状态变更接口 |
| `BookingServiceImpl` | `service.impl` | 状态机流转、权限校验 |
| `PropertyClient` | `client` | Feign/RestTemplate 调用 property-service |

**数据库**：`booking_db` — `bookings`

---

## 五、关键类与函数详解

### 5.1 网关层

#### `ForwardingController.forward(HttpServletRequest)`
```java
@RequestMapping("/**")
public ResponseEntity<byte[]> forward(HttpServletRequest request)
```
- **职责**：通用请求转发，匹配 `resource.routes` 前缀规则
- **关键逻辑**：
  1. 跳过 `/error`, `/token`, `/config`, `/actuator`
  2. 按配置顺序匹配路由前缀
  3. 透传原始请求头（过滤 `Host`, `Content-Length`, `Transfer-Encoding`）
  4. 注入 `X-User-Id`（仅当 JWT subject 为纯数字时）
  5. 读取原始 body 字节流，保持 Content-Type
  6. 使用 `RestTemplate.exchange()` 转发，返回 `byte[]` 避免编码问题

#### `JwtUtil.parseClaims(String token)`
- **职责**：支持双算法（HS256 + RS256）的 JWT 解析
- **逻辑**：通过 `JwsHeader.getAlgorithm()` 动态选择验证密钥
- **应用 Token**：使用 `jwt.secret-base64` 配置的 HMAC 密钥
- **用户 Token**：使用 `jwt.user-public-key` 配置的 RSA 公钥

#### `IpWhitelistFilter.doFilter()`
- **职责**：生产环境 IP 访问控制
- **支持格式**：
  - 精确 IP：`192.168.1.1`
  - CIDR 段：`192.168.1.0/24`, `::1/128`
- **优先级**：位于 `JwtAuthenticationFilter` 之前，先校验 IP 再验 Token

### 5.2 认证层

#### `AuthServiceImpl.login(LoginRequest)`
```java
public JwtResponse login(LoginRequest request)
```
- 查询用户 → Bcrypt 密码比对 → 生成 JWT → 发送 `USER_LOGIN` 统计事件

#### `JwtTokenProvider.generateToken(Long userId)`
```java
public String generateToken(Long userId)
```
- 使用 jjwt 0.12.6 API：`Jwts.builder().subject(userId.toString()).signWith(key, Jwts.SIG.HS256)`
- Token 类型 claim：`type=user`，issuer=`auth-service`

### 5.3 房源层

#### `PropertyServiceImpl.create(PropertyCreateRequest, String appId, Long ownerId)`
- 校验图片数量上限 → GeoHash 编码 → 插入房源 → 保存图片关联 → 发布 MQ 同步消息 → 发布通知消息 → 发送统计事件

#### `PropertyServiceImpl.search(PropertySearchRequest, String appId)`
- 动态 SQL 拼接：城市、类型、价格区间、关键词、热门/精选标签
- 地图搜索：根据半径选择 GeoHash 精度，计算中心点 + 8 邻域前缀，使用 `likeRight` 匹配

#### `GeoHashUtil`
```java
public static String encode(double lat, double lon, int precision)
public static String[] getAdjacent(String geohash)
public static int selectPrecisionByRadius(double radiusKm)
```

### 5.4 消息层

#### `MessageSender` 策略接口
```java
public interface MessageSender {
    boolean send(String receiver, String subject, String content);
    MessageChannel getChannel();
}
```
- **实现类**：`EmailSender`（EMAIL）、`SmsSender`（SMS）
- **注入方式**：通过 `List<MessageSender>` 手动构建 `Map<MessageChannel, MessageSender>`（避免 Spring Map 注入使用 Bean 名而非枚举值的问题）

### 5.5 统计层

#### `StatsEventConsumer.handleEvent(String message)`
- **事件类型处理**：
  | 事件类型 | 操作 |
  |---------|------|
  | `PROPERTY_VIEW` | Hash 计数 + HyperLogLog UV |
  | `PROPERTY_CREATE` | 用户行为计数 |
  | `IMAGE_UPLOAD` | 上传次数 + 文件大小累加 |
  | `USER_REGISTER/LOGIN` | 用户行为计数 |
  | `FAVORITE_ADD/REMOVE` | 用户行为计数 |

---

## 六、依赖关系

### 6.1 服务间调用关系

```
gateway-service
    ├──→ auth-service (/auth/**)
    ├──→ dict-service (/api/dict/**, /api/provinces, ...)
    ├──→ property-service (/api/properties)
    ├──→ image-service (/api/images, /api/groups)
    ├──→ search-service (/api/search)
    ├──→ analytics-service (/api/stats)
    ├──→ message-service (/api/messages)
    ├──→ favorite-service (/api/favorites)
    ├──→ review-service (/api/audit, /api/reviews)
    └──→ booking-service (/api/bookings)

property-service
    ├──→ image-service (RestTemplate 获取图片URL)
    ├──→ dict-service (Feign 获取字典项)
    └──→ RabbitMQ (发送同步消息、统计事件、通知事件)

booking-service
    └──→ property-service (PropertyClient.exists())

search-service
    ├──→ RabbitMQ (消费房源变更消息)
    └──→ Elasticsearch (索引读写)

analytics-service
    └──→ RabbitMQ (消费统计事件)

message-service
    └──→ RabbitMQ (消费通知事件)
```

### 6.2 MQ 主题与队列

| Exchange | Routing Key | 生产者 | 消费者 | 用途 |
|----------|-------------|--------|--------|------|
| `property.sync.exchange` | `property.sync` | property-service | search-service | 房源数据同步 |
| `analytics.event.exchange` | `analytics.event` | 各服务 | analytics-service | 统计事件收集 |
| `message.send.exchange` | `message.send` | property-service | message-service | 通知消息 |
| `booking.event.exchange` | `booking.event` | booking-service | (待扩展) | 预约状态变更 |

---

## 七、数据库设计概览

### 7.1 数据库清单

| 数据库 | 服务 | 核心表 |
|--------|------|--------|
| `auth_db` | auth-service | `users` |
| `dict_db` | dict-service | `china_region`, `countries`, `currencies`, `languages`, `timezones`, `sys_property_dict_type`, `sys_property_dict_item` |
| `property_db` | property-service | `properties`, `property_images` |
| `image_db` | image-service | `images`, `image_groups`, `image_group_items` |
| `analytics_db` | analytics-service | `stats_property_view`, `stats_user_action`, `stats_image_upload` |
| `message_db` | message-service | `message_records`, `message_templates` |
| `favorite_db` | favorite-service | `user_favorites` |
| `review_db` | review-service | `audit_tasks`, `audit_records` |
| `booking_db` | booking-service | `bookings` |

### 7.2 关键表结构

#### `properties`（房源主表）
| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT PK | 房源ID |
| `app_id` | VARCHAR | 应用隔离 |
| `owner_id` | BIGINT | 发布者ID |
| `title` | VARCHAR | 标题 |
| `price` | BIGINT | 价格（分） |
| `type` | VARCHAR | 房源类型 |
| `city_code` | VARCHAR | 城市编码 |
| `lat` / `lng` | DECIMAL | 经纬度 |
| `geohash` | VARCHAR | GeoHash 编码 |
| `publish_status` | TINYINT | 0草稿/1已发布 |
| `status` | TINYINT | 审核状态 |
| `hot` / `featured` | BOOLEAN | 热门/精选标记 |

#### `bookings`（预约表）
| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT PK | 预约ID |
| `user_id` | BIGINT | 预约用户 |
| `property_id` | BIGINT | 房源ID |
| `agent_id` | BIGINT | 经纪人ID |
| `appointment_time` | DATETIME | 预约时间 |
| `status` | TINYINT | 0待确认/1已确认/2已完成/3用户取消/4经纪人拒绝 |
| `remark` | VARCHAR | 备注 |
| `cancel_reason` | VARCHAR | 取消原因 |

---

## 八、项目运行方式

### 8.1 本地开发环境

**前置条件**：JDK 21+, Maven 3.9+, Docker（基础设施）

```bash
# 1. 启动基础设施（Nacos, MySQL, Redis, RabbitMQ, ES, MinIO）
docker run -d --name nacos -p 8848:8848 -e MODE=standalone nacos/nacos-server:v2.3.0
# ... 其他中间件

# 2. 编译
mvn clean compile

# 3. 按顺序启动服务（每个服务独立终端）
mvn spring-boot:run -pl gateway-service
mvn spring-boot:run -pl auth-service
mvn spring-boot:run -pl property-service
mvn spring-boot:run -pl dict-service
mvn spring-boot:run -pl image-service
mvn spring-boot:run -pl search-service
mvn spring-boot:run -pl analytics-service
mvn spring-boot:run -pl message-service
mvn spring-boot:run -pl favorite-service
mvn spring-boot:run -pl review-service
mvn spring-boot:run -pl booking-service
```

### 8.2 Docker Compose 部署

**启动顺序**（profiles 分批）：

```bash
cd one-click-deployment/deploy

# 第1批：基础设施（等待 healthy）
docker compose --profile infra up -d

# 第2批：核心业务（等待 30s）
docker compose --profile core up -d

# 第3批：其他业务（等待 30s）
docker compose --profile business up -d

# 第4批：Nginx 反向代理
docker compose --profile nginx up -d
```

**资源限制**（4核4GB 服务器）：
- MySQL: 512M
- Elasticsearch: 384M
- Nacos: 512M
- 业务服务: 128M-192M

---

## 九、目录结构

```
xss/
├── pom.xml                              # 根 POM（统一依赖管理）
├── README.md                            # 项目说明
├── CODE_WIKI.md                         # 本 Wiki 文档
├── .env.example                         # 环境变量模板
│
├── docs/                                # 全局文档
│   ├── api-tester.html                  # API 测试页面
│   ├── api-types.ts                     # TypeScript 类型定义
│   └── 新服务适配对接指南.md
│
├── gateway-service/                     # 网关服务
│   ├── src/main/java/com/xss/gatewayservice/
│   │   ├── controller/                  # TokenController, ForwardingController, ConfigController
│   │   ├── filter/                      # JwtAuthenticationFilter, IpWhitelistFilter, SentinelLoggingFilter
│   │   ├── util/                        # JwtUtil, HmacUtil
│   │   └── config/                      # SecurityConfig, SentinelFlowRuleConfig, *Properties
│   ├── src/main/resources/
│   │   ├── application.yaml             # 路由、JWT、IP白名单配置
│   │   └── bootstrap.yaml               # Nacos 配置
│   ├── docs/                            # API文档.md
│   └── pom.xml
│
├── auth-service/                        # 认证服务
│   ├── src/main/java/com/xss/authservice/
│   │   ├── controller/AuthController.java
│   │   ├── security/JwtTokenProvider.java
│   │   ├── service/impl/AuthServiceImpl.java
│   │   └── mq/StatsEventPublisher.java
│   ├── database/auth_db.sql
│   └── docs/API文档.md
│
├── property-service/                    # 房源服务
│   ├── src/main/java/com/xss/propertyservice/
│   │   ├── controller/PropertyController.java
│   │   ├── service/impl/PropertyServiceImpl.java
│   │   ├── client/ImageHubClient.java
│   │   └── util/GeoHashUtil.java
│   ├── database/property_db.sql
│   └── docs/
│
├── dict-service/                        # 字典服务
├── image-service/                       # 图片服务
├── search-service/                      # 搜索服务
├── analytics-service/                   # 统计服务
├── message-service/                     # 消息服务
├── favorite-service/                    # 收藏服务
├── review-service/                      # 审核服务
├── booking-service/                     # 预约服务
│
└── one-click-deployment/                # 一键部署包
    └── deploy/
        ├── docker-compose.yml           # Docker Compose 编排
        ├── full-deploy.sh               # 全自动部署脚本
        ├── init-db.sh                   # 数据库初始化
        ├── nginx/nginx.conf             # Nginx 配置
        └── sql/                         # 全部 SQL 脚本
```

---

## 十、关键设计决策

### 10.1 网关透明转发
- **问题**：Spring Cloud Gateway 解析 multipart 请求导致 boundary 丢失
- **方案**：使用 Servlet `ForwardingController` + `StreamUtils.copyToByteArray()` 直接转发原始字节
- **要求**：Controller 方法不使用 `@RequestParam`, `@RequestPart`, `MultipartFile`

### 10.2 JWT 双算法支持
- **应用 Token**：HMAC-SHA256（网关签发，服务间调用）
- **用户 Token**：HMAC-SHA256（auth-service 签发，用户身份）
- **扩展**：支持 RSA 公钥验证（预留用户 JWT 使用外部认证中心场景）

### 10.3 Sentinel 资源名设计
- **问题**：`@RequestMapping("/api/**")` 无法区分不同接口
- **方案**：自定义过滤器使用 `SphU.entry("METHOD:URI")` 手动限流
- **规则示例**：`GET:/api/properties` QPS=30, `POST:/token` QPS=20

### 10.4 跨服务消息序列化
- **问题**：Spring AMQP 默认使用 `__TypeId__` 类名，消费者类路径不一致导致反序列化失败
- **方案**：生产者使用 `ObjectMapper.writeValueAsString()` 手动序列化为 JSON 字符串，消费者手动反序列化

### 10.5 GeoHash 附近搜索
- **方案**：根据搜索半径选择 GeoHash 精度（1-12），计算中心点 + 8 邻域共 9 个前缀
- **查询**：使用 `LIKE 'geohash%'` 匹配同一网格内房源
- **精度对照**：
  | 半径 | 精度 |
  |------|------|
  | < 1km | 7 |
  | 1-5km | 6 |
  | 5-20km | 5 |
  | 20-100km | 4 |

---

## 十一、环境变量参考

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `NACOS_SERVER_ADDR` | Nacos 地址 | `127.0.0.1:8848` |
| `MYSQL_HOST` | MySQL 主机 | `172.24.35.23` |
| `REDIS_HOST` | Redis 主机 | `172.24.35.23` |
| `RABBITMQ_HOST` | RabbitMQ 主机 | `172.24.35.23` |
| `JWT_SECRET` | JWT 密钥（Base64） | `eW91ci0yNTYtYml0...` |
| `APP_SECRET_BACKEND` | 后端应用密钥 | `dev-secret-key-change-in-production` |
| `IP_WHITELIST_ENABLED` | IP 白名单开关 | `false` |
| `SENTINEL_DASHBOARD` | Sentinel 控制台 | `127.0.0.1:8718` |

---

*文档版本：2025.7.22*
*对应代码版本：0.0.1-SNAPSHOT*
