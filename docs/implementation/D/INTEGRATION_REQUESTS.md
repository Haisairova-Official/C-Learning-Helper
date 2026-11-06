# D 前端接入约定

契约：v0.1。当前前端已有真实 HTTP 客户端与开发模拟适配，模拟仅在 DEV 且 VITE_USE_MOCK=true 时启用。真实服务失败不会回退模拟。

## 文件与启动

- frontend/src/api/types.ts：请求/响应类型。
- frontend/src/api/real.ts：真实 /api 客户端。
- frontend/src/api/http.ts：Session、CSRF、15 秒请求超时、统一错误和 401 会话失效处理。
- npm run dev：本地模拟，显式 .env.development 配置。
- npm run dev:api：真实 /api 请求，由 Vite 代理到 127.0.0.1:8080。
- npm run build：生产永远使用真实 API；dist/ 是 Nginx 静态目录。

## A 已同意按此前端形状提供

- Assignment：id/title/className/opensAt/closesAt/problems/completedCount/score/maxScore/state；problems 为 id/versionId/title/maxScore。
- Classroom：id/name/memberCount；Member：id/name/studentNumber。
- ResultRow：studentId/name/completedCount/totalCount/score/maxScore。
- 发布 POST /api/teacher/assignments：{title,classId,problemIds,opensAt,closesAt}，A 在服务器创建题目集和快照。
- 题目保存 POST /api/teacher/problems 或 PUT /api/teacher/problems/{id}：TeacherProblem 扩展公开题目，包含 referenceAnswer/testCases/state。具体类型以 types.ts 为准。
- 列表统一 Page 结构。auth/me 保持 id/role；显示名可选后续增加，不影响当前页面。

## B/C 对齐

- B 结果采用 docs/implementation/B/API_EXAMPLES.md，compileMessage 纯文本、memoryKb 显示 KiB，SYSTEM_ERROR 的 score 为 null。
- C 候选结构采用 docs/implementation/C/DTO_EXAMPLES.md，保留 privateNotes/unresolvedContent；缺失样例字段 null 与明确空串区别展示。
- AI 请求仅传题目版本与当前代码；代码编辑后旧分析显示提醒。当前前端省略可选 submissionId。
- 提交和确认导入使用 Idempotency-Key；同一失败重试保留键，内容修改生成新键。

## 待确认/未完整定义

- GET /api/submissions 当前使用 assignmentId/problemId/page/pageSize 查询；历史结果如需“查看代码”，GET /api/submissions/{id} 请提供已授权的 sourceCode。缺字段时前端提示尚未提供，不编造代码。
- 班级新增已接入 POST /api/classes {name}；加入已有学生已接入 POST /api/classes/{id}/members {studentId}，204。学生目录搜索及题库/成绩分页筛选留待扩展。
- TXT/DOCX 通过 multipart 文件输入，真实接口若不支持需明确拒绝；模拟只支持 TXT。
- 发布和作业时间由服务端最终校验；列表按 Asia/Shanghai 展示，时间输入框注明当前设备时区。

## 验证状态

页面及真实客户端已实现，类型检查、构建与9项核心测试通过。真实dev:api服务在127.0.0.1:5173，已通过D的teacher/student登录与主要列表/详情HTTP结构验证。主Agent负责浏览器与完整业务验收；学校AI尚未接入，后端demo指导有明确提示。
