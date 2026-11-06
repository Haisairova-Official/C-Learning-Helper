# B 第二轮交接：真实提交、持久队列与安全评测

日期：2026-11-06。状态：本地初步成品所需评测流程已实现并完成真实联调；目标 Rocky Linux 8 仍待单独部署验收。

## 本轮交付

- POST /api/submissions：学生身份、作业/题目版本与截止校验，UTF-8 代码长度，C17，Idempotency-Key，受理后返回 202。
- GET /api/submissions/{id}：本人或获授权教师可查看，返回代码、阶段、最终结果和公开用例状态。
- GET /api/submissions：按 page/pageSize、assignmentId、problemId、studentId 查询授权范围内的历史；列表不返回代码。
- JdbcTemplate + A 的 V2 迁移：submission、judge_task、case_result 真实持久化。当前用户行锁使同一人的幂等创建和最多 5 个活动提交限制保持一致。
- 2 个有限工作线程，数据库 SKIP LOCKED 原子领取；120 秒租约、每秒续期；过期任务恢复，最多 3 次尝试；完成写入用 lease_token 和未过期条件防止旧 worker 覆盖。
- 编译与执行使用已有 JudgeEngine + 真实 go-judge HTTP transport，完整成绩和用例结果在一个事务中落库；基础设施故障有限重试，最终为 SYSTEM_ERROR 且 score=null。
- Runtime 需要显式 JUDGE_ENABLED，认证值必填；请求受理前检查本机 go-judge 的 cpu/memory/pids 控制器和 seccomp。未配置或不可用时返回 503 / JUDGE_UNAVAILABLE，没有宿主执行或伪造结果的后备路径。
- 编译/运行使用源代码之外的固定 argv 与固定环境；内部保留原始 CPU/墙钟/内存测量，学生 CaseView 只返回公开字段。

## 实际本地环境

本地操作系统为 Arch Linux，普通用户 UID 1000，内核 7.2.14，cgroup v2；GCC 16.2.1。go-judge v1.14.0 通过官方 Go module 构建，运行时启用 no-fallback、seccomp 和 cpu/memory/pids 控制器。

本地 go-judge 地址为 127.0.0.1:15050。凭据在 /tmp/cassist-judge-1000/judge.env，目录权限 0700、文件权限 0600；交接中不记录凭据内容。此文件由本地启动工具加载，不进入学生响应或前端资源。

当前本地数据库为 A 启动的项目私有 MySQL 9.7 演示实例，使用 MariaDB Connector/J；正式数据库选型仍为 MariaDB。不能把本地 SQL 验证称为目标 MariaDB / Rocky 8 验收。

## 验证结果

| 验证 | 结果 | 证据 |
| --- | --- | --- |
| 原有评测核心、协议、HTTP 边界、快照转换 | 21 项测试通过 | 第一轮测试与本轮重新执行 |
| 真实数据库旧 worker token 防覆盖 | 通过 | SubmissionRepositoryIntegrationTest |
| 真实过期 lease 自动重新领取并执行 C 程序 | 通过，最终 ACCEPTED，attempt=2 | 同上 |
| 尝试次数耗尽的过期任务 | 通过，SYSTEM_ERROR，score=null | 同上 |
| 正确、WA、CE、RE、TLE、OLE、MLE | 真实 API 与 go-judge 全通过 | [API 结果](PHASE2_API_RESULTS.md) |
| 幂等重放/冲突、其他学生越权、教师读取、历史查询、隐藏数据边界 | 通过 | [API 结果](PHASE2_API_RESULTS.md) |
| 唯一 canary 的读/改/删、system 写入、最多32次 fork、本机网络连接 | 限制检查全部通过；canary 不变，无标记进程残留 | [非法操作测试](SECURITY_TESTS.md) |

模块测试命令：在 backend 目录执行 ./mvnw -q -Dtest=JudgeCoreTest,HttpGoJudgeTransportTest,JudgeProblemMapperTest test。

SQL 集成测试需要启动本地演示应用/评测 worker，并从受限配置加载 DB_URL、DB_USERNAME、DB_PASSWORD，再设置 CASSIST_INTEGRATION_DB=true，执行 ./mvnw -q -Dtest=SubmissionRepositoryIntegrationTest test。未显式启用时这 3 项测试跳过，避免普通单元测试意外接触数据库。

API 验证：项目根目录运行 python3 deploy/judge/verify_demo_api.py。它会创建明确的本地演示提交记录。

非法操作验证：项目根目录运行 python3 deploy/judge/verify_isolation_api.py。它只在自己创建的临时目录建立 canary，所有 C 程序经真实提交 API 进入沙箱。它检查程序确认限制后才返回正确答案，因此不把普通 WA 当作“攻击被防住”。测试结束清理自己的 canary，保留演示提交记录。

主 Agent 已统一重启本地 SQL 与应用进程，并确认独立于子 Agent 生命周期继续运行。旧 AC 记录保留，新提交再次 AC，证据为本机 .local/restart-result.json。上面的租约恢复测试与真正进程重启验收分别记录，不等同于全部故障注入或正式备份恢复验收。

## 运行与接入

- 整体本机启停由主 Agent 的 scripts/local_app.py 统一协调。
- go-judge 单独启动脚本：python3 deploy/judge/start_local.py，固定 v1.14.0，普通用户启动，凭据只写受限本地文件。
- 初始良性隔离探针：python3 deploy/judge/probe_local.py，真实编译并验证宿主测试文件与本机连接不可达。
- A 已实现 AccessPolicy / ProblemAccessPort 并合并 V2；B 只操作自身提交/任务/结果表。
- D 已收到真实 API 就绪通知，可切换到真实模式。完整字段示例见 [API_EXAMPLES.md](API_EXAMPLES.md)。

## 保留限制与下一步

- 本机演示采用系统 GCC 与只读标准系统目录；正式 Rocky 8 仍需独立 rootfs、固定 GCC 包版本和专门验收，部署模板见 deploy/judge/。
- 安全结果只覆盖列出的可控场景；system 调用在本次进程数与文件限制下无法修改宿主 canary，不代表所有 system 调用均被禁用。
- 目前服务 worker 固定 2 个、每人最多 5 个活动提交。更大班级规模的并发容量应在目标机测量。
- 第一版文本判题：EXACT 只统一 CRLF，TOKENS 比较 ASCII 空白分隔字符串，不做隐式浮点容差。
- 编译资源超限统一归为 COMPILATION_ERROR；运行阶段分别返回资源超限 verdict。
- 当前线程退出后未完成任务依靠租约恢复；实际进程重启、备份恢复、目标 MariaDB 版本与学校上线环境由整体部署验收继续覆盖。
