# B → A / D 第一轮接入需求

日期：2026-11-06；状态：第二轮已由 A 合并 V2，并接通真实服务；契约依据 v0.1。

## 依赖

评测核心和协议工厂仅使用 Java 21。HTTP 层使用 JDK HttpClient 与 Boot 4 自带 Jackson 3（tools.jackson.databind）；不新增框架依赖。测试需要 starter-test / JUnit Jupiter。

## 数据库迁移请求（由 A 维护 SQL）

- submission：id、student_id、assignment_id、problem_id、problem_version_id、source_code、source_hash、language、status、verdict（可空）、score（可空 DECIMAL）、max_score、compile_message、submitted_at、finished_at；UNIQUE(student_id, idempotency_key)，另存 request_hash 防止同 key 不同内容；按 student_id/assignment_id/submitted_at 与 assignment_id/problem_id 建索引。
- judge_task：id、submission_id（唯一）、state、attempt、max_attempts、available_at、lease_owner、lease_token、lease_until、last_error_code、created_at、updated_at；索引(state, available_at, lease_until)。lease_token 每次领取更新，完成结果必须条件匹配 token，防止过期工作线程覆盖新结果。
- case_result：id、submission_id、case_index、test_case_id、verdict、score、cpu_time_ns、wall_time_ns、memory_bytes；UNIQUE(submission_id, case_index)。默认不落库原始 stdout/stderr，隐藏用例数据不进入学生 DTO。
- 分数统一 BigDecimal；数据库精度请 A 与题目表统一。最终 score 在系统故障时为空；基础设施错误不能当作零分。
- 提交与 judge_task 同一事务落库；领取任务短事务，隔离执行在事务外；所有用例结果与最终成绩条件写入同一事务。

## 内部接口请求

需要一个服务器专用题目读取入口：根据当前用户、作业、题目和版本校验访问/时间条件，返回不可变的 JudgeProblemSnapshot，包含 versionId、maxScore、资源限额、用例 id/index/input/expectedOutput/score/comparisonMode。公开题目 DTO 不包含这些私有字段。

需要授权接口：检查当前身份、读取本人提交以及教师对班级/作业的管理范围。B 不从客户端读取 studentId。

A 已提供 ProblemAccessPort / ProblemSnapshot 开发基线；B 已实现 JudgeProblemMapper 转换。SQL 已通过 V2 合并，真实服务已接通。

## 第一轮边界（历史）

本轮交付确定性比较、go-judge 协议工厂/HTTP transport、单次完整评测引擎与测试。数据库队列、HTTP submission controller、真实身份与快照连接在 A 契约固定后继续；不得对外宣称已支持完整提交流程。

## 比较与汇总建议

- EXACT：仅将 CRLF 换成 LF，不 trim，不忽略末尾换行，不自动容忍数字精度。
- TOKENS：以 ASCII 空白（空格及 U+0009 到 U+000D）切分，逐 token 精确比较，1 与 1.0 不相同。
- 全部计分测试点成功为 ACCEPTED；部分计分点成功为 PARTIAL_ACCEPTED；全部失败时按测试点顺序返回首个失败 verdict。
- 任一基础设施错误为 SYSTEM_ERROR，最终得分为空；已成功的测试点仅用于诊断，不能先计入成绩。
- 测试点无原始输出字段；timeMs 由纳秒向上取整，memoryKb 由字节向上取整。

## 第二轮状态

SubmissionRepository/Service/Controller/JudgeWorker已实现，真实API、数据库与go-judge联调通过；详细结果见PHASE2.md。
