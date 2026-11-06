# 账号设置与 AI 风格：本轮接口交接

日期：2026-11-06。用户追加：学生可填写昵称、头像、bio，并选择 AI 回复风格。

## 分工与约定

- A：负责 V5 迁移、profile 后端、图片规范化和权限测试；不重启正在使用的服务，不修改 AI 模块。
- D：负责设置页面、导航与个人资料状态接入；不修改 WorkspaceView、ProblemNavigation、problemProgress，主 Agent 正维护这些文件。
- 主 Agent：完成题目目录/动效验收、PTA 蓝本整理，以及读取账号偏好并传给 AI 的服务器风格约束。统一构建和重启由主 Agent 完成。

## 接口

- GET `/api/profile`：当前登录账号资料。
- PUT `/api/profile`，JSON `{nickname, bio, aiReplyStyle}`：只更新当前账号，返回完整资料。
- POST `/api/profile/avatar`，multipart `file`：上传 PNG/JPEG，原文件上限 512 KiB；检查实际图片类型、尺寸（单边最多 4096），解码后缩为最多 256×256 并重编码 PNG，存 DB，不保留原始文件或元数据。返回完整资料。
- DELETE `/api/profile/avatar`：移除头像，返回完整资料。
- GET `/api/profile/avatar`：只返回当前账号头像 image/png，不重定向；无头像返回 404。禁共享缓存。
- 完整资料 `{username, displayName, nickname, bio, avatarUrl, aiReplyStyle, updatedAt}`。avatarUrl 为同源 `/api/profile/avatar?v=<版本>` 或 null；不接受任意外部头像 URL。
- nickname 0–32 个字符，bio 0–500 个字符；空昵称使用既有 displayName 展示。不得修改登录名、角色或原学校姓名 displayName。
- aiReplyStyle 枚举 `CONCISE`（简洁直达）、`GUIDED`（循序引导，默认）、`DETAILED`（详细讲解）。仅改变语气/详略，不允许提供学生自定义系统提示词。
- 所有写操作保留 CSRF，身份只取服务端 CurrentActor；资料字段不进入其他学生接口。

## 进度

- 接口已约定，A / D 待接任务。
- 主 Agent 的目录状态与动效已完成实现和构建，正在补真实颜色/隔离验收；PTA 只读取到链接指定单题，整套目录要求登录。
