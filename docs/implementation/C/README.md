# C 模块第一轮交接

更新日期：2026-11-06。公共契约：v0.1。状态：核心实现与本地测试完成，可供下一阶段接入。

## 已实现

### Easy Parse 文本核心

- EasyParseTextParser：纯 Java，无新增运行时依赖。入口 parse(String)，返回教师私有 ImportPreview。
- 支持明确题号、常见编号题目、字段别名、中英文冒号、样例编号配对、分值与常见时间/内存单位。
- 识别参考代码、答案解析和教师备注；不明代码进入 unresolvedContent。跟踪 C 字符串、注释和花括号，避免代码中的题号/字段名触发拆题。
- 输出原文行号、问题码、严重程度、默认值说明和 needsReview。
- 显式测试输入输出可解析，但默认隐藏、不自动分配分数，需要教师确认；公开样例不自动变成计分测试点。
- 文本体积和候选数量有限制；该限制不等于上传文件、ZIP/DOCX 解压或储存安全已经实现。

### AI 核心与 A 端口适配

- CodeSnapshot 按当前代码 UTF-8 字节生成 SHA-256，不修改换行后再算摘要。
- AiProvider 为供应商无关接口；AiProviderSettings 提供运行模式、地址、模型、超时和并发配置。实际 HTTP 适配器尚未实现，因此超时设置尚未被真实网络请求使用。
- AiGuidanceCore 提供有限并发、固定提示词、完整响应校验和不含参考答案的结果对象。
- AuthorizedAiGuidanceService 调用 A 的 ProblemAccessPort.requireForStudent，校验题目版本和代码长度，使用授权快照组装题干/公开样例/参考答案。
- MockAiProvider 必须显式构造，响应清楚标明学校 AI 未接入。学校模式错误不会自动回退为 mock。
- GuidanceGuard 校验行号、结构、长度并拦截代码块、完整程序特征和明显参考答案复制。它是启发式限制，存在误拦截及漏检可能，不提供绝对防泄露保证。
- 内部代码快照和参考答案上下文的 toString() 隐去原文，避免普通调试输出泄露这些内容。

## 文件

| 路径 | 用途 |
| --- | --- |
| backend/src/main/java/com/cassist/importer/ | 解析核心与候选 DTO |
| backend/src/main/java/com/cassist/ai/ | 适配接口、授权桥接、代码快照、提示词加载、输出校验、显式模拟供应商 |
| backend/src/main/resources/prompts/c-debug-guidance-v1.txt | 版本化服务端限制提示词 |
| backend/src/test/java/com/cassist/importer/ | 11 项解析测试 |
| backend/src/test/java/com/cassist/ai/ | 9 项 AI 边界测试 |
| backend/src/test/resources/importer/loose-problems.txt | 合成的非敏感导入样例 |
| [DTO_EXAMPLES.md](DTO_EXAMPLES.md) | D 可用的预览与 AI 字段、HTTP 外层约定、模拟示例 |
| [INTEGRATION_REQUESTS.md](INTEGRATION_REQUESTS.md) | A 的依赖/表结构请求及端口边界 |

## 实际验证

本地工具链：Java 27，Maven Wrapper 3.9.11，编译目标 --release 21；没有对 Rocky 8 目标机进行验证。

在 backend 目录执行：

```sh
./mvnw -B -ntp '-Dtest=EasyParseTextParserTest,AiGuidanceCoreTest' test
```

结果：BUILD SUCCESS；20 项测试通过，0 失败、0 错误、0 跳过。最后一次运行时间为 2026-11-06 15:27（Asia/Shanghai）。测试为纯核心及模拟端口测试，不需要数据库或外部 AI。

解析场景：松散字段、不同样例编号、输出先于输入、代码注释与字符串含题号、代码隔离、私有解析、明确空输入与缺输入的区别、CRLF/BOM、显式测试用例、体积/数量限制、未闭合代码块、非法限制值、合成材料。

AI 场景：代码快照与 UTF-8 大小限制、显式模拟模式、固定指令与代码数据分离、明显泄露拦截、结构/行号/长度校验、合理自然语言指导、异常传播与并发许可回收、未授权请求不调用供应商、授权版本与当前代码适配。

这些测试没有证明学校模型能抵御所有提示词注入，也没有评估真实模型对不同正确算法的判断质量。

## 支持与限制

| 输入或行为 | 第一轮状态 |
| --- | --- |
| 粘贴文本核心 | 已实现 |
| TXT 内容字符串进入解析器 | 已验证；文件上传/编码识别未实现 |
| DOCX 段落与表格读取 | 未实现 |
| PDF、OCR | 不在第一版范围 |
| 题目边界 | 支持明确标题；普通编号为启发式并要求确认 |
| 完全无题号材料 | 形成一个待确认候选，不能保证自动拆成多题 |
| 未标注的题干代码示例 | 保守隔离后由教师归类，可能需要额外确认 |
| 换行与区块分隔 | 统一换行并清理区块边缘空行；EXACT 测试末尾换行需人工确认 |
| 参考答案保密 | 通过分类与预览降低风险；仍需服务端权限和发布前校验 |
| AI 调用 | 只有 mock 与供应商接口；学校 HTTP 协议、真实调用和配额未实现 |
| HTTP API / 持久化 | 尚未实现，不能把 Java 核心当作已可访问的 endpoint |

## 下一阶段

1. A 合并导入任务、导入确认和 AI 分析表迁移，实现授权题目读取与草稿保存端口。
2. C 实现文本/TXT/DOCX 上传读取、任务 Controller 与持久化、确认修正及幂等入库；保持原文和候选的教师私有访问。
3. C 接入学校 HTTP 文档，落实真实连接/响应超时、认证、错误分类、submissionId 归属校验、AI 分析记录与安全的公开错误映射。
4. D 用 DTO_EXAMPLES.md 联调真实服务，关闭生产 mock。完成“导入—修正—草稿—发布—AI指导”的流程验证。
5. 使用真实教师文档扩大格式覆盖，使用真实学校模型评估指导质量与答案泄露风险。
