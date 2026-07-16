# review-service API 文档

## 1. 服务概览

审核服务（review-service）是微服务集群中的内容审核服务，负责管理房源等内容的审核任务，支持人工审核和机器审核两种模式。

| 项目 | 值 |
|---|---|
| 服务名称 | review-service |
| 服务端口 | 8091 |
| 绑定地址 | 0.0.0.0 |
| API 路径前缀 | `/api/audit` |
| 数据库 | `review_db` |

---

## 2. 接口总览表

| 序号 | 接口名称 | 请求方法 | 路径 | 说明 |
|---|---|---|---|---|
| 1 | 查询审核任务列表 | GET | `/api/audit/tasks` | 分页查询审核任务列表 |
| 2 | 查询单个审核任务 | GET | `/api/audit/tasks/{taskId}` | 获取审核任务详情 |
| 3 | 人工审核 | POST | `/api/audit/manual` | 提交人工审核结果 |
| 4 | 创建审核任务 | POST | `/api/audit/tasks` | 创建新的审核任务 |

---

## 3. 通用约定

### 3.1 请求头

| 请求头 | 必填 | 说明 |
|---|---|---|
| `Content-Type` | 是 | 请求体为 JSON 时需设置为 `application/json` |
| `X-App-Id` | 否 | 应用标识，默认值 `default` |
| `Authorization` | 是 | Bearer Token，应用 JWT 令牌 |

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
| 400 | 请求参数错误 |
| 401 | 未授权 |
| 404 | 审核任务不存在 |
| 500 | 服务内部错误 |

### 3.5 审核任务状态

| 状态码 | 说明 |
|---|---|
| 1 | 待审核 |
| 2 | 审核通过 |
| 3 | 审核拒绝 |

### 3.6 审核结果

| 结果码 | 说明 |
|---|---|
| 1 | 通过 |
| 2 | 拒绝 |

---

## 4. 接口详情

### 4.1 查询审核任务列表

分页查询审核任务列表，支持按状态筛选。

**请求方式**：`GET`

**请求路径**：`/api/audit/tasks`

**请求参数**：

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `page` | int | 否 | 1 | 页码 |
| `size` | int | 否 | 10 | 每页条数 |
| `status` | Integer | 否 | - | 审核状态筛选（1-待审核，2-通过，3-拒绝） |

**响应示例**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 100,
    "records": [
      {
        "id": 1,
        "propertyId": 100,
        "appId": "default",
        "taskType": "CONTENT_AUDIT",
        "status": 1,
        "resultDetail": null,
        "createdAt": "2024-01-01T12:00:00",
        "updatedAt": "2024-01-01T12:00:00"
      }
    ]
  }
}
```

**响应字段**：

| 字段 | 类型 | 说明 |
|---|---|---|
| `data.total` | Long | 总记录数 |
| `data.records` | `AuditTaskVO[]` | 审核任务列表 |

---

### 4.2 查询单个审核任务

根据任务ID获取审核任务详情，包含审核记录。

**请求方式**：`GET`

**请求路径**：`/api/audit/tasks/{taskId}`

**路径参数**：

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `taskId` | Long | 是 | 审核任务ID |

**响应示例**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "propertyId": 100,
    "appId": "default",
    "taskType": "CONTENT_AUDIT",
    "status": 2,
    "resultDetail": null,
    "createdAt": "2024-01-01T12:00:00",
    "updatedAt": "2024-01-01T13:00:00",
    "auditRecords": [
      {
        "id": 1,
        "taskId": 1,
        "auditType": "MANUAL",
        "result": 1,
        "reason": "内容合规",
        "auditorId": 100,
        "auditAt": "2024-01-01T13:00:00"
      }
    ]
  }
}
```

**响应字段**：

| 字段 | 类型 | 说明 |
|---|---|---|
| `data.id` | Long | 任务ID |
| `data.propertyId` | Long | 房源ID |
| `data.appId` | String | 应用ID |
| `data.taskType` | String | 任务类型 |
| `data.status` | Integer | 任务状态 |
| `data.auditRecords` | `AuditRecordVO[]` | 审核记录列表 |

**错误响应**：

```json
{
  "code": 404,
  "message": "审核任务不存在: 1",
  "data": null
}
```

---

### 4.3 人工审核

提交人工审核结果，通过或拒绝。

**请求方式**：`POST`

**请求路径**：`/api/audit/manual`

**请求体**：

```json
{
  "taskId": 1,
  "result": 1,
  "reason": "内容合规",
  "auditorId": 100
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `taskId` | Long | 是 | 审核任务ID |
| `result` | Integer | 是 | 审核结果：1-通过，2-拒绝 |
| `reason` | String | 否 | 审核原因 |
| `auditorId` | Long | 是 | 审核人ID |

**响应示例**：

```json
{
  "code": 200,
  "message": "审核完成",
  "data": null
}
```

**错误响应**：

```json
{
  "code": 404,
  "message": "审核任务不存在: 1",
  "data": null
}
```

---

### 4.4 创建审核任务

创建新的审核任务。

**请求方式**：`POST`

**请求路径**：`/api/audit/tasks`

**请求参数**：

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `propertyId` | Long | 是 | 房源ID |
| `taskType` | String | 否 | 任务类型，默认 `CONTENT_AUDIT` |

**请求头**：

| 名称 | 必填 | 说明 |
|---|---|---|
| `X-App-Id` | 否 | 应用标识，默认 `default` |

**响应示例**：

```json
{
  "code": 200,
  "message": "success",
  "data": 1
}
```

**响应字段**：

| 字段 | 类型 | 说明 |
|---|---|---|
| `data` | Long | 新创建的审核任务ID |

---

## 5. 数据模型

### 5.1 AuditTaskVO

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | Long | 任务ID |
| `propertyId` | Long | 房源ID |
| `appId` | String | 应用ID |
| `taskType` | String | 任务类型 |
| `status` | Integer | 状态：1-待审核，2-通过，3-拒绝 |
| `resultDetail` | String | 结果详情 |
| `createdAt` | LocalDateTime | 创建时间 |
| `updatedAt` | LocalDateTime | 更新时间 |
| `auditRecords` | `AuditRecordVO[]` | 审核记录列表（仅详情接口返回） |

### 5.2 AuditRecordVO

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | Long | 记录ID |
| `taskId` | Long | 任务ID |
| `auditType` | String | 审核类型：MANUAL-人工，AUTO-机器 |
| `result` | Integer | 结果：1-通过，2-拒绝 |
| `reason` | String | 原因 |
| `auditorId` | Long | 审核人ID |
| `auditAt` | LocalDateTime | 审核时间 |
