# analytics-service API 文档

## 一、服务概览

数据统计服务（analytics-service）是整个微服务集群的统一数据统计中心，负责收集和聚合各业务服务的埋点事件数据，提供多维度统计查询能力。服务通过 RabbitMQ 异步接收事件消息，使用 Redis 实现高并发实时计数，定时批量刷入 MySQL 持久化存储。

| 项目 | 值 |
|---|---|
| 服务名称 | analytics-service |
| 服务端口 | 8087 |
| 绑定地址 | 127.0.0.1（通过网关转发） |
| API 路径前缀 | `/api/stats` |
| 服务启动类 | `com.xss.analyticsservice.AnalyticsServiceApplication` |
| MQ Exchange | `analytics.event.exchange` |
| MQ Queue | `analytics.event.queue` |
| MQ Routing Key | `analytics.event` |
| 数据库名 | `analytics_db` |

---

## 二、接口总览表

| 序号 | 接口路径 | HTTP 方法 | 接口名称 | 说明 |
|---|---|---|---|---|
| 1 | `/api/stats/dashboard` | GET | 仪表盘总览 | 获取今日全量统计概览数据 |
| 2 | `/api/stats/property/views` | GET | 房产浏览统计 | 按应用和时间范围查询房产浏览量 |
| 3 | `/api/stats/image/upload-summary` | GET | 图片上传统计 | 查询各应用图片上传数量和存储大小 |
| 4 | `/api/stats/user/actions` | GET | 用户行为统计 | 查询用户行为事件统计数据 |

---

## 三、通用约定

### 3.1 请求头约定

| 请求头 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `X-App-Id` | String | 否 | 应用标识，预留字段，当前接口通过 query 参数传递 appId |
| `Content-Type` | String | 否 | GET 请求无需设置 |

> **说明**：当前版本所有接口的 `appId` 均通过查询参数传递，`X-App-Id` 请求头为预留扩展字段。

### 3.2 数据格式

- 请求格式：`application/x-www-form-urlencoded`（GET 请求使用 Query 参数）
- 响应格式：`application/json;charset=UTF-8`
- 日期格式：`yyyy-MM-dd`
- 时间格式：`yyyy-MM-dd HH:mm:ss`
- 时区：`Asia/Shanghai`

### 3.3 统一响应结构

所有接口返回统一的 `Result<T>` 包装结构：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | int | 状态码，200 表示成功 |
| `message` | String | 状态描述 |
| `data` | T | 响应数据，泛型对象 |

**Result 类定义位置**：`com.xss.analyticsservice.common.Result`

### 3.4 错误码说明

| 错误码 | 说明 | 场景 |
|---|---|---|
| 200 | 成功 | 请求处理成功 |
| 400 | 业务异常 | 参数错误、业务逻辑错误 |
| 500 | 服务内部错误 | 未捕获的系统异常 |

**异常处理类**：`com.xss.analyticsservice.config.GlobalExceptionHandler`

---

## 四、接口详细说明

### 4.1 仪表盘总览

**GET** `/api/stats/dashboard`

获取今日全量统计概览，包含房产浏览、图片上传、用户注册/登录、房产发布等核心指标，以及热门房产 TOP10 和各应用图片存储排行。

#### 请求参数

| 参数名 | 类型 | 必填 | 位置 | 说明 |
|---|---|---|---|---|
| 无 | - | - | - | - |

#### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "today": "2026-07-10",
    "todayPropertyViews": 1250,
    "todayImageUploads": 86,
    "todayUserRegisters": 23,
    "todayUserLogins": 156,
    "todayPropertyCreates": 12,
    "topProperties": [
      {
        "appId": "my-backend-system",
        "propertyId": 1001,
        "statsDate": "2026-07-10",
        "viewCount": 328,
        "uniqueVisitors": 215
      }
    ],
    "appImageSummary": [
      {
        "appId": "my-backend-system",
        "statsDate": "2026-07-10",
        "uploadCount": 86,
        "totalSize": 15728640,
        "totalSizeFormatted": "15.00 MB"
      }
    ]
  }
}
```

#### 响应字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `today` | LocalDate | 统计日期（今日） |
| `todayPropertyViews` | Long | 今日房产浏览总量（PV） |
| `todayImageUploads` | Long | 今日图片上传总量 |
| `todayUserRegisters` | Long | 今日用户注册数 |
| `todayUserLogins` | Long | 今日用户登录数 |
| `todayPropertyCreates` | Long | 今日房产发布数 |
| `topProperties` | List\<PropertyViewStatsVO\> | 热门房产 TOP10（按浏览量降序） |
| `appImageSummary` | List\<ImageUploadSummaryVO\> | 各应用图片上传汇总（按总大小降序） |

**VO 类**：`com.xss.analyticsservice.vo.DashboardSummaryVO`

#### 数据来源说明

- 今日实时指标（`todayPropertyViews`、`todayImageUploads` 等）：从 Redis 实时计数读取
- 热门房产 TOP10：从 MySQL `stats_property_views` 表查询（按浏览量降序取前 10 条）
- 各应用图片汇总：优先从数据库查询，数据库无数据时回退到 Redis

**对应 Service 方法**：`StatsQueryService.getDashboard()`
**对应 Controller 方法**：`StatsController.getDashboard()`

---

### 4.2 房产浏览统计

**GET** `/api/stats/property/views`

按应用和时间范围查询房产浏览量数据，按天聚合，包含浏览量（PV）和独立访客数（UV）。

#### 请求参数

| 参数名 | 类型 | 必填 | 位置 | 说明 |
|---|---|---|---|---|
| `appId` | String | 是 | query | 应用标识 |
| `startDate` | String | 是 | query | 开始日期，格式 `yyyy-MM-dd` |
| `endDate` | String | 否 | query | 结束日期，格式 `yyyy-MM-dd`，默认当天 |

#### 请求示例

```bash
curl "http://localhost:8087/api/stats/property/views?appId=my-backend-system&startDate=2026-07-01&endDate=2026-07-10"
```

#### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "appId": "my-backend-system",
      "propertyId": 1001,
      "statsDate": "2026-07-10",
      "viewCount": 328,
      "uniqueVisitors": 215
    },
    {
      "appId": "my-backend-system",
      "propertyId": 1002,
      "statsDate": "2026-07-10",
      "viewCount": 256,
      "uniqueVisitors": 180
    }
  ]
}
```

#### 响应字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `appId` | String | 应用标识 |
| `propertyId` | Long | 房产 ID |
| `statsDate` | LocalDate | 统计日期 |
| `viewCount` | Long | 浏览量（PV） |
| `uniqueVisitors` | Long | 独立访客数（UV） |

**VO 类**：`com.xss.analyticsservice.vo.PropertyViewStatsVO`

#### 数据来源说明

- 从 MySQL `stats_property_views` 表查询
- 查询条件：`appId` 匹配，`stats_date` 在 `[startDate, endDate]` 范围内，`stats_hour = -1`（日级聚合）
- 排序：按 `stats_date` 降序

**对应 Service 方法**：`StatsQueryService.getPropertyViews()`
**对应 Controller 方法**：`StatsController.getPropertyViews()`

---

### 4.3 图片上传统计

**GET** `/api/stats/image/upload-summary`

查询各应用的图片上传数量和存储大小汇总，仅返回当日数据。

#### 请求参数

| 参数名 | 类型 | 必填 | 位置 | 说明 |
|---|---|---|---|---|
| `appId` | String | 否 | query | 应用标识，不传则返回所有应用 |

#### 请求示例

```bash
# 查询指定应用
curl "http://localhost:8087/api/stats/image/upload-summary?appId=my-backend-system"

# 查询所有应用
curl "http://localhost:8087/api/stats/image/upload-summary"
```

#### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "appId": "my-backend-system",
      "statsDate": "2026-07-10",
      "uploadCount": 86,
      "totalSize": 15728640,
      "totalSizeFormatted": "15.00 MB"
    }
  ]
}
```

#### 响应字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `appId` | String | 应用标识 |
| `statsDate` | LocalDate | 统计日期 |
| `uploadCount` | Long | 上传图片数量（张） |
| `totalSize` | Long | 总存储大小（字节） |
| `totalSizeFormatted` | String | 格式化后的大小（如 "15.00 MB"） |

**VO 类**：`com.xss.analyticsservice.vo.ImageUploadSummaryVO`

#### 数据来源说明

- **优先查询 MySQL**：从 `stats_image_uploads` 表查询当日数据
- **数据库为空时回退到 Redis**：扫描 Redis 中 `stats:image:upload:*:{date}:count` 模式的键，实时计算
- `totalSizeFormatted` 由服务端格式化，单位自动换算（B / KB / MB / GB / TB）

**对应 Service 方法**：`StatsQueryService.getImageUploadSummary()`
**对应 Controller 方法**：`StatsController.getImageUploadSummary()`

---

### 4.4 用户行为统计

**GET** `/api/stats/user/actions`

查询用户行为事件的统计数据，支持按应用、事件类型、时间范围筛选。

#### 请求参数

| 参数名 | 类型 | 必填 | 位置 | 说明 |
|---|---|---|---|---|
| `appId` | String | 否 | query | 应用标识 |
| `eventType` | String | 否 | query | 事件类型，见下方事件类型枚举 |
| `startDate` | String | 否 | query | 开始日期，格式 `yyyy-MM-dd`，默认当天 |
| `endDate` | String | 否 | query | 结束日期，格式 `yyyy-MM-dd`，默认当天 |

#### 事件类型枚举

| 事件类型 | 说明 | 触发方服务 |
|---|---|---|
| `PROPERTY_CREATE` | 房产发布 | property-service |
| `IMAGE_DELETE` | 图片删除 | image-service |
| `USER_REGISTER` | 用户注册 | auth-service |
| `USER_LOGIN` | 用户登录 | auth-service |
| `FAVORITE_ADD` | 收藏房产 | 收藏服务（预留） |
| `FAVORITE_REMOVE` | 取消收藏 | 收藏服务（预留） |

> **注意**：`PROPERTY_VIEW` 和 `IMAGE_UPLOAD` 有独立的统计表和查询接口，不在用户行为统计中。

#### 请求示例

```bash
# 查询指定应用、指定事件类型、指定时间范围
curl "http://localhost:8087/api/stats/user/actions?appId=my-backend-system&eventType=USER_LOGIN&startDate=2026-07-01&endDate=2026-07-10"

# 查询所有应用、所有事件类型（默认当天）
curl "http://localhost:8087/api/stats/user/actions"
```

#### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "appId": "my-backend-system",
      "eventType": "USER_LOGIN",
      "statsDate": "2026-07-10",
      "actionCount": 156
    },
    {
      "appId": "my-backend-system",
      "eventType": "USER_LOGIN",
      "statsDate": "2026-07-09",
      "actionCount": 142
    }
  ]
}
```

#### 响应字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `appId` | String | 应用标识 |
| `eventType` | String | 事件类型 |
| `statsDate` | LocalDate | 统计日期 |
| `actionCount` | Long | 行为发生次数 |

**VO 类**：`com.xss.analyticsservice.vo.UserActionStatsVO`

#### 数据来源说明

- 从 MySQL `stats_user_actions` 表查询
- 查询条件：`appId`（可选）、`eventType`（可选）、`stats_date` 在 `[startDate, endDate]` 范围内、`stats_hour = -1`（日级聚合）
- 排序：按 `stats_date` 降序

**对应 Service 方法**：`StatsQueryService.getUserActions()`
**对应 Controller 方法**：`StatsController.getUserActions()`

---

## 五、统计数据说明

### 5.1 指标含义

| 指标名称 | 英文 | 说明 | 统计口径 |
|---|---|---|---|
| 浏览量 | PV (Page View) | 房产详情页被访问的次数 | 每次访问计 1 次，同一用户多次访问累计 |
| 独立访客数 | UV (Unique Visitor) | 访问房产详情页的不同用户数 | 按 userId 去重，同一用户多次访问只计 1 次 |
| 上传数量 | Upload Count | 图片上传的张数 | 每次成功上传计 1 张 |
| 存储大小 | Total Size | 图片占用的存储空间 | 字节为单位，自动格式化显示 |
| 行为次数 | Action Count | 特定用户行为发生的次数 | 每次事件计 1 次 |

### 5.2 统计口径

- **时间粒度**：日级聚合（`stats_hour = -1`），表结构预留了小时级字段但当前未使用
- **统计日期**：以服务端本地日期（Asia/Shanghai）为准，事件消费时取当天日期
- **数据延迟**：
  - Redis 实时计数：秒级延迟（事件消费后立即更新）
  - MySQL 持久化数据：最多 5 分钟延迟（定时刷入周期）
- **数据来源优先级**：
  - 历史数据（非当日）：完全从 MySQL 查询
  - 当日数据：仪表盘接口优先从 Redis 读取实时值，图片上传接口优先查 MySQL 再回退 Redis

### 5.3 UV 去重方案

房产浏览的 UV 统计使用 **Redis HyperLogLog** 数据结构：

- **优点**：内存占用极低（每个 key 约 12KB），适合高基数去重
- **精度**：标准误差约 0.81%，对于 UV 统计足够准确
- **实现**：`redisTemplate.opsForHyperLogLog().add(uvKey, userId.toString())`
- **Key 格式**：`stats:property:uv:{appId}:{propertyId}:{date}`

### 5.4 实时计数实现

| 统计类型 | Redis 数据结构 | 原子操作 | Key 格式 |
|---|---|---|---|
| 房产浏览量 | Hash | `HINCRBY` | `stats:property:view:{appId}:{date}`，field 为 `{propertyId}:count` |
| 房产 UV | HyperLogLog | `PFADD` | `stats:property:uv:{appId}:{propertyId}:{date}` |
| 图片上传数量 | String | `INCR` | `stats:image:upload:{appId}:{date}:count` |
| 图片上传大小 | String | `INCRBY` | `stats:image:upload:{appId}:{date}:size` |
| 用户行为计数 | String | `INCR` | `stats:user:{eventType}:{appId}:{date}` |
