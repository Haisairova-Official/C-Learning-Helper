# 公共接口与数据契约

版本：v0.2

2026-11-06 主 Agent 追加向后兼容的 DeepSeek 扩展：GET `/api/ai/analyses/provider` 需登录，返回 `{providerMode, configured}`；mode 为 MOCK / SCHOOL / DEEPSEEK / DISABLED。分析结果新增可选 `providerMode`、`promptVersion`。POST 202 可返回 RUNNING，客户端必须轮询；并发受限返回 429 / AI_BUSY。安全失败码补充 AUTHENTICATION / QUOTA_EXCEEDED / RATE_LIMITED / INTERRUPTED。无需数据库迁移，前后端已同时采用。详见 [AI 接入记录](../implementation/C/DEEPSEEK.md)。

状态：第二轮可运行开发基线；实际实现和验证状态见 backend/README.md。负责人：Agent A。

B/C 的第一轮接入需求已审查。B、C、D 按本基线建立适配接口和模拟数据；尚未实现的内部端口不能默认放行。接口变更先写跨 Agent 请求，由 A 更新版本和采用记录。

## 通用约定

- HTTP 前缀：/api；JSON 使用 camelCase。
- ID 在 JSON 中统一使用字符串，避免 JavaScript 大整数精度问题。
- 时间在数据库中按 UTC 保存，接口使用带时区的 ISO 8601；界面按 Asia/Shanghai 展示。截止判定使用服务端时间。
- 成功返回资源对象或分页对象；错误统一为 code、message、traceId，可选 fieldErrors。错误中不得包含敏感上下文。
- 分页默认结构：items、page、pageSize、total；page 从 1 开始，后端限制最大 pageSize。
- 使用 Session Cookie；前端与 API 同域，保留 CSRF 校验。A 提供获取 CSRF token 的接口和登录后刷新规则，D 接入。
- 前端无权传入或覆盖 userId、角色、评测分数、参考答案上下文等由后端确定的值。
- POST 提交代码、确认导入使用 Idempotency-Key 请求头，16–128 个 ASCII 字母、数字或连字符，推荐 UUID。同一用户、同一动作和同一键重放相同内容返回原记录，内容变化返回 HTTP 409 / IDEMPOTENCY_CONFLICT。

## 核心实体与负责人

| 实体 | 核心信息 | 负责人 |
| --- | --- | --- |
| User / Class / ClassMember | 角色、教师归属、班级成员关系 | A |
| Problem / ProblemVersion | 题干、输入输出说明、样例、限制、分值、版本 | A |
| ReferenceAnswer / TestCase | 教师参考代码、测试输入输出、是否公开、计分配置 | A，B/C 提出读取需求 |
| ProblemSet / Assignment | 有序题目版本集合、发布班级、开放与截止时间 | A |
| Submission / JudgeTask / CaseResult | 提交快照、任务状态、评测结果、租约与恢复信息 | B 设计，A 合并迁移 |
| ImportJob / ImportCandidate | 解析状态、候选题目、原文位置、问题列表、确认记录 | C 设计，A 合并迁移 |
| AiAnalysis | 题目版本、当前代码快照、请求状态、指导内容、模型及提示词版本 | C 设计，A 合并迁移 |

发布的作业绑定题目版本和测试配置快照；教师后续编辑产生新版本，不能让历史提交失去对应的题意和评分依据。

## 题目公开与私有结构

StudentProblemView：id、versionId、title、statement、inputDescription、outputDescription、samples、timeLimitMs、memoryLimitMb、codeLimitBytes、maxScore。

TeacherProblemView 在公开结构之外，可以包含 referenceAnswer、testCases、教师备注、草稿状态等私有数据。禁止直接复用数据库实体生成学生响应。

- Sample：input、output、可选 explanation。
- TestCase：id、input、expectedOutput、visibility、score、comparisonMode。
- visibility 为 PUBLIC 或 HIDDEN；公开样例与计分测试点的关系需明确配置，不自动重复计分。
- 所有计分测试点的 score 之和等于题目 maxScore。
- 比较方式支持 EXACT 和 TOKENS：EXACT 将 CRLF 转为 LF，并允许单个末尾换行有无，不 trim；额外空格/空行仍参与比较。精确不相等但 ASCII 空白分词一致时返回 PRESENTATION_ERROR（输出格式不符），该点零分；其他内容差异为 WRONG_ANSWER。TOKENS 使用 ASCII 空白（空格及 U+0009 至 U+000D）分隔并逐 token 比较，不隐式加入浮点容差，1 与 1.0 不相等。2026-11-06 最新用户要求覆盖初版末尾换行严格规则。
- 时间、内存、代码和输出限制有系统上限，教师配置不得突破服务器允许的上限。

## API 基线与归属

| 方法与路径 | 用途 | 负责人 |
| --- | --- | --- |
| GET /api/auth/csrf | 获取 CSRF token | A |
| POST /api/auth/login、POST /api/auth/logout、GET /api/auth/me | 登录、退出、当前身份 | A |
| /api/classes、/api/classes/{id}/members | 班级及成员管理，具体操作补充到基线 | A |
| /api/teacher/problems、/api/teacher/problems/{id} | 题目草稿与教师详情 | A |
| /api/teacher/problem-sets、/api/teacher/assignments | 组题与作业发布管理 | A |
| GET /api/student/assignments | 当前学生可见的作业 | A |
| GET /api/student/assignments/{id}/problems/{problemId} | 作业内题目的公开视图 | A |
| GET /api/teacher/assignments/{id}/results | 作业完成情况与成绩 | A，B 提供评测结果 |
| POST /api/submissions、GET /api/submissions/{id} | 提交与查询评测结果 | B |
| GET /api/submissions | 根据权限查询自己的或教学范围内的提交历史 | B |
| POST /api/imports/parse、GET /api/imports/{id} | 创建解析任务与取得预览 | C |
| POST /api/imports/{id}/commit | 校验修正内容、保存选中的题目和题目集草稿 | C，调用 A 的题库服务 |
| POST /api/ai/analyses、GET /api/ai/analyses/{id} | 请求并查询当前代码的 AI 指导 | C |

网页路径由 D 维护；HTTP 方法、请求体和详细响应样例由所属 Agent 提出，A 汇总到本文件或其链接的契约文件中。正式契约以文档为准，不能只在代码里改字段。

## 提交与评测

提交请求：assignmentId、problemId、problemVersionId、sourceCode、language。初版 language 只接受 C17，服务端校验该版本确实属于当前作业。

接收成功返回 HTTP 202，包含 id、status、submittedAt；不等待编译或测试结束。

任务状态：QUEUED、COMPILING、RUNNING、FINISHED、SYSTEM_ERROR。

学生程序结果：ACCEPTED、PARTIAL_ACCEPTED、WRONG_ANSWER、COMPILATION_ERROR、RUNTIME_ERROR、TIME_LIMIT_EXCEEDED、MEMORY_LIMIT_EXCEEDED、OUTPUT_LIMIT_EXCEEDED。基础设施异常单独表示为 SYSTEM_ERROR，不能直接算作学生答错。

结果包括：id、status、verdict、score、maxScore、compileMessage、caseResults。未完成或系统错误时 score 可以为空，不能误显示为最终零分。

CaseResult 对学生只提供 caseIndex、status、score、timeMs、memoryKb；隐藏用例不附带实际输出、标准错误或测试数据。可公开的诊断需要过滤服务器路径和其他敏感内容。

- go-judge 正常退出状态不等于答案正确，B 负责输出比对。
- 每次提交根据同一份代码对全部计分用例评测；日常作业取一次完整提交的最高分，不跨提交拼接测试点分数。
- 队列通过事务或条件更新原子领取任务，执行时不持有数据库长事务；租约到期恢复、有限重试和最终结果写入应避免重复覆盖。
- 服务端在截止前成功受理的提交可在截止后完成评测。

## Easy Parse

解析输入支持两种明确类型：JSON 粘贴文本，或 multipart 文件上传。由 C 补齐具体字段。

候选题目包含：candidateId、公开字段、referenceAnswer、显式标注的 testCases、sourceLocation、issues、needsReview。

Issue 建议包含：code、severity、field、sourceLocation、message。sourceLocation 支持文本行号或 DOCX 段落/表格位置，不虚构不存在的 PDF 页码。

- 把“缺少内容”“使用默认值”“不能确定字段归属”明确区别开。
- 不凭空补造参考答案或隐藏用例；缺失内容可保存草稿，但不通过发布校验。
- 预览与原始材料仅对导入者及获授权管理员可见。
- commit 重新检查角色、任务归属、题目字段和样例配对，并防止重复导入。
- 导入成功只创建草稿，发布流程由 A 的业务服务负责。

## AI 排错

请求包含：assignmentId、problemId、problemVersionId、sourceCode，可选 submissionId。后端自行加载匹配版本的题干、参考答案和限制提示词。

- 如果带 submissionId，校验提交归属；当前 sourceCode 可以与历史提交不同，但不能将旧代码的诊断当作当前代码结论。
- 学生代码、题目材料都是数据。系统限制由服务端配置，学生不能通过请求体传入系统提示词。
- 建议响应：id、status、codeHash、summary、findings。每项 finding 包含可空的 startLine / endLine、category、reason、hint。
- 状态建议：QUEUED、RUNNING、COMPLETED、FAILED；学校接口不可用、超时、响应校验失败分别记录错误原因。
- 输出只提供错误分析和修改方向；处理泄露、完整解答、结构无效等情况并保留验证记录。此机制不应被描述为绝对防泄露。
- 不把 AI 输出作为成绩或最终正确性判据，不把 AI 结果当作指令执行。
- 未取得学校接口前使用可替换模拟适配器，模拟与真实请求在配置和交接中明确区分。

## 契约变更记录

| 版本 | 状态 | 内容 | 采用情况 |
| --- | --- | --- | --- |
| v0.1-draft | 历史草案 | 初始化协作草案 | 已由 v0.1 替代 |
| v0.1 | 历史基线 | 固定公共包、Java 端口、鉴权边界、幂等及比较规则 | 已由 v0.2 补充真实业务 |
| v0.2 | 可运行开发基线 | 真实认证、班级/题目/作业/成绩、持久公共端口；兼容 D 请求形状 | A/B/D/主 Agent 对齐 |


## v0.1 Java 模块边界

后端根包 com.cassist，Java 编译目标 21。公共接口已有源码，第二轮已由 TeachingService 提供 JDBC 数据库实现：

- identity.CurrentActor.require()：从受信任 SecurityContext 取得 Actor(id, role)；匿名或未知 principal 返回 401。
- identity.CurrentActor.requireProblemManager()：仅允许 TEACHER / ADMIN；这只是角色边界，对象归属还必须检查。
- problem.ProblemAccessPort.requireForStudent(actor, assignmentId, problemId, versionId)：检查学生对作业及题目版本的访问权，返回服务器内部 ProblemSnapshot。
- problem.ProblemAccessPort.requireForSubmission(actor, assignmentId, problemId, versionId, receivedAt)：额外检查作业开放/截止时间，receivedAt 由服务器创建，禁止客户端传入。
- problem.ProblemAccessPort.loadForEvaluation(versionId)：仅供后台工作线程使用已授权且落库的提交版本，不暴露为 HTTP 接口；截止后继续执行不重新按当前时间拒绝。
- problem.ProblemSnapshot：problem 为 StudentProblemView；referenceAnswer 和 testCases 是服务器私有信息。调用者自行映射公开 DTO，不能整对象返回前端。
- problem.DraftImportPort.saveDrafts(actor, importJobId, drafts, title)：检查管理角色及导入任务归属，以事务和幂等方式保存草稿；返回 problemSetId 与 problemIds。
- common.BusinessException(HttpStatus, code, safeMessage)：业务拒绝；safeMessage 必须不含代码、SQL、参考答案和密钥。
- common.PageResult(items, page, pageSize, total)：page 从 1 开始，pageSize 最大 100。

ProblemSnapshot 的用例集合顺序即 caseIndex 顺序，HTTP caseIndex 从 1 开始。评分使用 BigDecimal，SQL DECIMAL(10,2)。所有计分点成功为 ACCEPTED，有部分计分点成功为 PARTIAL_ACCEPTED；全部失败时按用例顺序取首个失败 verdict。任一基础设施错误最终为 SYSTEM_ERROR 且 score 为 null。timeMs 从纳秒向上取整，memoryKb 从字节向上取整。

## v0.1 前端请求形状

以下为基线约定，不能将尚未实现的端点视为已可调用：

- POST /api/auth/login：JSON {username, password}；成功 200 返回 {id, role}，建立 Session 并轮换会话 ID；失败 401 / INVALID_CREDENTIALS。
- POST /api/auth/logout：成功 204，服务端撤销 Session。
- GET /api/auth/me：返回 {id, role}。显示名等展示字段后续通过显式契约升级添加。
- GET /api/auth/csrf：返回 {headerName, parameterName, token}。前端将 token 放入 headerName 指定请求头；登录/退出后重新获取。
- POST /api/submissions：JSON {assignmentId, problemId, problemVersionId, sourceCode, language:"C17"} + Idempotency-Key；202 返回 {id, status:"QUEUED", submittedAt}。
- POST /api/imports/parse：粘贴使用 JSON {sourceType:"TEXT", text}；上传使用 multipart，file 为文件、sourceType 为 TXT 或 DOCX；202 返回 {id, status:"QUEUED"}。
- GET /api/imports/{id}：返回 {id, status, parserVersion, candidates, issues}；status 为 QUEUED/PARSING/READY/FAILED/COMMITTED；候选具体字段由 C 在其集成文档维护，不能包含未识别内容的假定正确值。
- POST /api/imports/{id}/commit：JSON {title, candidates} + Idempotency-Key；candidates 是用户选择并修正的候选题目，201 返回 {problemSetId, problemIds}；生成草稿。
- POST /api/ai/analyses：JSON {assignmentId, problemId, problemVersionId, sourceCode, submissionId?}；202 返回 {id, status:"QUEUED", codeHash}。
- GET /api/ai/analyses/{id}：返回 {id, status, codeHash, summary, findings, errorCode}；未完成时 summary/errorCode 可以为 null、findings 为空数组；失败使用安全的公开错误码。

公共错误示例：{code:"FORBIDDEN", message:"无权访问该资源", traceId:"服务端生成的UUID", fieldErrors:{}}。格式错误 400、未登录 401、对象权限失败 403、资源不存在 404、状态或幂等冲突 409、上传过大 413、限流 429、上游不可用 503。

B/C 的数据库请求已由 A 合并到 V2（提交与队列）、V3（导入/AI/导入回执）；V4 允许未完成草稿保留不完整测试点。后续迁移仍由 A 统一维护。


## v0.2 可运行业务补充

- GET /api/classes 返回 Page<ClassView>：{id,name,memberCount}；学生只见自己班级，教师只见自己管理班级，管理员可见全部。
- POST /api/classes，JSON {name}，201 返回 ClassView；拥有者由当前身份确定。
- GET /api/classes/{id}/members 返回 Page<MemberView>：{id,name,studentNumber}；仅管理者可访问。
- POST /api/classes/{id}/members，JSON {studentId}，204；只能添加已存在且启用的学生账户。
- GET /api/teacher/problems、GET /api/teacher/problems/{id} 返回 TeacherProblemView；POST 新建、PUT 编辑；字段与 D 的 TeacherProblem 一致，state 为 DRAFT/PUBLISHED。
- PUT 编辑创建新 problem_version；客户端传来的 versionId 和测试点 id 不用于覆盖已有版本。
- GET /api/student/assignments 与 GET /api/teacher/assignments 返回 Page<AssignmentView>；两端各有 GET .../assignments/{id} 详情。
- AssignmentView：{id,title,className,opensAt,closesAt,problems:[{id,versionId,title,maxScore}],completedCount,score,maxScore,state}；state 在已发布作业中为 PUBLISHED。
- POST /api/teacher/assignments，JSON {title,classId,problemIds,opensAt,closesAt}，201 返回 AssignmentView；服务端内部建立题目集并固定所有题目版本。
- GET /api/teacher/assignments/{id}/results 返回 Page<ResultView>：{studentId,name,completedCount,totalCount,score,maxScore}。
- completedCount 计全部测试点通过的题目数；score 为每道题的一次完整提交最高分之和，SYSTEM_ERROR 不参与计分。
- 导入先保存草稿；发布时检查题干/参考答案/用例/分值总和，缺项返回400。时间限制100–10000 ms，内存16–512 MB，代码1024–262144字节，发布总分大于0且最多10000。
- AccessPolicy.requireSubmissionRead(actor,studentId,assignmentId)：学生限本人并检查作业访问，教师限本人管理作业，管理员可访问。
- DraftImportPort 保存时锁定 import_job 并核对 owner_id，按 importJobId+内容摘要幂等；同任务相同内容重放返回原结果，不同内容返回409。
- /api/auth/login 与 csrf 匿名可进入，但所有写请求仍需 CSRF；登录后前端重新获取 token。退出后 Session 失效。

本机演示 profile 名称 demo，使用私有 MySQL；正式 MariaDB 配置仍由 application.yml 环境变量提供。该差异是环境说明，不影响上述 API。

## v0.2 追加：账号资料设置

2026-11-06 的 [资料接口](PROFILE_SETTINGS.md) 为本轮冻结补充，A 后端已实现并通过定向测试，V5 迁移和服务更新由主 Agent 统一执行。GET/PUT `/api/profile`、POST/DELETE/GET `/api/profile/avatar` 均只操作当前启用账号；写入保留 CSRF。nickname/bio 按 Unicode code point 计 32/500，风格为 CONCISE/GUIDED/DETAILED，默认 GUIDED；username/displayName/role 不可由资料接口修改。

服务器内部 `identity.ProfileService.getAiReplyStyle(actorId)` 供 AI 读取风格枚举；不能作为任意学生自定义提示词入口。完整返回字段、头像大小/尺寸/规范化和缓存要求见冻结补充，验证范围见 [A交接](../implementation/A/PROFILE_SETTINGS.md)。V5 已预留并编写，V6 由 B 在本轮负责函数题扩展，避免迁移编号冲突。

## v0.2 追加：函数题、题目进度与 AI 风格

- [函数题契约](FUNCTION_PROBLEMS.md)：公开 problemType（PROGRAM/FUNCTION）、language（C17/CPP17）、functionSignature、starterCode；教师私有 judgeTemplate。旧题默认 PROGRAM/C17。FUNCTION 发布时接口和含单一占位符的裁判模板必填；草稿可缺失待补，非空模板仍检查占位符。提交语言必须匹配已授权发布版本。
- AssignmentView.problems 项新增 progress（教师为null）：status=UNSUBMITTED/PENDING/SYSTEM_ERROR/WRONG/PARTIAL/ACCEPTED，bestScore及pending。按本人+作业+题目版本聚合；曾全部通过即ACCEPTED，系统失败不算答错。
- AI GET 分析结果新增 replyStyle（CONCISE/GUIDED/DETAILED；旧记录可空）。仅从受理时当前账号偏好读取固定枚举，禁止请求体自由提示词。函数题附公开接口与语言；私有裁判模板不发给模型。提示词版本0.3。

## v0.2 追加：管理员用户管理与学生名单

2026-11-06 实现已接入真实本机服务。以下均沿用 Session、CSRF 和公共错误结构；角色及对象权限必须在服务端验证。详细规则、迁移与实际验证见 [管理交接](../implementation/A/USER_MANAGEMENT.md)。

管理员接口均限制 ADMIN，返回数据不含密码、哈希或任何密钥，响应 `Cache-Control: private, no-store`、`Vary: Cookie`。

| 方法与路径 | 输入 | 成功结果 |
| --- | --- | --- |
| GET `/api/admin/users` | page=1、pageSize=20（最大100）、q（最多100字） | 200，Page<UserView>；匹配登录名、姓名、学号 |
| GET `/api/admin/users/{id}` | — | 200，UserView |
| POST `/api/admin/users` | username、displayName、role、studentNumber、password | 200，UserView；角色可为 STUDENT/TEACHER/ADMIN |
| PUT `/api/admin/users/{id}` | displayName、studentNumber、nickname、bio、aiReplyStyle | 200，UserView；不修改登录名、角色、密码 |
| POST `/api/admin/users/{id}/avatar` | multipart 的 file | 200，更新后的 UserView |
| GET `/api/admin/users/{id}/avatar` | — | 200，规范化 PNG；没有头像时404 |
| DELETE `/api/admin/users/{id}/avatar` | — | 200，更新后的 UserView |

UserView：`{id,username,displayName,role,studentNumber,nickname,bio,aiReplyStyle,enabled,avatarUrl,tinoUnlocked}`。学号可以为空，但非学生不能填写；非空学号唯一。上传限制沿用账号资料头像契约。创建账号的默认风格为 GUIDED，TINO 规则见下节。

名单导入基路径 `/api/classes/{classId}/students/import`；教师仅限自己班级，管理员可管理所有班级。

| 方法与后缀 | 输入 | 成功结果 |
| --- | --- | --- |
| POST `/preview` | JSON `{text}`，或 multipart 的 file | 200，Preview；不创建账号或成员 |
| POST `/validate` | `{rows}` | 200，Preview；供修改候选后重新核对 |
| POST `/commit` | `{rows,initialPassword}` + Idempotency-Key | 200，`{created,added,unchanged,studentIds}`；全批事务 |

- Row：`{rowNumber,studentNumber,name,username,className}`。学号、姓名必填；登录名和班级名可空。
- Preview：`{rows,warnings}`；每个候选保留 Row 字段并增加 `action`、字符串数组 `issues`。action 为 NEW / EXISTING / ALREADY_MEMBER / INVALID。
- 支持文本、TXT、CSV、TSV、XLSX、DOCX；文本1 MiB、文件5 MiB，每次最多200人。预览结果需要用户确认；不调用 AI。
- 已有账号按学号匹配并校验姓名、可选登录名和学生身份；新账号未指定登录名则使用学号，仍须通过登录名格式检查。
- 统一初始密码仅用于新账号，确认接口总是要求有效密码；原有账号资料、密码和权限保持不变。每个账号最多加入同一班级一次。
- 同用户同键同内容和密码返回原回执；更换内容或密码返回409。新键重复导入已有成员只累计 unchanged。全批重新校验，失败不留下部分账号或成员关系。
- V8 维护唯一学号和导入回执，初始密码仅保存不可逆比较摘要，不在预览或响应返回。

## v0.2 追加：题库层级与固定抽题

教师仅管理自己的题库；管理员可管理全部。题库成员必须与题库拥有者一致。新题库内新建题目沿用题库拥有者；不会因管理员操作而改为管理员拥有。

| 方法与路径 | 输入 | 成功结果 |
| --- | --- | --- |
| GET `/api/teacher/libraries` | page=1、pageSize=20（最大100）、q（最多200字） | 200，Page<Library>；Library={id,title,problemCount} |
| POST `/api/teacher/libraries` | `{title}` | 200，Library |
| GET `/api/teacher/libraries/{id}` | — | 200，`{id,title,problems:TeacherProblemView[]}` |
| PUT `/api/teacher/libraries/{id}` | `{title}` | 204 |
| POST `/api/teacher/libraries/{id}/problems` | TeacherProblemView 输入字段 | 200，TeacherProblemView |
| PUT `/api/teacher/libraries/{id}/problems/{problemId}` | TeacherProblemView 输入字段 | 200，TeacherProblemView；产生新版本 |
| POST `/api/teacher/libraries/{id}/members` | 1–100 项 problemId 数组 | 204；已有成员不重复添加 |

题库标题1–200字。成员列表显示最新题目版本，已发布作业仍用原快照。题目导入返回结构不变，新建题库 ID 与返回 problemSetId 一致。V9 将旧题归入相应题库或拥有者的“未分类题目”。旧 `POST /api/teacher/problems` 保留201响应，通过事务入口将新题加入拥有者默认题库；默认库 ID 为拥有者 ID 去除连字符，存在时保留其标题。此兼容修复已部署并通过并发创建与权限隔离验证，证据见管理交接。

发布请求在原 `{title,classId,problemIds,opensAt,closesAt}` 上增加可选 `libraryId,selectionMode,drawCount,aiEnabled,chatEnabled`：

- libraryId 非空时以该库当前所有题目作为候选池，覆盖请求中的 problemIds；发布最多100道候选题。
- selectionMode：FULL（默认）全量；SHARED_RANDOM 全班统一抽取；PER_STUDENT_RANDOM 按学生抽取。随机模式 drawCount 必须在1至候选池数量之间，均无放回。
- FULL/SHARED_RANDOM 在发布时固定版本与题序；PER_STUDENT_RANDOM 固定候选版本并为学生持久保存结果，晚入班首次访问也只生成一次。
- PER_STUDENT_RANDOM 候选题必须同分值；AssignmentView.maxScore 按实际抽题数计算。成绩 totalCount 同步实际题数。
- AssignmentView 新增 `selectionMode,questionCount,poolSize,aiEnabled,chatEnabled`。教师看到固定候选池；学生只见本人抽中的题目，poolSize 对学生等于实际 questionCount。
- 学生对未分配题目的详情、提交、AI、讨论都不能访问；所有业务端口必须共用题目分配校验。
- aiEnabled 默认true、chatEnabled 默认false；关闭 AI 返回403/AI_DISABLED，关闭讨论返回403/CHAT_DISABLED。用户追加发布后参数编辑，见下文接口；题目和抽题方式固定。
- V10 增加选题字段和学生绑定；旧作业迁移为FULL且保留原题序。V9/V10 不改历史题目版本和成绩。

## v0.2 追加：题目讨论、撤回和禁言

讨论基路径 `/api/assignments/{assignmentId}/problems/{problemId}/chat`。后端解析该作业的固定版本，并核对本人题目访问权，不能由客户端指定任意版本或作者。

| 方法与路径 | 输入 | 成功结果 |
| --- | --- | --- |
| GET 讨论基路径 | after（非负整数，默认0） | 200，`{items,nextCursor,muted}` |
| POST 讨论基路径 | `{body}` + Idempotency-Key | 200，Message |
| DELETE 讨论基路径`/messages/{messageId}` | — | 204；仅管理该作业的教师或管理员 |
| GET `/api/assignments/{assignmentId}/chat/mutes` | — | 200，MutedStudent[]；仅作业管理者 |
| POST 同一 mutes 路径 | `{studentId}` | 204；必须是该作业班级的学生 |
| DELETE 同一 mutes 路径`/{studentId}` | — | 204；幂等解禁 |

- Message：`{id,authorId,authorName,role,body,createdAt,retracted}`；id 与 nextCursor 为字符串以避免 JavaScript 精度丢失。authorName 用当前昵称，空昵称回退学校姓名。
- after=0 取最近50条后按正序返回；after>0 取其后最多50条正序。当前前端刷新最近50条以同步撤回与禁言状态。
- body 去除首尾空白后1–1000个 Unicode code point，拒绝 NUL；纯文本展示。发送每个作者至少间隔2秒，超限429/CHAT_RATE_LIMIT。
- 同作者、同幂等键在同题同内容重放返回原消息；改正文或聊天室返回409。已禁言后即使重放也拒绝。
- 撤回后响应 body 为空，retracted=true；原内容仅保留服务器记录，不从学生接口返回。
- MutedStudent：`{studentId,name,studentNumber,mutedAt}`。禁言作用于同一作业所有题，读权限保留；禁言发言返回403/CHAT_MUTED。
- 教师仅管理自己的作业，管理员可管理全部；学生不能查看禁言名单、撤回或解禁。
- chatEnabled=false 后学生不能读取讨论，任何角色不能发言；教师/管理员可读取和管理历史。V11 维护消息与禁言表。

## v0.2 追加：TINO 解锁与 AI 展示位置

完整人格、提示词限制、测试与迁移见 [TINO 后端交接](../implementation/C/TINO.md)。V12 为账号保存解锁和启用状态，不把自由提示词暴露给客户端。

- `POST /api/profile/tino/unlock`：JSON `{sequence:"12332112323"}`，Session + CSRF；只解锁当前账号，返回 ProfileView，不自动切换风格。
- ProfileView / 管理员 UserView 增加 `tinoUnlocked`。PUT `/api/profile` 的 aiReplyStyle 增加 TINO；未解锁时403/TINO_LOCKED。管理员编辑资料也不能绕过。
- 前端前三个普通风格对应1、2、3；序列完成后显示第四项“小侦探Tino”，选择并保存才启用。已解锁状态随账号保存，可切回普通风格。
- AI replyStyle 绑定受理时服务端偏好；人格不能绕过作业开关、题目权限或参考答案输出检查。
- 用户最新布局要求：AI 按钮保留在编辑器旁，输出位于左侧青色栏对话框；TINO 使用用户已有形象资源显示在框下。前端交接单独记录其视觉与真实浏览器验证。

## v0.2 追加：已发布作业参数编辑

用户明确仅编辑名称、开放/截止时间、AI 与聊天室开关；班级、题目、版本与抽题方式固定。

- `PUT /api/teacher/assignments/{id}`：`{title,opensAt,closesAt,aiEnabled,chatEnabled}`，返回200 AssignmentView。所有字段必填，名称1–200字，截止时间晚于开放时间，开关必须为布尔值。
- 仅作业属主教师与管理员允许；Session/CSRF与对象权限校验保持。修改元数据，不重建题集、学生分配、提交、成绩、聊天或禁言。
- 点击作业发布页名称读取详情并打开编辑窗口；保存失败保留输入。关闭辅助功能后后端立即拒绝相应学生请求。
- 本接口不改变已有提交的受理时间或历史分数；更改时间会影响此后新提交的开放判断，已受理提交继续计分，成绩仍取已完成提交的最高分。最终验证记录见 A/ASSIGNMENT_EDIT.md。

## v0.2 追加：输出格式反馈

- Submission.verdict 与 caseResults[].status 新增 PRESENTATION_ERROR；数据库已有 VARCHAR(40) 字段足够，无迁移。
- 全部失败点仅格式不符且无得分时总结果为 PRESENTATION_ERROR；有得分则 PARTIAL_ACCEPTED；零分且另有内容/运行失败时保留对应失败结论。历史记录不自动重判。
- 页面显示“输出格式不符”并提示检查空格、换行及多余空行。接口继续不返回隐藏输入、期望输出、stdout 或 stderr。

## v0.2 追加：聊天室发送者头像

- Message 增加可空 `authorAvatarUrl`。无头像为null；有头像使用当前作业/题目/作者限定的同源URL及资料更新时间版本，同一作者同一房间URL一致。
- `GET /api/assignments/{assignmentId}/problems/{problemId}/chat/participants/{authorId}/avatar`：先校验聊天室读取权限（含学生抽题与开关），再要求作者确实在当前作业、题目版本有消息；无头像/无该房间消息返回404。成功返回规范化PNG，private/no-store、Vary:Cookie、nosniff。
- 不改变本人 `/profile/avatar` 权限，不新增全站任意用户头像读取接口，不暴露简介、学号等额外信息。既有教师/管理员在关闭讨论时管理历史的规则保持。
- 每条消息旁32px圆头像；缺失或加载失败时显示姓名首个Unicode字符。图片不重复朗读姓名。无新增图片资产或数据库迁移。
