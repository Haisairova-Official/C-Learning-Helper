# C 语言教学辅助系统后端

第二轮已提供真实 Session 认证、题库作业、班级查询、公共授权端口、持久提交及导入/AI 接口。完整契约见 [CONTRACTS.md](../docs/agents/CONTRACTS.md)，启动细节见 [部署说明](../docs/implementation/A/DEPLOYMENT.md)。

## 本机演示启动

推荐在项目根目录使用统一后台管理脚本：

```sh
python3 scripts/local_app.py start
python3 scripts/local_app.py status
python3 scripts/local_app.py stop
```

该脚本由主 Agent 维护，后台托管演示数据库、后端和前端，避免子 Agent 交互会话结束时进程被清理。下方是分组件调试方式。

在 backend/ 运行：

```sh
./mvnw -B -ntp verify
python3 scripts/start-demo-db.py
```

数据库脚本前台运行，另开终端：

```sh
python3 scripts/run-demo.py
```

当前演示数据库为项目私有 MySQL 9.7.1，监听 127.0.0.1:33077，数据位于 .local/mysql-data。正式数据库仍为 MariaDB，不能把本机结果表述为 MariaDB / Rocky 8 已验收。脚本不会停止已有进程或修改系统服务。已有实例运行时无需再次启动。

后端监听 127.0.0.1:8080，默认加载 .local/demo.env 和存在的 /tmp/cassist-judge-当前用户ID/judge.env。秘密值不应复制到聊天、前端或仓库。日志在 .local/backend.log。

演示账号 teacher、student、student2、admin，密码均为 CassistDemo2026!；仅显式 demo profile 初始化。正式环境不启用该 profile。当前本机从权限 0600 的 `.local/ai.env` 读取 DeepSeek 私有配置并调用真实模型；未配置的 demo 默认 mock。学校私有部署接口仍待接入，详见 [DeepSeek 接入验收](../docs/implementation/C/DEEPSEEK.md)。

## 当前接口

| 范围 | 状态 |
| --- | --- |
| GET csrf/me、POST login/logout | 真实数据库账号、Session、CSRF，已通过 HTTP 验证 |
| 教师题目列表/详情/新建/编辑 | 已实现；编辑生成新版本 |
| 班级列表/新建、成员列表/加入已有学生 | 已实现并检查对象权限 |
| 作业列表/详情/发布、学生公开题目 | 已实现；发布时固定题目版本 |
| 作业成绩与完成情况 | 读取提交记录，按一次完整提交的最高分汇总 |
| ProblemAccessPort / DraftImportPort | 已有 JDBC 数据库实现与授权、事务、导入幂等 |
| 评测提交/查询、导入/AI | 已并入工程，实际验收见 B 与主 Agent 的交接 |
| Flyway V1–V4 | 在私有 MySQL 演示库从空库执行通过；MariaDB 目标环境待验收 |
| 正式账户开通/重置、Rocky 8 部署 | 后续完善；部署模板已提供 |

## 技术与构建

- Java 编译目标 21；当前构建和演示使用 JDK 27，目标 JDK 21 运行时待实测。
- Spring Boot 4.1.2、MyBatis Starter 4.0.0、Maven Wrapper 3.9.11。
- 首轮持久业务统一使用 Spring JdbcTemplate，避免各模块维护不同存储；MyBatis 依赖保留，但目前没有 Mapper 实现。
- MariaDB Connector/J 3.5.10、Flyway 12.4.0、Jackson 3.1.7，由固定 Boot BOM 管理。
- 最近一次统一 Maven verify：53 项测试全部通过；A 模块另有 20 项真实 HTTP 验收。主 Agent 已完成数据库/后端冷启动复验，确认历史评测数据保留且新提交再次通过；见 [HTTP_TESTS.md](../docs/implementation/A/HTTP_TESTS.md)。

## 正式配置

application.yml 读取 DB_URL、DB_USERNAME、DB_PASSWORD；默认本机监听，Session Cookie 默认 Secure/HttpOnly/SameSite=Lax。数据库和 Java 进程按 UTC 处理时间，页面按 Asia/Shanghai 显示。

生产参考 deploy/config/backend/backend.env.example、deploy/systemd/c-assist-backend.service 和 deploy/nginx/c-assist.conf.example。正式 MariaDB、Java 21、TLS 和评测隔离须在 Rocky 8 上单独验证。运行程序的隔离规则由 B 的评测服务负责，后端业务进程不直接执行学生代码。
