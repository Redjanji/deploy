# favorite-service API 文档

## 1. 服务概览

收藏服务（favorite-service）是微服务集群中的用户收藏管理服务，负责管理用户对房源的收藏操作。

| 项目 | 值 |
|---|---|
| 服务名称 | favorite-service |
| 服务端口 | 8089 |
| 绑定地址 | 0.0.0.0 |
| API 路径前缀 | `/api/favorites` |
| 数据库 | `favorite_db` |

---

## 2. 接口总览表

| 序号 | 接口名称 | 请求方法 | 路径 | 说明 |
|---|---|---|---|---|
| 1 | 查询收藏列表 | GET | `/api/favorites` | 获取当前用户的收藏列表 |
| 2 | 添加收藏 | POST | `/api/favorites/{propertyId}` | 将指定房源加入收藏 |
| 3 | 检查收藏状态 | GET | `/api/favorites/check/{propertyId}` | 检查指定房源是否已收藏 |
| 4 | 取消收藏 | DELETE | `/api/favorites/{propertyId}` | 取消指定房源的收藏 |

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
| 401 | 未授权（缺少用户 Token） |
| 404 | 资源不存在 |
| 500 | 服务内部错误 |

---

## 4. 接口详情

### 4.1 查询收藏列表

获取当前用户的所有收藏房源列表。

**请求方式**：`GET`

**请求路径**：`/api/favorites`

**请求头**：

| 名称 | 必填 | 说明 |
|---|---|---|
| `X-User-Id` | 是 | 用户ID，由网关注入 |

**响应示例**：

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "propertyId": 100,
      "userId": 1,
      "createdAt": "2024-01-01T12:00:00"
    }
  ]
}
```

**响应字段**：

| 字段 | 类型 | 说明 |
|---|---|---|
| `data` | `FavoriteVO[]` | 收藏列表数组 |
| `data[].id` | Long | 收藏记录ID |
| `data[].propertyId` | Long | 房源ID |
| `data[].userId` | Long | 用户ID |
| `data[].createdAt` | String | 收藏时间 |

---

### 4.2 添加收藏

将指定房源加入用户收藏。

**请求方式**：`POST`

**请求路径**：`/api/favorites/{propertyId}`

**路径参数**：

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `propertyId` | Long | 是 | 房源ID |

**请求头**：

| 名称 | 必填 | 说明 |
|---|---|---|
| `X-User-Id` | 是 | 用户ID，由网关注入 |

**响应示例**：

```json
{
  "code": 200,
  "message": "收藏成功",
  "data": null
}
```

---

### 4.3 检查收藏状态

检查指定房源是否已被当前用户收藏。

**请求方式**：`GET`

**请求路径**：`/api/favorites/check/{propertyId}`

**路径参数**：

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `propertyId` | Long | 是 | 房源ID |

**请求头**：

| 名称 | 必填 | 说明 |
|---|---|---|
| `X-User-Id` | 是 | 用户ID，由网关注入 |

**响应示例**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "favorited": true
  }
}
```

**响应字段**：

| 字段 | 类型 | 说明 |
|---|---|---|
| `data.favorited` | Boolean | 是否已收藏 |

---

### 4.4 取消收藏

取消指定房源的收藏。

**请求方式**：`DELETE`

**请求路径**：`/api/favorites/{propertyId}`

**路径参数**：

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `propertyId` | Long | 是 | 房源ID |

**请求头**：

| 名称 | 必填 | 说明 |
|---|---|---|
| `X-User-Id` | 是 | 用户ID，由网关注入 |

**响应示例**：

```json
{
  "code": 200,
  "message": "取消收藏成功",
  "data": null
}
```

---

## 5. 数据模型

### 5.1 FavoriteVO

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | Long | 收藏记录ID |
| `propertyId` | Long | 房源ID |
| `userId` | Long | 用户ID |
| `createdAt` | LocalDateTime | 收藏时间 |
