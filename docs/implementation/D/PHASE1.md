# D 第一轮前端实现与交接

日期：2026-11-06。初始契约 v0.1，当前公共契约 v0.2。状态：页面与客户端已实现并切换真实后端，主 Agent 已完成主要浏览器流程验收。

## 已交付

- frontend/：Vue 3、TypeScript、Vite、Element Plus、Monaco，包含锁定的 package-lock.json。
- 学生：作业/题目列表、公开题目说明、C 代码编辑、分用户分版本草稿、提交评测、结果轮询、历史代码只读预览、AI 请求及旧代码提示。
- 教师：题库查看与草稿编辑、公开/私有内容分区、测试用例编辑、题目集导入预览、问题提示、字段修正、确认保存草稿、作业发布、班级创建/已有学生加入、班级学生和基础成绩查看。
- 真实 API 客户端与开发模拟适配分离；生产不包含模拟角色与固定数据模块，不在服务失败时回退模拟。
- Session/CSRF、幂等键、超时、401 处理、空/错/加载状态；不可信文本安全渲染。

## 已执行验证

| 项目 | 结果 | 环境与限制 |
| --- | --- | --- |
| TypeScript / Vue 类型检查 | 通过 | Node 20.20.2 |
| npm run build | 通过 | 生成 dist/；Monaco 懒加载块约 2.7 MB，有体积提示 |
| npm run test | 9 项通过 | 轮询完成/取消/超时、CSRF/幂等/401/503、草稿隔离/清理/存储失败 |
| npm audit | 0 个漏洞 | 升级 Vitest 至 4.1.12，DOMPurify 固定 3.4.17 |
| 生产模拟数据检查 | 通过 | dist 中不存在 cassist:demo-actor、ui-demo/0.1、demo-teacher 标记 |
| 本地开发服务 | 已启动 | 127.0.0.1:5173 使用 api 模式，连接真实后端 8080；主 Agent 使用独立启动器接管，子 Agent 结束后仍可访问 |
| 浏览器交互 | 主 Agent 已检查 | 登录、作业列表、Monaco 作答、真实提交与逐点结果；教师解析两题、选题、改名并保存一题草稿；AI 页面明确显示模拟提示。截图在本轮浏览器工具结果中查看，未另存文件 |
| D真实HTTP结构烟测 | 通过 | 经5173代理验证teacher/student登录、题库、班级成员、作业、成绩、公开题目、历史；字段与types一致，学生响应无referenceAnswer/testCases |
| 完整业务HTTP | 主Agent报告13项通过 | 导入、发布、AC/WA/CE/TLE、成绩、权限、AI标注、DOCX；完整记录由主Agent维护 |

## 对齐信息

- B：遵循 API_EXAMPLES.md 中的 compileMessage、memoryKb/KiB、SYSTEM_ERROR/null score。
- C：遵循 DTO_EXAMPLES.md 中的候选、样例缺失 null、privateNotes/unresolvedContent 和 AI 扁平响应。
- A：已确认按 frontend/src/api/types.ts 的 Assignment/Class/Member/ResultRow 与发布请求实现服务。
- 主 Agent：导入及 AI 202 响应可能已为终态，前端仍查询 GET 并正确结束轮询；DOCX 上传走真实接口。

## 后续工作

1. 补齐多窗口尺寸、登录过期、截止边界及其他异常页面的完整交互矩阵。
2. 学校模型可用后验证真实 AI 指导及代码变化后的提示。
3. 后续答辩材料需要时另存页面截图；当前保留运行中的作答页面。
4. 班级成员变更、分页筛选、正式账号初始化与目标 Rocky/Nginx 部署继续按协作计划完善。
