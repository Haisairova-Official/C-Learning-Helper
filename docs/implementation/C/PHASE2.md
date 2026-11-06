# C 第二阶段：HTTP、文件读取与记录持久化

日期：2026-11-06。本阶段由主 Agent 接手，复用 C 第一轮解析和 AI 核心。

## 已写入的实现

- DocumentTextReader：TXT（UTF-8、带 BOM 的 UTF-16）与 DOCX 正文/表格文本读取；5 MB 上传上限、8 MB 解压上限、条目/层级限制、禁止 DTD 和外部实体；不向磁盘解包。
- ImportController：教师/管理员文本或文件解析、私有预览查询、候选校验、确认保存草稿。确认前拒绝未处理的归属不明内容、不成对样例与无效运行限制。
- JsonJobStore：import_job / ai_analysis 的 JDBC JSON 记录，按 owner 或管理员权限读取；表名为固定服务端允许列表。
- AiAnalysisController：按题目和版本授权、代码快照、可选提交归属检查、结果持久化；默认学校接口未配置时返回 503。仅 demo/local profile 显式启用 mock，结果明确说明不是实际 AI 分析。
- 重复确认使用 A 的 DraftImportPort 事务和导入回执，测试点 ID 在同一候选内保持稳定，避免重放产生不同请求哈希。

## 接口实现说明

当前解析为有大小限制的同步操作，仍按契约返回 HTTP 202 + id，status 可直接为 READY；AI mock 可直接为 COMPLETED。前端随后 GET 记录即可，不强制经过每个中间状态。

参考答案、候选与当前代码快照只在受限记录内保存，学生读取 AI 结果只得到指导字段和代码摘要。

## 验证

执行：backend 下 ./mvnw -q -Dtest=DocumentTextReaderTest,EasyParseTextParserTest,AiGuidanceCoreTest test。

结果：25 项读取/解析/AI 核心测试通过；随后新增 4 项 Controller 测试，验证学生角色拒绝、未知候选拒绝、未处理私有材料拒绝、重复确认产生稳定的草稿与测试点 ID。合计 C 模块 29 项，已纳入后端 53 项统一 verify。

HTTP 真实联调已通过：教师解析、私有预览、确认入库、幂等重放、发布给班级、学生作答、AI 代码快照与明确模拟标识、他人记录拒绝、DOCX 上传。14 项负面 API 检查覆盖了相关越权和恶意 DOCX 输入。浏览器也实际完成教师填入样例、识别两题、选择一题、修正名称、勾选核对并保存草稿。

## 剩余范围

- 当前学校 AI 协议及凭据未取得，真实学校调用保持未完成；不能用 mock 结果作正确性判断。
- DOCX 定位是提取后的文本行号，不保证映射回 Word 页码；扫描件和 PDF 不在首版范围。
- 当前文本读取只支持明确编码，不猜测 GBK，错误会要求转为 UTF-8。
- 正式 MariaDB 与 Rocky 8 上仍需重复部署验证；当前 HTTP 使用本机私有 MySQL 兼容演示实例。
