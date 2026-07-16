# API 文档

> search-service 房源搜索服务 API 文档
> 服务版本：`0.0.1-SNAPSHOT`
> 服务名称：`search-service`
> 监听地址：`127.0.0.1:8086`
> 接口前缀：`/api/search`
> 对外访问需经**认证网关**转发，本服务不直接对公网暴露。

---

## 一、服务概览

| 项目 | 值 | 说明 |
|---|---|---|
| 服务名 | search-service | 房源搜索微服务 |
| 监听端口 | 8086 | HTTP 端口 |
| 绑定地址 | 127.0.0.1 | 仅本机访问，外部流量经网关转发 |
| 接口前缀 | /api/search | 所有接口的统一前缀 |
| 数据源 | property-service + Elasticsearch | 房源数据通过 MQ 同步到 ES |
| 数据同步 | RabbitMQ | 接收 property-service 的变更消息 |

---

## 二、接口总览

| # | 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|---|
| 1 | GET | `/api/search/properties` | 房源搜索（多条件筛选 + 分页 + 排序） | 应用级 |
| 2 | POST | `/api/search/admin/reindex` | 全量重建索引（管理接口） | 应用级 |

---

## 三、通用约定

### 3.1 请求约定

| 项目 | 说明 |
|---|---|
| 协议 | HTTP/1.1 |
| 编码 | UTF-8 |
| Content-Type | application/json（POST 无请求体） |
| 参数传递 | GET 通过 Query String；POST 无请求体 |

### 3.2 请求头 X-App-Id

| Header | 必填 | 说明 |
|---|---|---|
| `X-App-Id` | 推荐 | 应用标识，用于应用级数据隔离。网关转发时自动注入，直连调试时需手动传入。 |

> **重要**：搜索结果会根据 `X-App-Id` 过滤，仅返回对应应用的房源。若未携带该 Header，`appId` 条件为 `null`，可能导致查询结果异常。

### 3.3 统一响应格式 Result

所有接口统一返回 JSON，结构如下：

```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| code | integer | 业务状态码，200 表示成功，其余为错误 |
| message | string | 状态描述 |
| data | object \| null | 业务数据，错误时为 null |

对应源码：`com.xss.searchservice.common.Result`

```java
@Data
public class Result<T> {
    private int code;
    private String message;
    private T data;

    public static <T> Result<T> success(T data) { ... }
    public static <T> Result<T> success() { ... }
    public static <T> Result<T> fail(int code, String message) { ... }
}
```

### 3.4 错误码说明

| code | HTTP Status | 含义 | 场景 |
|---|---|---|---|
| 200 | 200 OK | 请求成功 | 正常响应 |
| 500 | 500 Internal Server Error | 服务端异常 | ES 故障、参数异常等 |

### 3.5 数据可见性规则

搜索结果仅返回同时满足以下所有条件的房源：

| 条件 | 字段 | 值 | 说明 |
|---|---|---|---|
| 应用隔离 | appId | = 请求方 X-App-Id | 不同应用的数据互不可见 |
| 发布状态 | publishStatus | = 1 | 已发布（草稿 0、已下架 2 不可见） |
| 审核状态 | status | = 1 | 已审核通过（待审核、驳回不可见） |

> 以上过滤条件在 `PropertySearchServiceImpl.buildCriteria()` 中自动追加，调用方无需手动传入。

---

## 四、房源搜索接口

### 4.1 接口说明

**GET `/api/search/properties`**

支持关键词全文检索、多条件精确筛选、价格区间过滤、分页查询。结果按创建时间倒序排列。

对应源码：`SearchController.search()` → `PropertySearchServiceImpl.search()`

### 4.2 请求参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| keyword | String | 否 | — | 关键词，在 title、description、address 三个字段上做 OR 包含匹配 |
| cityCode | String | 否 | — | 城市代码（精确匹配，如 `110000`） |
| type | String | 否 | — | 房源类型（精确匹配，如 `APARTMENT`） |
| rooms | String | 否 | — | 户型（精确匹配，如 `2室1厅`） |
| minPrice | Long | 否 | — | 最低价格（含，单位：元/月或总价，依业务定义） |
| maxPrice | Long | 否 | — | 最高价格（含，单位：元） |
| hot | Boolean | 否 | — | 是否热门（`true` 仅返回热门房源，`false` 仅返回非热门） |
| featured | Boolean | 否 | — | 是否精选（`true` 仅返回精选房源，`false` 仅返回非精选） |
| page | Integer | 否 | 1 | 页码，从 1 开始 |
| size | Integer | 否 | 20 | 每页条数，最大建议不超过 100 |

> **预留字段**：`centerLat`、`centerLng`、`radiusKm`（附近搜索）、`topLeftLat`、`topLeftLng`、`bottomRightLat`、`bottomRightLng`（视口搜索）已在 DTO 中定义，但当前版本未实现查询逻辑。

### 4.3 搜索过滤条件详解

| 过滤维度 | 对应字段 | 匹配方式 | 说明 |
|---|---|---|---|
| 关键词搜索 | title / description / address | OR 包含匹配 |  keyword 非空时，在三个字段上做 OR 模糊匹配 |
| 城市 | cityCode | 精确匹配（Keyword） | 城市行政区划代码 |
| 房源类型 | type | 精确匹配（Keyword） | 房源类型字典值 |
| 户型 | rooms | 精确匹配（Keyword） | 户型描述，如 "2室1厅" |
| 价格区间 | price | 范围查询（Long） | minPrice ≤ price ≤ maxPrice |
| 热门标记 | hot | 精确匹配（Boolean） | 是否热门房源 |
| 精选标记 | featured | 精确匹配（Boolean） | 是否精选房源 |
| 应用隔离 | appId | 精确匹配（Keyword） | 自动从 X-App-Id Header 获取 |
| 发布状态 | publishStatus | 精确匹配（Integer） | 固定为 1，自动追加 |
| 审核状态 | status | 精确匹配（Integer） | 固定为 1，自动追加 |

### 4.4 搜索排序规则

| 排序字段 | 方向 | 说明 |
|---|---|---|
| createdAt | DESC（降序） | 按创建时间倒序，最新创建的房源排在前面 |

> 当前仅支持按创建时间倒序一种排序方式，不支持自定义排序字段。排序逻辑在 `PropertySearchServiceImpl.search()` 中硬编码。

### 4.5 请求示例

**基础搜索：**

```http
GET /api/search/properties?page=1&size=10
X-App-Id: my-backend-system
```

**关键词搜索：**

```http
GET /api/search/properties?keyword=精装两居&page=1&size=20
X-App-Id: my-backend-system
```

**多条件组合搜索：**

```http
GET /api/search/properties?cityCode=110000&type=APARTMENT&rooms=2室1厅&minPrice=2000&maxPrice=5000&hot=true&featured=true&page=1&size=10
X-App-Id: my-backend-system
```

### 4.6 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [
      {
        "id": 123,
        "appId": "my-backend-system",
        "title": "朝阳区精装两居室",
        "type": "APARTMENT",
        "price": 3500,
        "rentalArea": 88,
        "rooms": "2室1厅",
        "orientation": "南",
        "floor": "中层",
        "address": "北京市朝阳区建国路88号",
        "lat": 39.9042,
        "lon": 116.4074,
        "provinceName": "北京市",
        "cityName": "北京市",
        "districtName": "朝阳区",
        "decoration": "精装修",
        "description": "地铁口精装两房，交通便利，配套齐全",
        "hot": true,
        "featured": true,
        "coverUrl": "http://example.com/cover.jpg",
        "distance": null
      }
    ],
    "total": 156,
    "size": 10,
    "current": 1,
    "pages": 16
  }
}
```

### 4.7 响应字段说明

#### data（SearchResultVO）

| 字段 | 类型 | 说明 |
|---|---|---|
| records | array | 房源列表（PropertyDocumentVO 数组） |
| total | long | 符合条件的总记录数 |
| size | integer | 每页条数（回显请求参数） |
| current | integer | 当前页码（回显请求参数） |
| pages | integer | 总页数 = ceil(total / size) |

#### records 元素（PropertyDocumentVO）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | long | 房源 ID |
| appId | string | 应用标识 |
| title | string | 房源标题 |
| type | string | 房源类型（字典 key） |
| price | long | 价格（单位：元） |
| rentalArea | integer | 面积（㎡） |
| rooms | string | 户型 |
| orientation | string | 朝向 |
| floor | string | 楼层描述（如 "中层"） |
| address | string | 详细地址 |
| lat | double | 纬度 |
| lon | double | 经度 |
| provinceName | string | 省份名称 |
| cityName | string | 城市名称 |
| districtName | string | 区县名称 |
| decoration | string | 装修情况 |
| description | string | 房源描述 |
| hot | boolean | 是否热门 |
| featured | boolean | 是否精选 |
| coverUrl | string | 封面图 URL |
| distance | double | 距离（预留字段，当前为 null） |

---

## 五、全量重建索引接口

### 5.1 接口说明

**POST `/api/search/admin/reindex`**

从 property-service 分页拉取所有房源列表，逐条调用详情接口获取完整数据并重新索引到 Elasticsearch。

> **用途**：首次部署、索引损坏、mapping 变更、数据不一致时使用。
> **注意**：该操作为同步执行，数据量大时耗时较长。执行期间搜索服务正常可用，但索引结果可能逐步变化。

对应源码：`SearchController.reindex()` → `PropertySearchServiceImpl.reindexAll()`

### 5.2 请求参数

无请求体参数，通过 `X-App-Id` Header 指定应用。

| Header | 必填 | 说明 |
|---|---|---|
| X-App-Id | 推荐 | 应用标识，指定重建哪个应用的索引 |

### 5.3 工作流程

```
1. 接收 reindex 请求，获取 appId
2. 从 property-service 分页拉取房源列表（每页 100 条）
   GET /api/properties?page=N&size=100
3. 对每条房源，调用详情接口获取完整数据
   GET /api/properties/{id}
4. 将数据转换为 Elasticsearch 文档并写入索引（upsert）
5. 循环直到所有分页数据处理完毕
6. 返回成功响应
```

### 5.4 请求示例

```http
POST /api/search/admin/reindex
X-App-Id: my-backend-system
```

### 5.5 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

---

## 六、关键词搜索说明

### 6.1 搜索字段

keyword 参数在以下三个字段上做 **OR 包含匹配**：

| 字段 | ES 类型 | 说明 |
|---|---|---|
| title | text | 房源标题 |
| description | text | 房源描述 |
| address | text | 地址（注意：address 字段 `index=false`，但仍可通过 contains 匹配） |

### 6.2 实现逻辑

```java
if (StringUtils.isNotBlank(request.getKeyword())) {
    Criteria keywordCriteria = new Criteria("title").contains(request.getKeyword())
            .or(new Criteria("description").contains(request.getKeyword()))
            .or(new Criteria("address").contains(request.getkeyword()));
    criteria = criteria.and(keywordCriteria);
}
```

> **关键点**：关键词 OR 条件使用独立的子 Criteria 构造，再 AND 到主 Criteria 链上，避免破坏 AND/OR 优先级。

### 6.3 中文分词说明

当前使用 Elasticsearch 标准分词器（standard analyzer），中文会被按字拆分。例如搜索 "精装" 时，会被拆分为 "精" 和 "装" 两个词项，可能匹配到任何包含这两个字的文档。

如需更精确的中文搜索，建议安装 IK 分词器插件，并在 `PropertyDocument` 的 `title`、`description` 字段上指定 `analyzer = "ik_max_word"`、`searchAnalyzer = "ik_smart"`。

---

## 七、错误处理

| 场景 | HTTP Status | 响应 | 说明 |
|---|---|---|---|
| 正常请求 | 200 | `{"code":200,...}` | 请求成功 |
| Elasticsearch 不可达 | 500 | `{"code":500,...}` | 搜索接口抛出异常 |
| 参数格式错误 | 500 | `{"code":500,...}` | 服务端异常 |
| reindex 时 property-service 不可达 | 200 | `{"code":200,...}` | 错误被 catch，日志记录，接口正常返回 |
| MQ 消费失败 | — | — | 错误被 catch，日志记录，不影响接口 |

> **容错设计**：索引创建失败、单条房源索引失败、MQ 消费失败等场景均会 catch 异常并记录日志，不会导致服务崩溃或接口报错。
