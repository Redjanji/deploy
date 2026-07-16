# 实施计划：新增"用户私有图片"功能

## Context（背景）

当前 Image Service 服务仅支持应用级公开图片（Banner、轮播等），所有图片对应用内所有人可见。新增需求：支持用户私有图片场景（用户头像、用户相册），私有图片只有归属用户本人能管理，其他人不可见。

**设计决策（已与用户确认）：**
- 用户身份通过 HTTP Header `X-Owner-Id` 传递（与 `X-App-Id` 风格一致，为后续 OAuth2 网关预留）
- `owner_id = NULL`：公开图片，现有逻辑完全不变
- `owner_id` 有值：私有图片，仅归属用户可见/可管理
- 私有图片访问 URL 不鉴权（安全模糊，UUID 路径即凭证），便于后续 CDN 场景
- 列表接口带 `X-Owner-Id` 时返回"公开图片 + 该用户私有图片"，不带则只返回公开图片
- 需同步更新所有文档（用户后续要基于文档对接 OAuth2 网关）

---

## 实施步骤

### 步骤 1：数据库变更

执行 SQL（由用户在 WSL MySQL 中执行）：
```sql
ALTER TABLE images ADD COLUMN owner_id BIGINT DEFAULT NULL COMMENT '用户ID，为空表示应用级公开图片';
CREATE INDEX idx_owner_id ON images(owner_id);
```

### 步骤 2：实体类修改

**文件：** `src/main/java/com/xss/imageservice/model/entity/ImageEntity.java`

新增字段：
```java
private Long ownerId;
```

### 步骤 3：ImageController 修改

**文件：** `src/main/java/com/xss/imageservice/controller/ImageController.java`

三个接口均新增可选 header 参数（保持 `X-App-Id` 必填不变）：
```java
@RequestHeader(value = "X-Owner-Id", required = false) Long ownerId
```

- `upload`：传 `ownerId` 给 service
- `list`：传 `ownerId` 给 service
- `delete`：传 `ownerId` 给 service

### 步骤 4：ImageService 修改

**文件：** `src/main/java/com/xss/imageservice/service/ImageService.java`

**4.1 upload 方法**：新增 `Long ownerId` 参数，在创建 entity 时设置 `entity.setOwnerId(ownerId)`（可为 null）。

**4.2 listByApp 方法**：新增 `Long ownerId` 参数，修改查询条件：
- `ownerId == null`：追加 `isNull(ImageEntity::getOwnerId)`（只查公开图片）
- `ownerId != null`：追加 `and(w -> w.isNull(ImageEntity::getOwnerId).or().eq(ImageEntity::getOwnerId, ownerId))`

**4.3 delete 方法**：新增 `Long ownerId` 参数，在原有 appId 校验后追加：
```java
if (entity.getOwnerId() != null && !entity.getOwnerId().equals(ownerId)) {
    throw new BusinessException(403, "无权操作他人私有图片");
}
```

### 步骤 5：ImageGroupController 修改

**文件：** `src/main/java/com/xss/imageservice/controller/ImageGroupController.java`

- `addImage`：新增 `@RequestHeader(value="X-Owner-Id", required=false) Long ownerId`，传给 service
- `getGroupWithImages`：同上

### 步骤 6：ImageGroupService 修改

**文件：** `src/main/java/com/xss/imageservice/service/ImageGroupService.java`

**6.1 addImage 方法**：新增 `Long ownerId` 参数，在原有校验后追加私有图片归属校验：
```java
if (image.getOwnerId() != null && !image.getOwnerId().equals(ownerId)) {
    throw new BusinessException(403, "无权操作他人私有图片");
}
```

**6.2 getGroupWithImages 方法**：新增 `Long ownerId` 参数，调用 mapper 时传入，让 SQL 按 ownerId 过滤。

### 步骤 7：ImageGroupItemMapper 修改

**文件：** `src/main/java/com/xss/imageservice/mapper/ImageGroupItemMapper.java`

将 `selectImagesByGroupId` 改为支持 ownerId 过滤（使用 `@Select` script）：
```java
@Select("<script>" +
        "SELECT i.* FROM images i " +
        "JOIN image_group_items gi ON i.id = gi.image_id " +
        "WHERE gi.group_id = #{groupId} AND i.status = 'READY' " +
        "<if test='ownerId != null'>" +
        "  AND (i.owner_id IS NULL OR i.owner_id = #{ownerId}) " +
        "</if>" +
        "<if test='ownerId == null'>" +
        "  AND i.owner_id IS NULL " +
        "</if>" +
        "ORDER BY gi.sort_order" +
        "</script>")
List<ImageEntity> selectImagesByGroupId(@Param("groupId") Long groupId, @Param("ownerId") Long ownerId);
```

### 步骤 8：文档更新

用户特别强调文档准确性（后续要对接 OAuth2 网关），需更新以下文档：

**8.1 docs/实现文档.md**
- images 表建表语句：新增 `owner_id BIGINT DEFAULT NULL` 字段和索引
- ImageEntity 代码片段：新增 ownerId 字段
- ImageController 代码片段：新增 X-Owner-Id header
- ImageService 代码片段：listByApp/delete 方法新增 ownerId 逻辑
- ImageGroupItemMapper 代码片段：selectImagesByGroupId 新增 ownerId 参数
- ImageGroupService 代码片段：addImage/getGroupWithImages 新增 ownerId 逻辑

**8.2 docs/API文档.md**
- 1.2 通用请求头表：新增 `X-Owner-Id` 行（选填，用户ID，用于区分私有图片）
- 2.1 上传图片：参数表新增 X-Owner-Id，补充说明"传则上传为私有图片，不传为公开图片"
- 2.2 查询图片列表：参数表新增 X-Owner-Id，补充说明"传则返回公开+该用户私有，不传只返回公开"
- 2.3 删除图片：参数表新增 X-Owner-Id，补充说明"私有图片仅归属用户可删"
- 3.x 分组接口：addImage 和 getGroupWithImages 新增 X-Owner-Id 说明
- 新增"私有图片机制"说明章节

**8.3 docs/使用文档.md**
- 核心特性列表：新增"支持用户私有图片隔离"
- 新增"私有图片使用"章节：场景说明、X-Owner-Id 使用方式、与公开图片共存说明

**8.4 docs/配置文档.md**
- 检查是否需要更新（预计不需要，无新配置项）

### 步骤 9：测试脚本更新

**文件：** `test-api.ps1`

新增私有图片测试用例（在安全检测测试之后、权限验证之前插入）：
- 上传私有图片（带 X-Owner-Id: 1001）
- 验证用户 1001 列表能看到该私有图片
- 验证不带 X-Owner-Id 列表看不到该私有图片
- 验证用户 1002 列表看不到用户 1001 的私有图片
- 验证用户 1002 不能删除用户 1001 的私有图片（应返回 403）
- 验证用户 1001 能删除自己的私有图片

注意：由于 Send-MultipartRequest 函数目前硬编码 AppId，需要扩展支持 OwnerId 参数。

---

## 验证方式

1. **编译验证**：`./mvnw.cmd clean compile` 通过
2. **服务启动**：`./mvnw.cmd spring-boot:run` 正常启动
3. **运行测试**：`powershell -ExecutionPolicy Bypass -File test-api.ps1` 全部通过
4. **手动验证关键路径**：
   - 不带 X-Owner-Id 上传 → 公开图片，列表可见
   - 带 X-Owner-Id 上传 → 私有图片，仅同 owner 可见
   - 跨 owner 删除 → 403

---

## 关键文件清单

| 文件 | 修改类型 |
| :--- | :--- |
| `src/main/java/com/xss/imageservice/model/entity/ImageEntity.java` | 新增字段 |
| `src/main/java/com/xss/imageservice/controller/ImageController.java` | 新增参数 |
| `src/main/java/com/xss/imageservice/service/ImageService.java` | 业务逻辑 |
| `src/main/java/com/xss/imageservice/controller/ImageGroupController.java` | 新增参数 |
| `src/main/java/com/xss/imageservice/service/ImageGroupService.java` | 业务逻辑 |
| `src/main/java/com/xss/imageservice/mapper/ImageGroupItemMapper.java` | SQL 修改 |
| `docs/实现文档.md` | 文档更新 |
| `docs/API文档.md` | 文档更新 |
| `docs/使用文档.md` | 文档更新 |
| `test-api.ps1` | 新增测试用例 |

**不修改的文件**（保持兼容）：
- `ImageVO.java`：URL 不区分公开/私有，前端无感知
- `LocalImageController.java`：访问不鉴权
- `ImageConfigProperties.java`：无新配置项
- `application.yaml`：无新配置项
