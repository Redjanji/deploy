# booking-service API 文档

## 1. 服务概览

预约服务（booking-service）是微服务集群中的预约看房管理服务，负责管理用户的房源预约看房申请，支持用户发起预约、经纪人确认/拒绝/完成等全流程。

| 项目 | 值 |
|---|---|
| 服务名称 | booking-service |
| 服务端口 | 8092 |
| 绑定地址 | 0.0.0.0 |
| API 路径前缀 | `/api/bookings` |
| 数据库 | `booking_db` |

---

## 2. 接口总览表

| 序号 | 接口名称 | 请求方法 | 路径 | 说明 |
|---|---|---|---|---|
| 1 | 创建预约 | POST | `/api/bookings` | 用户发起预约看房申请 |
| 2 | 确认预约 | PUT | `/api/bookings/{bookingId}/confirm` | 经纪人确认预约 |
| 3 | 完成预约 | PUT | `/api/bookings/{bookingId}/complete` | 经纪人标记预约完成 |
| 4 | 用户取消预约 | PUT | `/api/bookings/{bookingId}/cancel/user` | 用户取消预约 |
| 5 | 经纪人取消预约 | PUT | `/api/bookings/{bookingId}/cancel/agent` | 经纪人取消预约 |
| 6 | 查询预约详情 | GET | `/api/bookings/{bookingId}` | 获取预约详情 |
| 7 | 用户预约列表 | GET | `/api/bookings/user` | 获取用户的预约列表 |
| 8 | 经纪人预约列表 | GET | `/api/bookings/agent` | 获取经纪人的预约列表 |

---

## 3. 通用约定

### 3.1 请求头

| 请求头 | 必填 | 说明 |
|---|---|---|
| `Content-Type` | 是 | 请求体为 JSON 时需设置为 `application/json` |
| `X-User-Id` | 是 | 用户ID，由网关 JWT 认证后注入 |
| `Authorization` | 是 | Bearer Token，用户 JWT 令牌 |

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
| 403 | 无权限操作 |
| 404 | 预约不存在 |
| 500 | 服务内部错误 |

### 3.5 预约状态

| 状态码 | 说明 |
|---|---|
| 0 | 待确认 |
| 1 | 已确认 |
| 2 | 已完成 |
| 3 | 已取消（用户） |
| 4 | 已取消（经纪人） |

---

## 4. 接口详情

### 4.1 创建预约

用户发起预约看房申请。

**请求方式**：`POST`

**请求路径**：`/api/bookings`

**请求头**：

| 名称 | 必填 | 说明 |
|---|---|---|
| `X-User-Id` | 是 | 用户ID |

**请求体**：

```json
{
  "propertyId": 100,
  "agentId": 50,
  "name": "张三",
  "phone": "13800138000",
  "visitTime": "2024-01-15T14:00:00",
  "remark": "想了解一下周边配套"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `propertyId` | Long | 是 | 房源ID |
| `agentId` | Long | 是 | 经纪人ID |
| `name` | String | 是 | 联系人姓名 |
| `phone` | String | 是 | 联系电话 |
| `visitTime` | String | 是 | 预约看房时间 |
| `remark` | String | 否 | 备注 |

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
| `data` | Long | 新创建的预约ID |

---

### 4.2 确认预约

经纪人确认预约申请。

**请求方式**：`PUT`

**请求路径**：`/api/bookings/{bookingId}/confirm`

**路径参数**：

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `bookingId` | Long | 是 | 预约ID |

**请求头**：

| 名称 | 必填 | 说明 |
|---|---|---|
| `X-User-Id` | 是 | 经纪人ID |

**响应示例**：

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

---

### 4.3 完成预约

经纪人标记预约为已完成。

**请求方式**：`PUT`

**请求路径**：`/api/bookings/{bookingId}/complete`

**路径参数**：

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `bookingId` | Long | 是 | 预约ID |

**请求头**：

| 名称 | 必填 | 说明 |
|---|---|---|
| `X-User-Id` | 是 | 经纪人ID |

**响应示例**：

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

---

### 4.4 用户取消预约

用户取消自己的预约申请。

**请求方式**：`PUT`

**请求路径**：`/api/bookings/{bookingId}/cancel/user`

**路径参数**：

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `bookingId` | Long | 是 | 预约ID |

**请求参数**：

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `reason` | String | 否 | 取消原因 |

**请求头**：

| 名称 | 必填 | 说明 |
|---|---|---|
| `X-User-Id` | 是 | 用户ID |

**响应示例**：

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

---

### 4.5 经纪人取消预约

经纪人取消预约。

**请求方式**：`PUT`

**请求路径**：`/api/bookings/{bookingId}/cancel/agent`

**路径参数**：

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `bookingId` | Long | 是 | 预约ID |

**请求参数**：

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `reason` | String | 否 | 取消原因 |

**请求头**：

| 名称 | 必填 | 说明 |
|---|---|---|
| `X-User-Id` | 是 | 经纪人ID |

**响应示例**：

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

---

### 4.6 查询预约详情

获取预约详情信息。

**请求方式**：`GET`

**请求路径**：`/api/bookings/{bookingId}`

**路径参数**：

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `bookingId` | Long | 是 | 预约ID |

**请求参数**：

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `isAgent` | boolean | 否 | false | 是否为经纪人视角 |

**请求头**：

| 名称 | 必填 | 说明 |
|---|---|---|
| `X-User-Id` | 是 | 用户ID或经纪人ID |

**响应示例**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "propertyId": 100,
    "propertyTitle": "精装修三居室",
    "propertyCover": "https://...",
    "userId": 1,
    "userName": "张三",
    "userPhone": "13800138000",
    "agentId": 50,
    "agentName": "李经纪人",
    "agentPhone": "13900139000",
    "visitTime": "2024-01-15T14:00:00",
    "status": 1,
    "remark": "想了解一下周边配套",
    "cancelReason": null,
    "createdAt": "2024-01-01T10:00:00",
    "updatedAt": "2024-01-01T11:00:00"
  }
}
```

---

### 4.7 用户预约列表

获取当前用户的预约列表。

**请求方式**：`GET`

**请求路径**：`/api/bookings/user`

**请求参数**：

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `page` | int | 否 | 1 | 页码 |
| `size` | int | 否 | 10 | 每页条数 |

**请求头**：

| 名称 | 必填 | 说明 |
|---|---|---|
| `X-User-Id` | 是 | 用户ID |

**响应示例**：

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "propertyId": 100,
      "propertyTitle": "精装修三居室",
      "status": 1,
      "visitTime": "2024-01-15T14:00:00",
      "createdAt": "2024-01-01T10:00:00"
    }
  ]
}
```

---

### 4.8 经纪人预约列表

获取当前经纪人的预约列表。

**请求方式**：`GET`

**请求路径**：`/api/bookings/agent`

**请求参数**：

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `page` | int | 否 | 1 | 页码 |
| `size` | int | 否 | 10 | 每页条数 |

**请求头**：

| 名称 | 必填 | 说明 |
|---|---|---|
| `X-User-Id` | 是 | 经纪人ID |

**响应示例**：

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "propertyId": 100,
      "propertyTitle": "精装修三居室",
      "userId": 1,
      "userName": "张三",
      "userPhone": "13800138000",
      "status": 0,
      "visitTime": "2024-01-15T14:00:00",
      "createdAt": "2024-01-01T10:00:00"
    }
  ]
}
```

---

## 5. 数据模型

### 5.1 BookingVO

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | Long | 预约ID |
| `propertyId` | Long | 房源ID |
| `propertyTitle` | String | 房源标题 |
| `propertyCover` | String | 房源封面图 |
| `userId` | Long | 用户ID |
| `userName` | String | 用户姓名 |
| `userPhone` | String | 用户电话 |
| `agentId` | Long | 经纪人ID |
| `agentName` | String | 经纪人姓名 |
| `agentPhone` | String | 经纪人电话 |
| `visitTime` | LocalDateTime | 预约看房时间 |
| `status` | Integer | 预约状态 |
| `remark` | String | 备注 |
| `cancelReason` | String | 取消原因 |
| `createdAt` | LocalDateTime | 创建时间 |
| `updatedAt` | LocalDateTime | 更新时间 |
