# API 文档

> property-service 房源服务 API 文档
> 服务版本：0.0.1-SNAPSHOT
> 监听地址：`127.0.0.1:8085`
> Base URL：`http://127.0.0.1:8085`
> 接口前缀：`/api/properties`

> ⚠️ 对外访问需经**认证网关**（gateway-service:8080）转发，本服务不直接对公网暴露。

---

## 一、服务概览

### 1.1 服务信息

| 项目 | 值 | 说明 |
|---|---|---|
| 服务名 | property-service | 房源服务 |
| 端口 | 8085 | HTTP 服务端口 |
| 监听地址 | 127.0.0.1 | 仅本机访问，通过网关对外服务 |
| 接口前缀 | /api/properties | 所有房源接口的统一前缀 |
| 数据隔离 | appId | 按应用隔离数据，不同应用只能看到自己的房源 |

### 1.2 接口总览表

| # | 方法 | 路径 | 说明 | 是否需要登录 |
|---|---|---|---|---|
| 1 | POST | `/api/properties` | 创建房源 | 是（用户/应用） |
| 2 | PUT | `/api/properties/{id}` | 更新房源 | 是（用户/应用） |
| 3 | GET | `/api/properties/{id}` | 获取房源详情（触发浏览统计） | 否（可选） |
| 4 | GET | `/api/properties` | 搜索房源（多条件、附近搜索） | 否（可选） |
| 5 | DELETE | `/api/properties/{id}` | 删除房源 | 是（用户/应用） |
| 6 | PUT | `/api/properties/{id}/publish-status` | 更新发布状态 | 是（用户/应用） |
| 7 | PUT | `/api/properties/{id}/audit-status` | 更新审核状态 | 是（管理员） |

---

## 二、通用约定

### 2.1 请求约定

- **协议**：HTTP/1.1
- **编码**：UTF-8
- **Content-Type**：`application/json`（除特别说明外）
- **日期格式**：`yyyy-MM-dd HH:mm:ss`（如 `2026-07-08 10:30:00`）
- **时区**：Asia/Shanghai

### 2.2 请求头

| 请求头 | 类型 | 必填 | 说明 |
|---|---|---|---|
| X-App-Id | string | 否 | 应用标识，由网关从 JWT 中提取并注入。未传入时默认 `default` |
| X-User-Id | long | 否 | 用户 ID，由网关从用户 JWT 中提取并注入。用于私有房源权限校验 |

> ⚠️ `X-App-Id` 和 `X-User-Id` 由网关注入，**调用方不应直接传递**。网关会根据 JWT 自动注入。

### 2.3 统一响应结构（Result）

所有接口统一返回 JSON，结构如下：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| code | integer | 业务状态码，200 表示成功，其余为错误 |
| message | string | 状态描述 |
| data | array \| object \| null | 业务数据，错误时为 null |

### 2.4 错误码说明

| code | HTTP Status | 含义 | 触发场景 |
|---|---|---|---|
| 200 | 200 OK | 请求成功 | 正常请求 |
| 400 | 400 Bad Request | 参数错误 | 字段校验失败、图片数量超限 |
| 403 | 403 Forbidden | 无权操作 | 非 owner 修改/删除他人房源 |
| 404 | 404 Not Found | 资源不存在 | 指定 ID 的房源不存在或不属于当前应用 |
| 500 | 500 Internal Server Error | 服务内部错误 | 数据库异常等 |

### 2.5 数据格式约定

- **价格单位**：分（long 类型）
- **面积单位**：平方米（integer 类型）
- **经纬度**：decimal(10,6)，精度到小数点后 6 位
- **字典字段**：存储字典 key，需配合 dict-service 获取中文名称

---

## 三、接口详情

### 3.1 创建房源

**接口**：`POST /api/properties`

**描述**：创建一条新房源，默认为草稿状态（publishStatus=0, status=0）。创建成功后会发送 `PROPERTY_CREATE` 统计事件、同步消息和通知消息。

#### 请求头

| 请求头 | 必填 | 说明 |
|---|---|---|
| X-App-Id | 否 | 应用标识（网关注入） |
| X-User-Id | 否 | 用户 ID（网关注入，私有房源 owner） |

#### 请求参数

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| title | string | 是 | 房源标题（不能为空） |
| type | string | 是 | 房源类型（字典 key，如 `residential`） |
| price | long | 否 | 价格（单位：分） |
| rentalArea | integer | 否 | 面积（㎡） |
| rooms | string | 否 | 户型（字典 key，如 `two_room`） |
| orientation | string | 否 | 朝向（字典 key，如 `south`） |
| floor | string | 否 | 楼层描述 |
| totalFloors | integer | 否 | 总楼层 |
| address | string | 否 | 详细地址 |
| lat | number | 是 | 纬度（不能为空） |
| lng | number | 是 | 经度（不能为空） |
| provinceCode | string | 否 | 省份编码 |
| provinceName | string | 否 | 省份名称 |
| cityCode | string | 否 | 城市编码 |
| cityName | string | 否 | 城市名称 |
| districtCode | string | 否 | 区县编码 |
| districtName | string | 否 | 区县名称 |
| decoration | string | 否 | 装修情况（字典 key） |
| heatingMethod | string | 否 | 供暖方式（字典 key） |
| waterSupply | string | 否 | 供水（字典 key） |
| powerSupply | string | 否 | 供电（字典 key） |
| gasSupply | string | 否 | 供气（字典 key） |
| internet | string | 否 | 网络接入（字典 key） |
| tvService | string | 否 | 电视服务（字典 key） |
| description | string | 否 | 房源描述 |
| contactPhone | string | 否 | 联系电话 |
| agentName | string | 否 | 经纪人姓名 |
| agentTitle | string | 否 | 经纪人职务 |
| agentPhone | string | 否 | 经纪人电话 |
| imageIds | array\<long\> | 否 | 图片 ID 列表（最多 20 张，第一张为封面） |

#### 请求示例

```bash
curl -X POST "http://127.0.0.1:8085/api/properties" \
  -H "Content-Type: application/json" \
  -H "X-App-Id: my-app" \
  -H "X-User-Id: 1001" \
  -d '{
    "title": "南山区精装两房",
    "type": "residential",
    "price": 800000,
    "rentalArea": 85,
    "rooms": "two_room",
    "orientation": "south",
    "floor": "中层",
    "totalFloors": 30,
    "address": "深圳市南山区科技园",
    "lat": 22.5431,
    "lng": 113.9412,
    "provinceCode": "440000",
    "provinceName": "广东省",
    "cityCode": "440300",
    "cityName": "深圳市",
    "districtCode": "440305",
    "districtName": "南山区",
    "decoration": "fine",
    "heatingMethod": "central",
    "waterSupply": "city",
    "powerSupply": "city",
    "gasSupply": "pipe",
    "internet": "fiber",
    "tvService": "iptv",
    "description": "南北通透，近地铁，拎包入住",
    "contactPhone": "13800138000",
    "agentName": "张三",
    "agentTitle": "资深房产经纪人",
    "agentPhone": "13900139000",
    "imageIds": [1, 2, 3]
  }'
```

#### 响应参数

| 字段 | 类型 | 说明 |
|---|---|---|
| id | long | 房源 ID |
| appId | string | 应用标识 |
| title | string | 标题 |
| type | string | 房源类型 |
| price | long | 价格（分） |
| rentalArea | integer | 面积（㎡） |
| rooms | string | 户型 |
| orientation | string | 朝向 |
| floor | string | 楼层 |
| totalFloors | integer | 总楼层 |
| address | string | 地址 |
| lat | number | 纬度 |
| lng | number | 经度 |
| provinceCode | string | 省份编码 |
| provinceName | string | 省份名称 |
| cityCode | string | 城市编码 |
| cityName | string | 城市名称 |
| districtCode | string | 区县编码 |
| districtName | string | 区县名称 |
| decoration | string | 装修情况 |
| heatingMethod | string | 供暖方式 |
| waterSupply | string | 供水 |
| powerSupply | string | 供电 |
| gasSupply | string | 供气 |
| internet | string | 网络接入 |
| tvService | string | 电视服务 |
| description | string | 描述 |
| contactPhone | string | 联系电话 |
| agentName | string | 经纪人姓名 |
| agentTitle | string | 经纪人职务 |
| agentPhone | string | 经纪人电话 |
| publishStatus | integer | 发布状态（0草稿 1已发布 2已下架） |
| status | integer | 审核状态（0草稿 1通过 2待审核 3驳回） |
| hot | boolean | 是否热门 |
| featured | boolean | 是否精选 |
| ownerId | long | 发布者用户 ID |
| images | array\<string\> | 所有图片 URL（大图） |
| coverUrl | string | 封面图 URL（中图） |
| createdAt | string | 创建时间 |
| updatedAt | string | 更新时间 |

#### 响应示例（成功）

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "appId": "my-app",
    "title": "南山区精装两房",
    "type": "residential",
    "price": 800000,
    "rentalArea": 85,
    "rooms": "two_room",
    "orientation": "south",
    "floor": "中层",
    "totalFloors": 30,
    "address": "深圳市南山区科技园",
    "lat": 22.5431,
    "lng": 113.9412,
    "provinceCode": "440000",
    "provinceName": "广东省",
    "cityCode": "440300",
    "cityName": "深圳市",
    "districtCode": "440305",
    "districtName": "南山区",
    "decoration": "fine",
    "heatingMethod": "central",
    "waterSupply": "city",
    "powerSupply": "city",
    "gasSupply": "pipe",
    "internet": "fiber",
    "tvService": "iptv",
    "description": "南北通透，近地铁，拎包入住",
    "contactPhone": "13800138000",
    "agentName": "张三",
    "agentTitle": "资深房产经纪人",
    "agentPhone": "13900139000",
    "publishStatus": 0,
    "status": 0,
    "hot": false,
    "featured": false,
    "ownerId": 1001,
    "images": [
      "https://cdn.example.com/images/1/large.webp",
      "https://cdn.example.com/images/2/large.webp",
      "https://cdn.example.com/images/3/large.webp"
    ],
    "coverUrl": "https://cdn.example.com/images/1/medium.webp",
    "createdAt": "2026-07-08 10:30:00",
    "updatedAt": "2026-07-08 10:30:00"
  }
}
```

#### 响应示例（失败 - 参数校验错误）

```json
{
  "code": 400,
  "message": "title 标题不能为空, type 房源类型不能为空",
  "data": null
}
```

---

### 3.2 更新房源

**接口**：`PUT /api/properties/{id}`

**描述**：更新房源信息。仅 owner 或管理员可操作。传入的字段为 null 时表示不更新该字段。若提供 `imageIds`，则全量替换图片关联。

#### 路径参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | long | 是 | 房源 ID |

#### 请求头

| 请求头 | 必填 | 说明 |
|---|---|---|
| X-App-Id | 否 | 应用标识（网关注入） |
| X-User-Id | 否 | 用户 ID（网关注入，权限校验） |

#### 请求参数

同创建接口，所有字段均为可选。

#### 请求示例

```bash
curl -X PUT "http://127.0.0.1:8085/api/properties/1" \
  -H "Content-Type: application/json" \
  -H "X-App-Id: my-app" \
  -H "X-User-Id: 1001" \
  -d '{
    "title": "更新后的标题",
    "price": 900000,
    "description": "新描述"
  }'
```

#### 响应示例（成功）

同创建接口的响应格式，返回更新后的房源详情。

#### 响应示例（失败 - 无权操作）

```json
{
  "code": 403,
  "message": "无权操作该房源",
  "data": null
}
```

#### 响应示例（失败 - 房源不存在）

```json
{
  "code": 404,
  "message": "房源不存在",
  "data": null
}
```

---

### 3.3 获取房源详情

**接口**：`GET /api/properties/{id}`

**描述**：获取单个房源的完整详情，包含所有图片 URL。调用此接口会触发 `PROPERTY_VIEW` 统计事件（用于统计浏览量）。

#### 路径参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | long | 是 | 房源 ID |

#### 请求头

| 请求头 | 必填 | 说明 |
|---|---|---|
| X-App-Id | 否 | 应用标识（网关注入） |
| X-User-Id | 否 | 用户 ID（网关注入，用于统计） |

#### 请求示例

```bash
curl "http://127.0.0.1:8085/api/properties/1" \
  -H "X-App-Id: my-app" \
  -H "X-User-Id: 2002"
```

#### 响应示例（成功）

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "appId": "my-app",
    "title": "南山区精装两房",
    "type": "residential",
    "price": 800000,
    "rentalArea": 85,
    "rooms": "two_room",
    "orientation": "south",
    "floor": "中层",
    "totalFloors": 30,
    "address": "深圳市南山区科技园",
    "lat": 22.5431,
    "lng": 113.9412,
    "provinceCode": "440000",
    "provinceName": "广东省",
    "cityCode": "440300",
    "cityName": "深圳市",
    "districtCode": "440305",
    "districtName": "南山区",
    "decoration": "fine",
    "heatingMethod": "central",
    "waterSupply": "city",
    "powerSupply": "city",
    "gasSupply": "pipe",
    "internet": "fiber",
    "tvService": "iptv",
    "description": "南北通透，近地铁，拎包入住",
    "contactPhone": "13800138000",
    "agentName": "张三",
    "agentTitle": "资深房产经纪人",
    "agentPhone": "13900139000",
    "publishStatus": 1,
    "status": 1,
    "hot": false,
    "featured": false,
    "ownerId": 1001,
    "images": [
      "https://cdn.example.com/images/1/large.webp",
      "https://cdn.example.com/images/2/large.webp",
      "https://cdn.example.com/images/3/large.webp"
    ],
    "coverUrl": "https://cdn.example.com/images/1/medium.webp",
    "createdAt": "2026-07-08 10:30:00",
    "updatedAt": "2026-07-08 11:00:00"
  }
}
```

#### 响应示例（失败 - 房源不存在）

```json
{
  "code": 404,
  "message": "房源不存在",
  "data": null
}
```

> 💡 **注意**：此接口调用 `viewDetail` 方法，会发送 `PROPERTY_VIEW` 统计事件。内部获取详情请使用 `getDetail` 方法（不发送统计事件）。

---

### 3.4 搜索房源

**接口**：`GET /api/properties`

**描述**：多条件搜索房源。仅返回 `publishStatus=1（已发布）` 且 `status=1（审核通过）` 的房源。支持分页、条件筛选、附近搜索（GeoHash）。

#### 请求头

| 请求头 | 必填 | 说明 |
|---|---|---|
| X-App-Id | 否 | 应用标识（网关注入） |

#### 请求参数（Query String）

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| cityCode | string | 否 | 城市编码筛选 |
| type | string | 否 | 房源类型筛选（字典 key） |
| minPrice | long | 否 | 最低价格（单位：分） |
| maxPrice | long | 否 | 最高价格（单位：分） |
| lat | number | 否 | 附近搜索纬度（与 lng 同时提供生效） |
| lng | number | 否 | 附近搜索经度（与 lat 同时提供生效） |
| radius | double | 否 | 搜索半径（公里），默认 5.0 |
| keyword | string | 否 | 标题关键字（模糊匹配） |
| hot | boolean | 否 | 仅查热门房源（true 时生效） |
| featured | boolean | 否 | 仅查精选房源（true 时生效） |
| page | integer | 否 | 页码，默认 1 |
| size | integer | 否 | 每页条数，默认 20 |

#### 请求示例

```bash
# 按城市搜索
curl "http://127.0.0.1:8085/api/properties?cityCode=440300&page=1&size=10" \
  -H "X-App-Id: my-app"

# 按价格区间搜索
curl "http://127.0.0.1:8085/api/properties?minPrice=500000&maxPrice=1000000" \
  -H "X-App-Id: my-app"

# 附近搜索（5公里内）
curl "http://127.0.0.1:8085/api/properties?lat=22.5431&lng=113.9412&radius=5" \
  -H "X-App-Id: my-app"

# 关键字搜索
curl "http://127.0.0.1:8085/api/properties?keyword=南山区" \
  -H "X-App-Id: my-app"

# 查询精选房源
curl "http://127.0.0.1:8085/api/properties?featured=true" \
  -H "X-App-Id: my-app"
```

#### 响应参数（分页对象）

| 字段 | 类型 | 说明 |
|---|---|---|
| records | array | 房源列表 |
| total | long | 总记录数 |
| size | long | 每页条数 |
| current | long | 当前页码 |
| pages | long | 总页数 |

#### records 字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| id | long | 房源 ID |
| title | string | 标题 |
| type | string | 房源类型（字典 key） |
| price | long | 价格（单位：分） |
| rentalArea | integer | 面积（㎡） |
| rooms | string | 户型（字典 key） |
| orientation | string | 朝向（字典 key） |
| floor | string | 楼层描述 |
| address | string | 详细地址 |
| provinceName | string | 省份名称 |
| cityName | string | 城市名称 |
| districtName | string | 区县名称 |
| coverUrl | string | 封面图 URL（小图） |
| hot | boolean | 是否热门 |
| featured | boolean | 是否精选 |
| createdAt | string | 创建时间 |

#### 响应示例（成功）

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [
      {
        "id": 1,
        "title": "南山区精装两房",
        "type": "residential",
        "price": 800000,
        "rentalArea": 85,
        "rooms": "two_room",
        "orientation": "south",
        "floor": "中层",
        "address": "深圳市南山区科技园",
        "provinceName": "广东省",
        "cityName": "深圳市",
        "districtName": "南山区",
        "coverUrl": "https://cdn.example.com/images/1/small.webp",
        "hot": false,
        "featured": false,
        "createdAt": "2026-07-08 10:30:00"
      }
    ],
    "total": 1,
    "size": 20,
    "current": 1,
    "pages": 1
  }
}
```

---

### 3.5 删除房源

**接口**：`DELETE /api/properties/{id}`

**描述**：删除房源及其图片关联（级联删除）。仅 owner 或管理员可操作。

#### 路径参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | long | 是 | 房源 ID |

#### 请求头

| 请求头 | 必填 | 说明 |
|---|---|---|
| X-App-Id | 否 | 应用标识（网关注入） |
| X-User-Id | 否 | 用户 ID（网关注入，权限校验） |

#### 请求示例

```bash
curl -X DELETE "http://127.0.0.1:8085/api/properties/1" \
  -H "X-App-Id: my-app" \
  -H "X-User-Id: 1001"
```

#### 响应示例（成功）

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

#### 响应示例（失败 - 无权操作）

```json
{
  "code": 403,
  "message": "无权操作该房源",
  "data": null
}
```

---

### 3.6 更新发布状态

**接口**：`PUT /api/properties/{id}/publish-status`

**描述**：更新房源的发布状态。仅 owner 或管理员可操作。

#### 路径参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | long | 是 | 房源 ID |

#### 请求参数（Query String）

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| status | integer | 是 | 发布状态：0-草稿，1-已发布，2-已下架 |

#### 请求头

| 请求头 | 必填 | 说明 |
|---|---|---|
| X-App-Id | 否 | 应用标识（网关注入） |
| X-User-Id | 否 | 用户 ID（网关注入，权限校验） |

#### 发布状态枚举

| 值 | 含义 | 说明 |
|---|---|---|
| 0 | 草稿 | 默认状态，不在搜索结果中出现 |
| 1 | 已发布 | 已发布，等待审核或已审核通过 |
| 2 | 已下架 | 已下架，不在搜索结果中出现 |

#### 请求示例

```bash
# 发布房源
curl -X PUT "http://127.0.0.1:8085/api/properties/1/publish-status?status=1" \
  -H "X-App-Id: my-app" \
  -H "X-User-Id: 1001"

# 下架房源
curl -X PUT "http://127.0.0.1:8085/api/properties/1/publish-status?status=2" \
  -H "X-App-Id: my-app" \
  -H "X-User-Id: 1001"
```

#### 响应示例（成功）

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

---

### 3.7 更新审核状态

**接口**：`PUT /api/properties/{id}/audit-status`

**描述**：更新房源的审核状态。通常由管理员通过后台调用。审核状态变更时会发送通知消息。

#### 路径参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | long | 是 | 房源 ID |

#### 请求参数（Query String）

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| status | integer | 是 | 审核状态：0-草稿，1-通过，2-待审核，3-驳回 |

#### 请求头

| 请求头 | 必填 | 说明 |
|---|---|---|
| X-App-Id | 否 | 应用标识（网关注入） |

#### 审核状态枚举

| 值 | 含义 | 说明 |
|---|---|---|
| 0 | 草稿 | 默认状态 |
| 1 | 通过 | 审核通过，出现在搜索结果中 |
| 2 | 待审核 | 等待管理员审核 |
| 3 | 驳回 | 审核未通过 |

#### 请求示例

```bash
# 审核通过
curl -X PUT "http://127.0.0.1:8085/api/properties/1/audit-status?status=1" \
  -H "X-App-Id: my-app"

# 审核驳回
curl -X PUT "http://127.0.0.1:8085/api/properties/1/audit-status?status=3" \
  -H "X-App-Id: my-app"
```

#### 响应示例（成功）

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

> 💡 **说明**：搜索接口仅返回 `publishStatus=1（已发布）` 且 `status=1（审核通过）` 的房源。

---

## 四、数据模型

### 4.1 房源主表（properties）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 主键，自增 |
| app_id | varchar(32) | 应用标识 |
| title | varchar(200) | 标题 |
| type | varchar(50) | 房源类型（字典 key） |
| price | bigint | 价格（单位：分） |
| rental_area | int | 面积（㎡） |
| rooms | varchar(50) | 户型（字典 key） |
| orientation | varchar(50) | 朝向（字典 key） |
| floor | varchar(50) | 楼层描述 |
| total_floors | int | 总楼层 |
| address | varchar(500) | 详细地址 |
| lat | decimal(10,6) | 纬度 |
| lng | decimal(10,6) | 经度 |
| geohash | varchar(12) | GeoHash 编码 |
| province_code | varchar(20) | 省份编码 |
| province_name | varchar(50) | 省份名称 |
| city_code | varchar(20) | 城市编码 |
| city_name | varchar(50) | 城市名称 |
| district_code | varchar(20) | 区县编码 |
| district_name | varchar(50) | 区县名称 |
| decoration | varchar(50) | 装修情况（字典 key） |
| heating_method | varchar(50) | 供暖方式（字典 key） |
| water_supply | varchar(50) | 供水（字典 key） |
| power_supply | varchar(50) | 供电（字典 key） |
| gas_supply | varchar(50) | 供气（字典 key） |
| internet | varchar(50) | 网络接入（字典 key） |
| tv_service | varchar(50) | 电视服务（字典 key） |
| description | text | 描述 |
| contact_phone | varchar(50) | 联系电话 |
| agent_name | varchar(100) | 经纪人姓名 |
| agent_title | varchar(50) | 经纪人职务 |
| agent_phone | varchar(50) | 经纪人电话 |
| publish_status | tinyint | 发布状态：0草稿 1已发布 2已下架 |
| status | tinyint | 审核状态：0草稿 1通过 2待审核 3驳回 |
| hot | tinyint | 是否热门 |
| featured | tinyint | 是否精选 |
| branch_id | bigint | 门店 ID |
| building_id | bigint | 楼栋 ID |
| owner_id | bigint | 发布者用户 ID |
| created_at | timestamp | 创建时间 |
| updated_at | timestamp | 更新时间 |

### 4.2 房源图片关联表（property_images）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 主键，自增 |
| property_id | bigint | 房源 ID（外键，级联删除） |
| image_id | bigint | image-service 中的图片 ID |
| is_cover | tinyint | 是否封面（1是 0否） |
| sort_order | int | 排序值 |
| created_at | timestamp | 创建时间 |

---

## 五、字典字段说明

房源中的以下字段存储的是 dict-service 提供的字典 key，需配合字典服务使用：

| 字段 | 字典类型 type | 可选值示例 | 对应名称 |
|---|---|---|---|
| type | property_type | residential / office / commercial / industrial | 住宅 / 办公 / 商业 / 工业 |
| rooms | room_type | studio / one_room / two_room / three_room / four_plus | 开间 / 一室 / 两室 / 三室 / 四室以上 |
| orientation | orientation | north / south / east / west / ns / ns_through | 北 / 南 / 东 / 西 / 南北 / 南北通透 |
| decoration | decoration | rough / simple / fine / luxury | 毛坯 / 简装 / 精装 / 豪装 |
| heatingMethod | heating_method | central / floor / ac / gas_wall / none | 集中供暖 / 地暖 / 空调 / 燃气壁挂炉 / 无 |
| waterSupply | water_supply | city / well / river | 市政供水 / 井水 / 河水 |
| powerSupply | power_supply | city / solar / generator | 市政供电 / 太阳能 / 自发电 |
| gasSupply | gas_supply | pipe / tank / none | 管道燃气 / 罐装气 / 无 |
| internet | internet | fiber / adsl / cable / none | 光纤 / ADSL / 有线通 / 无 |
| tvService | tv_service | cable / iptv / satellite / none | 有线电视 / IPTV / 卫星 / 无 |

通过 dict-service 获取完整字典数据：

```bash
curl "http://127.0.0.1:8081/api/dict/items?type=property_type"
```

---

## 六、错误响应汇总

### 6.1 参数校验失败

```json
{
  "code": 400,
  "message": "title 标题不能为空, lat 纬度不能为空",
  "data": null
}
```

### 6.2 房源不存在

```json
{
  "code": 404,
  "message": "房源不存在",
  "data": null
}
```

### 6.3 无权操作

```json
{
  "code": 403,
  "message": "无权操作该房源",
  "data": null
}
```

### 6.4 图片数量超限

```json
{
  "code": 400,
  "message": "图片数量不能超过20",
  "data": null
}
```

### 6.5 服务内部错误

```json
{
  "code": 500,
  "message": "服务内部错误",
  "data": null
}
```
