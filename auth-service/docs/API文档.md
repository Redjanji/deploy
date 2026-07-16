# API 文档

## 1. 服务概览

| 项目 | 值 |
|------|-----|
| 服务名 | auth-service |
| 服务描述 | 用户认证服务，提供注册、登录、登出、Token 刷新、用户信息查询等能力 |
| 监听地址 | 127.0.0.1 |
| 监听端口 | 8083 |
| 接口前缀 | `/auth` |
| 完整基础 URL | `http://127.0.0.1:8083/auth` |

---

## 2. 接口总览表

| 序号 | 接口名称 | HTTP 方法 | 路径 | 是否需要登录 | 说明 |
|------|---------|-----------|------|-------------|------|
| 1 | 用户注册 | POST | `/auth/register` | 否 | 创建新用户账号，用户名全局唯一 |
| 2 | 用户登录 | POST | `/auth/login` | 否 | 用户名密码登录，成功返回 JWT |
| 3 | 用户登出 | POST | `/auth/logout` | 是 | 将当前 Token 加入黑名单使其失效 |
| 4 | 刷新 Token | POST | `/auth/refresh` | 是 | 用旧 Token 换取新 Token，旧 Token 立即失效 |
| 5 | 获取用户信息 | GET | `/auth/userinfo` | 是 | 获取当前登录用户的基本信息（脱敏） |

---

## 3. 通用约定

### 3.1 请求头

| 请求头 | 说明 | 是否必填 |
|--------|------|---------|
| `Content-Type` | 请求体格式，固定为 `application/json` | POST/PUT 请求必填 |
| `Authorization` | 认证令牌，格式为 `Bearer <token>` | 需要登录的接口必填 |
| `X-User-Id` | 用户 ID（由网关注入，服务内部接口可直接使用） | 经网关转发时自动携带 |
| `X-App-Id` | 应用 ID（由网关注入，标识调用方应用） | 经网关转发时自动携带 |

> **说明**：`X-User-Id` 和 `X-App-Id` 由网关层在 Token 校验通过后注入下游服务。本服务内部接口通过 JWT 自行认证，不依赖这两个请求头。

### 3.2 数据格式

- 请求体：`application/json; charset=UTF-8`
- 响应体：`application/json; charset=UTF-8`
- 字符编码：UTF-8

### 3.3 统一响应结构

本服务未使用统一的 `Result` 包装类，直接返回业务数据或字符串。

**成功响应**：
- 注册、登出：返回字符串消息（如 `"注册成功"`）
- 登录、刷新 Token：返回 `JwtResponse` JSON 对象
- 用户信息：返回 `UserInfoDTO` JSON 对象

**失败响应**：

由 Spring Boot 默认错误机制返回，格式如下：

```json
{
  "timestamp": "2024-07-10T12:00:00.000+00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "用户名不能为空",
  "path": "/auth/register"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| timestamp | string | 错误发生时间（ISO 8601 格式） |
| status | int | HTTP 状态码 |
| error | string | HTTP 状态描述 |
| message | string | 具体错误信息 |
| path | string | 请求路径 |

### 3.4 HTTP 状态码与错误码

| HTTP 状态码 | 场景 | 说明 |
|-------------|------|------|
| 200 OK | 请求成功 | 接口正常返回 |
| 400 Bad Request | 参数校验失败 | 用户名/密码不符合长度要求、字段缺失等 |
| 401 Unauthorized | 认证失败 | Token 无效、过期、已被加入黑名单（登出） |
| 403 Forbidden | 权限不足 | 未登录访问需要认证的接口 |
| 500 Internal Server Error | 服务内部错误 | 业务异常（如用户名已存在）、服务器内部错误 |

### 3.5 分页约定

本服务当前无分页接口。

---

## 4. 接口详细说明

### 4.1 用户注册

**POST** `/auth/register`

注册一个新用户。用户名全局唯一，密码使用 BCrypt 加密存储。注册成功后会发送 `USER_REGISTER` 统计事件到 RabbitMQ。

#### 请求参数

| 字段 | 类型 | 必填 | 约束 | 说明 |
|------|------|------|------|------|
| username | string | 是 | 长度 3-32 个字符 | 用户名，全局唯一 |
| password | string | 是 | 长度 8-64 个字符 | 密码（明文传输，服务端 BCrypt 加密存储） |
| email | string | 否 | - | 邮箱地址 |
| phone | string | 否 | - | 手机号码 |

#### 请求示例

```bash
curl -X POST http://127.0.0.1:8083/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "zhangsan",
    "password": "mypassword123",
    "email": "zhangsan@example.com",
    "phone": "13800138000"
  }'
```

```json
{
  "username": "zhangsan",
  "password": "mypassword123",
  "email": "zhangsan@example.com",
  "phone": "13800138000"
}
```

#### 响应参数

成功时直接返回字符串，无 JSON 结构。

#### 响应示例

**成功（HTTP 200）**：

```
"注册成功"
```

**失败 - 用户名已存在（HTTP 500）**：

```json
{
  "timestamp": "2024-07-10T12:00:00.000+00:00",
  "status": 500,
  "error": "Internal Server Error",
  "message": "用户名已存在",
  "path": "/auth/register"
}
```

**失败 - 参数校验失败（HTTP 400）**：

```json
{
  "timestamp": "2024-07-10T12:00:00.000+00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "密码长度需在 8-64 个字符之间",
  "path": "/auth/register"
}
```

---

### 4.2 用户登录

**POST** `/auth/login`

使用用户名和密码登录，验证通过后签发 JWT。登录成功后会发送 `USER_LOGIN` 统计事件到 RabbitMQ。

#### 请求参数

| 字段 | 类型 | 必填 | 约束 | 说明 |
|------|------|------|------|------|
| username | string | 是 | 非空 | 用户名 |
| password | string | 是 | 非空 | 密码（明文） |

#### 请求示例

```bash
curl -X POST http://127.0.0.1:8083/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "zhangsan",
    "password": "mypassword123"
  }'
```

```json
{
  "username": "zhangsan",
  "password": "mypassword123"
}
```

#### 响应参数

| 字段 | 类型 | 说明 |
|------|------|------|
| token | string | JWT 访问令牌，有效期 2 小时 |
| userId | long | 用户 ID |
| username | string | 用户名 |

#### 响应示例

**成功（HTTP 200）**：

```json
{
  "token": "eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiIxIiwidHlwZSI6InVzZXIiLCJpc3MiOiJhdXRoLXNlcnZpY2UiLCJpYXQiOjE3MTk3NTAwMDAsImV4cCI6MTcxOTc1NzIwMH0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c",
  "userId": 1,
  "username": "zhangsan"
}
```

**失败 - 用户名或密码错误（HTTP 403）**：

```json
{
  "timestamp": "2024-07-10T12:00:00.000+00:00",
  "status": 403,
  "error": "Forbidden",
  "path": "/auth/login"
}
```

---

### 4.3 用户登出

**POST** `/auth/logout`

将当前请求携带的 Token 加入 Redis 黑名单，使其立即失效。Token 到期后会自动从 Redis 中清除（TTL 等于 Token 剩余有效期）。

#### 请求头

```
Authorization: Bearer <token>
```

#### 请求参数

无请求体。

#### 请求示例

```bash
curl -X POST http://127.0.0.1:8083/auth/logout \
  -H "Authorization: Bearer eyJhbGciOiJIUzM4NCJ9..."
```

#### 响应参数

成功时直接返回字符串。

#### 响应示例

**成功（HTTP 200）**：

```
"登出成功"
```

**失败 - Token 无效（HTTP 401）**：

```json
{
  "timestamp": "2024-07-10T12:00:00.000+00:00",
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid token",
  "path": "/auth/logout"
}
```

---

### 4.4 刷新 Token

**POST** `/auth/refresh`

使用当前有效的 Token 换取新的 Token。旧 Token 会被立即加入黑名单失效，新 Token 有效期重新计算 2 小时。

#### 请求头

```
Authorization: Bearer <token>
```

#### 请求参数

无请求体。

#### 请求示例

```bash
curl -X POST http://127.0.0.1:8083/auth/refresh \
  -H "Authorization: Bearer eyJhbGciOiJIUzM4NCJ9..."
```

#### 响应参数

| 字段 | 类型 | 说明 |
|------|------|------|
| token | string | 新的 JWT 访问令牌，有效期重置为 2 小时 |
| userId | long | 用户 ID |
| username | string | 用户名 |

#### 响应示例

**成功（HTTP 200）**：

```json
{
  "token": "eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiIxIiwidHlwZSI6InVzZXIiLCJpc3MiOiJhdXRoLXNlcnZpY2UiLCJpYXQiOjE3MTk3NTM2MDAsImV4cCI6MTcxOTc2MDgwMH0.newSignature",
  "userId": 1,
  "username": "zhangsan"
}
```

**失败 - Token 已失效（HTTP 401）**：

```json
{
  "timestamp": "2024-07-10T12:00:00.000+00:00",
  "status": 401,
  "error": "Unauthorized",
  "message": "Token has been revoked",
  "path": "/auth/refresh"
}
```

---

### 4.5 获取用户信息

**GET** `/auth/userinfo`

获取当前登录用户的基本信息，返回数据已脱敏（不含密码哈希）。

#### 请求头

```
Authorization: Bearer <token>
```

#### 请求参数

无。

#### 请求示例

```bash
curl http://127.0.0.1:8083/auth/userinfo \
  -H "Authorization: Bearer eyJhbGciOiJIUzM4NCJ9..."
```

#### 响应参数

| 字段 | 类型 | 说明 |
|------|------|------|
| id | long | 用户 ID |
| username | string | 用户名 |
| email | string | 邮箱（可能为 null） |
| phone | string | 手机号（可能为 null） |

#### 响应示例

**成功（HTTP 200）**：

```json
{
  "id": 1,
  "username": "zhangsan",
  "email": "zhangsan@example.com",
  "phone": "13800138000"
}
```

**失败 - 未登录（HTTP 403）**：

```json
{
  "timestamp": "2024-07-10T12:00:00.000+00:00",
  "status": 403,
  "error": "Forbidden",
  "path": "/auth/userinfo"
}
```

---

## 5. JWT 结构详细说明

### 5.1 JWT 组成

JWT（JSON Web Token）由三部分组成，用 `.` 分隔：

```
<Header>.<Payload>.<Signature>
```

### 5.2 Header（头部）

```json
{
  "alg": "HS384",
  "typ": "JWT"
}
```

| 字段 | 说明 |
|------|------|
| alg | 签名算法，由 jjwt 库根据密钥长度自动选择（HS256/HS384/HS512），32 字节密钥对应 HS256，48 字节对应 HS384，64 字节对应 HS512 |
| typ | 令牌类型，固定为 `JWT` |

### 5.3 Payload（载荷）

本服务签发的 JWT Payload 示例：

```json
{
  "sub": "1",
  "type": "user",
  "iss": "auth-service",
  "iat": 1719750000,
  "exp": 1719757200
}
```

| 声明（Claim） | 类型 | 说明 |
|--------------|------|------|
| `sub` | string | 主体（Subject），即用户 ID 的字符串形式 |
| `type` | string | Token 类型，固定为 `"user"`，网关据此识别为普通用户 Token |
| `iss` | string | 签发者（Issuer），固定为 `"auth-service"` |
| `iat` | long | 签发时间（Issued At），Unix 时间戳，单位秒 |
| `exp` | long | 过期时间（Expiration），Unix 时间戳，单位秒 |

### 5.4 Signature（签名）

- **签名算法**：HMAC-SHA（HS256/HS384/HS512，由密钥长度决定）
- **密钥**：Base64 编码的密钥，通过 `jwt.secret-base64` 配置项指定
- **签名内容**：`Base64UrlEncode(Header) + "." + Base64UrlEncode(Payload)`
- **作用**：确保 Token 未被篡改，验证数据完整性和真实性

### 5.5 有效期

- 默认有效期：7200000 毫秒（2 小时）
- 可通过 `jwt.expiration` 配置项修改

---

## 6. 请求头 X-User-Id、X-App-Id 说明

### 6.1 X-User-Id

| 项目 | 说明 |
|------|------|
| 名称 | `X-User-Id` |
| 来源 | 网关 gateway-service 注入 |
| 值类型 | 字符串（用户 ID） |
| 作用 | 将已认证的用户 ID 传递给下游微服务 |

**工作流程**：
1. 客户端请求携带 `Authorization: Bearer <token>` 访问网关
2. 网关校验 JWT 有效性，解析出 `sub` 声明（用户 ID）
3. 网关将用户 ID 放入 `X-User-Id` 请求头，转发给下游服务
4. 下游服务可直接从 `X-User-Id` 获取当前用户 ID，无需再次解析 JWT

### 6.2 X-App-Id

| 项目 | 说明 |
|------|------|
| 名称 | `X-App-Id` |
| 来源 | 网关 gateway-service 注入 |
| 值类型 | 字符串（应用标识） |
| 作用 | 标识调用方应用，用于统计和权限控制 |

> **注意**：本服务（auth-service）内部接口通过 `JwtAuthenticationFilter` 自行解析 JWT 进行认证，不依赖 `X-User-Id` 和 `X-App-Id`。这两个请求头主要用于网关与其他业务服务（如 image-service、dict-service 等）之间的身份传递。
