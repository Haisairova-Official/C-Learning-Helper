# 函数题与 C++17：本轮契约

日期：2026-11-06。用户明确要求函数题，并选择 C17、C++17 同时支持。PTA 采集从实验1-1开始，按可读取的函数题目录逐题建立蓝本。

## 责任与文件协调

- A 正在做账号设置，使用 V5。函数题迁移预留 **V6**，由 B 在本轮负责；不得覆盖 V5。
- B：函数题/语言后端字段、教师校验、学生公开 DTO、提交语言授权、评测组装、固定 gcc/g++ 配置及真实隔离验证。可以修改 TeachingService/ProblemSnapshot/StudentProblemView/TeacherProblemView；保留主 Agent 已加入的 ProblemProgress 聚合。
- D：设置页完成后接函数题前端；先等 B 字段确认。主 Agent 完成当前 Workspace 目录/动效后交接该文件。
- 主 Agent：AI 风格/函数上下文、PTA 蓝本、整体数据导入与验收，统一重启服务。

## 题目字段

- `problemType`: `PROGRAM` / `FUNCTION`，旧题默认 PROGRAM。
- `language`: `C17` / `CPP17`，旧题默认 C17；学生提交 language 必须匹配该发布题目版本，不能自行挑选任意工具链。
- `functionSignature`: 公开函数接口说明，FUNCTION 发布时必填，草稿可为空待修正。
- `starterCode`: 公开起始模板，可为空（PROGRAM 默认现有 main 模板）。FUNCTION 模板只含接口/函数骨架，不是参考答案。
- `judgeTemplate`: 教师私有评测主程序，FUNCTION 发布时必须含且仅含一次 `/*__STUDENT_CODE__*/`。程序题不使用该字段。
- `referenceAnswer`: PROGRAM 为完整程序，FUNCTION 为函数实现，始终教师私有。
- StudentProblemView 仅增加 type/language/functionSignature/starterCode；严禁包含 judgeTemplate/referenceAnswer。
- TeacherProblemView 与版本表保存上述字段。保持现有调用兼容（旧构造器/缺省请求以 PROGRAM+C17 处理）。题目发布快照机制不变。
- 不新增自由编译命令、路径或学生环境参数；C++ 固定 g++ / -std=c++17。先验证本机隔离环境可用。
- 原学生 sourceCode 独立存储；合成源代码只进入评测私有上下文，用 #line 使诊断尽量定位学生代码。

## 验收

- C17 与 CPP17 各完成函数正确、错误、编译失败的真实隔离评测。
- 程序题现有 C17 回归不破坏；如实现 CPP17 程序题也需实测。
- 学生公共题目、提交历史和 AI 结果均不返回教师模板/答案。
- 语言不匹配、无/多占位标记、未授权版本拒绝。
- 主 Agent 新增的目录颜色依然以当前学生、作业、发布版本范围聚合。

草稿校验补充：导入缺接口/裁判模板可保留为 DRAFT；一旦填写非空裁判模板，占位符数量仍须恰好一个。发布与实际评测继续执行严格完整性校验。
