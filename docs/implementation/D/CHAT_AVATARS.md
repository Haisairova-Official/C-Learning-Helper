# 聊天室消息头像

## 当前状态

- Agent：frontend_finish；2026-11-06 23:44，Asia/Shanghai。
- 阶段：前端实现与构建完成，源码已冻结；统一部署与真实页面截图由主 Agent 完成。
- 用户需求：每条消息显示发送者头像，保留已有聊天与管理操作。

## 修改

- `frontend/src/api/management.ts`：`ChatMessage` 增加 `authorAvatarUrl?: string | null`，兼容旧响应与未设置头像的用户。
- `frontend/src/components/ProblemChat.vue`：每条消息左侧显示 32 px 圆形头像，右侧保留姓名、身份、时间、正文和撤回/禁言操作；自己消息继续使用已有青色气泡。
- 使用当前作业、题目和发送者对应的后端地址：`/api/assignments/{assignmentId}/problems/{problemId}/chat/participants/{authorId}/avatar`，可附 `?v=版本标记`。已与后端确认当前输出 UTC 秒与纳秒；前端将其视作不透明的缓存版本，接受字母、数字及常见 URL 安全字符，不依赖日期序列化格式。没有使用当前用户 `/profile/avatar` 代替他人头像，也不加载任意外链。
- 未设置、地址不符合当前房间、图片加载失败均显示去除前后空白后的姓名首个 Unicode 字符；空姓名显示“同”。失败按图片 URL 保留为回退，头像版本 URL 改变后可重新加载。
- 头像容器 `aria-hidden="true"`、图片 `alt=""`，不与右侧姓名重复朗读；图片固定尺寸、居中裁切，不新增或生成图片资产。

## 验证与交接

| 场景 | 方式 | 实际结果 | 限制 |
| --- | --- | --- | --- |
| 类型与生产构建 | `npm run build` | 通过 | 本机；保留既有大包与 npm/Node 版本提示 |
| 现有回归 | `npm test -- --run` | 10 文件、34 项通过 | 不替代头像图片的实际页面验收 |
| 真实消息头像/回退截图 | 主 Agent 使用已有 fixture 消息 | 由主 Agent 补记 | 本 Agent 不发消息、不调用 AI、不重启服务 |

后端字段与头像读取权限由 management_qa 实现，主 Agent 统一部署。前端此次只修改上述两份源码及 Markdown，没有继续修改日期或作业编辑页面。

## 主 Agent 真实浏览器验收

2026-11-06 最终部署后，以独立s2学生打开既有聊天室：

- 未设置头像的消息作者显示姓名首字“验”，左侧32px圆形区域，姓名/时间/正文位置正确，撤回消息也保持作者标识。
- 临时为既有合成作者s1上传1px PNG测试图片，两条既有消息的img均complete且naturalWidth=1、实际渲染宽32px；两者使用相同的受保护URL，alt为空。未发送任何新消息。
- 删除临时图片恢复s1原先无头像状态，轮询后自动回退姓名首字；原资料保持。截图 `.local/chat-avatars.png` 为恢复后的默认头像布局。
- 后端另3组真实HTTP通过，含权限、同房间作者约束、缓存头、头像版本更新和关闭聊天的边界，见 [后端头像交接](../A/CHAT_AVATARS.md)。
