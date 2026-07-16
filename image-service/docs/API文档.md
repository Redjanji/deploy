# Image Service API 文档

## 一、服务概览

### 1.1 基础信息

| 项目 | 说明 |
| :--- | :--- |
| 服务名称 | image-service |
| 服务端口 | 8082 |
| 监听地址 | 127.0.0.1（仅本地监听，通过网关转发） |
| API 前缀 | `/api/images`（图片管理）、`/api/groups`（分组管理） |
| 数据格式 | JSON |
| 认证方式 | 通过 `X-App-Id` 请求头进行应用级隔离，`X-Owner-Id` 请求头区分用户私有图片 |

### 1.2 接口总览表

#### 图片管理接口

| 方法 | 路径 | 说明 | 请求头 |
| :--- | :--- | :--- | :--- |
| POST | `/api/images/upload` | 上传图片，自动生成多尺寸缩略图 | X-App-Id（可选）、X-Owner-Id（可选） |
| GET | `/api/images` | 分页查询图片列表 | X-App-Id（可选）、X-Owner-Id（可选） |
| DELETE | `/api/images/{id}` | 删除图片（软删除） | X-App-Id（可选）、X-Owner-Id（可选） |
| GET | `/api/images/{appId}/{date}/{sizeType}/{filename}` | 本地访问图片（无 CDN 时使用） | - |

#### 分组管理接口

| 方法 | 路径 | 说明 | 请求头 |
| :--- | :--- | :--- | :--- |
| POST | `/api/groups` | 创建分组（幂等） | X-App-Id（必填） |
| GET | `/api/groups` | 查询分组列表 | X-App-Id（必填） |
| POST | `/api/groups/{groupId}/images` | 添加图片到分组 | X-App-Id（必填）、X-Owner-Id（可选） |
| GET | `/api/groups/{groupId}/images` | 获取分组图片列表 | X-App-Id（必填）、X-Owner-Id（可选） |

---

## 二、通用约定

### 2.1 请求头约定

| 头名称 | 必填 | 说明 |
| :--- | :--- | :--- |
| `X-App-Id` | 图片接口可选，分组接口必填 | 应用标识，用于隔离不同应用的图片数据。不传或为空则默认为 `default` |
| `X-Owner-Id` | 否 | 用户 ID，用于区分用户私有图片。不传则按公开图片处理 |
| `Content-Type` | 否 | 请求体类型，文件上传时使用 `multipart/form-data`，JSON 请求使用 `application/json` |

> **私有图片机制说明**：
> - 上传时传 `X-Owner-Id`，图片标记为该用户的私有图片
> - 查询列表时传 `X-Owner-Id`，返回公开图片 + 该用户私有图片；不传则只返回公开图片
> - 删除/操作私有图片时需传 `X-Owner-Id`，且必须与图片归属用户一致
> - 该机制为后续 OAuth2 网关集成预留，网关实现后由网关注入用户身份

### 2.2 统一响应结构

所有接口返回统一的 `Result` 包装结构：

```json
{
  "code": 200,
  "message": "ok",
  "data": {}
}
```

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `code` | int | 状态码，200 表示成功 |
| `message` | string | 状态信息 |
| `data` | any | 响应数据，成功时返回业务数据，失败时为 null |

### 2.3 错误码说明

| 错误码 | HTTP 状态码 | 说明 |
| :--- | :--- | :--- |
| 200 | 200 | 成功 |
| 400 | 400 | 请求参数错误或安全校验失败 |
| 403 | 403 | 无权操作（应用不匹配、操作他人私有图片等） |
| 500 | 500 | 服务器内部错误 |

**常见错误信息：**

| 错误信息 | 触发场景 |
| :--- | :--- |
| `不支持的文件后缀: xxx` | 文件后缀不在白名单内 |
| `文件过大` | 文件大小超过限制（默认 10MB） |
| `文件内容非图片，检测为: xxx` | 文件内容与扩展名不一致 |
| `文件头魔数不匹配` | 文件头不符合图片格式 |
| `无法解析为有效图片` | 无法解码为有效图片 |
| `无权操作` | 图片不存在或应用不匹配 |
| `无权操作他人私有图片` | 操作不属于自己的私有图片 |
| `无权操作该分组` | 分组不存在或应用不匹配 |
| `图片不存在或无权操作` | 图片不存在或应用不匹配 |

### 2.4 数据格式约定

- 所有时间字段使用 ISO 8601 格式：`2024-01-01T12:00:00`
- 图片尺寸单位：像素（px）
- 文件大小单位：字节（byte）

---

## 三、图片管理接口

### 3.1 上传图片

**POST** `/api/images/upload`

上传图片并自动转换为 WebP 格式，同时生成多尺寸缩略图。

#### 请求参数

| 参数 | 位置 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| `file` | form-data | MultipartFile | 是 | 图片文件 |
| `X-App-Id` | Header | String | 否 | 应用标识，默认为 `default` |
| `X-Owner-Id` | Header | Long | 否 | 用户 ID。传则上传为该用户的私有图片，不传为公开图片 |

#### 支持的图片格式

| 格式 | 后缀 | MIME 类型 |
| :--- | :--- | :--- |
| JPEG | jpg, jpeg | image/jpeg |
| PNG | png | image/png |
| GIF | gif | image/gif |
| WebP | webp | image/webp |
| BMP | bmp | image/bmp |

#### 安全检查流程

上传时会经过 5 层安全检查：
1. **后缀白名单检查**：验证文件扩展名是否在允许列表中
2. **文件大小检查**：验证文件大小不超过限制（默认 10MB）
3. **MIME 类型检测**：使用 Apache Tika 检测文件真实类型
4. **魔数校验**：检查文件头字节是否符合图片格式
5. **可解析性校验**：尝试用 ImageIO 解码，验证为有效图片

#### 请求示例

```bash
# 上传公开图片
curl -X POST http://localhost:8082/api/images/upload \
  -H "X-App-Id: test-app" \
  -F "file=@photo.jpg"

# 上传用户私有图片（如用户头像）
curl -X POST http://localhost:8082/api/images/upload \
  -H "X-App-Id: test-app" \
  -H "X-Owner-Id: 1001" \
  -F "file=@avatar.jpg"
```

#### 成功响应（200）

```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "id": 1,
    "url": "https://cdn.example.com/images/test-app/20240101/original/abc123.webp",
    "originUrl": "https://cdn.example.com/images/test-app/20240101/original/abc123.webp",
    "largeUrl": "https://cdn.example.com/images/test-app/20240101/large/def456.webp",
    "mediumUrl": "https://cdn.example.com/images/test-app/20240101/medium/ghi789.webp",
    "smallUrl": "https://cdn.example.com/images/test-app/20240101/small/jkl012.webp",
    "width": 1920,
    "height": 1080,
    "fileSize": 123456,
    "mimeType": "image/webp"
  }
}
```

#### 响应字段说明

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | Long | 图片 ID |
| `url` | String | 默认图片 URL（原图） |
| `originUrl` | String | 原图 URL（WebP 格式） |
| `largeUrl` | String | 大图 URL（宽度 1280px，等比缩放） |
| `mediumUrl` | String | 中图 URL（宽度 640px，等比缩放） |
| `smallUrl` | String | 小图 URL（宽度 200px，等比缩放） |
| `width` | int | 原图宽度（像素） |
| `height` | int | 原图高度（像素） |
| `fileSize` | Long | 原图大小（字节，WebP 格式） |
| `mimeType` | String | MIME 类型，统一为 `image/webp` |

#### 失败响应（400）

```json
{
  "code": 400,
  "message": "不支持的文件后缀: txt",
  "data": null
}
```

---

### 3.2 查询图片列表

**GET** `/api/images`

分页查询指定应用的图片列表，按创建时间倒序排列。

#### 请求参数

| 参数 | 位置 | 类型 | 必填 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `X-App-Id` | Header | String | 否 | default | 应用标识 |
| `X-Owner-Id` | Header | Long | 否 | - | 用户 ID。传则返回公开+该用户私有图片，不传只返回公开图片 |
| `page` | Query | int | 否 | 1 | 页码（从 1 开始） |
| `size` | Query | int | 否 | 20 | 每页数量 |

#### 查询规则

- 只返回状态为 `READY` 的图片
- 按 `created_at` 倒序排列
- 不传 `X-Owner-Id`：只查询公开图片（`owner_id IS NULL`）
- 传 `X-Owner-Id`：查询公开图片 + 该用户私有图片（`owner_id IS NULL OR owner_id = ?`）

#### 请求示例

```bash
# 查询公开图片
curl "http://localhost:8082/api/images?page=1&size=10" \
  -H "X-App-Id: test-app"

# 查询公开图片 + 当前用户的私有图片
curl "http://localhost:8082/api/images?page=1&size=10" \
  -H "X-App-Id: test-app" \
  -H "X-Owner-Id: 1001"
```

#### 成功响应（200）

```json
{
  "code": 200,
  "message": "ok",
  "data": [
    {
      "id": 1,
      "url": "https://cdn.example.com/images/test-app/20240101/original/abc123.webp",
      "originUrl": "https://cdn.example.com/images/test-app/20240101/original/abc123.webp",
      "largeUrl": "https://cdn.example.com/images/test-app/20240101/large/def456.webp",
      "mediumUrl": "https://cdn.example.com/images/test-app/20240101/medium/ghi789.webp",
      "smallUrl": "https://cdn.example.com/images/test-app/20240101/small/jkl012.webp",
      "width": 1920,
      "height": 1080,
      "fileSize": 123456,
      "mimeType": "image/webp"
    }
  ]
}
```

---

### 3.3 删除图片

**DELETE** `/api/images/{id}`

软删除指定图片（标记为 `DELETED` 状态），不再出现在查询结果中。

#### 请求参数

| 参数 | 位置 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| `id` | Path | Long | 是 | 图片 ID |
| `X-App-Id` | Header | String | 否 | 应用标识，需与图片所属应用一致 |
| `X-Owner-Id` | Header | Long | 否 | 用户 ID。删除私有图片时必传，且需与图片归属用户一致 |

#### 权限规则

- 图片不存在或应用不匹配 → 返回 403 "无权操作"
- 图片为私有图片但 `X-Owner-Id` 不匹配 → 返回 403 "无权操作他人私有图片"
- 公开图片（`owner_id IS NULL`）：应用内可删除
- 私有图片（`owner_id` 有值）：仅归属用户可删除

#### 请求示例

```bash
# 删除公开图片
curl -X DELETE http://localhost:8082/api/images/1 \
  -H "X-App-Id: test-app"

# 删除自己的私有图片
curl -X DELETE http://localhost:8082/api/images/2 \
  -H "X-App-Id: test-app" \
  -H "X-Owner-Id: 1001"
```

#### 成功响应（200）

```json
{
  "code": 200,
  "message": "ok",
  "data": null
}
```

#### 失败响应（403）

```json
{
  "code": 403,
  "message": "无权操作他人私有图片",
  "data": null
}
```

> **注意**：删除为软删除，图片记录标记为 `DELETED` 状态，物理文件仍保留在 MinIO 中。

---

### 3.4 本地图片访问

**GET** `/api/images/{appId}/{date}/{sizeType}/{filename}`

当未配置 CDN 时，通过此接口直接从 MinIO 读取图片并返回。

#### 请求参数

| 参数 | 位置 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| `appId` | Path | String | 是 | 应用标识 |
| `date` | Path | String | 是 | 日期（格式：yyyyMMdd，如 20240101） |
| `sizeType` | Path | String | 是 | 尺寸类型：original / large / medium / small |
| `filename` | Path | String | 是 | 文件名（UUID.webp） |

#### 响应

- 成功：返回图片二进制数据，Content-Type 为 `image/webp`
- 失败：返回 404 Not Found

---

## 四、分组管理接口

### 4.1 创建分组

**POST** `/api/groups`

创建一个新的图片分组。支持幂等创建，当检测到同名分组已存在时直接返回已有分组。

#### 请求参数

| 参数 | 位置 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| `X-App-Id` | Header | String | 是 | 应用标识 |

#### 请求体（JSON）

| 字段 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| `name` | String | 是 | 分组名称（同一应用内唯一） |
| `description` | String | 否 | 分组描述 |

#### 请求示例

```bash
curl -X POST http://localhost:8082/api/groups \
  -H "X-App-Id: test-app" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "首页轮播",
    "description": "网站首页轮播图"
  }'
```

#### 成功响应（200）

```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "id": 1,
    "appId": "test-app",
    "name": "首页轮播",
    "description": "网站首页轮播图",
    "sortOrder": 0,
    "createdAt": "2024-01-01T12:00:00"
  }
}
```

#### 响应字段说明

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | Long | 分组 ID |
| `appId` | String | 应用标识 |
| `name` | String | 分组名称 |
| `description` | String | 分组描述 |
| `sortOrder` | Integer | 排序号 |
| `createdAt` | String | 创建时间（ISO 格式） |

> **幂等创建说明**：当使用相同的 `appId` 和 `name` 再次调用此接口时，不会抛出重复创建异常，而是直接返回已存在的分组。

---

### 4.2 查询分组列表

**GET** `/api/groups`

查询指定应用的所有分组，按 `sort_order` 升序排列。

#### 请求参数

| 参数 | 位置 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| `X-App-Id` | Header | String | 是 | 应用标识 |

#### 请求示例

```bash
curl http://localhost:8082/api/groups \
  -H "X-App-Id: test-app"
```

#### 成功响应（200）

```json
{
  "code": 200,
  "message": "ok",
  "data": [
    {
      "id": 1,
      "appId": "test-app",
      "name": "首页轮播",
      "description": "网站首页轮播图",
      "sortOrder": 0,
      "createdAt": "2024-01-01T12:00:00"
    },
    {
      "id": 2,
      "appId": "test-app",
      "name": "商品展示",
      "description": "商品详情页图片",
      "sortOrder": 1,
      "createdAt": "2024-01-02T10:00:00"
    }
  ]
}
```

---

### 4.3 添加图片到分组

**POST** `/api/groups/{groupId}/images`

将指定图片添加到分组中。

#### 请求参数

| 参数 | 位置 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| `groupId` | Path | Long | 是 | 分组 ID |
| `X-App-Id` | Header | String | 是 | 应用标识 |
| `X-Owner-Id` | Header | Long | 否 | 用户 ID。添加私有图片到分组时必传，且需与图片归属用户一致 |

#### 请求体（JSON）

| 字段 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| `imageId` | Long | 是 | 图片 ID |
| `sortOrder` | Integer | 否 | 排序号，默认 0 |

#### 权限规则

- 分组不存在或应用不匹配 → 403 "无权操作该分组"
- 图片不存在或应用不匹配 → 400 "图片不存在或无权操作"
- 图片为私有图片但 `X-Owner-Id` 不匹配 → 403 "无权操作他人私有图片"

#### 请求示例

```bash
curl -X POST http://localhost:8082/api/groups/1/images \
  -H "X-App-Id: test-app" \
  -H "Content-Type: application/json" \
  -d '{
    "imageId": 1,
    "sortOrder": 1
  }'
```

#### 成功响应（200）

```json
{
  "code": 200,
  "message": "ok",
  "data": null
}
```

---

### 4.4 获取分组图片

**GET** `/api/groups/{groupId}/images`

获取分组中的图片列表，可指定返回的图片尺寸。

#### 请求参数

| 参数 | 位置 | 类型 | 必填 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `groupId` | Path | Long | 是 | - | 分组 ID |
| `X-App-Id` | Header | String | 是 | - | 应用标识 |
| `X-Owner-Id` | Header | Long | 否 | - | 用户 ID。传则返回分组中公开+该用户私有图片，不传只返回公开图片 |
| `sizeType` | Query | String | 否 | large | 图片尺寸类型 |

**sizeType 取值：**

| 值 | 说明 |
| :--- | :--- |
| `original` | 原图 |
| `large` | 大图（宽度 1280px） |
| `medium` | 中图（宽度 640px） |
| `small` | 小图（宽度 200px） |

> 指定 sizeType 后，返回的 ImageVO 中 `url` 字段会替换为对应尺寸的 URL，但各尺寸 URL 仍保留在各自字段中。

#### 请求示例

```bash
# 获取分组大图（仅公开图片）
curl http://localhost:8082/api/groups/1/images \
  -H "X-App-Id: test-app"

# 获取分组小图（公开+该用户私有图片）
curl "http://localhost:8082/api/groups/1/images?sizeType=small" \
  -H "X-App-Id: test-app" \
  -H "X-Owner-Id: 1001"
```

#### 成功响应（200）

```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "id": 1,
    "name": "首页轮播",
    "description": "网站首页轮播图",
    "sortOrder": 0,
    "images": [
      {
        "id": 1,
        "url": "https://cdn.example.com/images/test-app/20240101/large/def456.webp",
        "originUrl": "https://cdn.example.com/images/test-app/20240101/original/abc123.webp",
        "largeUrl": "https://cdn.example.com/images/test-app/20240101/large/def456.webp",
        "mediumUrl": "https://cdn.example.com/images/test-app/20240101/medium/ghi789.webp",
        "smallUrl": "https://cdn.example.com/images/test-app/20240101/small/jkl012.webp",
        "width": 1920,
        "height": 1080,
        "fileSize": 123456,
        "mimeType": "image/webp"
      }
    ]
  }
}
```

#### 响应字段说明

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | Long | 分组 ID |
| `name` | String | 分组名称 |
| `description` | String | 分组描述 |
| `sortOrder` | Integer | 排序号 |
| `images` | Array | 图片列表，按 `sort_order` 排序 |

---

## 五、图片 URL 规则说明

### 5.1 URL 组成

图片 URL 由基础路径 + 存储路径组成：

```
{baseUrl}{appId}/{date}/{sizeType}/{uuid}.webp
```

**示例：**
```
https://cdn.example.com/images/test-app/20240101/original/abc123-def456.webp
```

### 5.2 路径各段说明

| 段 | 说明 | 示例 |
| :--- | :--- | :--- |
| `baseUrl` | 基础路径，CDN 模式为 CDN 域名，本地模式为 `/api/images/` | `https://cdn.example.com/images/` 或 `/api/images/` |
| `appId` | 应用标识 | `test-app` |
| `date` | 上传日期，格式为 yyyyMMdd | `20240101` |
| `sizeType` | 尺寸类型 | `original` / `large` / `medium` / `small` |
| `uuid` | 随机 UUID，保证文件名唯一 | `abc123-def456-...` |

### 5.3 基础路径选择规则

- 配置了 `image.cdn-url` 且不为空 → 使用 CDN 地址
- 未配置或为空 → 使用 `image.local-url`（默认 `/api/images/`）

```java
public String getBaseUrl() {
    if (config.getCdnUrl() != null && !config.getCdnUrl().isEmpty()) {
        return config.getCdnUrl();
    }
    return config.getLocalUrl();
}
```

### 5.4 尺寸类型说明

| 尺寸类型 | 宽度 | 说明 |
| :--- | :--- | :--- |
| `original` | 原始宽度 | 原图转换为 WebP 格式，不改变尺寸 |
| `large` | 1280px | 等比缩放到宽度 1280px |
| `medium` | 640px | 等比缩放到宽度 640px |
| `small` | 200px | 等比缩放到宽度 200px |

> 缩略图尺寸可通过 `image.thumbnail-widths` 配置项调整。

---

## 六、完整调用示例

### 6.1 完整流程（curl）

```bash
# 1. 创建分组
curl -X POST http://localhost:8082/api/groups \
  -H "X-App-Id: test-app" \
  -H "Content-Type: application/json" \
  -d '{"name": "首页轮播"}'

# 2. 上传图片
curl -X POST http://localhost:8082/api/images/upload \
  -H "X-App-Id: test-app" \
  -F "file=@photo1.jpg"

# 3. 将图片添加到分组
curl -X POST http://localhost:8082/api/groups/1/images \
  -H "X-App-Id: test-app" \
  -H "Content-Type: application/json" \
  -d '{"imageId": 1, "sortOrder": 1}'

# 4. 获取分组图片（小图）
curl "http://localhost:8082/api/groups/1/images?sizeType=small" \
  -H "X-App-Id: test-app"

# 5. 查询图片列表
curl "http://localhost:8082/api/images?page=1&size=10" \
  -H "X-App-Id: test-app"

# 6. 删除图片
curl -X DELETE http://localhost:8082/api/images/1 \
  -H "X-App-Id: test-app"
```

### 6.2 前端调用示例（JavaScript）

```javascript
class ImageServiceClient {
  constructor(baseUrl, appId, ownerId = null) {
    this.baseUrl = baseUrl;
    this.headers = { 'X-App-Id': appId };
    if (ownerId) {
      this.headers['X-Owner-Id'] = ownerId;
    }
  }

  async upload(file) {
    const formData = new FormData();
    formData.append('file', file);
    const response = await fetch(`${this.baseUrl}/api/images/upload`, {
      method: 'POST',
      headers: { ...this.headers },
      body: formData
    });
    return response.json();
  }

  async listImages(page = 1, size = 20) {
    const response = await fetch(`${this.baseUrl}/api/images?page=${page}&size=${size}`, {
      headers: this.headers
    });
    return response.json();
  }

  async deleteImage(id) {
    const response = await fetch(`${this.baseUrl}/api/images/${id}`, {
      method: 'DELETE',
      headers: this.headers
    });
    return response.json();
  }

  async createGroup(name, description = '') {
    const response = await fetch(`${this.baseUrl}/api/groups`, {
      method: 'POST',
      headers: { ...this.headers, 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, description })
    });
    return response.json();
  }

  async addImageToGroup(groupId, imageId, sortOrder = 0) {
    const response = await fetch(`${this.baseUrl}/api/groups/${groupId}/images`, {
      method: 'POST',
      headers: { ...this.headers, 'Content-Type': 'application/json' },
      body: JSON.stringify({ imageId, sortOrder })
    });
    return response.json();
  }

  async getGroupImages(groupId, sizeType = 'large') {
    const response = await fetch(`${this.baseUrl}/api/groups/${groupId}/images?sizeType=${sizeType}`, {
      headers: this.headers
    });
    return response.json();
  }
}

// 使用示例
const client = new ImageServiceClient('http://localhost:8082', 'test-app');
client.upload(fileInput.files[0]).then(console.log);
```
