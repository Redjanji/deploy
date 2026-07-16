# message-service API 文档

## 1. 服务概览

消息服务（message-service）是微服务集群中的统一消息通知服务，负责处理系统中的各类消息通知，支持邮件、短信（预留）等多种渠道的消息发送。

| 项目 | 值 |
|---|---|
| 服务名称 | message-service |
| 服务端口 | 8088 |
| 绑定地址 | 127.0.0.1 |
| API 路径前缀 | `/api/messages` |
| MQ Exchange | `message.send.exchange` |
| MQ Queue | `message.send.queue` |
| MQ Routing Key | `message.send` |

> **设计原则**：生产环境推荐通过 RabbitMQ 异步发送消息事件（非关键路径，允许延迟）；REST API 主要用于测试、调试、手动触发和管理后台场景。

---

## 2. 接口总览表

| 序号 | 接口名称 | 请求方法 | 路径 | 说明 |
|---|---|---|---|---|
| 1 | 发送测试邮件 | POST | `/api/messages/send-test` | 无需模板，直接发送测试邮件 |
| 2 | 通过模板发送邮件 | POST | `/api/messages/send` | 根据模板编码渲染并发送邮件 |
| 3 | 消息记录列表（分页） | GET | `/api/messages/records` | 分页查询消息发送记录 |
| 4 | 消息记录详情 | GET | `/api/messages/records/{id}` | 根据 ID 获取单条消息记录详情 |
| 5 | 消息重试 | POST | `/api/messages/retry/{id}` | 重试发送指定的失败消息 |

---

## 3. 通用约定

### 3.1 请求头

| 请求头 | 必填 | 说明 |
|---|---|---|
| `Content-Type` | 是 | 请求体为 JSON 时需设置为 `application/json` |
| `X-App-Id` | 否 | 来源应用标识，用于消息记录筛选（列表查询接口使用） |

### 3.2 数据格式

- 请求体：JSON 格式
- 响应体：JSON 格式
- 字符编码：UTF-8

### 3.3 统一响应结构 Result

所有接口返回统一的响应格式，封装在 `Result<T>` 对象中：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | int | 状态码，200 表示成功，其他表示失败 |
| `message` | String | 响应消息，成功时为 "success"，失败时为错误描述 |
| `data` | T | 响应数据，泛型，具体类型由接口决定 |

### 3.4 错误码说明

| 错误码 | 说明 |
|---|---|
| 200 | 请求成功 |
| 400 | 请求参数错误（如模板不存在） |
| 500 | 服务内部错误 |

### 3.5 消息状态码

用于消息记录的 `status` 字段：

| 状态码 | 枚举值 | 说明 |
|---|---|---|
| 0 | `PENDING` | 待发送 |
| 1 | `SUCCESS` | 发送成功 |
| 2 | `FAILED` | 发送失败 |

### 3.6 消息渠道枚举

| 渠道值 | 说明 |
|---|---|
| `EMAIL` | 邮件通知 |
| `SMS` | 短信通知（当前为 Mock 实现） |

---

## 4. 接口详细说明

### 4.1 发送测试邮件

**接口**：`POST /api/messages/send-test`

**说明**：发送一封测试邮件，无需预先配置模板。用于快速验证 SMTP 配置是否正确。

**请求参数（Query）**：

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `email` | String | 是 | 接收测试邮件的邮箱地址 |

**请求示例**：

```bash
curl -X POST "http://127.0.0.1:8088/api/messages/send-test?email=test@example.com"
```

**成功响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": true
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `data` | Boolean | `true` 表示发送成功，`false` 表示发送失败 |

**说明**：
- 测试邮件包含应用名称和发送时间两个变量
- 发送结果会保存到 `message_records` 表中，`appId` 固定为 `"test"`

---

### 4.2 通过模板发送邮件

**接口**：`POST /api/messages/send`

**说明**：根据模板编码查找模板，渲染模板内容（替换占位符），然后发送邮件。

**请求体（JSON）**：

```json
{
  "appId": "property-service",
  "templateCode": "PROPERTY_CREATE_NOTIFY",
  "receiver": "user@example.com",
  "channel": "EMAIL",
  "subject": "房源创建通知",
  "params": {
    "propertyName": "阳光小区3号楼",
    "price": "5000元/月",
    "link": "http://example.com/property/123"
  },
  "traceId": "abc123-def456"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `appId` | String | 否 | 来源应用 ID，用于追踪和记录 |
| `templateCode` | String | 是 | 模板编码，需在 `message_templates` 表中配置且启用 |
| `receiver` | String | 是 | 接收地址（邮箱/手机号） |
| `channel` | String | 否 | 消息渠道：`EMAIL` 或 `SMS`，默认从模板获取 |
| `subject` | String | 否 | 邮件主题，优先级高于模板中配置的主题 |
| `params` | Object | 否 | 模板参数，用于替换占位符 `${paramName}` |
| `traceId` | String | 否 | 追踪 ID，用于链路追踪 |

**请求示例**：

```bash
curl -X POST "http://127.0.0.1:8088/api/messages/send" \
  -H "Content-Type: application/json" \
  -d '{
    "appId": "property-service",
    "templateCode": "PROPERTY_CREATE_NOTIFY",
    "receiver": "user@example.com",
    "subject": "房源创建通知",
    "params": {
      "propertyName": "阳光小区3号楼",
      "price": "5000元/月"
    }
  }'
```

**成功响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": true
}
```

**失败响应（模板不存在）**：

```json
{
  "code": 400,
  "message": "模板不存在",
  "data": null
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `data` | Boolean | `true` 表示发送成功，`false` 表示发送失败 |

**说明**：
- 发送结果会保存到 `message_records` 表中
- 模板必须存在且处于启用状态（`is_enabled = 1`）
- 若 `subject` 未传，则使用模板中配置的主题

---

### 4.3 消息记录列表（分页）

**接口**：`GET /api/messages/records`

**说明**：分页查询消息发送记录，支持按应用 ID 和状态筛选。

**请求参数（Query）**：

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `status` | Integer | 否 | - | 状态筛选：0-待发送，1-成功，2-失败 |
| `page` | int | 否 | 1 | 页码，从 1 开始 |
| `size` | int | 否 | 20 | 每页条数 |

**请求头**：

| 请求头 | 必填 | 说明 |
|---|---|---|
| `X-App-Id` | 否 | 按应用 ID 筛选记录 |

**请求示例**：

```bash
# 查询所有记录（第1页，每页20条）
curl "http://127.0.0.1:8088/api/messages/records?page=1&size=20"

# 查询失败记录
curl "http://127.0.0.1:8088/api/messages/records?status=2"

# 按应用 ID 筛选
curl "http://127.0.0.1:8088/api/messages/records" \
  -H "X-App-Id: property-service"
```

**成功响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [
      {
        "id": 1,
        "appId": "property-service",
        "messageType": "EMAIL",
        "receiver": "user@example.com",
        "content": "<h1>房源创建成功</h1><p>您创建的房源「阳光小区3号楼」已成功发布。</p>",
        "status": "SUCCESS",
        "retryCount": 0,
        "errorMessage": null,
        "createdAt": "2026-07-10T10:00:00",
        "updatedAt": "2026-07-10T10:00:00"
      }
    ],
    "total": 100,
    "size": 20,
    "current": 1,
    "pages": 5
  }
}
```

**响应字段说明（Page<MessageRecord>）**：

| 字段 | 类型 | 说明 |
|---|---|---|
| `data.records` | Array | 消息记录列表 |
| `data.total` | Long | 总记录数 |
| `data.size` | Long | 每页条数 |
| `data.current` | Long | 当前页码 |
| `data.pages` | Long | 总页数 |

**MessageRecord 字段说明**：

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | Long | 记录 ID |
| `appId` | String | 来源应用 ID |
| `messageType` | String | 消息类型：`EMAIL` / `SMS` |
| `receiver` | String | 接收地址 |
| `content` | String | 最终发送的内容 |
| `status` | String | 状态：`PENDING` / `SUCCESS` / `FAILED` |
| `retryCount` | Integer | 已重试次数 |
| `errorMessage` | String | 错误信息（失败时有值） |
| `createdAt` | LocalDateTime | 创建时间 |
| `updatedAt` | LocalDateTime | 更新时间 |

**说明**：
- 记录按创建时间倒序排列（最新的在前）
- 同时提供 `X-App-Id` 请求头和 `status` 参数时，两个条件同时生效

---

### 4.4 消息记录详情

**接口**：`GET /api/messages/records/{id}`

**说明**：根据记录 ID 获取单条消息记录的详细信息。

**路径参数**：

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | Long | 是 | 消息记录 ID |

**请求示例**：

```bash
curl "http://127.0.0.1:8088/api/messages/records/1"
```

**成功响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "appId": "property-service",
    "messageType": "EMAIL",
    "receiver": "user@example.com",
    "content": "<h1>房源创建成功</h1><p>您创建的房源「阳光小区3号楼」已成功发布。</p>",
    "status": "SUCCESS",
    "retryCount": 0,
    "errorMessage": null,
    "createdAt": "2026-07-10T10:00:00",
    "updatedAt": "2026-07-10T10:00:00"
  }
}
```

**说明**：
- 若记录不存在，`data` 为 `null`

---

### 4.5 消息重试

**接口**：`POST /api/messages/retry/{id}`

**说明**：重试发送指定的失败消息。

**路径参数**：

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | Long | 是 | 消息记录 ID |

**请求示例**：

```bash
curl -X POST "http://127.0.0.1:8088/api/messages/retry/1"
```

**成功响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": true
}
```

**失败响应（记录不存在）**：

```json
{
  "code": 500,
  "message": "消息记录不存在",
  "data": null
}
```

**失败响应（已成功无需重试）**：

```json
{
  "code": 500,
  "message": "消息已发送成功，无需重试",
  "data": null
}
```

**失败响应（已达最大重试次数）**：

```json
{
  "code": 500,
  "message": "已达最大重试次数",
  "data": null
}
```

**说明**：
- 只有失败状态的消息才能重试
- 重试次数不能超过配置的最大重试次数（`message.max-retry`，默认 3 次）
- 重试成功后状态更新为 `SUCCESS`，失败则保持 `FAILED`，`retryCount` 加 1

---

## 5. RabbitMQ 消息接口（推荐）

### 5.1 MQ 配置信息

| 配置项 | 值 | 说明 |
|---|---|---|
| Exchange | `message.send.exchange` | Direct Exchange，持久化 |
| Queue | `message.send.queue` | 持久化队列 |
| Routing Key | `message.send` | 路由键 |
| 消息格式 | JSON 字符串 | 使用 ObjectMapper 手动序列化 |
| 重试机制 | Spring Retry | 最大 3 次，初始间隔 2000ms |

### 5.2 事件消息格式

所有业务服务通过 RabbitMQ 发送的消息必须是 **JSON 字符串**格式：

```json
{
  "appId": "property-service",
  "templateCode": "PROPERTY_CREATE_NOTIFY",
  "receiver": "user@example.com",
  "channel": "EMAIL",
  "subject": "房源创建通知",
  "params": {
    "propertyName": "阳光小区3号楼",
    "price": "5000元/月",
    "link": "http://example.com/property/123"
  },
  "traceId": "abc123-def456"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `appId` | String | 否 | 来源应用 ID，用于追踪和记录 |
| `templateCode` | String | 是 | 模板编码，在数据库中配置 |
| `receiver` | String | 是 | 接收地址（邮箱/手机号） |
| `channel` | String | 否 | 消息通道：`EMAIL` 或 `SMS`，默认从模板获取 |
| `subject` | String | 否 | 邮件主题（优先级高于模板），短信忽略此字段 |
| `params` | Object | 否 | 模板参数，用于替换占位符 |
| `traceId` | String | 否 | 追踪 ID，用于链路追踪 |

> **重要**：消息必须以 JSON 字符串形式发送，避免使用 Java 对象序列化导致的类型不匹配问题。

---

## 6. 模板变量语法说明

### 6.1 占位符语法

模板内容中使用 **`${变量名}`** 作为占位符，发送时会被 `params` 中对应的值替换。

**示例模板**：

```html
<h1>欢迎加入我们</h1>
<p>尊敬的 ${username}，您好！</p>
<p>您的订单 ${orderId} 已创建成功。</p>
<p>订单金额：${amount} 元</p>
```

**对应 params**：

```json
{
  "username": "张三",
  "orderId": "ORD202607100001",
  "amount": "299.00"
}
```

**渲染结果**：

```html
<h1>欢迎加入我们</h1>
<p>尊敬的 张三，您好！</p>
<p>您的订单 ORD202607100001 已创建成功。</p>
<p>订单金额：299.00 元</p>
```

### 6.2 替换规则

| 规则 | 说明 |
|---|---|
| 完全匹配 | 占位符 `${varName}` 会被完全替换为对应的值 |
| 大小写敏感 | 变量名大小写敏感，`${userName}` 和 `${username}` 是不同的变量 |
| null 值处理 | 如果参数值为 `null`，占位符会被替换为空字符串 `""` |
| 缺失参数 | 如果 `params` 中没有对应的参数，占位符会保留原样（`${varName}`） |
| 空 params | 如果 `params` 为 `null` 或空 Map，不做任何替换，直接返回原模板内容 |
| 非字符串值 | 非字符串类型的参数会调用 `toString()` 转换为字符串后替换 |

### 6.3 模板管理

模板存储在 `message_templates` 表中，通过 SQL 进行管理。

**添加模板示例**：

```sql
INSERT INTO message_templates (template_code, type, subject, content, is_enabled)
VALUES (
  'USER_REGISTER_WELCOME',
  'EMAIL',
  '欢迎注册',
  '<h1>欢迎加入我们</h1><p>尊敬的 ${username}，您好！</p><p><a href="${loginUrl}">立即登录</a></p>',
  1
);
```

**模板字段说明**：

| 字段 | 类型 | 说明 |
|---|---|---|
| `template_code` | VARCHAR(50) | 模板编码，唯一标识 |
| `type` | ENUM | 模板类型：`EMAIL` 或 `SMS` |
| `subject` | VARCHAR(200) | 邮件主题（短信类型可为 NULL） |
| `content` | TEXT | 模板内容，包含 `${var}` 占位符 |
| `is_enabled` | TINYINT(1) | 是否启用：1-启用，0-禁用 |

> **注意**：只有 `is_enabled = 1` 的模板才会被查询和使用。

---

## 7. 典型业务场景示例

| 场景 | 触发时机 | 模板编码示例 | 通道 |
|---|---|---|---|
| 用户注册欢迎 | 用户注册成功后 | `USER_REGISTER_WELCOME` | EMAIL |
| 用户密码重置 | 用户申请重置密码 | `USER_PASSWORD_RESET` | EMAIL |
| 用户验证码 | 用户登录/注册需要验证 | `USER_VERIFY_CODE` | SMS/EMAIL |
| 房源创建通知 | 房源创建成功后 | `PROPERTY_CREATE_NOTIFY` | EMAIL |
| 房源审核通知 | 房源审核通过/拒绝 | `PROPERTY_AUDIT_NOTIFY` | EMAIL/SMS |
| 房源到期提醒 | 房源即将到期 | `PROPERTY_EXPIRE_REMINDER` | EMAIL/SMS |
| 订单确认 | 订单创建成功 | `ORDER_CONFIRM` | EMAIL/SMS |
