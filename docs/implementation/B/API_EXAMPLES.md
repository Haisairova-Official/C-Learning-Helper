# B → D：提交与评测结果示例

日期：2026-11-06；契约：v0.1。状态：第二轮真实 HTTP Controller、数据库队列和成绩持久化已实现，本地真实 go-judge 已联调。

## 接口

- POST /api/submissions：assignmentId、problemId、problemVersionId、sourceCode、language（C17），请求身份来自 Session，带 CSRF 与 Idempotency-Key。
- GET /api/submissions/{id}：查询本人或授权教学范围内的提交。
- GET /api/submissions：page（默认1）、pageSize（默认20，最大100）、可选assignmentId/problemId/studentId。学生默认本人；教师按指定作业查看授权范围。列表sourceCode=null。

提交返回 202：

```json
{"id":"s1","status":"QUEUED","submittedAt":"2026-11-06T06:00:00Z"}
```

## 部分正确

```json
{
  "id":"s1",
  "status":"FINISHED",
  "verdict":"PARTIAL_ACCEPTED",
  "score":6.5,
  "maxScore":10,
  "compileMessage":"",
  "caseResults":[
    {"caseIndex":1,"status":"WRONG_ANSWER","score":0,"timeMs":2,"memoryKb":1024},
    {"caseIndex":2,"status":"ACCEPTED","score":6.5,"timeMs":2,"memoryKb":1024}
  ]
}
```

## 编译失败

```json
{
  "id":"s2","status":"FINISHED","verdict":"COMPILATION_ERROR",
  "score":0,"maxScore":10,
  "compileMessage":"main.c:2:1: error: expected expression",
  "caseResults":[]
}
```

## 评测基础设施故障

```json
{
  "id":"s3","status":"SYSTEM_ERROR","verdict":"SYSTEM_ERROR",
  "score":null,"maxScore":10,"compileMessage":"","caseResults":[]
}
```

排队、编译和运行中也不显示最终零分；score 保持 null。SYSTEM_ERROR 不算学生答错。内部 errorCode 暂仅供服务日志及任务重试使用，前端根据 status 给出“评测服务暂不可用”的提示。

## 显示约定

- 任务状态：QUEUED → COMPILING → RUNNING → FINISHED；系统故障进入 SYSTEM_ERROR。
- 最终 verdict：ACCEPTED、PARTIAL_ACCEPTED、WRONG_ANSWER、COMPILATION_ERROR、RUNTIME_ERROR、TIME_LIMIT_EXCEEDED、MEMORY_LIMIT_EXCEEDED、OUTPUT_LIMIT_EXCEEDED、SYSTEM_ERROR。
- 隐藏用例的 input、expectedOutput、stdout、stderr 不出现在 CaseResult；前端无需这些字段。
- timeMs 是 CPU 纳秒向上取整为毫秒；memoryKb 是字节向上取整为 KiB，界面标为 KiB。
- compileMessage 按纯文本渲染；保留学生代码文件 main.c 的必要诊断，服务端过滤路径。
- 本地真实模式已完成评测验收；目标 Rocky 8 仍未验收。仍使用前端模拟开关时必须明确标注模拟。

## 第二轮详情补充

GET详情还返回studentId、assignmentId、problemId、problemVersionId、language、sourceCode、submittedAt、finishedAt。仅通过对象授权后返回详情，列表不带代码。未完成和系统异常的score均为空。
