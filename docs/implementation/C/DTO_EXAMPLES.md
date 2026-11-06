# C 模块 DTO 与前端样例

契约基线：v0.1。第一轮核心已实现；主 Agent 第二轮已写入 HTTP Controller、JDBC 记录、TXT/DOCX 读取，正在与 A/D 联调。具体进展见 [PHASE2.md](PHASE2.md)。真实学校 AI 尚未接入，demo profile 仅显式模拟。

## Easy Parse 请求与结果

粘贴请求：POST /api/imports/parse，JSON：

```json
{"sourceType":"TEXT","text":"第1题：两数之和\n题干：输出两个整数的和。"}
```

202：`{"id":"import-demo-1","status":"READY"}`。同步解析可以直接 READY；客户端也兼容 QUEUED、PARSING、FAILED、COMMITTED。

GET /api/imports/import-demo-1 的 READY 示例（完全合成的演示内容）：

```json
{
  "id": "import-demo-1",
  "status": "READY",
  "parserVersion": "easy-parse-text/0.1",
  "candidates": [
    {
      "candidateId": "candidate-1",
      "title": "两数之和",
      "statement": "输出两个整数的和。",
      "inputDescription": "两个整数。",
      "outputDescription": "输出和。",
      "samples": [{"key":"1","input":"1 2","output":"3"}],
      "referenceAnswer": "",
      "privateNotes": "",
      "unresolvedContent": "",
      "testCases": [],
      "maxScore": null,
      "timeLimitMs": 1000,
      "memoryLimitMb": 128,
      "sourceLocation": {"startLine":1,"endLine":8},
      "issues": [
        {"code":"MISSING_REFERENCE","severity":"WARNING","field":"referenceAnswer","sourceLocation":{"startLine":1,"endLine":1},"message":"缺少明确标注的参考答案。"},
        {"code":"DEFAULT_TIME_LIMIT","severity":"INFO","field":"timeLimitMs","sourceLocation":{"startLine":1,"endLine":1},"message":"暂用系统默认时间限制，请在发布前确认。"}
      ],
      "needsReview": true
    }
  ],
  "issues": []
}
```

真实核心类型为 `ImportPreview` 及嵌套 `Candidate / Sample / TestCase / Issue / SourceLocation`。HTTP adapter 后续在该预览外加 id/status。当前 sourceLocation 是文本行号，从 1 开始且两端包含；尚无 DOCX 位置映射。

### 前端字段处理

- samples[].input/output：null 表示对应段缺失；空字符串表示文档明确提供了空样例，两者不同。
- samples[].key / testCases[].key：同一候选题内的原文编号，用于配对，不能代替数据库 ID。
- testCases：显式测试数据包含 key、input、expectedOutput、visibility、score、comparisonMode。当前解析默认 HIDDEN / null / EXACT，并强制需要教师确认，不直接生成计分用例。
- privateNotes：解析/题解/教师备注，始终属于教师私有预览。
- unresolvedContent：无法确定用途的代码或元数据，不能直接并入公开题干。界面让教师明确选择归属并确认。
- maxScore 为 null 时提示补充，不能用 0 假装已识别。
- 默认时间和内存通过 INFO issue 标明；WARNING / ERROR 使 needsReview=true。
- 纯文本拆分是启发式，编号边界会有 HEURISTIC_BOUNDARY 提醒。不能将 needsReview=false 当作“必然正确”的保证。
- 解析会统一 CRLF/CR 为 LF，并去掉区块分隔空行，保留代码内部缩进/字符。EXACT 用例的末尾换行需要在用例编辑页确认；此阶段不能承诺文档格式与判题字节完全等价。
- commit 请求是 `{title,candidates}` 加 Idempotency-Key，候选由老师选择并修正。仅保存草稿；服务端仍需重新校验权限、归属和字段。

## AI 请求与响应

POST /api/ai/analyses：

```json
{
  "assignmentId":"assignment-demo-1",
  "problemId":"problem-demo-1",
  "problemVersionId":"version-demo-1",
  "sourceCode":"int main(void) { return 0; }"
}
```

前端仅发送这些字段和可选的 submissionId。不要发送参考答案、系统提示词、用户 ID 或教师权限。当前核心已接入 `ProblemAccessPort.requireForStudent`；submissionId 对象校验与记录尚待 HTTP/持久化层完成，首轮前端可以省略。

202 及查询采用 v0.1：

```json
{"id":"analysis-demo-1","status":"QUEUED","codeHash":"服务端对当前代码UTF-8字节计算的SHA-256十六进制字符串"}
```

```json
{
  "id":"analysis-demo-1",
  "status":"COMPLETED",
  "codeHash":"服务端返回的同一代码摘要",
  "summary":"模拟响应：学校 AI 尚未接入，本结果只用于验证界面与接口，不代表代码分析。",
  "findings":[],
  "errorCode":null
}
```

findings 的独立界面测试样例（非模型结论）：

```json
{"startLine":2,"endLine":2,"category":"边界","reason":"索引可能等于数组长度。","hint":"检查循环终止条件。"}
```

- startLine/endLine 必须同时为 null，或为当前代码内合法的闭区间。
- codeHash 使用原始代码 UTF-8 字节计算，不统一换行。前端最好保留本次请求的代码文本，编辑发生变化即可标记为旧分析，无须依赖异步重算 hash。
- 第一轮核心返回 `AiGuidanceCore.Result(codeHash,promptVersion,providerMode,guidance)`；未来 HTTP 层需将 guidance 展平到 summary/findings，不能直接序列化带题干或参考答案的内部请求。
- MockAiProvider 只能显式构造；学校服务失败不会自动回退到 mock。页面在 mock 数据模式持续显示“演示数据”，不能把模拟结果标为真实学校 AI 分析。
- 核心错误码 UNAVAILABLE、TIMEOUT、INVALID_RESPONSE、UNSAFE_RESPONSE，需要未来 HTTP 层映射为安全的公开 errorCode；严禁将上游原始响应或完整提示词返回学生。

## 当前接入状态

/api/imports/* 和 /api/ai/* 的 Controller 已写入，依赖 A 的数据库与授权端口；真实联调结果见 PHASE2.md。学校 HTTP 协议和真实模型评估仍待学校提供信息，默认模式返回 AI_NOT_CONFIGURED；demo/local 显式模拟可验证前后端流程。
