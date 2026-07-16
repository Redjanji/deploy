# XSS 微服务系统

> 基于 Spring Boot 3.3 + Spring Cloud Alibaba 2023.0.1.0 的微服务架构系统

## 项目概述

XSS 是一个面向房地产行业的微服务系统，包含 11 个核心服务：

| 服务 | 端口 | 职责 |
|------|------|------|
| **gateway-service** | 8080 | 统一入口、鉴权、路由转发、限流熔断 |
| **dict-service** | 8081 | 字典数据（行政区划、国家、货币、语言、时区） |
| **image-service** | 8082 | 图片上传、存储、处理、分组管理 |
| **auth-service** | 8083 | 用户注册、登录、JWT 签发、Token 黑名单 |
| **property-service** | 8085 | 房源 CRUD、搜索、上下架、审核 |
| **search-service** | 8086 | 房源全文检索、地图找房、聚合统计 |
| **analytics-service** | 8087 | 业务统计、数据报表、趋势分析 |
| **message-service** | 8088 | 消息通知（邮件/短信）、模板管理 |
| **favorite-service** | 8089 | 用户收藏、收藏列表管理 |
| **review-service** | 8091 | 房源审核、审核任务管理 |
| **booking-service** | 8092 | 预约看房、预约记录管理 |

## 技术栈

| 分类 | 技术 | 版本 |
|------|------|------|
| 语言 | Java | 21 |
| 框架 | Spring Boot | 3.3.1 |
| 微服务 | Spring Cloud | 2023.0.0 |
| 微服务 | Spring Cloud Alibaba | 2023.0.1.0 |
| 服务发现 | Nacos | 2.3.0 |
| 配置中心 | Nacos Config | 2.3.0 |
| 限流熔断 | Sentinel | 1.8.7 |
| ORM | MyBatis-Plus | 3.5.5 |
| 搜索引擎 | Elasticsearch | 8.13.x |
| 消息队列 | RabbitMQ | 3.13.x |
| 对象存储 | MinIO | 9.x |
| 缓存 | Redis | 7.x |
| 认证 | JWT (jjwt) | 0.12.6 |

## 快速开始

### 前置条件

- JDK 21+
- Maven 3.9+
- Docker（用于运行基础设施服务）

### 1. 启动基础设施

```bash
# 启动 Nacos Server（必须先启动）
docker run -d --name nacos -p 8848:8848 -p 9848:9848 -p 9849:9849 \
  -e MODE=standalone -e NACOS_AUTH_ENABLE=false nacos/nacos-server:v2.3.0

# 启动 Sentinel Dashboard（用于限流规则可视化管理和动态推送）
# 注意：容器内端口为 8858，需映射到宿主机 8718
docker run -d --name sentinel-dashboard -p 8718:8858 bladex/sentinel-dashboard:1.8.7
```

> **Nacos 控制台**：http://127.0.0.1:8848/nacos（默认账号密码：nacos/nacos）  
> **Sentinel 控制台**：http://127.0.0.1:8718（默认账号密码：sentinel/sentinel）

### 2. 启动 WSL 基础设施

本项目开发环境使用 WSL 运行 MySQL、Redis、MinIO、Elasticsearch、RabbitMQ：

```bash
# 进入 WSL
wsl -d Ubuntu-26.04

# 启动所有基础设施
sudo service mysql start
sudo service redis-server start
docker start minio
docker start es
docker start rabbitmq
```

> 如需重新搭建基础设施，参考 [docs/配置外部化与环境切换说明.md](docs/配置外部化与环境切换说明.md)

### 3. 编译项目

```bash
cd xss
mvn clean compile
```

### 4. 启动服务（按顺序）

```bash
# 方式一：使用 Maven（每个服务一个终端）
mvn spring-boot:run -pl dict-service
mvn spring-boot:run -pl image-service
mvn spring-boot:run -pl auth-service
mvn spring-boot:run -pl property-service
mvn spring-boot:run -pl search-service
mvn spring-boot:run -pl analytics-service
mvn spring-boot:run -pl message-service
mvn spring-boot:run -pl favorite-service
mvn spring-boot:run -pl review-service
mvn spring-boot:run -pl booking-service
mvn spring-boot:run -pl gateway-service

# 方式二：使用已编译的 Jar（每个服务一个终端）
java -jar dict-service/target/dict-service-0.0.1-SNAPSHOT.jar
java -jar image-service/target/image-service-0.0.1-SNAPSHOT.jar
java -jar auth-service/target/auth-service-0.0.1-SNAPSHOT.jar
java -jar property-service/target/property-service-0.0.1-SNAPSHOT.jar
java -jar search-service/target/search-service-0.0.1-SNAPSHOT.jar
java -jar analytics-service/target/analytics-service-0.0.1-SNAPSHOT.jar
java -jar message-service/target/message-service-0.0.1-SNAPSHOT.jar
java -jar favorite-service/target/favorite-service-0.0.1-SNAPSHOT.jar
java -jar review-service/target/review-service-0.0.1-SNAPSHOT.jar
java -jar booking-service/target/booking-service-0.0.1-SNAPSHOT.jar
java -jar gateway-service/target/gateway-service-0.0.1-SNAPSHOT.jar
```

> **启动顺序关键**：search-service 必须在 property-service 之后启动，因为 MQ 消费时需要调用 property-service 的详情接口。

## Spring Cloud Alibaba 配置

### Nacos 服务发现

所有服务已接入 Nacos 服务发现，通过服务名调用而非硬编码 IP：

```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:8848}
        namespace: ${NACOS_NAMESPACE:public}
```

**服务名映射**：

| 服务名 | 服务 |
|--------|------|
| `gateway-service` | gateway-service |
| `dict-service` | dict-service |
| `image-service` | image-service |
| `auth-service` | auth-service |
| `property-service` | property-service |
| `search-service` | search-service |
| `analytics-service` | analytics-service |
| `message-service` | message-service |
| `favorite-service` | favorite-service |
| `review-service` | review-service |
| `booking-service` | booking-service |

**服务间调用示例**（property-service）：

```java
// RestTemplate 必须添加 @LoadBalanced 注解
@Bean
@LoadBalanced
public RestTemplate loadBalancedRestTemplate(RestTemplateBuilder builder) {
    return builder.build();
}

// 使用服务名调用
String url = "http://image-service/api/images/" + imageId;
restTemplate.getForObject(url, String.class);
```

### Nacos 配置中心

所有服务已接入 Nacos 配置中心，使用 `spring.config.import` 方式引入远程配置：

```yaml
spring:
  config:
    import: "optional:nacos:{service-name}.yaml"
  cloud:
    nacos:
      config:
        server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:8848}
        namespace: ${NACOS_NAMESPACE:public}
        file-extension: yaml
```

> **注意**：Spring Cloud 2023.x 默认不加载 `bootstrap.yaml`，需使用 `spring.config.import` 方式，或添加 `spring-cloud-starter-bootstrap` 依赖。

**配置文件命名规则**：

| 配置类型 | Data ID | 说明 |
|----------|---------|------|
| 公共配置 | `common.yaml` | 所有服务共享（MySQL/Redis/RabbitMQ 连接） |
| 服务专属 | `gateway-service.yaml` | gateway-service 专属配置 |
| 服务专属 | `dict-service.yaml` | dict-service 专属配置 |
| 服务专属 | `property-service.yaml` | property-service 专属配置 |
| ... | ... | 各服务专属配置 |

### Sentinel 限流熔断

gateway-service 已接入 Sentinel，保护后端服务：

**配置**：

```yaml
spring:
  cloud:
    sentinel:
      transport:
        dashboard: ${SENTINEL_DASHBOARD:127.0.0.1:8718}
        port: 8719
      eager: true
```

**限流规则**（通过 `SentinelFlowRuleConfig.java` 在启动时加载）：

| 资源名 | 限流类型 | QPS阈值 | 说明 |
|--------|----------|---------|------|
| `GET:/api/dict/items` | QPS | 50 | 字典项查询 |
| `GET:/api/properties` | QPS | 30 | 房源列表 |
| `POST:/token` | QPS | 20 | Token 获取 |

> **注意**：资源名使用 `METHOD:URI` 格式。Sentinel Spring MVC 适配器默认使用路径模式（如 `/api/**`），
> 无法区分不同接口。通过自定义 `SentinelRateLimitFilter` 使用 `SphU.entry()` 手动限流，实现精确的资源名匹配。
> 已通过压测验证（100 请求/10 QPS 阈值，90 个被限流返回 429）。

**压测脚本**：`gateway-service/stress-test.ps1`

```powershell
.\stress-test.ps1 -Url "http://localhost:8080/token?appId=test&timestamp=0&nonce=test&sign=test" -TotalRequests 100 -Concurrency 20
```

### Actuator 健康检查

所有服务已启用 Actuator 健康检查：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: always
```

**访问端点**：

| 端点 | 说明 |
|------|------|
| `/actuator/health` | 健康状态（数据库、Redis、MQ 等） |
| `/actuator/info` | 应用信息 |
| `/actuator/metrics` | 运行指标 |

## 环境变量配置

### Nacos 相关

| 环境变量 | 说明 | 默认值 |
|---------|------|--------|
| `NACOS_SERVER_ADDR` | Nacos Server 地址 | `127.0.0.1:8848` |
| `NACOS_NAMESPACE` | Nacos 命名空间 | `public` |

### Sentinel 相关

| 环境变量 | 说明 | 默认值 |
|---------|------|--------|
| `SENTINEL_DASHBOARD` | Sentinel Dashboard 地址 | `127.0.0.1:8718` |

### 基础设施相关

| 环境变量 | 说明 | 默认值 |
|---------|------|--------|
| `MYSQL_HOST` | MySQL 主机 | `172.24.35.23` |
| `MYSQL_USER` | MySQL 用户名 | `root` |
| `MYSQL_PASS` | MySQL 密码 | `root` |
| `REDIS_HOST` | Redis 主机 | `172.24.35.23` |
| `REDIS_PASSWORD` | Redis 密码 | `redisroot` |
| `RABBITMQ_HOST` | RabbitMQ 主机 | `172.24.35.23` |

### 服务地址（开发环境回退）

| 环境变量 | 说明 | 默认值 |
|---------|------|--------|
| `SERVICE_DICT` | dict-service 地址 | `http://dict-service` |
| `SERVICE_IMAGE` | image-service 地址 | `http://image-service` |
| `SERVICE_AUTH` | auth-service 地址 | `http://auth-service` |
| `SERVICE_PROPERTY` | property-service 地址 | `http://property-service` |
| `SERVICE_SEARCH` | search-service 地址 | `http://search-service` |
| `SERVICE_ANALYTICS` | analytics-service 地址 | `http://analytics-service` |
| `SERVICE_MESSAGE` | message-service 地址 | `http://message-service` |

> **生产环境**：通过 Nacos 服务发现自动解析服务名，无需配置这些环境变量。

## 验证服务发现

启动所有服务后，通过以下方式验证 Nacos 服务发现是否生效：

### 1. 检查 Nacos 控制台

访问 http://127.0.0.1:8848/nacos，进入「服务管理」→「服务列表」，应能看到所有 8 个服务已注册。

### 2. 验证健康检查

```bash
# 检查各服务健康状态
curl http://127.0.0.1:8081/actuator/health  # dict-service
curl http://127.0.0.1:8082/actuator/health  # image-service
curl http://127.0.0.1:8083/actuator/health  # auth-service
curl http://127.0.0.1:8085/actuator/health  # property-service
curl http://127.0.0.1:8086/actuator/health  # search-service
curl http://127.0.0.1:8087/actuator/health  # analytics-service
curl http://127.0.0.1:8088/actuator/health  # message-service
curl http://127.0.0.1:8080/actuator/health  # gateway-service
```

### 3. 验证服务间调用

通过网关调用，验证服务名解析是否正常：

```bash
# 获取应用 Token
curl -X POST http://127.0.0.1:8080/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "appId=my-backend-system&timestamp=$(date +%s)&nonce=$(uuidgen | tr -d '-')&sign=test"

# 调用字典服务（通过服务名解析）
curl http://127.0.0.1:8080/api/dict/items?type=property_type \
  -H "Authorization: Bearer <your-token>"
```

## 目录结构

```
xss/
├── pom.xml                              # 根 POM（统一依赖管理）
├── .env.example                         # 环境变量模板
├── docs/                                # 文档目录
│   ├── 微服务集群问题盘点与待实现清单.md
│   ├── 配置外部化与环境切换说明.md
│   ├── 服务通信与部署文档.md
│   └── 单元测试汇总报告.md
├── gateway-service/                     # 网关服务
├── dict-service/                        # 字典服务
├── image-service/                       # 图片服务
├── auth-service/                        # 认证服务
├── property-service/                    # 房源服务
├── search-service/                      # 搜索服务
├── analytics-service/                   # 统计服务
└── message-service/                     # 消息服务
```

## 详细文档

| 服务 | API 文档 | 使用文档 | 实现方案 | 配置文档 |
|------|---------|---------|---------|---------|
| gateway-service | [API文档.md](gateway-service/docs/API文档.md) | [使用文档.md](gateway-service/docs/使用文档.md) | [实现方案.md](gateway-service/docs/实现方案.md) | [配置文档.md](gateway-service/docs/配置文档.md) |
| dict-service | [API文档.md](dict-service/docs/API文档.md) | [使用文档.md](dict-service/docs/使用文档.md) | [实现方案.md](dict-service/docs/实现方案.md) | [配置文档.md](dict-service/docs/配置文档.md) |
| image-service | [API文档.md](image-service/docs/API文档.md) | [使用文档.md](image-service/docs/使用文档.md) | [实现方案.md](image-service/docs/实现方案.md) | [配置文档.md](image-service/docs/配置文档.md) |
| auth-service | [API文档.md](auth-service/docs/API文档.md) | [使用文档.md](auth-service/docs/使用文档.md) | [实现方案.md](auth-service/docs/实现方案.md) | [配置文档.md](auth-service/docs/配置文档.md) |
| property-service | [API文档.md](property-service/docs/API文档.md) | [使用文档.md](property-service/docs/使用文档.md) | [实现方案.md](property-service/docs/实现方案.md) | [配置文档.md](property-service/docs/配置文档.md) |
| search-service | [API文档.md](search-service/docs/API文档.md) | [使用文档.md](search-service/docs/使用文档.md) | [实现方案.md](search-service/docs/实现方案.md) | [配置文档.md](search-service/docs/配置文档.md) |
| analytics-service | [API文档.md](analytics-service/docs/API文档.md) | [使用文档.md](analytics-service/docs/使用文档.md) | [实现方案.md](analytics-service/docs/实现方案.md) | [配置文档.md](analytics-service/docs/配置文档.md) |
| message-service | [API文档.md](message-service/docs/API文档.md) | [使用文档.md](message-service/docs/使用文档.md) | [实现方案.md](message-service/docs/实现方案.md) | [配置文档.md](message-service/docs/配置文档.md) |

## 问题排查

### Nacos 服务注册失败

1. 检查 Nacos Server 是否启动：`curl http://127.0.0.1:8848/nacos/v1/ns/instance/list?serviceName=gateway-service`
2. 检查服务配置的 `NACOS_SERVER_ADDR` 是否正确
3. 检查防火墙是否允许访问 8848 端口

### Sentinel 规则不生效

1. 检查 Sentinel Dashboard 是否启动：`curl http://127.0.0.1:8718`
2. 检查网关日志中是否有 Sentinel 连接成功的日志
3. 确认资源名格式为 `METHOD:URI`（如 `POST:/token`、`GET:/api/properties`），而非纯路径
4. 检查 `spring.cloud.sentinel.eager` 是否设置为 `true`
5. 查看 Sentinel metrics 日志确认实际资源名：`C:\Users\<user>\logs\csp\gateway-service-metrics.log.*`
6. 使用压测脚本验证：`.\gateway-service\stress-test.ps1`

### 服务间调用失败

1. 检查目标服务是否已注册到 Nacos
2. 检查 RestTemplate 是否添加了 `@LoadBalanced` 注解
3. 检查调用 URL 是否使用服务名（如 `http://image-service`）而非 IP

## 许可证

MIT License