# 网关服务 API 文档

## 1. 服务概览

| 项 | 说明 |
|----|------|
| 服务名称 | gateway-service |
| 服务端口 | 8080 |
| 绑定地址 | 0.0.0.0 |
| 网关定位 | 微服务集群统一入口，负责身份认证、请求转发、IP 白名单、CORS 跨域 |

网关服务是整个微服务架构的统一入口层，所有外部调用必须经过网关。网关提供两种认证模式：
- **应用认证**：服务间调用，通过 HMAC-SHA256 签名换取 JWT
- **用户认证**：前端用户调用，通过 auth-service 签发的用户 JWT 访问

---

## 2. 接口总览表

### 2.1 网关自有接口

| 接口路径 | 方法 | 说明 | 是否需要鉴权 |
|----------|------|------|-------------|
| `/token` | POST | 应用 Token 签发接口（HMAC 签名换 JWT） | 否（需 HMAC 签名验证） |
| `/api/**` | * | 资源服务转发（所有内部 API） | 是（JWT） |
| `/auth/**` | * | 用户认证服务转发（登录/注册等） | 否 |

### 2.2 下游服务路由表

| 服务名称 | 服务端口 | 路由前缀 | 说明 |
|----------|----------|----------|------|
| dict-service | 8081 | `/api/provinces` | 中国省份列表 |
| dict-service | 8081 | `/api/cities` | 城市列表（按省份） |
| dict-service | 8081 | `/api/districts` | 区县列表（按城市） |
| dict-service | 8081 | `/api/towns` | 乡镇列表 |
| dict-service | 8081 | `/api/villages` | 村庄列表 |
| dict-service | 8081 | `/api/regions` | 地区路径查询 |
| dict-service | 8081 | `/api/countries` | 国家列表 |
| dict-service | 8081 | `/api/currencies` | 货币列表 |
| dict-service | 8081 | `/api/languages` | 语言列表 |
| dict-service | 8081 | `/api/timezones` | 时区列表 |
| dict-service | 8081 | `/api/dict` | 通用字典接口 |
| dict-service | 8081 | `/api/admin` | 管理接口（缓存刷新等） |
| image-service | 8082 | `/api/images` | 图片上传、列表、删除、文件访问 |
| image-service | 8082 | `/api/groups` | 图片分组管理 |
| auth-service | 8083 | `/auth` | 用户认证（注册/登录/刷新等） |
| property-service | 8085 | `/api/properties` | 房源 CRUD、搜索、上下架、审核 |
| search-service | 8086 | `/api/search` | 房源全文检索、地图找房、聚合统计 |
| analytics-service | 8087 | `/api/stats` | 数据统计、看板汇总 |
| message-service | 8088 | `/api/messages` | 消息记录查询、重试 |

---

## 3. 通用约定

### 3.1 统一响应格式

所有网关自有接口（如 `/token`）及网关错误响应均采用统一 JSON 格式：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| code | int | 业务状态码，200 表示成功 |
| message | string | 状态描述 |
| data | object/null | 响应数据，失败时为 null |

### 3.2 错误状态码总览

| HTTP 状态码 | 业务 code | 错误信息 | 触发场景 |
|------------|-----------|----------|---------|
| 401 | 401 | Timestamp expired | 请求时间戳超出容忍范围 |
| 401 | 401 | Unknown appId | 应用 ID 不存在 |
| 401 | 401 | Invalid signature | HMAC 签名验证失败 |
| 401 | 401 | Nonce reused | nonce 在容忍窗口内重复使用 |
| 401 | 401 | Missing or invalid Authorization header | 缺少或格式错误的 Authorization 头 |
| 401 | 401 | Invalid or expired token | JWT 无效或已过期 |
| 403 | 403 | IP address not allowed: {ip} | IP 不在白名单中 |
| 404 | 404 | No route found | 请求路径未匹配任何路由 |
| 500 | 500 | Token generation failed (null) | JWT 签发失败 |
| 500 | 500 | Gateway forwarding error | 网关转发未知异常 |
| 503 | 503 | Backend service temporarily unavailable, please try later | 后端服务不可达 |

### 3.3 HTTP 方法支持

- `/api/**` 和 `/auth/**` 路径支持所有 HTTP 方法（GET、POST、PUT、DELETE、PATCH 等）
- `/token` 仅支持 POST 方法

---

## 4. Token 签发接口 (POST /token)

### 4.1 接口说明

应用调用方通过 HMAC-SHA256 签名验证身份，换取短期有效的应用级 JWT Token。

**接口地址**：`POST /token`

**Content-Type**：`application/x-www-form-urlencoded`

### 4.2 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
|--------|------|------|------|------|
| appId | form | String | 是 | 应用唯一标识 |
| timestamp | form | long | 是 | 当前 Unix 时间戳（秒），与服务器时间偏差不能超过 `hmac.timestamp-tolerance`（默认 300 秒） |
| nonce | form | String | 是 | 随机字符串，用于防止重放攻击，建议使用 UUID（去掉横线）；同一 nonce 在容忍窗口内只能使用一次 |
| sign | form | String | 是 | HMAC-SHA256 签名（Base64 编码）；**必须进行 URL 编码**，因为 Base64 可能包含 `+`、`/`、`=` 特殊字符 |

### 4.3 签名计算方式

```
payload = appId + ":" + timestamp + ":" + nonce
sign    = Base64(HMAC-SHA256(payload, appSecret))
```

> **重要提示**：`sign` 是 Base64 字符串，可能包含 `+`、`/`、`=` 等特殊字符。在 `application/x-www-form-urlencoded` 表单中提交时，**必须**对 `sign` 进行 URL 编码，否则 `+` 会被解析为空格，导致签名校验失败。使用 `URLSearchParams`（JS）或 `URLEncoder.encode`（Java）会自动处理编码。

### 4.4 成功响应 (200)

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJteS1iYWNrZW5kLXN5c3RlbSIsInR5cGUiOiJhcHAiLCJpYXQiOjE3MDAwMDAwMDAsImV4cCI6MTcwMDAwNzIwMH0.xxx"
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| data.token | String | 应用 JWT Token，有效期 2 小时 |

**JWT Payload 结构**：

```json
{
  "sub": "my-backend-system",
  "type": "app",
  "iat": 1700000000,
  "exp": 1700007200
}
```

| Claim | 说明 |
|-------|------|
| sub | 应用 ID（appId） |
| type | 固定为 `app`，标识为应用 Token |
| iat | 签发时间（Unix 秒时间戳） |
| exp | 过期时间（Unix 秒时间戳），默认签发后 2 小时 |

### 4.5 失败响应

**时间戳过期 (401)**：
```json
{
  "code": 401,
  "message": "Timestamp expired",
  "data": null
}
```

**未知 appId (401)**：
```json
{
  "code": 401,
  "message": "Unknown appId",
  "data": null
}
```

**签名无效 (401)**：
```json
{
  "code": 401,
  "message": "Invalid signature",
  "data": null
}
```

**Nonce 重放 (401)**：
```json
{
  "code": 401,
  "message": "Nonce reused",
  "data": null
}
```

---

## 5. 资源访问接口 (/api/**)

### 5.1 接口说明

所有 `/api/**` 路径的请求都会被网关转发到对应的后端服务。请求必须携带有效的 JWT Token，且调用方 IP 必须在白名单中（若启用）。

### 5.2 请求方式

- 支持所有 HTTP 方法：GET、POST、PUT、DELETE、PATCH 等
- 请求体、请求头、查询参数均原样透传给后端服务

### 5.3 请求头

```
Authorization: Bearer <token>
```

Token 可以是**应用 Token**（HS256，`type=app`）或**用户 Token**（RS256 或 HS256，`type=user`）。

### 5.4 请求头注入说明

网关在校验 Token 通过后，会根据 Token 类型向**转发到后端**的请求中注入以下 Header，后端服务可直接读取：

| Header 名称 | 来源 Token 类型 | 说明 |
|-------------|---------------|------|
| `X-App-Id` | 应用 Token (type=app) | 调用方应用 ID，即 JWT 的 `sub` 字段 |
| `X-User-Id` | 用户 Token (type=user) | 登录用户 ID，即 JWT 的 `sub` 字段 |
| `X-Owner-Id` | 用户 Token (type=user) | 同 `X-User-Id`，用于图片服务等需要标识资源所有者的场景 |

> 两种 Token 互斥：同一请求只会注入其中一组 Header。后端服务可根据请求是否携带 `X-User-Id` 判断是否为用户调用。

### 5.5 请求头过滤

网关在转发时会过滤以下请求头：
- `host`：避免与后端服务的 Host 头冲突
- `authorization`：避免 JWT Token 传递到后端（身份信息已通过 X-* 头注入）

### 5.6 响应说明

- 网关会将后端服务的响应（状态码、响应头、响应体）原样返回给调用方
- 网关会过滤 hop-by-hop 响应头（`transfer-encoding`、`content-length`），避免与 Tomcat 自身响应头管理冲突

### 5.7 示例请求

```bash
# 携带应用 Token 访问字典服务
curl -X GET "http://localhost:8080/api/cities?province=广东省" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.xxx"

# 携带用户 Token 访问图片服务
curl -X GET "http://localhost:8080/api/images" \
  -H "Authorization: Bearer <用户 JWT>"
```

---

## 6. 用户认证服务接口 (/auth/**)

### 6.1 接口说明

网关将 `/auth/**` 路径直接转发给用户认证服务（auth-service），**无需 JWT 鉴权**（用户此时还未登录，没有 Token）。

> IP 白名单仍然生效（若启用）。

### 6.2 常见接口

| 路径 | 方法 | 说明 |
|------|------|------|
| `POST /auth/login` | POST | 用户登录，auth-service 校验账号密码后签发用户 Token |
| `POST /auth/register` | POST | 用户注册 |
| `POST /auth/refresh` | POST | 刷新用户 Token |
| `POST /auth/logout` | POST | 用户登出 |

> 具体接口定义由 auth-service 提供，网关仅做透明转发，不参与业务逻辑。

---

## 7. 用户 Token 说明

### 7.1 两种模式

网关支持两种用户 Token 验签模式：

**默认模式（HS256 共享密钥）**：
- 网关与 auth-service 共用 `jwt.secret-base64`
- 使用 HS256 对称密钥签名验签
- 部署简单，适合中小规模系统

**可选模式（RS256 非对称密钥）**：
- auth-service 使用 RSA 私钥签发
- 网关使用 RSA 公钥验签（配置 `jwt.user-public-key`）
- 适合跨团队、高级别安全隔离场景

### 7.2 Token 格式

```json
{
  "sub": "user-123",
  "type": "user",
  "iat": 1700000000,
  "exp": 1700007200
}
```

| Claim | 说明 |
|-------|------|
| sub | 用户唯一标识（用户 ID） |
| type | 固定为 `user`，用于区分应用 Token |
| iat | 签发时间（Unix 秒时间戳） |
| exp | 过期时间（Unix 秒时间戳） |

### 7.3 使用示例

```bash
# 携带用户 Token 访问资源
curl -X GET "http://localhost:8080/api/cities?province=广东省" \
  -H "Authorization: Bearer <用户 JWT>"
```

网关验签通过后，会向转发请求注入：
- `X-User-Id: user-123`
- `X-Owner-Id: user-123`

---

## 8. 鉴权流程说明

### 8.1 HMAC 签名验证流程（获取应用 Token）

```
调用方                      网关
  |                          |
  |  POST /token             |
  |  (appId, timestamp,      |
  |   nonce, sign)           |
  |------------------------->|
  |                          | 1. 校验时间戳是否在容忍范围内
  |                          | 2. 根据 appId 查找 appSecret
  |                          | 3. 计算 HMAC-SHA256 签名
  |                          | 4. 常数时间比较签名
  |                          | 5. 检查 nonce 是否已使用
  |                          | 6. 缓存 nonce（防重放）
  |                          | 7. 签发应用 JWT (HS256)
  |                          |
  |  { code:200, data:       |
  |    { token: "..." } }    |
  |<-------------------------|
```

### 8.2 JWT 验证流程（访问资源）

```
调用方                      网关                      后端服务
  |                          |                          |
  |  GET /api/xxx            |                          |
  |  Authorization: Bearer   |                          |
  |  <token>                 |                          |
  |------------------------->|                          |
  |                          | 1. IP 白名单校验         |
  |                          | 2. 提取 Authorization 头 |
  |                          | 3. 解析 JWT              |
  |                          |    - 根据 alg 选择密钥   |
  |                          |    - HS256 → hmacKey     |
  |                          |    - RS256 → rsaPublicKey|
  |                          | 4. 验证签名和有效期      |
  |                          | 5. 提取 type 和 sub      |
  |                          | 6. 注入 X-App-Id /       |
  |                          |    X-User-Id Header      |
  |                          |------------------------->|
  |                          |  转发请求                 |
  |                          |<-------------------------|
  |                          |  透传响应                 |
  |<-------------------------|                          |
```

---

## 9. CORS 跨域支持

网关已内置 CORS 支持，前端浏览器可直接跨域调用。

### 9.1 CORS 配置

| 配置项 | 值 | 说明 |
|--------|-----|------|
| 允许的来源 | `*` | 所有来源（支持凭证模式） |
| 允许的方法 | GET、POST、PUT、DELETE、OPTIONS | 常用 HTTP 方法 |
| 允许的请求头 | `*` | 所有请求头 |
| 允许凭证 | true | 支持携带 Cookie / Authorization |
| 预检缓存时间 | 3600 秒 | 1 小时 |

### 9.2 预检请求（OPTIONS）

浏览器在发送实际请求前，会先发送 OPTIONS 预检请求。网关已对 `/**` 的 OPTIONS 请求放行，无需鉴权，会自动返回 CORS 头。

### 9.3 示例响应头

```
Access-Control-Allow-Origin: http://localhost:3000
Access-Control-Allow-Methods: GET,POST,PUT,DELETE,OPTIONS
Access-Control-Allow-Headers: Authorization
Access-Control-Allow-Credentials: true
Access-Control-Max-Age: 3600
```

> 生产环境建议将 `allowedOriginPatterns` 限制为具体的前端域名，避免开放给所有来源。

---

## 10. 调用示例

### 10.1 JavaScript / Node.js 示例

```javascript
const crypto = require('crypto');

const APP_ID = 'my-backend-system';
const APP_SECRET = 'your-app-secret';
const GATEWAY_URL = 'http://localhost:8080';

function generateSign(appId, timestamp, nonce, appSecret) {
    const payload = `${appId}:${timestamp}:${nonce}`;
    const hmac = crypto.createHmac('sha256', appSecret);
    hmac.update(payload);
    return hmac.digest('base64');
}

async function getToken() {
    const timestamp = Math.floor(Date.now() / 1000);
    const nonce = crypto.randomUUID().replace(/-/g, '');
    const sign = generateSign(APP_ID, timestamp, nonce, APP_SECRET);

    const response = await fetch(`${GATEWAY_URL}/token`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: new URLSearchParams({
            appId: APP_ID,
            timestamp: timestamp.toString(),
            nonce: nonce,
            sign: sign,
        }),
    });

    const data = await response.json();
    if (data.code === 200) {
        return data.data.token;
    }
    throw new Error(`获取 Token 失败: ${data.message}`);
}

async function callApi(path, method = 'GET', body = null) {
    const token = await getToken();
    
    const options = {
        method: method,
        headers: {
            'Authorization': `Bearer ${token}`,
        },
    };

    if (body) {
        options.headers['Content-Type'] = 'application/json';
        options.body = JSON.stringify(body);
    }

    const response = await fetch(`${GATEWAY_URL}${path}`, options);
    return await response.json();
}

// 使用示例
callApi('/api/cities?province=广东省', 'GET')
    .then(console.log)
    .catch(console.error);
```

### 10.2 Java 示例

```java
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

public class GatewayClient {
    private static final String APP_ID = "my-backend-system";
    private static final String APP_SECRET = "your-app-secret";
    private static final String GATEWAY_URL = "http://localhost:8080";

    public static String generateSign(String appId, long timestamp, String nonce) throws Exception {
        String payload = appId + ":" + timestamp + ":" + nonce;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(APP_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(raw);
    }

    public static void main(String[] args) throws Exception {
        long timestamp = System.currentTimeMillis() / 1000;
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String sign = generateSign(APP_ID, timestamp, nonce);
        String encodedSign = URLEncoder.encode(sign, StandardCharsets.UTF_8);

        String body = String.format("appId=%s&timestamp=%d&nonce=%s&sign=%s",
                APP_ID, timestamp, nonce, encodedSign);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GATEWAY_URL + "/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());
        System.out.println(response.body());
    }
}
```

### 10.3 PowerShell 示例

```powershell
$GATEWAY_URL = "http://127.0.0.1:8080"
$APP_ID = "my-backend-system"
$APP_SECRET = "test-secret-123"

# 1. 计算签名
$timestamp = [math]::Floor([datetime]::UtcNow.Subtract([datetime]::new(1970, 1, 1)).TotalSeconds)
$nonce = ([guid]::NewGuid()).ToString("N")
$payload = "$APP_ID`:$timestamp`:$nonce"
$hmac = New-Object System.Security.Cryptography.HMACSHA256
$hmac.Key = [System.Text.Encoding]::UTF8.GetBytes($APP_SECRET)
$sign = [Convert]::ToBase64String($hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($payload)))
$encodedSign = [System.Uri]::EscapeDataString($sign)

# 2. 获取 Token
$resp = Invoke-RestMethod -Uri "$GATEWAY_URL/token" -Method POST `
    -ContentType "application/x-www-form-urlencoded" `
    -Body "appId=$APP_ID&timestamp=$timestamp&nonce=$nonce&sign=$encodedSign"
$token = $resp.data.token

# 3. 调用资源接口
$province = [System.Uri]::EscapeDataString("广东省")
$result = Invoke-RestMethod -Uri "$GATEWAY_URL/api/cities?province=$province" -Method GET `
    -Headers @{ "Authorization" = "Bearer $token" }
$result | ConvertTo-Json -Depth 5
```

---

## 11. Token 缓存策略

为了避免每次请求都重新获取 Token，建议调用方实现以下缓存策略：

1. **缓存 Token**：获取 Token 后，将其存储在内存或本地存储中
2. **过期时间**：Token 的有效期为 2 小时
3. **提前刷新**：在 Token 过期前 5 分钟重新获取新的 Token
4. **失效处理**：当收到 401 响应时，立即刷新 Token 并重试请求（最多重试 1 次）

**示例缓存实现（JavaScript）**：

```javascript
let cachedToken = null;
let tokenExpireTime = 0;

async function getTokenWithCache() {
    const now = Date.now();
    if (cachedToken && now < tokenExpireTime - 5 * 60 * 1000) {
        return cachedToken;
    }
    const token = await getToken();
    cachedToken = token;
    tokenExpireTime = now + 2 * 60 * 60 * 1000;
    return token;
}
```
