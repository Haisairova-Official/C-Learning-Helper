# 账号设置后端交接

## 当前状态

- Agent：A；更新时间：2026-11-06 18:15（Asia/Shanghai）。
- 阶段：可联调；依据 [本轮接口](../../agents/PROFILE_SETTINGS.md)，兼容原 v0.2 业务。
- 本轮完成后端代码和 19 项定向测试；没有重启服务或直接更新运行数据库。V5 的真实迁移、页面联调由主 Agent 与本轮其他功能统一执行。

## 已完成

- `backend/src/main/resources/db/migration/V5__account_profile.sql`：给 app_user 新增 nickname、bio、ai_reply_style、avatar_png、profile_updated_at；默认昵称和简介为空、风格 GUIDED、头像为空。风格另有数据库 CHECK，既有 username/display_name/role 保留。
- `identity/ProfileController.java`、`ProfileService.java`、`ProfileView.java`、`ProfileUpdate.java`、`AiReplyStyle.java`：实现 GET/PUT `/api/profile` 和 POST/DELETE/GET `/api/profile/avatar`。
- `identity/AvatarNormalizer.java`：只接收实际 PNG/JPEG；原数据和实际读取上限都为 512 KiB；先检查 1–4096 尺寸，再采样解码、缩放到最大 256×256，保留长宽比、不放大小图，并用新 BufferedImage 重编码 PNG。原始元数据、尾随内容不落库。使用内存 ImageIO 流，不新增运行时依赖。
- `common/ApiExceptionHandler.java`：缺 multipart `file` 返回安全 400 INVALID_REQUEST；图片超限返回 413，损坏/类型/尺寸错误返回 400，不返回解码异常原文。
- 所有端点使用 CurrentActor，SQL 同时约束当前 id 和 enabled。URI/表单不接受目标账号；头像和资料都带 private/no-store，头像 image/png + nosniff，不向其他学生 DTO 增加资料字段。
- nickname/bio 按 Unicode code point 分别限制 32/500；昵称去除首尾空白，简介保留换行；null 文本按空字符串。风格必填且仅三个枚举，写入保留 CSRF。

## 主 Agent AI 接入

注入 `com.cassist.identity.ProfileService`，调用：

```java
String style = profiles.getAiReplyStyle(actor.id());
```

返回 `CONCISE`、`GUIDED`、`DETAILED` 之一；账号不存在或已禁用返回 401 UNAUTHENTICATED。这里只提供可信枚举，不提供学生自定义系统提示词。AI 提示词和输出防泄露仍由主 Agent/C 模块管理，A 未修改 AI 目录。

## D 接口信息

所有返回资料：`{username, displayName, nickname, bio, avatarUrl, aiReplyStyle, updatedAt}`。updatedAt 为 UTC Instant；头像存在时 avatarUrl 是 `/api/profile/avatar?v=<更新时间微秒>`，没有头像为 null。GET 头像没有文件时 404 AVATAR_NOT_FOUND。请求与 [冻结接口](../../agents/PROFILE_SETTINGS.md) 一致。

## 验证

执行：`cd backend && ./mvnw -B -ntp -Dtest=AvatarNormalizerTest,ProfileServiceTest,ProfileControllerSecurityTest test`。

| 场景 | 测试 | 实际结果与限制 |
| --- | --- | --- |
| PNG/JPEG、缩放、禁止放大、元数据/尾随内容移除、真实类型、空/损坏/尺寸/字节超限 | AvatarNormalizerTest，6 项 | 通过，真实 ImageIO 解码与生成，无数据库 |
| 32 个 emoji、500 字符简介、超限/空风格、保持学校身份、禁用账户、规范化头像保存、风格读取 | ProfileServiceTest，7 项 | 通过，JdbcTemplate 使用 mock；未宣称真实迁移完成 |
| 匿名拒绝、三种写操作 CSRF、当前身份而非 query userId、无效枚举/缺文件安全400、PNG 私有缓存、404 | ProfileControllerSecurityTest，6 项 | 通过，真实 MVC 与 SecurityFilterChain，业务服务 mock |
| 共享工程编译 | 同一 Maven 命令 | 通过（Java 27，release 21）；仅运行上述 19 项定向测试 |
| V5 数据库迁移、资料刷新/重启持久化、前端上传与 AI 风格联调 | 主 Agent 后续统一执行 | 本轮未执行，不影响当前服务 |

## 跨 Agent 与下一步

- D 已收到 DTO、code point 长度、缓存和 pending migration 信息。
- 主 Agent 已收到 getAiReplyStyle 方法与定向测试结果；统一 verify、应用 V5/V6、重启和真实 HTTP/页面联调。
- V6 已留给 B 的函数题/C++17；A 未修改 TeachingService、ProblemSnapshot 或 ProblemProgress。
- 正式 MariaDB/Rocky 8 验证沿用原待办；不能用 mock 仓储测试替代实际数据库验收。
