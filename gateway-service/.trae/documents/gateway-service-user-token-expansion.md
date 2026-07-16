# 网关服务业务拓展:支持用户 Token

## Context

当前 gateway-service 只支持"应用 Token":调用方用 `appId+appSecret` 通过 HMAC-SHA256 签名换取 JWT(HMAC 对称密钥签发),携带 JWT 访问 `/api/**`,网关校验后转发到后端。

业务需要新增"用户 Token"支持:用户通过独立的 `auth-service` 登录后获得 JWT(由 auth-service 用 RS256 私钥签发),网关用 RSA 公钥验签,识别后注入 `X-User-Id` Header 给后端。两种 Token 共存,网关在过滤器中按 JWT 的 `type` claim 区分。

**关键决策**(已与用户确认):
- 应用 Token:type=app,继续用 HMAC(网关自签自验),sub=appId
- 用户 Token:type=user,用 RS256(auth-service 私钥签发、网关公钥验签),sub=userId
- 密钥不共用,职责清晰
- auth-service 地址暂留占位

## 改造文件清单

### 1. 新建 `src/main/java/com/xss/gatewayservice/config/JwtProperties.java`

用 `@ConfigurationProperties(prefix="jwt")` 承载两个密钥配置,替代散落的 `@Value`:
- `secretBase64` — 应用 Token HMAC 密钥(原 `jwt.secret-base64`)
- `userPublicKey` — 用户 Token RSA 公钥(PEM 字符串,新配置 `jwt.user-public-key`,可空)

### 2. 改造 `src/main/java/com/xss/gatewayservice/util/JwtUtil.java`

- 构造器改为注入 `JwtProperties`
- 持有 `SecretKeySpec hmacKey`(已有)与 `PublicKey rsaPublicKey`(新增,占位未配时为 null)
- `generateToken(String appId)`:保持签名不变,内部加 `.claim("type", "app")`,显式 `signWith(hmacKey, Jwts.SIG.HS256)`
- **新增** `parseClaims(String token)`:用 `Jwts.parser().keyLocator(Locator<Key>)` 根据 `header.alg` 动态选密钥(HS256→hmacKey,RS256→rsaPublicKey),返回 `Claims`。这是 jjwt 0.12.6 推荐的动态密钥写法,优于"先试 HMAC 再试 RSA"的异常驱动方式。
- 保留 `validateTokenAndGetAppId(String token)`:内部改为 `return parseClaims(token).getSubject()`,向后兼容
- 新增私有 `loadPublicKey(String pem)`:去掉 PEM 头尾标记与空白后 Base64 解码为 DER,用 `KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der))`。空值返回 null。

### 3. 改造 `src/main/java/com/xss/gatewayservice/filter/JwtAuthenticationFilter.java`

- 保持只处理 `/api/` 前缀(`/auth/**` 自动跳过 JWT 校验)
- 把 `validateTokenAndGetAppId` 换成 `parseClaims`,读取 `type` claim:
  - `type=app`(或无 type 兼容旧 Token):`request.setAttribute("appId", subject)`
  - `type=user`:`request.setAttribute("userId", subject)`
  - 其他:401 `Invalid token`
- principal 仍用 subject,`UsernamePasswordAuthenticationToken` 不变

### 4. 改造 `src/main/java/com/xss/gatewayservice/controller/GatewayController.java`

- `@RequestMapping("/api/**")` 改为 `@RequestMapping({"/api/**", "/auth/**"})`(Spring MVC 原生支持数组写法)
- 转发前从 request attribute 取 `appId`/`userId`,在剥离 `host`/`authorization` 之后、构建 `HttpEntity` 之前,添加 `X-App-Id` / `X-User-Id` Header
- 路由匹配逻辑 `path.startsWith(r.prefix)` 无需改(`/api` 与 `/auth` 前缀各自匹配)

### 5. 改造 `src/main/java/com/xss/gatewayservice/config/SecurityConfig.java`

- 在 `authorizeHttpRequests` 中 `/api/**` 规则前加 `auth.requestMatchers("/auth/**").permitAll()`
- IP 白名单逻辑只在 `/api/**` 的 `access()` 内,`/auth/**` 自动不受限(符合登录注册接口需求)
- Spring Security 6 lambda 风格保持一致

### 6. 改造 `src/main/resources/application.yaml`

- `jwt` 节点下新增 `user-public-key: ${JWT_USER_PUBLIC_KEY:}`(留空占位,过渡期用户 Token 验签一律 401,应用 Token 不受影响)
- `resource.routes` 新增 `- prefix: /auth, target: http://127.0.0.1:8082`(占位地址,部署时替换为实际 auth-service)

## 复用的现有代码

- `HmacUtil.hmacSha256` / `constantTimeEquals` — `/token` 签发流程不变
- `TokenController` — 不用改,`generateToken(appId)` 内部由 JwtUtil 加 `type=app`
- `AppCredentialsProperties` / `ResourceRoutesProperties` / `IpWhitelistProperties` — 不用改
- `CacheConfig` — nonce 缓存机制不变
- `GatewayController` 现有的路由匹配、Header 透传、错误处理逻辑 — 不变

## 安全要点

1. **type claim 受签名保护**:篡改 type 会导致验签失败,过滤器在 `parseClaims` 验签通过后才读 type
2. **alg 混淆攻击防御**:keyLocator 严格按 alg 返回对应密钥,HS256 拿不到 RSA 公钥、RS256 拿不到 HMAC 密钥,`alg=none` 默认禁用
3. **RSA 公钥未配时的降级**:占位空值下,用户 Token 一律 401,应用 Token 不受影响——符合过渡期需求
4. **/auth/** 不带 IP 白名单**:登录注册源 IP 不固定,permitAll 合理;后续可在网关层加速率限制,或由 auth-service 内部做账号锁定

## 验证方式

1. **单元测试**:运行 `mvnw test`,现有 `gatewayserviceApplicationTests` 应全部通过(应用 Token 加 `type=app` 后 sub 不变,断言不受影响)
2. **自动化自检**:运行 `test-api.ps1`,9 个核心用例应全部通过
3. **用户 Token 手动验证**(可选,需临时生成 RSA 密钥对):
   - 用 OpenSSL 生成 2048 位 RSA 密钥对
   - 把公钥 PEM 填入 `JWT_USER_PUBLIC_KEY` 环境变量
   - 用私钥签发一个 `type=user, sub=test-user` 的 JWT
   - 携带该 Token 调用 `/api/cities`,确认网关能识别(后端未启动时返回 503 即代表验签通过)
4. **/auth/** 转发验证**:启动一个简单的占位服务监听 8082,POST `http://localhost:8080/auth/login` 确认能转发

## 潜在风险

- RSA 公钥配置错误(格式不符)会启动失败,`loadPublicKey` 抛 `IllegalStateException`
- auth-service 私钥泄露将导致所有用户 Token 可伪造,应独立部署、最小权限访问
- 集群部署下 nonce 缓存为本地内存(已有问题,本次不引入新风险)
