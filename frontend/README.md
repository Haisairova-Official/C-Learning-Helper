# C 课堂前端

Vue 3 / TypeScript / Vite / Element Plus / Monaco Editor。接口基线见 [CONTRACTS.md](../docs/agents/CONTRACTS.md)，当前接入需求见 [D 接入说明](../docs/implementation/D/INTEGRATION_REQUESTS.md)。

## 开发

需要 Node.js 20.19+ 或 22.12+。当前开发验证使用 Node 20.20.2，安装依赖时使用 npm 10.9.4；主机全局 npm 12 对 Node 20 会给出兼容性警告。

```sh
npm ci
npm run dev
```

默认开发模式由 .env.development 显式开启本地模拟，页面持续显示演示标记。模拟不执行 C 代码，不调用学校 AI，评测返回固定展示结果；不能作为业务正确性证据。

连接本地真实后端：

```sh
npm run dev:api
```

开发服务器监听 `0.0.0.0:5173`，可从本机 http://127.0.0.1:5173/ 或局域网 http://192.168.2.100:5173/ 访问（后者为当前 IP）。`/api` 由 Vite 代理到 http://127.0.0.1:8080。后端尚未启动或接口返回错误时直接提示，不回退模拟数据。

## 验证与部署

```sh
npm run typecheck
npm run test
npm run build
```

生产 dist/ 始终使用真实 API；DEV-only 模拟模块从构建中移除。Nginx 将前端路由 fallback 到 index.html，将 /api 转发到后端，使用同域 HTTPS。

Monaco 按作答路由懒加载，编辑器资源较大，构建会提示约 2.7 MB 的压缩前分块；当前不影响构建成功，后续可优化贡献模块及缓存。

## 主要页面

| 页面 | 地址 |
| --- | --- |
| 登录 | /login |
| 学生作业 | / |
| 在线作答 | /assignments/:assignmentId/problems/:problemId |
| 教师题库 | /teacher/problems |
| Easy Parse | /teacher/import |
| 作业发布 | /teacher/assignments |
| 教学班级 | /teacher/classes |
| 学习情况 | /teacher/results |

## 数据与交互边界

- Session 由后端管理；写请求获取并携带服务端定义的 CSRF 头。
- 提交与导入确认带幂等键；请求失败不自动重放写操作。
- 代码草稿使用 sessionStorage，按用户、作业和题目版本隔离；退出登录清理当前用户草稿，关闭标签页后不承诺长期保存。
- 轮询有上限，离开页面会取消后续更新；失败可重新查询已有结果。
- AI 使用本次请求的代码快照；编辑后标记指导已过期。
- 题干、编译消息、导入原文与 AI 文本全部使用 Vue 文本绑定，不使用 v-html。
- 学生结果不依赖隐藏测试的输入输出。参考答案只出现在授权教师编辑/导入响应中。
- DOMPurify 覆盖到 3.4.17，以修复 Monaco 0.57.0 固定传递版本的已知漏洞；Vitest 使用 4.1.12。当前 lockfile 审计为 0 个漏洞。

## 账号设置

所有已登录角色可访问 `/settings`。资料使用 `/api/profile`，头像使用同源 `/api/profile/avatar`；写操作沿用 CSRF。昵称与简介、AI 风格一并保存；头像上传/移除立即保存。学校姓名与登录账号只读。头像只接受 PNG/JPEG、512 KiB 以内；真实服务还校验图片内容、尺寸并重编码。开发模拟资料只保留于当前页面运行期间，生产不会回退模拟。

## 函数题与双语言

教师题库支持完整程序题 / 函数题以及 C17 / C++17。函数接口与起始模板属于公开内容；评测主程序仅在教师私有页编辑，必须包含一个 `/*__STUDENT_CODE__*/`。学生只能按发布版本的语言提交，函数题不要求编写 main。恢复代码时优先使用当前用户/作业/版本的草稿，草稿为空也保留；否则使用公开起始模板。Easy Parse 的 JSON 导入会保留题型、语言、函数接口、起始代码与私有评测主程序，并提供预览修正；TXT / DOCX 不自动推断函数题。函数题缺接口或主程序可保存待修正草稿，发布前必须补齐。

JSON 可粘贴单题对象、题目数组或 `{schemaVersion, title, source, problems: [...]}` 题目集，也可上传 `.json`（内容最多 1 MiB）。`state` 缺省或为 `DRAFT`，其他状态拒绝；`codeLimitBytes` 和 `samples[].explanation` 会保留。导入预览中的教师备注保存在私有导入记录，未映射为题库独立备注字段。

左下角学生资料显示真实班级与独立学号；多班显示首个及总数，完整列表可悬停查看。资料区可进入设置，退出按钮独立。后端需应用 V7，缺少真实学号显示“学号未录入”；正式账号不会从登录名推导学号。

局域网 HTTP 下提交、导入和模拟记录统一使用 `crypto.getRandomValues` 生成 128 位客户端标识，不依赖仅限 HTTPS/localhost 的 `crypto.randomUUID`。请求幂等与重试规则保持不变。
