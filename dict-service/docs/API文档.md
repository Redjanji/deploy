# API 文档

> dict-service 基础字典服务 API 文档
> 服务版本：0.0.1-SNAPSHOT
> 服务名：dict-service
> 监听地址：`127.0.0.1:8081`
> Base URL：`http://127.0.0.1:8081`
> 对外访问需经**认证网关**转发，本服务不直接对公网暴露。

---

## 一、服务概览

| 项目 | 值 | 说明 |
|---|---|---|
| 服务名 | dict-service | Spring Boot 应用名 |
| 端口 | 8081 | HTTP 监听端口 |
| 监听地址 | 127.0.0.1 | 仅本机访问，通过网关对外服务 |
| 接口前缀 | `/api` | 所有业务接口均此前缀 |
| 字典接口前缀 | `/api/dict` | 通用字典服务接口前缀 |
| 管理接口前缀 | `/api/admin` | 缓存刷新等管理接口 |

---

## 二、接口总览表

| # | 方法 | 路径 | 说明 | 所属模块 |
|---|---|---|---|---|
| 1 | GET | `/api/provinces` | 获取所有省份列表 | 中国行政区划 |
| 2 | GET | `/api/cities` | 获取城市列表（支持按省份筛选） | 中国行政区划 |
| 3 | GET | `/api/districts` | 获取区县列表（支持按城市筛选） | 中国行政区划 |
| 4 | GET | `/api/towns` | 获取乡镇/街道列表（支持按区县筛选） | 中国行政区划 |
| 5 | GET | `/api/villages` | 获取村/社区列表（支持按乡镇筛选） | 中国行政区划 |
| 6 | GET | `/api/regions/path` | 获取完整路径（名称链+代码链） | 中国行政区划 |
| 7 | POST | `/api/admin/refresh-regions` | 清空中国行政区划缓存 | 中国行政区划 |
| 8 | GET | `/api/countries` | 获取国家/地区列表 | 国家/地区字典 |
| 9 | GET | `/api/countries/{country_code}` | 获取单个国家/地区详情 | 国家/地区字典 |
| 10 | POST | `/api/admin/refresh-countries` | 清空国家字典缓存 | 国家/地区字典 |
| 11 | GET | `/api/currencies` | 获取货币列表 | 货币字典 |
| 12 | GET | `/api/currencies/{currency_code}` | 获取单个货币详情 | 货币字典 |
| 13 | POST | `/api/admin/refresh-currencies` | 清空货币字典缓存 | 货币字典 |
| 14 | GET | `/api/languages` | 获取语言列表 | 语言字典 |
| 15 | GET | `/api/languages/{lang_code}` | 获取单个语言详情 | 语言字典 |
| 16 | POST | `/api/admin/refresh-languages` | 清空语言字典缓存 | 语言字典 |
| 17 | GET | `/api/timezones` | 获取时区列表 | 时区字典 |
| 18 | GET | `/api/timezones/detail` | 获取单个时区详情 | 时区字典 |
| 19 | POST | `/api/admin/refresh-timezones` | 清空时区字典缓存 | 时区字典 |
| 20 | GET | `/api/dict/types` | 获取所有支持的字典类型 | 通用字典 |
| 21 | GET | `/api/dict/{dictType}/list` | 获取普通字典列表 | 通用字典 |
| 22 | GET | `/api/dict/{dictType}/item/{code}` | 获取普通字典详情 | 通用字典 |
| 23 | GET | `/api/dict/{dictType}/tree` | 获取树形字典列表 | 通用字典 |
| 24 | GET | `/api/dict/items` | 获取房产字典项列表 | 房产字典 |
| 25 | GET | `/api/dict/items/{type}/{itemKey}` | 获取单个房产字典项 | 房产字典 |
| 26 | GET | `/api/dict/property-types` | 获取所有房产字典类型 | 房产字典 |
| 27 | POST | `/api/dict/admin/refresh/{dictType}` | 清空单个字典缓存 | 通用字典 |
| 28 | POST | `/api/dict/admin/refresh-all` | 清空所有字典缓存 | 通用字典 |

---

## 三、通用约定

### 3.1 请求约定

| 项目 | 约定 |
|---|---|
| 协议 | HTTP/1.1 |
| 编码 | UTF-8 |
| 方法 | GET（查询）、POST（管理操作） |
| 参数传递 | GET 通过 Query String；POST 无请求体 |
| Content-Type | application/json |

### 3.2 统一响应格式

所有接口统一返回 JSON，结构如下：

```json
{
  "code": 200,
  "message": "success",
  "data": []
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| code | integer | 业务状态码，200 表示成功，其余为错误 |
| message | string | 状态描述 |
| data | array \| object \| null | 业务数据，错误时为 null |

### 3.3 状态码说明

| code | HTTP Status | 含义 |
|---|---|---|
| 200 | 200 OK | 请求成功 |
| 404 | 404 Not Found | 资源不存在 |
| 500 | 500 Internal Server Error | 服务端异常（DB / Redis 故障等） |

> 已通过 `GlobalExceptionHandler` 统一处理异常，异常时返回 `{code:500, message:"服务内部错误", data:null}`，HTTP Status 500。

### 3.4 鉴权说明

- **业务查询接口（GET）**：依赖网关鉴权，本服务不再校验。
- **管理接口（POST `/api/admin/*`、`/api/dict/admin/*`）**：鉴权由网关层统一负责。本服务仅监听 `127.0.0.1`，不直接暴露，所有流量经认证网关转发。

### 3.5 缓存约定

- 所有查询接口均使用 Redis 缓存，TTL 统一为 1 小时
- 缓存 key 命名规范：`模块:业务:参数`
- 缓存 value 为 JSON 字符串
- 缓存异常（读取/写入/反序列化失败）均降级查 DB，不影响业务可用性
- 管理接口用于主动失效缓存，数据更新后需立即调用

---

## 四、普通字典接口

普通字典指结构统一（code + name + sort_order + status）的键值对字典，通过 `dictType` 路径参数区分字典类型。

### 4.1 获取所有字典类型

`GET /api/dict/types`

返回通用字典服务支持的所有字典类型列表，用于前端动态加载字典。

**请求参数**：无

**请求示例**：
```bash
curl "http://127.0.0.1:8081/api/dict/types"
```

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    "common_status",
    "degree",
    "education",
    "enterprise_type",
    "ethnicity",
    "gender",
    "id_document_type",
    "industry_category",
    "invoice_type",
    "marital_status",
    "occupation",
    "payment_method",
    "political_status",
    "professional_title",
    "property_type",
    "settlement_method",
    "taxpayer_qualification",
    "unit"
  ]
}
```

**data 字段**：字符串数组，为所有支持的字典类型编码。

---

### 4.2 获取普通字典列表

`GET /api/dict/{dictType}/list`

返回指定字典类型的列表，支持状态筛选和名称/代码模糊搜索。结果缓存到 Redis，TTL 1 小时。

**支持的 dictType**：

| dictType | 对应表 | 说明 |
|---|---|---|
| `gender` | `sys_gender` | 性别 |
| `education` | `sys_education` | 学历 |
| `degree` | `sys_degree` | 学位 |
| `ethnicity` | `sys_ethnicity` | 民族（56个） |
| `marital_status` | `sys_marital_status` | 婚姻状况 |
| `political_status` | `sys_political_status` | 政治面貌 |
| `id_document_type` | `sys_id_document_type` | 证件类型 |
| `professional_title` | `sys_professional_title` | 职称 |
| `enterprise_type` | `sys_enterprise_type` | 企业类型 |
| `taxpayer_qualification` | `sys_taxpayer_qualification` | 纳税人资质 |
| `settlement_method` | `sys_settlement_method` | 结算方式 |
| `invoice_type` | `sys_invoice_type` | 发票类型 |
| `unit` | `sys_unit` | 计量单位 |
| `common_status` | `sys_common_status` | 通用状态 |
| `payment_method` | `sys_payment_method` | 支付方式 |

**请求参数**：

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| dictType | path | string | 是 | 字典类型，见上表 |
| status | query | integer | 否 | 状态筛选：1-启用，0-停用；不传则返回全部 |
| keyword | query | string | 否 | 关键字（同时匹配 name 和 code） |

**请求示例**：
```bash
# 获取所有民族
curl "http://127.0.0.1:8081/api/dict/ethnicity/list"

# 获取启用的支付方式
curl "http://127.0.0.1:8081/api/dict/payment_method/list?status=1"

# 按名称搜索民族
curl "http://127.0.0.1:8081/api/dict/ethnicity/list?keyword=汉"
```

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {"code": "HA", "name": "汉族", "sort_order": 1, "status": 1},
    {"code": "MG", "name": "蒙古族", "sort_order": 2, "status": 1}
  ]
}
```

**data 字段说明**：

| 字段 | 类型 | 说明 |
|---|---|---|
| code | string | 字典代码 |
| name | string | 字典名称 |
| sort_order | integer | 排序值 |
| status | integer | 状态（1-启用，0-停用） |

**缓存 key**：`dict:{dictType}:list:{status|all}:{keyword|all}`

---

### 4.3 获取普通字典详情

`GET /api/dict/{dictType}/item/{code}`

按字典代码查询单个字典项详情。结果缓存到 Redis，TTL 1 小时。

**请求参数**：

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| dictType | path | string | 是 | 字典类型 |
| code | path | string | 是 | 字典代码 |

**请求示例**：
```bash
curl "http://127.0.0.1:8081/api/dict/gender/item/M"
```

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "code": "M",
    "name": "男",
    "sort_order": 1,
    "status": 1
  }
}
```

> 代码不存在时，`data` 为 `null`。

**缓存 key**：`dict:{dictType}:item:{code}`

---

## 五、树形字典接口

树形字典支持层级结构，通过 parent_code 和 level 进行层级查询。

### 5.1 获取树形字典列表

`GET /api/dict/{dictType}/tree`

返回树形字典列表，支持按父级代码、层级、关键字筛选。结果缓存到 Redis，TTL 1 小时。

**支持的 dictType**：

| dictType | 对应表 | 说明 |
|---|---|---|
| `industry_category` | `sys_industry_category` | 行业分类（GB/T 4754-2017） |
| `occupation` | `sys_occupation` | 职业分类 |

**请求参数**：

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| dictType | path | string | 是 | `industry_category` 或 `occupation` |
| parent_code | query | string | 否 | 父级代码；不传则返回全部 |
| level | query | integer | 否 | 层级筛选（行业：1-门类，2-大类，3-中类，4-小类；职业：1-大类，2-中类，3-小类，4-细类） |
| keyword | query | string | 否 | 关键字（同时匹配 name 和 code） |

**请求示例**：
```bash
# 获取所有行业门类（一级）
curl "http://127.0.0.1:8081/api/dict/industry_category/tree?level=1"

# 获取 A 门类下的大类
curl "http://127.0.0.1:8081/api/dict/industry_category/tree?parent_code=A"

# 按名称搜索行业
curl "http://127.0.0.1:8081/api/dict/industry_category/tree?keyword=软件"

# 获取所有职业大类
curl "http://127.0.0.1:8081/api/dict/occupation/tree?level=1"

# 获取专业技术人员下中类
curl "http://127.0.0.1:8081/api/dict/occupation/tree?parent_code=200000"
```

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "code": "A",
      "name": "农、林、牧、渔业",
      "parent_code": null,
      "level": 1,
      "sort_order": 1,
      "status": 1
    }
  ]
}
```

**data 字段说明**：

| 字段 | 类型 | 说明 |
|---|---|---|
| code | string | 字典代码 |
| name | string | 字典名称 |
| parent_code | string | 父级代码（根节点为 null） |
| level | integer | 层级 |
| sort_order | integer | 排序值 |
| status | integer | 状态（1-启用，0-停用） |

**缓存 key**：`dict:{dictType}:tree:{parent_code|all}:{level|all}:{keyword|all}`

---

## 六、房产字典接口

房产业务专用字典，采用"类型+条目"的主从表模式，支持灵活扩展。

### 6.1 获取所有房产字典类型

`GET /api/dict/property-types`

返回所有房产字典类型列表。对应表 `sys_property_dict_type`。结果缓存到 Redis，TTL 1 小时。

**请求参数**：无

**请求示例**：
```bash
curl "http://127.0.0.1:8081/api/dict/property-types"
```

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {"type_code": "property_type", "type_name": "房源类型", "remark": "住宅/办公/商业/工业"},
    {"type_code": "decoration", "type_name": "装修情况", "remark": "毛坯/简装/精装/豪装"}
  ]
}
```

**data 字段说明**：

| 字段 | 类型 | 说明 |
|---|---|---|
| type_code | string | 字典类型编码 |
| type_name | string | 字典类型名称 |
| remark | string | 备注说明 |

**缓存 key**：`dict:property:types`

---

### 6.2 获取房产字典项列表

`GET /api/dict/items`

返回指定房产字典类型的所有选项列表。对应表 `sys_property_dict_item`。支持按状态筛选和名称/代码模糊搜索。结果缓存到 Redis，TTL 1 小时。

**支持的 type**：

| type | 说明 |
|---|---|
| `property_type` | 房源类型（住宅/办公/商业/工业） |
| `decoration` | 装修情况（毛坯/简装/精装/豪装） |
| `heating_method` | 供暖方式（集中供暖/地暖/空调/燃气壁挂炉/无） |
| `water_supply` | 供水（市政供水/井水/河水） |
| `power_supply` | 供电（市政供电/自发电/太阳能） |
| `gas_supply` | 供气（管道燃气/罐装气/无） |
| `internet` | 网络接入（光纤/ADSL/有线通/无） |
| `tv_service` | 电视服务（有线电视/IPTV/卫星/无） |
| `orientation` | 朝向（北/南/东/西/南北/南北通透） |
| `room_type` | 户型（开间/一室/两室/三室/四室以上） |
| `rental_area_unit` | 面积单位（平方米/平方英尺） |
| `lease_term` | 租期（短租/长租） |
| `publish_status` | 发布状态（草稿/已发布/已下架） |
| `audit_status` | 审核状态（草稿/待审核/通过/驳回） |
| `property_label` | 房源标签（近地铁/公园旁/学区房/商圈等） |

**请求参数**：

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| type | query | string | 是 | 字典类型编码，见上表 |
| status | query | integer | 否 | 状态筛选：1-启用，0-停用；不传则返回全部 |
| keyword | query | string | 否 | 关键字（同时匹配 item_key 和 item_value） |

**请求示例**：
```bash
# 获取所有房源类型
curl "http://127.0.0.1:8081/api/dict/items?type=property_type"

# 获取启用的装修情况
curl "http://127.0.0.1:8081/api/dict/items?type=decoration&status=1"

# 按名称搜索朝向
curl "http://127.0.0.1:8081/api/dict/items?type=orientation&keyword=南"
```

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {"code": "residential", "name": "住宅", "sort_order": 1, "status": 1},
    {"code": "office", "name": "办公", "sort_order": 2, "status": 1},
    {"code": "commercial", "name": "商业", "sort_order": 3, "status": 1},
    {"code": "industrial", "name": "工业", "sort_order": 4, "status": 1}
  ]
}
```

**data 字段说明**：

| 字段 | 类型 | 说明 |
|---|---|---|
| code | string | 选项编码（存入房源表的字段值） |
| name | string | 选项名称（前端展示） |
| sort_order | integer | 排序值 |
| status | integer | 是否启用（1-是，0-否） |

**业务说明**：
- `item_key` 用于存储到房源表的对应字段（如 `decoration` 字段存 `rough`）
- `item_value` 用于前端展示（如 "毛坯"）
- 新增字典类型或条目，直接插入 `sys_property_dict_type` 和 `sys_property_dict_item` 表即可

**缓存 key**：`dict:property:{type}:items:{status|all}:{keyword|all}`

---

### 6.3 获取单个房产字典项

`GET /api/dict/items/{type}/{itemKey}`

按字典类型和选项编码查询单个房产字典项详情。结果缓存到 Redis，TTL 1 小时。

**请求参数**：

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| type | path | string | 是 | 字典类型编码 |
| itemKey | path | string | 是 | 选项编码 |

**请求示例**：
```bash
curl "http://127.0.0.1:8081/api/dict/items/decoration/rough"
```

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "code": "rough",
    "name": "毛坯",
    "sort_order": 1,
    "status": 1
  }
}
```

> 字典项不存在时，`data` 为 `null`。

**缓存 key**：`dict:property:{type}:item:{itemKey}`

---

## 七、中国行政区划接口

提供五级树形行政区划（省→市→区→镇→村）的查询服务。

### 7.1 获取省份列表

`GET /api/provinces`

返回所有启用的省份/直辖市列表。对应视图 `v_provinces`。结果缓存到 Redis，TTL 1 小时。

**请求参数**：无

**请求示例**：
```bash
curl "http://127.0.0.1:8081/api/provinces"
```

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {"region_code": "11", "region_name": "北京市", "region_type": "省/直辖市", "sort_order": 0},
    {"region_code": "440000", "region_name": "广东省", "region_type": "省", "sort_order": 1}
  ]
}
```

**data 字段说明**：

| 字段 | 类型 | 说明 |
|---|---|---|
| region_code | string | 省份代码 |
| region_name | string | 省份名称 |
| region_type | string | 类型（省、直辖市） |
| sort_order | integer | 排序值 |

**缓存 key**：`regions:provinces`

---

### 7.2 获取城市列表

`GET /api/cities`

按省份筛选城市列表。对应视图 `v_cities`。结果缓存到 Redis，TTL 1 小时。

**请求参数**：

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| province_code | query | string | 否 | 省份代码，如 `440000`；不传则返回全部 |

**请求示例**：
```bash
# 获取广东省所有城市
curl "http://127.0.0.1:8081/api/cities?province_code=440000"

# 获取全部城市
curl "http://127.0.0.1:8081/api/cities"
```

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "region_code": "440300",
      "region_name": "深圳市",
      "region_type": "地级市",
      "province_code": "440000",
      "province_name": "广东省",
      "sort_order": 1
    }
  ]
}
```

**data 字段说明**：

| 字段 | 类型 | 说明 |
|---|---|---|
| region_code | string | 城市代码 |
| region_name | string | 城市名称 |
| region_type | string | 类型（地级市等） |
| province_code | string | 所属省份代码 |
| province_name | string | 所属省份名称 |
| sort_order | integer | 排序 |

**缓存 key**：`regions:cities:{province_code|all}`

---

### 7.3 获取区县列表

`GET /api/districts`

按城市筛选区县列表。对应视图 `v_districts`。结果缓存到 Redis，TTL 1 小时。

**请求参数**：

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| city_code | query | string | 否 | 城市代码，如 `440300`；不传则返回全部 |

**请求示例**：
```bash
# 获取深圳市所有区
curl "http://127.0.0.1:8081/api/districts?city_code=440300"
```

**data 字段说明**：

| 字段 | 类型 | 说明 |
|---|---|---|
| region_code | string | 区县代码 |
| region_name | string | 区县名称 |
| region_type | string | 类型（区、县、县级市） |
| city_code | string | 所属城市代码 |
| city_name | string | 所属城市名称 |
| province_code | string | 所属省份代码 |
| province_name | string | 所属省份名称 |
| sort_order | integer | 排序 |

**缓存 key**：`regions:districts:{city_code|all}`

---

### 7.4 获取乡镇/街道列表

`GET /api/towns`

按区县筛选乡镇/街道列表。对应视图 `v_towns`。结果缓存到 Redis，TTL 1 小时。

**请求参数**：

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| district_code | query | string | 否 | 区县代码，如 `440305`；不传则返回全部 |

**请求示例**：
```bash
# 获取南山区所有街道
curl "http://127.0.0.1:8081/api/towns?district_code=440305"
```

**data 字段说明**：

| 字段 | 类型 | 说明 |
|---|---|---|
| region_code | string | 乡镇/街道代码（9位） |
| region_name | string | 乡镇/街道名称 |
| region_type | string | 类型（镇、乡、街道） |
| district_code | string | 所属区县代码 |
| district_name | string | 所属区县名称 |
| city_code | string | 所属城市代码 |
| city_name | string | 所属城市名称 |
| province_code | string | 所属省份代码 |
| province_name | string | 所属省份名称 |
| sort_order | integer | 排序 |

**缓存 key**：`regions:towns:{district_code|all}`

---

### 7.5 获取村/社区列表

`GET /api/villages`

按乡镇/街道筛选村/社区列表。对应视图 `v_villages`。结果缓存到 Redis，TTL 1 小时。

**请求参数**：

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| town_code | query | string | 否 | 乡镇/街道代码，如 `440305001`；不传则返回全部 |

**请求示例**：
```bash
# 获取南头街道所有社区
curl "http://127.0.0.1:8081/api/villages?town_code=440305001"
```

**data 字段说明**：

| 字段 | 类型 | 说明 |
|---|---|---|
| region_code | string | 村/社区代码（12位） |
| region_name | string | 村/社区名称 |
| region_type | string | 类型（村、社区） |
| town_code | string | 所属乡镇/街道代码 |
| town_name | string | 所属乡镇/街道名称 |
| district_code | string | 所属区县代码 |
| district_name | string | 所属区县名称 |
| city_code | string | 所属城市代码 |
| city_name | string | 所属城市名称 |
| province_code | string | 所属省份代码 |
| province_name | string | 所属省份名称 |
| sort_order | integer | 排序 |

**缓存 key**：`regions:villages:{town_code|all}`

---

### 7.6 获取完整路径

`GET /api/regions/path`

查询任意节点到根节点的完整路径（名称链 + 代码链）。对应视图 `v_region_path`（递归 CTE）。结果缓存到 Redis，TTL 1 小时。

**请求参数**：

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| region_code | query | string | 否 | 区域代码 |
| region_level | query | integer | 否 | 层级（1-5），用于列出某级的所有路径 |

**请求示例**：
```bash
# 查询南山区的完整地址路径
curl "http://127.0.0.1:8081/api/regions/path?region_code=440305"

# 查询所有乡镇的完整路径
curl "http://127.0.0.1:8081/api/regions/path?region_level=4"
```

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "region_code": "440305",
      "region_name": "南山区",
      "parent_code": "440300",
      "region_level": 3,
      "region_type": "市辖区",
      "full_path": "广东省 > 深圳市 > 南山区",
      "code_path": "440000/440300/440305",
      "depth": 2
    }
  ]
}
```

**data 字段说明**：

| 字段 | 类型 | 说明 |
|---|---|---|
| region_code | string | 区域代码 |
| region_name | string | 区域名称 |
| parent_code | string | 上级代码 |
| region_level | integer | 层级（1-省/直辖市，2-地级市，3-区/县，4-乡镇/街道，5-村/社区） |
| region_type | string | 类型 |
| full_path | string | 完整名称路径，如 `广东省 > 深圳市 > 南山区` |
| code_path | string | 完整代码路径，如 `440000/440300/440305` |
| depth | integer | 当前节点在树中的深度（从省份=0 开始） |

**缓存 key**：`regions:path:{region_code|all}:{region_level|all}`

---

### 7.7 清空中国行政区划缓存

`POST /api/admin/refresh-regions`

主动失效所有区域字典缓存（`regions:*`）。下一次 GET 请求会重新从数据库加载。

**请求参数**：无

**请求示例**：
```bash
curl -X POST "http://127.0.0.1:8081/api/admin/refresh-regions"
```

**响应示例**：
```json
{
  "code": 200,
  "message": "Region cache cleared",
  "data": null
}
```

**使用场景**：
- `sys_china_region` 表数据更新后，需要立即生效时调用
- Redis 数据异常需要强制刷新时调用

---

## 八、国家/地区字典接口

### 8.1 获取国家/地区列表

`GET /api/countries`

返回所有启用的国家/地区列表。对应视图 `v_countries`（基于 `sys_country` 表）。支持按大洲代码筛选和中英文名称模糊搜索。结果缓存到 Redis，TTL 1 小时。

**请求参数**：

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| continent_code | query | string | 否 | 大洲代码，如 `AS`、`EU`、`NA`；不传则返回全部 |
| keyword | query | string | 否 | 名称关键字（同时匹配中文名 `name_zh` 和英文名 `name_en`） |

**请求示例**：
```bash
# 获取所有国家
curl "http://127.0.0.1:8081/api/countries"

# 获取亚洲国家
curl "http://127.0.0.1:8081/api/countries?continent_code=AS"

# 按名称搜索（中英文均可）
curl "http://127.0.0.1:8081/api/countries?keyword=中国"
curl "http://127.0.0.1:8081/api/countries?keyword=China"

# 组合查询：亚洲中名含"朝"的国家
curl "http://127.0.0.1:8081/api/countries?continent_code=AS&keyword=朝"
```

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "country_code": "CN",
      "country_code3": "CHN",
      "numeric_code": "156",
      "name_zh": "中国",
      "name_en": "China",
      "phone_code": "86",
      "currency_code": "CNY",
      "continent_code": "AS",
      "flag_emoji": "🇨🇳",
      "sort_order": 1
    }
  ]
}
```

**data 字段说明**：

| 字段 | 类型 | 说明 |
|---|---|---|
| country_code | string | ISO 3166-1 二位字母代码（如 `CN`、`US`） |
| country_code3 | string | ISO 3166-1 三位字母代码（如 `CHN`、`USA`） |
| numeric_code | string | ISO 3166-1 三位数字代码（如 `156`） |
| name_zh | string | 中文名称 |
| name_en | string | 英文名称 |
| phone_code | string | 国际电话区号（如 `86`） |
| currency_code | string | 货币代码（如 `CNY`） |
| continent_code | string | 大洲代码（如 `AS`、`EU`、`NA`、`SA`、`AF`、`OC`） |
| flag_emoji | string | 国旗 Emoji |
| sort_order | integer | 排序值 |

**缓存 key**：`countries:list:{continent_code|all}:{keyword|all}`

---

### 8.2 获取国家/地区详情

`GET /api/countries/{country_code}`

按 ISO 3166-1 二位字母代码查询单个国家详情。对应视图 `v_countries`。结果缓存到 Redis，TTL 1 小时。

**请求参数**：

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| country_code | path | string | 是 | 国家代码（二位字母，如 `CN`、`US`） |

**请求示例**：
```bash
curl "http://127.0.0.1:8081/api/countries/CN"
```

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "country_code": "CN",
    "country_code3": "CHN",
    "numeric_code": "156",
    "name_zh": "中国",
    "name_en": "China",
    "phone_code": "86",
    "currency_code": "CNY",
    "continent_code": "AS",
    "flag_emoji": "🇨🇳",
    "sort_order": 1
  }
}
```

> 国家代码不存在时，`data` 为 `null`。

**缓存 key**：`countries:detail:{country_code}`

---

### 8.3 清空国家字典缓存

`POST /api/admin/refresh-countries`

主动失效所有国家字典缓存（`countries:*`）。下一次 GET 请求会重新从数据库加载。

**请求参数**：无

**请求示例**：
```bash
curl -X POST "http://127.0.0.1:8081/api/admin/refresh-countries"
```

**响应示例**：
```json
{
  "code": 200,
  "message": "Country cache cleared",
  "data": null
}
```

---

## 九、货币字典接口

### 9.1 获取货币列表

`GET /api/currencies`

返回货币字典列表。对应表 `sys_currency`。支持按状态筛选和名称/代码模糊搜索。结果缓存到 Redis，TTL 1 小时。

**请求参数**：

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| status | query | integer | 否 | 状态（1-启用，0-停用）；不传则返回全部 |
| keyword | query | string | 否 | 关键字（同时匹配中文名、英文名、货币代码） |

**请求示例**：
```bash
# 获取所有货币
curl "http://127.0.0.1:8081/api/currencies"

# 获取启用的货币
curl "http://127.0.0.1:8081/api/currencies?status=1"

# 按名称搜索
curl "http://127.0.0.1:8081/api/currencies?keyword=人民币"
curl "http://127.0.0.1:8081/api/currencies?keyword=CNY"
```

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "currency_code": "CNY",
      "name_zh": "人民币",
      "name_en": "Chinese Yuan",
      "symbol": "¥",
      "status": 1
    }
  ]
}
```

**data 字段说明**：

| 字段 | 类型 | 说明 |
|---|---|---|
| currency_code | string | ISO 4217 货币代码（如 `CNY`、`USD`） |
| name_zh | string | 中文名称 |
| name_en | string | 英文名称 |
| symbol | string | 货币符号（如 `¥`、`$`、`€`） |
| status | integer | 状态（1-启用，0-停用） |

**缓存 key**：`currencies:list:{status|all}:{keyword|all}`

---

### 9.2 获取货币详情

`GET /api/currencies/{currency_code}`

按货币代码查询单个货币详情。对应表 `sys_currency`。结果缓存到 Redis，TTL 1 小时。

**请求参数**：

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| currency_code | path | string | 是 | 货币代码（如 `CNY`、`USD`） |

**请求示例**：
```bash
curl "http://127.0.0.1:8081/api/currencies/CNY"
```

> 货币代码不存在时，`data` 为 `null`。

**缓存 key**：`currencies:detail:{currency_code}`

---

### 9.3 清空货币字典缓存

`POST /api/admin/refresh-currencies`

主动失效所有货币字典缓存（`currencies:*`）。下一次 GET 请求会重新从数据库加载。

**请求参数**：无

**请求示例**：
```bash
curl -X POST "http://127.0.0.1:8081/api/admin/refresh-currencies"
```

**响应示例**：
```json
{
  "code": 200,
  "message": "Currency cache cleared",
  "data": null
}
```

---

## 十、语言字典接口

### 10.1 获取语言列表

`GET /api/languages`

返回语言字典列表。对应表 `sys_language`。支持名称/代码模糊搜索。结果缓存到 Redis，TTL 1 小时。

**请求参数**：

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| keyword | query | string | 否 | 关键字（同时匹配中文名、英文名、语言代码） |

**请求示例**：
```bash
# 获取所有语言
curl "http://127.0.0.1:8081/api/languages"

# 按名称搜索
curl "http://127.0.0.1:8081/api/languages?keyword=中文"
curl "http://127.0.0.1:8081/api/languages?keyword=chi"
```

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "lang_code": "chi",
      "name_zh": "中文",
      "name_en": "Chinese",
      "native_name": "中文"
    }
  ]
}
```

**data 字段说明**：

| 字段 | 类型 | 说明 |
|---|---|---|
| lang_code | string | ISO 639-2b 三位语言代码（如 `chi`、`eng`） |
| name_zh | string | 中文名称 |
| name_en | string | 英文名称 |
| native_name | string | 母语名称 |

**缓存 key**：`languages:list:{keyword|all}`

---

### 10.2 获取语言详情

`GET /api/languages/{lang_code}`

按语言代码查询单个语言详情。对应表 `sys_language`。结果缓存到 Redis，TTL 1 小时。

**请求参数**：

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| lang_code | path | string | 是 | 语言代码（三位，如 `chi`） |

**请求示例**：
```bash
curl "http://127.0.0.1:8081/api/languages/chi"
```

> 语言代码不存在时，`data` 为 `null`。

**缓存 key**：`languages:detail:{lang_code}`

---

### 10.3 清空语言字典缓存

`POST /api/admin/refresh-languages`

主动失效所有语言字典缓存（`languages:*`）。下一次 GET 请求会重新从数据库加载。

**请求参数**：无

**请求示例**：
```bash
curl -X POST "http://127.0.0.1:8081/api/admin/refresh-languages"
```

**响应示例**：
```json
{
  "code": 200,
  "message": "Language cache cleared",
  "data": null
}
```

---

## 十一、时区字典接口

### 11.1 获取时区列表

`GET /api/timezones`

返回时区字典列表。对应表 `sys_timezone`。支持名称/描述模糊搜索。结果缓存到 Redis，TTL 1 小时。

**请求参数**：

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| keyword | query | string | 否 | 关键字（同时匹配时区ID、描述） |

**请求示例**：
```bash
# 获取所有时区
curl "http://127.0.0.1:8081/api/timezones"

# 按名称搜索
curl "http://127.0.0.1:8081/api/timezones?keyword=Shanghai"
curl "http://127.0.0.1:8081/api/timezones?keyword=北京"
```

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "timezone_id": "Asia/Shanghai",
      "offset_utc": "+08:00",
      "description": "中国标准时间（北京/上海）"
    }
  ]
}
```

**data 字段说明**：

| 字段 | 类型 | 说明 |
|---|---|---|
| timezone_id | string | 时区标识符（IANA 格式，如 `Asia/Shanghai`） |
| offset_utc | string | UTC 偏移量（如 `+08:00`、`-05:00`） |
| description | string | 描述说明 |

**缓存 key**：`timezones:list:{keyword|all}`

---

### 11.2 获取时区详情

`GET /api/timezones/detail`

按时区ID查询单个时区详情。对应表 `sys_timezone`。结果缓存到 Redis，TTL 1 小时。

> 注：时区ID含 `/`（如 `Asia/Shanghai`），使用 query parameter 传递，避免 PathVariable 路径匹配问题。

**请求参数**：

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| timezone_id | query | string | 是 | 时区ID（如 `Asia/Shanghai`） |

**请求示例**：
```bash
curl "http://127.0.0.1:8081/api/timezones/detail?timezone_id=Asia/Shanghai"
```

> 时区ID不存在时，`data` 为 `null`。

**缓存 key**：`timezones:detail:{timezone_id}`

---

### 11.3 清空时区字典缓存

`POST /api/admin/refresh-timezones`

主动失效所有时区字典缓存（`timezones:*`）。下一次 GET 请求会重新从数据库加载。

**请求参数**：无

**请求示例**：
```bash
curl -X POST "http://127.0.0.1:8081/api/admin/refresh-timezones"
```

**响应示例**：
```json
{
  "code": 200,
  "message": "Timezone cache cleared",
  "data": null
}
```

---

## 十二、缓存刷新接口（通用字典）

### 12.1 清空单个字典缓存

`POST /api/dict/admin/refresh/{dictType}`

主动失效指定字典类型的所有缓存（`dict:{dictType}:*`）。下一次 GET 请求会重新从数据库加载。

**请求参数**：

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| dictType | path | string | 是 | 字典类型 |

**请求示例**：
```bash
curl -X POST "http://127.0.0.1:8081/api/dict/admin/refresh/gender"
```

**响应示例**：
```json
{
  "code": 200,
  "message": "gender 字典缓存已清除",
  "data": null
}
```

---

### 12.2 清空所有字典缓存

`POST /api/dict/admin/refresh-all`

主动失效所有通用字典缓存。下一次 GET 请求会重新从数据库加载。

**请求参数**：无

**请求示例**：
```bash
curl -X POST "http://127.0.0.1:8081/api/dict/admin/refresh-all"
```

**响应示例**：
```json
{
  "code": 200,
  "message": "所有字典缓存已清除",
  "data": null
}
```

**使用场景**：
- 任意字典表数据更新后，需要立即生效时调用
- Redis 数据异常需要强制刷新时调用

---

## 十三、所有支持的字典类型列表

### 13.1 普通字典（15种）

| 序号 | dictType | 说明 | 对应表 |
|---|---|---|---|
| 1 | `gender` | 性别 | `sys_gender` |
| 2 | `education` | 学历 | `sys_education` |
| 3 | `degree` | 学位 | `sys_degree` |
| 4 | `ethnicity` | 民族（56个） | `sys_ethnicity` |
| 5 | `marital_status` | 婚姻状况 | `sys_marital_status` |
| 6 | `political_status` | 政治面貌 | `sys_political_status` |
| 7 | `id_document_type` | 证件类型 | `sys_id_document_type` |
| 8 | `professional_title` | 职称 | `sys_professional_title` |
| 9 | `enterprise_type` | 企业类型 | `sys_enterprise_type` |
| 10 | `taxpayer_qualification` | 纳税人资质 | `sys_taxpayer_qualification` |
| 11 | `settlement_method` | 结算方式 | `sys_settlement_method` |
| 12 | `invoice_type` | 发票类型 | `sys_invoice_type` |
| 13 | `unit` | 计量单位 | `sys_unit` |
| 14 | `common_status` | 通用状态 | `sys_common_status` |
| 15 | `payment_method` | 支付方式 | `sys_payment_method` |

### 13.2 树形字典（2种）

| 序号 | dictType | 说明 | 对应表 |
|---|---|---|---|
| 1 | `industry_category` | 行业分类（GB/T 4754-2017） | `sys_industry_category` |
| 2 | `occupation` | 职业分类 | `sys_occupation` |

### 13.3 房产字典（15种）

| 序号 | type | 说明 |
|---|---|---|
| 1 | `property_type` | 房源类型 |
| 2 | `decoration` | 装修情况 |
| 3 | `heating_method` | 供暖方式 |
| 4 | `water_supply` | 供水 |
| 5 | `power_supply` | 供电 |
| 6 | `gas_supply` | 供气 |
| 7 | `internet` | 网络接入 |
| 8 | `tv_service` | 电视服务 |
| 9 | `orientation` | 朝向 |
| 10 | `room_type` | 户型 |
| 11 | `rental_area_unit` | 面积单位 |
| 12 | `lease_term` | 租期 |
| 13 | `publish_status` | 发布状态 |
| 14 | `audit_status` | 审核状态 |
| 15 | `property_label` | 房源标签 |

### 13.4 国际化字典

| 类别 | 接口前缀 | 对应表/视图 |
|---|---|---|
| 中国行政区划 | `/api/provinces`、`/api/cities`、`/api/districts`、`/api/towns`、`/api/villages` | `sys_china_region` / 视图 `v_provinces` 等 |
| 国家/地区 | `/api/countries` | `sys_country` / 视图 `v_countries` |
| 货币 | `/api/currencies` | `sys_currency` |
| 语言 | `/api/languages` | `sys_language` |
| 时区 | `/api/timezones` | `sys_timezone` |

---

## 十四、缓存机制汇总

### 14.1 缓存 Key 总览

| 模块 | 缓存 key 模式 | TTL | 清除接口 |
|---|---|---|---|
| 中国行政区划-省份 | `regions:provinces` | 1 小时 | `POST /api/admin/refresh-regions` |
| 中国行政区划-城市 | `regions:cities:{pc\|all}` | 1 小时 | 同上 |
| 中国行政区划-区县 | `regions:districts:{cc\|all}` | 1 小时 | 同上 |
| 中国行政区划-乡镇 | `regions:towns:{dc\|all}` | 1 小时 | 同上 |
| 中国行政区划-村 | `regions:villages:{tc\|all}` | 1 小时 | 同上 |
| 中国行政区划-路径 | `regions:path:{rc\|all}:{lvl\|all}` | 1 小时 | 同上 |
| 国家列表 | `countries:list:{cc\|all}:{kw\|all}` | 1 小时 | `POST /api/admin/refresh-countries` |
| 国家详情 | `countries:detail:{country_code}` | 1 小时 | 同上 |
| 货币列表 | `currencies:list:{st\|all}:{kw\|all}` | 1 小时 | `POST /api/admin/refresh-currencies` |
| 货币详情 | `currencies:detail:{currency_code}` | 1 小时 | 同上 |
| 语言列表 | `languages:list:{kw\|all}` | 1 小时 | `POST /api/admin/refresh-languages` |
| 语言详情 | `languages:detail:{lang_code}` | 1 小时 | 同上 |
| 时区列表 | `timezones:list:{kw\|all}` | 1 小时 | `POST /api/admin/refresh-timezones` |
| 时区详情 | `timezones:detail:{timezone_id}` | 1 小时 | 同上 |
| 普通字典列表 | `dict:{dictType}:list:{st\|all}:{kw\|all}` | 1 小时 | `POST /api/dict/admin/refresh/{dictType}` |
| 普通字典详情 | `dict:{dictType}:item:{code}` | 1 小时 | 同上 |
| 树形字典列表 | `dict:{dictType}:tree:{pc\|all}:{lv\|all}:{kw\|all}` | 1 小时 | 同上 |
| 房产字典类型 | `dict:property:types` | 1 小时 | 同上（refresh-all 也会清除） |
| 房产字典项列表 | `dict:property:{type}:items:{st\|all}:{kw\|all}` | 1 小时 | 同上 |
| 房产字典项详情 | `dict:property:{type}:item:{itemKey}` | 1 小时 | 同上 |

**Key 占位符说明**：
- `{pc|all}`：按 province_code 取值，未传时用 `all`
- `{cc|all}`：按 city_code / continent_code 取值，未传时用 `all`
- `{dc|all}`：按 district_code 取值，未传时用 `all`
- `{tc|all}`：按 town_code 取值，未传时用 `all`
- `{rc|all}`：按 region_code 取值，未传时用 `all`
- `{lvl|all}`：按 region_level 取值，未传时用 `all`
- `{kw|all}`：按 keyword 取值，未传时用 `all`
- `{st|all}`：按 status 取值，未传时用 `all`
- `{lv|all}`：按 level 取值，未传时用 `all`（树形字典专用）
- `{dictType}`：字典类型，如 `gender`、`ethnicity`、`industry_category` 等

### 14.2 缓存特性

- **缓存结构**：Redis String，value 为 JSON 字符串
- **缓存失效方式**：
  - 被动：TTL 1 小时自动过期
  - 主动：调用 refresh 接口，使用 `SCAN` 命令迭代匹配后批量删除（避免 `KEYS` 阻塞）
- **容错降级**：
  - Redis 读取异常 → 降级查 DB
  - 缓存反序列化失败 → 降级查 DB
  - 缓存写入失败 → 不影响返回结果，仅记日志

---

## 十五、错误排查

| 现象 | 可能原因 | 排查建议 |
|---|---|---|
| 500 错误，返回 `{code:500,...}` | MySQL 未启动 / 库名错误 | 检查 `application.yaml` 数据源、MySQL 进程 |
| 500 错误，返回 `{code:500,...}` | Redis 连接失败 | 当前实现 Redis 异常会被吞掉走 DB，若 DB 也异常则 500 |
| 数据更新后查询仍是旧数据 | 缓存未失效 | 调用对应 refresh 接口或等待 1 小时 |
| 视图查询报错 | 视图未创建 | 确认已执行数据库初始化脚本中的全部 CREATE VIEW 语句 |
| 接口无法访问（连接拒绝） | 服务未监听 0.0.0.0 | 本服务仅监听 127.0.0.1，必须通过网关访问 |
| 普通字典返回 500 `不支持的字典类型` | dictType 参数错误 | 先调用 GET /api/dict/types 查看所有支持的类型 |
