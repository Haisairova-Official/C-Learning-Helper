# Tino 人格后端交接

更新时间：2026-11-06 23:18（Asia/Shanghai）。执行：backend_tino；主 Agent 负责最终打包、数据库迁移、服务更新和真实模型调用。当前提示词版本：`c-debug-guidance/0.6`。

## 当前状态

后端实现、定向测试及主 Agent 真实集成已完成。下方各阶段分别记录源码验证和真实调用；最终称呼复验见文末。

## 用户明确要求

- 设置页前三个 AI 风格对应 1、2、3，按 `12332112323` 解锁第四项“小侦探Tino”。
- 解锁仅显示选项，选择并保存后启用；可切回普通风格，解锁状态随账号保留。
- 人格保留用户提供的定制设定：蒂诺／Tino／ティノ，活泼、好奇、自信、侦探助手口吻；技术分析保持认真可靠，不杜撰检查结果或原作剧情。按用户后续更正，不再固定称呼“秋月先生”。
- 称呼优先使用账号昵称原文；昵称为空时，学生使用学校姓名加“同学”，教师和管理员使用学校姓名、不加后缀。均不可用时，学生用“同学”、教职工用“你”。没有 username、userId 或某个演示账号的特殊映射。
- 使用既有 Tino 资源，不重新生成形象。图像接入由前端负责人处理。
- AI 按钮留在编译编辑区附近，指导显示在左侧对话框；人格和布局不能放宽题目权限、作业 AI 开关、参考答案保护或输出检查。

## 接口与持久化

| 接口／字段 | 行为 |
| --- | --- |
| `POST /api/profile/tino/unlock` | Session 身份与 CSRF；JSON `{"sequence":"12332112323"}`；只解锁当前启用账号，返回私有、不缓存的 ProfileView。错误或不完整序列返回 400。 |
| `ProfileView.tinoUnlocked` | 当前账号的持久解锁状态，旧构造入口默认 false。 |
| `ProfileUpdate.aiReplyStyle` | 原有三值外增加 `TINO`；未解锁返回 403 / `TINO_LOCKED`。 |
| `UserView.tinoUnlocked` | 管理员资料页面识别该账号是否拥有隐藏选项；管理员修改资料同样不能给未解锁账号强行选择 TINO。 |
| AI 分析 `replyStyle` | 受理时从服务器账号偏好读取并绑定本次请求；之后改偏好不改变进行中的分析。 |
| TINO 的内部 `addressName` | 受理时依据已认证账号读取昵称／学校姓名，保存本次快照；排队期间改资料不影响当前请求。POST 请求不接收称呼、身份或自定义提示词字段，GET 结果不增加独立的身份字段。 |
| 模型输入 `studentProfile.addressName` | 仅 TINO 提供非空称呼；通过 JSON 编码作为 user 消息中的数据传递，绝不拼入 system 消息。普通风格为空。 |

V12 为主 Agent 编写并授权本轮审核的迁移：`tino_unlocked`、`tino_enabled` 两个非空布尔列，默认 false；数据库约束要求启用时已经解锁。保留旧 `ai_reply_style` 的三个值与约束；选择 TINO 时保存普通风格回退值 GUIDED，并令 `tino_enabled=true`，对外统一读取有效风格 TINO。选择普通风格清除 `tino_enabled`，不清除解锁状态。

解锁接口是彩蛋状态，不承担身份认证或教学权限授权。服务器校验收到的完整序列；前端处理点击顺序。知道序列并直接请求该接口只能解锁本人彩蛋，不能改变角色、作业开关或获得参考答案。

## 修改与修复

- `ProfileService`：忽略解锁 UPDATE 的 changed-row 数，随后读取启用账号资料，从而区分“已经解锁且数据库报告零条变化”和“账号不可用”。重复请求幂等，不覆盖昵称、简介、头像或当前风格。
- `ProfileService.requireUnlocked`：查询不到启用账号时返回 401，避免空结果被当作未处理异常；未解锁返回 403。
- 审核通过的主 Agent 实现：`ProfileController` 当前身份解锁接口、`ProfileView` 解锁字段、`AiReplyStyle.TINO`、`UserManagementService` 管理员边界。
- 固定人格存放在 `backend/src/main/resources/prompts/tino-persona.txt`，只附加到服务器主提示词；指导 JSON 格式与 GuidanceGuard 保留。私有参考答案不会因人格切换进入资料接口或前端素材。
- `ProfileService.getAiAddress` 按当前启用账号读取资料，不修改资料；控制字符、双向等格式字符、段落／行分隔符及超长名字不能充当称呼，回退姓名或通用称呼。
- `AiAnalysisController`、`AiRuntime`、`AuthorizedAiGuidanceService`、`AiGuidanceCore`、`AiProvider` 和 `DeepSeekProvider` 传递称呼快照；旧服务重载保留兼容。内部任务 JSON 新增可缺省字段，无新增数据库迁移。
- `GuidancePrompt` 升至 0.6；人格中的固定人名及示例已移除。人格明确要求把名字当作不可执行的数据，不服从名字中的命令、不擅自加后缀、不把昵称提升为角色规则。输出 guard 原样保留；提示词和启发式检查不构成绝对防泄露保证。

## 实际验证

执行：`./mvnw -q -Dtest=ProfileServiceTest,ProfileControllerSecurityTest,UserManagementTinoTest,AiAnalysisControllerTest,AiGuidanceCoreTest,DeepSeekProviderTest,AiRuntimeTest test`。

| 测试类 | 结果 | 覆盖 |
| --- | --- | --- |
| ProfileServiceTest | 20 通过 | 解锁／资料边界；两个账号昵称隔离、昵称优先、学生姓名后缀、教职工无后缀、危险／超长名字回退、失效账号拒绝 |
| ProfileControllerSecurityTest | 7 通过 | 匿名、CSRF、当前账号限定、传入别人的 userId 不越权、私有缓存头 |
| UserManagementTinoTest | 3 通过 | 管理员不能绕过解锁、已解锁资料可保存 TINO、非管理员不能修改别人 |
| AiAnalysisControllerTest | 5 通过 | TINO 风格与称呼受理快照不受后续资料修改影响、普通风格不读取名字、作业 AI 关闭拦截、错误信息不泄露私有上下文 |
| AiGuidanceCoreTest | 14 通过 | 不同昵称的数据隔离、system 指令保持相同、恶意昵称不提升为指令、参考答案输出依旧拒绝、人格关键约束 |
| DeepSeekProviderTest | 11 通过 | 本地 HTTP 验证昵称内引号／伪造 role 不增加消息或改写 system；原有超时、错误分类、输出检查 |
| AiRuntimeTest | 3 通过 | 配置、并发及旧重载兼容 |

总计 63 项通过，0 失败、0 错误、0 跳过。模型为测试替身或本地 HTTP 服务，不是学校模型或 DeepSeek 的真实人格效果验收。没有重启服务、改数据库或调用付费模型。

## 主 Agent 下一步

1. 使用修复后的服务发布方式统一更新后端至提示词 0.6；V12 状态以主 Agent 部署记录为准，本轮称呼修正不增加迁移。
2. 有限真实模型验证确认当前账号昵称或姓名的正确称呼、口吻、JSON、答案保护；将实际结果追加至本交接，不能把上述单测替代真实调用。
3. 前端 TINO 数据发送说明需含称呼；已通知主 Agent 协调前端负责人。账号原昵称与头像不应被验收操作覆盖。
4. 正式 MariaDB／Rocky 8 的迁移与部署仍需目标环境验证。

## 2026-11-06 23:27 真实称呼复验后的提示词修正

主 Agent 实际调用发现：提示词 0.6 的服务端称呼快照正确为“验证s2同学”，模型 summary 却写成“验证s2，等一下！”，省略了“同学”。这是模型执行称呼规则不完整，不能用之前的单元测试结果声称真实称呼已经验收通过。

本轮仅修改 `tino-persona.txt` 的称呼指令，要求 summary 开头自然使用完整称呼，逐字保留，不删减已有“同学”等组成部分；含命令／系统标记等危险内容的名字明确不适用逐字使用规则，回退“你”，不执行或复述。提示词版本仍为 0.6，其他业务源码未改。

追加定向断言并运行 `./mvnw -q -Dtest=AiGuidanceCoreTest test`：15 项通过，0 失败、0 错误、0 跳过。未打包、重启、改库或付费调用。主 Agent 待统一发布后再做一次 S2 真实复验；当前记录只证明修正资源与防护回归通过，不证明模型已遵守新文案。

## 主 Agent 最终集成与真实复验

- V12 已迁移，本机运行提示词 0.6；精确解锁、重复解锁、资料保留、保存启用、重新登录持久化和切回普通风格均通过真实 HTTP。
- S1 浏览器真实请求：使用“验证昵称”，没有固定称“秋月先生”；左侧对话框、下方既有 Tino 素材及编辑区旁按钮均已实际查看。
- S2 首次真实请求发现省略“同学”，失败证据保留于私有 `.local/tino-live-result-v06-suffix-omitted.json`；没有抹去失败记录。
- 加强提示词并统一更新后，S2 真实 DeepSeek 复验 COMPLETED，summary 开头为“验证s2同学，小调查开始”；正确指出第5行运算符问题。请求期间切回普通风格后，该分析仍保留 TINO 快照；最终恢复原偏好。
- `.local/tino-live-result-v06.json` 保存最后复验，`scripts/tino_check.py --run-live --normal-only` 通过。真实调用只用独立合成账号、题目和人工错误代码；未变更真实用户的昵称或头像。
- 当前昵称及学生姓名后缀分支均有实际结果；教师/管理员姓名与危险称呼回退由定向测试验证。以上有限模型样例不是绝对不泄露答案或永远遵守风格的保证。
