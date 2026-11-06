# D：JSON 函数题快速导入交接

日期：2026-11-06。主 Agent 明确授权 D 扩展 `backend/.../importer` 与前端；B 扩展 DraftProblem / TeachingService 端口，D 未修改 problem 包。

## 支持格式与边界

- `POST /api/imports/parse` 原 TEXT 请求保留，增加 `{sourceType:"JSON",text:"..."}`。文本以 `{` / `[` 开头时自动按 JSON 解析，格式错误不会回退为题干。
- multipart 增加 `.json` + `sourceType=JSON`，读取 UTF-8（含 BOM）和 BOM 标识 UTF-16，沿用文件大小 5 MB 限制；JSON 正文上限 1 MiB、最多 100 道题。
- 支持单题对象、题目数组、`{schemaVersion,title,source,problems:[...]}` 题目集。顶层已知元数据保留到首题的私有导入备注，不进入学生题干。其余未知字段保留到 `unresolvedContent` 待确认。
- `state` 缺省或明确 DRAFT，PUBLISHED 等值拒绝；全部提交仍经 DraftImportPort 创建草稿。
- 保留题目 `problemType/language/functionSignature/starterCode/judgeTemplate`、`codeLimitBytes` 与样例 `explanation`；保留字符串内容、空输入及末尾换行。计分用例未提供 key 时按序生成，未指定 visibility 默认 HIDDEN。
- JSON 不执行代码。重复键、尾随数据、错误类型和过大批次拒绝。参考答案与评测主程序只映射到教师私有字段。

## 草稿与预览

- 函数题缺接口 / 评测主程序会有明确的待修正提示，允许暂存草稿；非空模板无标记或多标记时必须先修正。
- 教师可在预览修改题型、语言、接口、公开起始代码、私有评测主程序、测试点、样例说明及资源限制；题库编辑同步允许不完整函数草稿。
- 公开 / 私有区分开。模板不会自动归入题干，程序题携带非空模板时要求人工确认题型或移入私有备注。
- JSON 文件位置以整个文件行范围表示，候选按数组顺序展示；不是逐字段的精确 JSON 行定位。
- 原 privateNotes 仅存于私有 import_job 记录，尚无题库独立备注字段；界面现明确标为“导入记录的私有备注”，不声称已同步至题库。
- 开发模拟明确拒绝 JSON 解析，提示切真实 API，不伪造结构化识别结果。TXT / DOCX 沿用原规则，默认 PROGRAM + C17，不自动推断函数题。

## 修改文件

后端：新增 `EasyParseJsonParser`；更新 `ImportPreview`（保留旧构造器）、`DocumentTextReader`、`web/ImportController` 与导入相关测试。

前端：`api/types.ts`、`api/real.ts`、`api/mock.ts`、`views/ImportView.vue`、`views/ProblemsView.vue`、`lib/problemCode.ts`；新增 `lib/importing.ts` / `tests/importing.test.ts`。README 与 D 任务同步。

## 验证

- `./mvnw -q -Dtest=EasyParseJsonParserTest,EasyParseTextParserTest,ImportControllerTest,DocumentTextReaderTest test`：4 类 30 项全部通过，包含 10 项新增检查。
- 使用仓库 `examples/pta/function-insert.json` **原顶层包及其两题数组** 分别执行 Controller parse → 私有 Job → commit（DraftImportPort 为测试替身）。两题无 unresolved，C17/CPP17、每题 10 个测试、16384 字节限制、接口/模板/答案及样例说明完整传递。此项没有宣称真实数据库或评测已执行。
- `npm test`：7 文件 24 项通过；`npm run build`：类型检查与生产构建通过。保留现有 Monaco 体积提示。
- 没有重启服务。Maven 定向测试结束，target 已释放给主 Agent 统一构建。

## 下一步

主 Agent 重启后以同一 JSON 样例完成真实 parse → commit → 发布 → 双语言评测；结合设置页验证两种 AI 回复风格。JSON 新字段均有兼容默认值，旧保存预览与文本解析构造器继续可用。
