# D 函数题与 C++17 前端交接

日期：2026-11-06。依据 [函数题约定](../../agents/FUNCTION_PROBLEMS.md)，B 已确认字段。

## 实现

- 公共 Problem 增加 `problemType`、`language`、`functionSignature`、`starterCode`；只有 TeacherProblem 增加 `judgeTemplate`。SubmissionRequest 支持 C17 / CPP17。
- 教师题库表格显示题型与语言；编辑器可选完整程序/函数题、C17/C++17，在学生可见区填接口与起始模板，在教师私有区填评测主程序与函数参考实现。
- 后续草稿策略调整：缺接口或私有主程序允许保存待修正草稿；非空主程序必须恰好含一次 `/*__STUDENT_CODE__*/`。提供不含答案的空白主程序框架按钮，仅字段为空时可填入，避免覆盖已有内容。服务器仍是最终校验方。
- 学生显示题型、语言、对应 `.c` / `.cpp` 文件名和 GCC / G++。函数题显示公开函数接口与“只提交函数实现，无需 main”提示；不会请求或渲染教师主程序。
- 初始代码按草稿 → 公开 starterCode → 对应题型默认提示/程序骨架恢复；空草稿也不会被示例覆盖。函数题无模板时不生成 main。
- 提交语言来自发布题目，学生界面不提供任意工具链选择。旧字段缺失时按 PROGRAM + C17 兼容。
- Workspace 中原目录轮询、颜色进度、焦点/可见性刷新、切题保存、反馈动效和 AI 供应商提示均保留。

## 修改文件

`frontend/src/api/types.ts`、`api/mock.ts`、`views/ProblemsView.vue`、`views/WorkspaceView.vue`、`components/CodeEditor.vue`、`App.vue`；新增 `lib/problemCode.ts`、`tests/problemCode.test.ts`，同步 README 和 D 任务文件。

没有修改 ProblemNavigation、problemProgress、后端或公共契约。

## 验证

- `npm run build` 通过 Vue/TypeScript 检查与生产构建，保留现有 Monaco 大块提示。
- `npm test`：6 文件、20 测试全部通过。新增 4 项覆盖已存在/空草稿优先、函数无 main、旧 C17 与 C++ 程序默认模板、教师接口及占位标记校验。
- 设置页的 7 项新增测试也随本轮回归通过。
- 未重启服务。当前运行实例尚未统一加载 A 的 V5 / B 的 V6，本轮未宣称真实函数评测或浏览器点击验证已完成。

## 后续与限制

由主 Agent 统一应用后端并检查：教师新建两种语言函数题 → 发布 → 学生模板/草稿 → 真实提交 → 返回目录进度。后端负责主程序隔离、语言授权与公开字段防泄露。

后续 JSON 导入已扩展函数元数据，并保留 codeLimitBytes 与 samples[].explanation；详见 [JSON 导入交接](JSON_FUNCTION_IMPORT.md)。TXT / DOCX 不自动推断函数元数据。开发模拟新建题目仍受原有模拟发布范围限制，JSON 解析仅使用真实 API。
