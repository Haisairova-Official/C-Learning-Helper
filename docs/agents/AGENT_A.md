# Agent A：基础权限、题库作业与公共契约

## 任务目标

建立可运行的后端业务基础，使教师可以管理题目和班级、发布作业，学生可以访问自己的作业。负责统一数据库结构、公共接口和依赖版本，为 B、C、D 提供稳定的接入点。

先阅读：[公共需求](PROJECT_BRIEF.md)、[契约草案](CONTRACTS.md)、[决策记录](DECISIONS.md)、[联调要求](INTEGRATION.md)。

## 文件归属

建议后端根包为 com.cassist，A 在初始化时确认并记录。

- backend/pom.xml、后端入口、backend/src/main/resources 下的公共配置。
- backend/src/main/java/com/cassist/common/、identity/、classroom/、problem/、assignment/。
- backend/src/main/resources/db/migration/：全部 Flyway 迁移统一由 A 维护。
- 与上述模块对应的 backend/src/test/ 文件。
- deploy/nginx/、deploy/systemd/c-assist-backend.service、deploy/config/backend/。
- CONTRACTS.md、DECISIONS.md 和本任务文件；详细实现说明可写入 docs/implementation/A/。

B、C 负责各自业务代码，D 负责 frontend/。公共依赖变更由对应 Agent 提出，A 汇总。

## TODO：优先建立公共基线

- [x] 核对根目录及已有项目内容，保留其他 Agent 的工作。
- [x] 初始化 Java 21 / Spring Boot 后端，锁定兼容的 MyBatis Starter、MariaDB Connector/J、Flyway 版本，记录选择依据。
- [x] 配置 MariaDB 与 Flyway，包括 MariaDB 所需的 Flyway 数据库支持模块；密钥从环境或受限配置注入。
- [x] 审查 CONTRACTS.md，确认 ID、时间、分页、错误、状态、权限和请求示例，发布契约基线并通知 B/C/D。
- [x] 与 B/C 确认评测、导入、AI 所需表结构，统一编排迁移编号；在任务文件中记录收到的迁移请求。
- [x] 建立统一校验、错误响应、日志 traceId 和开发配置，避免敏感信息进入日志。

## TODO：权限与班级

- [x] 实现登录、退出、当前身份、密码安全存储、Session 与 CSRF 配套接口。
- [x] 实现学生、教师、管理员角色及对象级访问校验，给 B/C 提供可复用的授权服务。
- [ ] 实现班级、教师归属和学生成员管理，提供开发演示账号数据及生产初始化说明。
- [x] 明确教师只能管理授权范围内的班级、题目和作业；管理员权限也必须由后端校验。

## TODO：题库与作业

- [x] 实现题目草稿管理：题干、输入输出说明、样例、参考答案、测试用例、分值、资源限制。
- [x] 分别定义学生公开 DTO 和教师私有 DTO，保证参考答案、隐藏用例不进入学生响应。
- [x] 实现题目版本和发布快照，提供 B/C 读取指定版本公开/私有信息的内部接口。
- [x] 实现题目集编排、作业发布、班级分配、开放与截止校验。
- [x] 校验样例与用例配对、计分总和、资源上限和必填字段；不完整的导入内容允许保存草稿，发布时必须补齐。
- [x] 为 C 提供批量保存题目及题目集草稿的业务接口，统一事务、授权和重复导入处理。
- [x] 与 B 对齐最高分规则，实现班级完成情况、学生成绩和题目通过情况查询。

## TODO：部署、验证与交接

- [x] 提供后端 systemd 服务、同域 Nginx 配置、数据库初始化及备份恢复说明。
- [ ] 验证 MariaDB 全新建库迁移、重复启动、核心事务和常用查询。
- [x] 验证登录退出、CSRF、跨班级/跨教师越权、学生接口敏感字段隔离。
- [ ] 验证题目版本固定、作业时间边界、部分分与最高分统计规则。
- [x] 交付可供 D 使用的接口示例和可供 B/C 调用的内部服务契约。
- [x] 更新本文件 TODO、CONTRACTS.md、DECISIONS.md，提交实际验证结果。

## 验收条件

教师能创建班级、维护题目并发布作业；学生只能访问授权内容；数据库可从空库初始化；B/C/D 能依据公共契约完成真实接入。依赖尚未接入时，清楚标出哪些统计或接口只通过模拟验证。

## 当前交接

2026-11-06 主 Agent 接手账号扩充：新增学生名单 Easy Parse（预览、统一初始密码、新建学生并加入当前班级、已有学生匹配）、管理员手动添加各角色账号与资料维护。初始实现由主 Agent 统一维护公共契约、依赖及 V8 迁移，随后按用户要求再次并行分工，最新状态见本文件末尾。详细方案/验证写入 `docs/implementation/A/USER_MANAGEMENT.md`。已有账号的密码、昵称和头像不因导入改变。

- 状态：第二轮核心业务与交接完成，主 Agent 已接管持久后台服务。
- 更新时间：2026-11-06（Asia/Shanghai）。契约版本：v0.2。
- 当前服务：127.0.0.1:8080；项目私有演示数据库127.0.0.1:33077，数据backend/.local/mysql-data。
- 真实实现：Session登录退出/CSRF、数据库账号、班级/成员、题目草稿/编辑/版本、作业发布/查询、公开题目、成绩统计、ProblemAccessPort、DraftImportPort。
- 演示：teacher/student/student2/admin，密码CassistDemo2026!；仅demo profile初始化，正式环境不启用。
- 验证：53项后端测试通过；20项A真实HTTP验收通过；Flyway V1–V4在私有MySQL9.7.1从空库执行成功。主 Agent 冷启动复验通过，历史记录持久保留且新提交再次 AC。
- 正式限制：MariaDB和Rocky8尚未实测；JDK21运行时尚未实测（当前JDK27/release21）；正式账号开通/重置流程待完善；学校AI真实接入由主 Agent 跟进。
- 启动方式：项目根目录运行 python3 scripts/local_app.py start/status/stop/restart；主 Agent 已接管后台进程。底层 run-demo.py 读取受限环境与 B 的 judge.env，并为私有 MySQL 显式指定 RSA 公钥路径。
- 交接资料：[部署说明](../implementation/A/DEPLOYMENT.md)、[HTTP验收](../implementation/A/HTTP_TESTS.md)、[后端README](../../backend/README.md)。第一轮历史：[BASELINE.md](../implementation/A/BASELINE.md)。

### 跨 Agent 交接

| 接收方 | 内容 | 状态 |
| --- | --- | --- |
| B | V2表、AccessPolicy对象权限、真实题目快照 | 已交付，B正在真实评测验收 |
| 主 Agent（C集成） | V3导入/AI表、DraftImportPort幂等持久化、异常安全映射 | 已交付，主Agent正在端到端验收 |
| D | 真实8080、账号、TeacherProblem/Assignment/Class/Member/ResultRow类型 | 已交付，D切真实模式联调 |

未勾选的 MariaDB 目标验收与正式账户流程保持未完成。后续调整按 [交接模板](HANDOFF_TEMPLATE.md) 记录。


## 本轮追加：账号设置（可联调）

- [x] 编写 V5：nickname、bio、ai_reply_style（GUIDED 默认）、avatar_png、profile_updated_at。
- [x] 当前账号资料 GET/PUT，保留 username/display_name/role。
- [x] 头像 POST/DELETE/GET：原文件512KiB、PNG/JPEG真实类型、单边4096上限、256缩放、重新编码PNG、禁共享缓存。
- [x] ProfileService.getAiReplyStyle(actorId) 为主 Agent 提供可信三选一风格。
- [x] Unicode长度、图片边界、身份隔离与CSRF的定向验证。
- [x] 更新资料设置交接；统一构建/重启由主 Agent 执行。
- [ ] V5 实际迁移、真实 HTTP 与页面联调、资料持久化验收（主 Agent 统一执行）。

当前交接：2026-11-06 18:15（Asia/Shanghai），代码与19项定向测试通过；依据 [冻结接口](PROFILE_SETTINGS.md)，细节见 [资料设置交接](../implementation/A/PROFILE_SETTINGS.md)。未修改 AI、TeachingService 或 ProblemProgress，未直接更新运行数据库或重启服务。主 Agent 调用 `com.cassist.identity.ProfileService.getAiReplyStyle(String actorId)`；D 接口无差异。V6 留给 B。

## 本轮追加：递减数组函数题样例（已交接）

- [x] 独立编写 C17 指针/struct 与 CPP17 引用/vector 函数题，保存为教师导入资料。
- [x] 独立编写公开接口/骨架、私有参考答案及单占位评测模板。
- [x] 每语言10个用例，核对空/满/重复/首中尾/负值及边界整数期望输出。
- [x] 文档记录导入方式、学生资料边界及验证限制；真实隔离执行交主 Agent。
- [ ] 真实隔离编译/AC/WA/CE、教师入库与学生字段隔离联调（主 Agent 执行）。

后续真实结果：主 Agent 已确认本样例两语言通过 JSON 导入、发布和每语言10用例 AC；作业 ID 为 `9631ff1d-3af8-4a7a-b3bb-3c5dd4f5f54b`。其他集成结果统一见 [第二轮验收](../ITERATION_02.md)，不据此把其余蓝本算作已入库。

当前交接：仅新增 [教师样例 JSON](../../examples/pta/function-insert.json) 与 [函数题说明](../implementation/A/FUNCTION_SAMPLE.md)，未修改业务代码或启动服务。UTF-8 JSON、分值/标记、10份期望输出的独立 set/sort oracle 核对全部通过；未在宿主执行 C/C++。每个 problems 元素可供教师保存接口使用，顶层数据包不是新增批量 HTTP 接口；答案和模板未放前端资源。

## 本轮追加：190 道编程题蓝本（已完成交接）

- [x] 按现有真实目录读取190条type7公开description，原响应只存.local/pta/programs。
- [x] 逐题形成原创简短题意、输入输出/边界、公开样例数量、资源与语言兼容说明。
- [x] 区分教材算法/依赖类题与可独立程序题；失败或未提供约束如实标记。
- [x] 交付C目录下PTA_PROGRAM_BLUEPRINTS.md/.json和读取统计；4道多文件题只分类。

### 当前交接

- 状态：2026-11-06 18:37（Asia/Shanghai），公开题面采集与蓝本完成，未入库/运行评测。
- 分工：A单并发抓取全部190条，间隔0.3秒，190条均成功；A逐题完整阅读并摘要1–100，B逐题完整阅读并摘要101–190，A统一合并和核对。
- 修改文件：[190题蓝本MD](../implementation/C/PTA_PROGRAM_BLUEPRINTS.md)、[JSON](../implementation/C/PTA_PROGRAM_BLUEPRINTS.json)、[全题集成果入口](../implementation/C/PTA_COLLECTION.md)。同时按主Agent真实结果更新[首题双语样例交接](../implementation/A/FUNCTION_SAMPLE.md)。
- 实际验证：190唯一ID与API标题吻合、190份非空description、190条原创摘要/输入输出概述、190个MD条目；所有正文输入/输出样例标题成对；各条来源指纹和资源配置已留存。
- 重要限制：4题明确特判，另有多解/近似/随机及教材步骤依赖；7题正文样例数和API去重样例数不一致。均已逐题注明，不能直接当作可评测题库。没有公开参考实现、隐藏用例或原题全文。
- 范围：与此前30函数题蓝本合计220份已读公开题面，另4多文件题仅目录。只有首题题意的两份独立语言改编已由主Agent导入发布并分别10例AC；不能据此说其余29函数题或本批190程序题已入库。
- 下一步：主Agent从统一成果入口交付；按教学需要选择条目后独立制作答案/测试，补齐教材定义、歧义和多解判题规则。A没有改共享业务、数据库或服务进程。

## 2026-11-06 主 Agent：扩展实现阶段记录

- 用户管理、名单 Easy Parse、题库层级、抽题模式和聊天管理由主 Agent 统一完成跨模块修改；无其他 Agent 在写这些文件。
- V8–V11 已在本机 MySQL 完成迁移，真实 LAN HTTP 通过7组业务与越权验证。V9 首次迁移失败后已修复并核对旧题全部可达；原数据保留，更新前有私有备份。
- 当前追加 Tino：用户明确 12332112323 解锁第四项“小侦探Tino”，已提供完整口吻设定。新增 V12 仅保存 tino_unlocked / tino_enabled，保留原三种风格字段；接口返回有效风格 TINO。尚待本次构建、迁移和验证。

## 2026-11-06 management_qa：管理复核与交接

### 当前状态

- Agent：management_qa；日期2026-11-06（Asia/Shanghai）；契约v0.2追加。
- 阶段：管理模块复核、MD与旧创建接口自动归库修复全部交接；本机部署后验证通过。
- 分工：主 Agent 处理运行稳定性与统一构建；backend_tino处理人格与V12；frontend_finish处理左侧AI对话框、形象与长题面编辑区；本 Agent 负责管理模块审核、测试和公共MD。
- 不重启、不打包、不调用真实AI，不修改真实学生的资料或凭据。

### 已完成

- [x] 复核管理员用户管理、名单导入、题库、抽题和聊天室的角色/对象权限、事务与前端操作路径。
- [x] 复用现有独立合成资料完成5组追加真实HTTP验证；头像临时修改已清除恢复。
- [x] 修正回归脚本中“统一初始密码等于原密码”的验证缺口，以不同密码验证已有密码没有被重置。
- [x] 更新管理交接、CONTRACTS与DECISIONS，区分真实HTTP、代码审阅和目标环境待验收。
- [x] 经主 Agent 扩展授权，修复旧 `POST /teacher/problems` 新题无题库归属；只修改 `ProblemLibraryService` 和 `TeachingController`，没有改公共保存逻辑或迁移。
- [x] 旧接口自动归库补丁由主 Agent 统一构建部署，随后完成真实并发HTTP验证。

修改文件：

- `backend/src/main/java/com/cassist/problem/ProblemLibraryService.java`：新增事务入口，沿V9默认库ID创建/锁定库并关联新题；已存在库不重命名。
- `backend/src/main/java/com/cassist/problem/TeachingController.java`：仅旧POST创建转到上述入口，仍返回201。
- `scripts/management_demo.py`：加强已有密码保留断言；本 Agent 未重新运行其创建整套资料的主流程。
- `scripts/management_followup.py`：默认4组现有资料复核；显式 `--check-legacy-create` 首次并发创建两道合成草稿并记录ID，后续复用。
- [管理交接](../implementation/A/USER_MANAGEMENT.md)、[公共契约](CONTRACTS.md)、[决策](DECISIONS.md)及本文件：最终行为、字段、迁移、实际证据、限制与交接。

### 验证

| 场景 | 执行方式 | 实际结果 | 环境或限制 |
| --- | --- | --- | --- |
| 已有学生密码/资料不被新统一初始密码覆盖 | management_followup | 通过，旧密码登录成功，新密码401，资料前后相同 | 现有合成账号，LAN80真接口 |
| 题库改名/重复成员状态码与去重 | management_followup | 通过，204且题库内容不变 | 同值写入，可重复 |
| 随机未分配题目聊天、跨教师解禁、禁言非学生 | management_followup | 通过，404/403/400 | 权限拒绝发生在服务端 |
| 管理员头像上传读删、缓存与低权限拒绝 | management_followup | 通过，删除后恢复无头像 | 仅合成教师账号，不改真实用户 |
| 两个Python脚本语法 | py_compile | 通过 | 不代表业务行为验证 |
| 旧入口并发归库 | management_followup --check-legacy-create | 通过，两个201且均归默认库，其他教师/学生403 | 两个新合成题目ID保存在原fixture，后续可复用 |
| 最终后端构建 | 主 Agent 统一执行 | 149项记录，144通过、5跳过、0失败 | 主 Agent 统一部署后，本 Agent 执行上述5组HTTP复核 |

### 跨 Agent 请求

| 请求 | 接收方 | 原因及影响 | 当前状态 |
| --- | --- | --- | --- |
| 统一部署两文件归库修复后通知 | 主 Agent | 保持不可变运行包策略，便于跑既有合成资料验证 | 已部署并完成验证 |
| TINO公共契约链接专项结果 | backend_tino / 主 Agent | 避免重复修改其提示词与V12 | 已链接TINO交接，已收到V12部署确认 |

### 未完成与下一步

最终部署后已执行 `CASSIST_BASE_URL=http://192.168.2.100/api python3 scripts/management_followup.py --check-legacy-create`，5组全部通过。两道题保存在原合成教师的默认题库，ID写入 `.local/management-check.json` 的 legacyProblemIds；后续复核不再创建同类资料。本轮没有剩余的管理补丁待部署项。

正式MariaDB、Rocky8和学校AI继续保留待验收；目前每次作业候选上限100，发布页题库/班级选择只取前100项，没有全量容量压测。管理页面本轮为代码复核，不能声称已独立走完所有浏览器流程。进一步功能范围详见管理交接。

## 2026-11-06 management_qa：已发布作业参数修改（已部署验证）

- 用户已确认只修改名称、开放/截止时间、AI与聊天室开关；题目、班级、抽题与成绩保持原样。
- 本 Agent 负责 `PUT /api/teacher/assignments/{id}`、权限/参数测试、独立合成资料验证脚本与 [专项交接](../implementation/A/ASSIGNMENT_EDIT.md)。frontend_finish负责页面，主 Agent 统一构建部署与公共CONTRACTS/DECISIONS。
- 不修改评测代码或WorkspaceView，不重启或打包、不调用真实AI。
- [x] 后端实现与有意义的权限/参数测试。
- [x] 准备并执行部署后的合成资料HTTP验证；原作业参数在finally恢复。
- [x] 记录实际结果、修改文件和目标环境限制。

### 当前交接

- 状态：后端完成并已由主 Agent 统一部署；用户编辑范围已确认，无待确认字段或后端部署项。
- 修改文件：TeachingController新增PUT；TeachingService新增AssignmentUpdate与仅更新5列的事务方法；AssignmentUpdateServiceTest与AssignmentUpdateControllerSecurityTest；`scripts/assignment_edit_check.py`；本文件及[专项交接](../implementation/A/ASSIGNMENT_EDIT.md)。无数据库迁移。
- 定向测试：22项通过，0失败/0错误/0跳过；包括真实AccessPolicy角色/属主校验、参数与数据库时间精度边界，以及真实SecurityFilterChain的登录/CSRF边界。JDBC在定向测试中为模拟。
- 真接口：部署后通过LAN80执行4组，覆盖匿名/学生/跨教师/CSRF/404、9种无效参数、FULL与每人随机作业修改；新开关与时间限制生效，原题目版本、抽题、班级、非零成绩、消息与禁言保持。AI仅检查关闭后拒绝，未执行模型请求；提交仅检查时间拒绝，未执行新程序。
- 恢复：两份合成作业5字段、完整教师/学生视图、成绩与原禁言列表均复核恢复；没有创建账号或作业。
- 跨Agent：frontend_finish维护名称点击和编辑对话框及中文日期显示；主 Agent维护公共契约/决策与部署。新专项覆盖此前管理交接中“发布后不能修改开关”的历史限制。
- 限制：正式MariaDB、Rocky8、生产JDK21仍未验收。没有乐观锁版本冲突提示，并发编辑按事务先后保存；已受理AI/评测任务不会因修改参数被取消。

## 2026-11-06 management_qa：聊天消息头像（已部署验证）

- 用户要求每条消息展示作者头像；后端新增authorAvatarUrl及当前题目聊天室范围内的PNG读取接口，头像为空返回null。
- 当前作业/题目访问、随机分配、聊天室开关仍执行；头像作者必须在同作业与同固定版本发过消息，不开放任意账号头像读取。
- [x] 后端实现和权限/缓存/消息映射定向测试。
- [x] 复用已发过消息的合成作者，临时上传头像并恢复；不发送新消息、不调用AI。
- [x] 主 Agent统一部署后完成真实HTTP验证及专项MD。
- 本Agent不改本人头像接口、前端、数据库迁移、CONTRACTS或DECISIONS，不打包或重启服务。

### 当前交接

- 已完成文件：ProblemChatController、ChatAvatarControllerSecurityTest、`scripts/chat_avatar_check.py`、[CHAT_AVATARS.md](../implementation/A/CHAT_AVATARS.md)与本文件。Message字段为authorAvatarUrl，路径与frontend_finish已直接对齐，v=profile_updated_at的UTC秒-纳秒；同作者同房间相同URL，无头像null。
- 定向测试8项全部通过，0失败/0错误/0跳过；真实SecurityFilterChain，TeachingService/JDBC模拟。最终完整verify由主 Agent执行：184项记录，179通过、5项环境跳过、0失败，随后统一部署。
- LAN80真实HTTP3组全部通过：共享URL与版本变化、PNG和缓存头；匿名/非成员/其他教师/未分配题/关闭讨论拦截；没有当前题消息的作者即使有头像也404；无头像null/404。教师关闭讨论后保留历史访问。
- 已恢复原合成作者的头像与资料、作业开关，逐项比较既有消息内容一致。未发送聊天消息、创建账号、调用AI或运行学生程序。
- 无剩余后端部署项。主 Agent进行有图/无图浏览器验收，更新公共契约；正式MariaDB/Rocky8与生产环境验收仍独立记录。

## 2026-11-07 主 Agent：GitHub 首次入库（完成）

按用户要求新建C-Learning-Helper仓库，AGPLv3。当前无Git历史；采用已登录账号下私有仓库，检查全量源码/文档/配置与密钥排除后初始化main并推送，不重启服务。详细过程见 [GITHUB_REPOSITORY.md](../implementation/A/GITHUB_REPOSITORY.md)。

GitHub已创建私有仓库Haisairova-Official/C-Learning-Helper并推送main。294文件远端树与本地逐blob一致，GitHub识别AGPLv3，敏感值扫描无命中。首次提交ba315a5；交接更新另行提交。不影响现有服务。
