# C 模块公共接入请求

状态：第一轮实现中。C 仅修改 importer/ai、对应测试与 prompts，不修改公共 pom 或数据库迁移。

## 依赖

- 第一轮核心使用 Java 标准库，无新增运行时依赖。
- 测试使用 A 的 spring-boot-starter-test 提供的 JUnit Jupiter API；测试不依赖 Spring 上下文或数据库。
- DOCX 文件读取将在后续阶段接入，首轮不会把未实现的上传、解压限制或 DOCX 读取标成完成。暂不请求 POI/Jackson 扩展依赖。

## 已收到 A 的接口约定

- com.cassist.identity.CurrentActor.require() 返回 Actor(id, role)。
- ProblemAccessPort.requireForStudent(actor, assignmentId, problemId, versionId) 返回服务器内部 ProblemSnapshot 并检查对象授权。
- DraftImportPort.saveDrafts(actor, importJobId, drafts, title) 是批量保存草稿边界。
- 以上真实数据库实现暂未交付，C 不能将缺少实现处理为默认放行。

## 持久化需求（下一阶段由 A 合并迁移）

| 表/记录 | 字段与约束 |
| --- | --- |
| import_job | 字符串ID、owner_id、source_type、原文受限引用、status、parser_version、created_at、expires_at；owner_id+created_at索引 |
| import_candidate | job_id、candidate_id、顺序、候选字段JSON、原文位置、issues、needs_review；job_id+candidate_id唯一 |
| import_commit | job_id、提交幂等键、操作者、创建的题目/题集ID、确认内容摘要；按job_id+幂等键唯一，事务内保存 |
| ai_analysis | 字符串ID、owner_id、assignment_id、problem_id、problem_version_id、可选submission_id、source_code快照、code_hash、status、prompt_version、provider_mode、model、结构化指导、公开错误码、created_at/completed_at；owner_id+created_at索引 |

参考答案按题目版本读取，不额外复制到学生可读的分析记录。学校密钥只存在配置，不写数据库或交接文件。

## 待基线发布后完成

- A/C 对齐内部 ProblemSnapshot 与 DraftProblem 的具体字段。
- C 提交 preview/AI DTO 实例供 D 使用；HTTP controller、任务持久化、角色校验待实际端口实现后接入。
- import_job 是教师私有预览，不能作为学生题目响应直接序列化。
