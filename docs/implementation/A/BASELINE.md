# A 第一轮公共基线交接

日期：2026-11-06（Asia/Shanghai）。契约版本：v0.1。

## 已交付

- backend/pom.xml：Java 编译目标 21，Spring Boot 4.1.2，MyBatis Starter 4.0.0。
- backend/mvnw、mvnw.cmd、.mvn/wrapper：Maven 3.9.11，仅脚本 wrapper。
- com.cassist.common：统一错误、分页、traceId 与异常处理骨架。
- com.cassist.identity：Actor / Role / CurrentActor、默认拒绝账户认证、CSRF 与认证失败 JSON、csrf/me 控制器。
- com.cassist.problem：公开题目与私有快照 DTO、ProblemAccessPort、DraftImportPort。
- backend/src/main/resources/application.yml：本机监听、MariaDB/Flyway、环境变量密钥、UTC 会话、Session Cookie 配置。
- backend/src/main/resources/db/migration/V1__identity_problem_assignment.sql：用户、班级、题目版本、样例、用例、题目集、作业及发布快照基础表。
- CONTRACTS.md v0.1：固定请求形状、Java 内部边界、幂等键、比较与评分规则，登记 B/C 迁移需求。

## 实际依赖版本

由固定 Spring Boot BOM 解析得到：

| 组件 | 版本 |
| --- | --- |
| Spring Boot | 4.1.2 |
| MyBatis Starter | 4.0.0 |
| MariaDB Connector/J | 3.5.10 |
| Flyway Core / MySQL-MariaDB 支持模块 | 12.4.0 |
| Jackson Databind | 3.1.7，tools.jackson.databind |
| JUnit Jupiter | 6.0.3，继续使用 org.junit.jupiter.api |
| Maven | 3.9.11 |

依据：[Spring Boot 系统要求](https://docs.spring.io/spring-boot/system-requirements.html)、[MyBatis 兼容表](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/)、[Flyway MariaDB 支持](https://documentation.red-gate.com/flyway/reference/database-driver-reference/mariadb)，并已用 Maven Central 制品与 dependency:tree 验证解析结果。

## 实际验证

| 验证 | 结果 | 说明 |
| --- | --- | --- |
| 官方 Maven 3.9.11 制品下载并验证 SHA-512 | 通过 | 工具仅置于 /tmp；工程使用 wrapper |
| Maven compile | 通过 | 使用本机 JDK 27，编译 release 21 |
| ./mvnw -B -ntp verify | 通过 | 37 个主源文件的当时快照编译、3 项 CurrentActorTest 全通过、Spring Boot JAR 打包成功 |
| dependency:tree | 通过 | 实际版本见上表 |
| MariaDB 迁移、账户仓储、HTTP 完整启动 | 未执行 | 未配置 MariaDB 服务与凭据；只有 MySQL 客户端不构成 MariaDB 实测 |
| JDK 21 运行时、Rocky 8 | 未执行 | 当前构建宿主为 Java 27，不能视为目标机验证 |

验证命令在 backend/ 运行：

```sh
./mvnw -B -ntp verify dependency:tree '-Dincludes=org.mariadb.jdbc:mariadb-java-client,org.flywaydb:flyway-core,org.flywaydb:flyway-mysql,org.junit.jupiter:junit-jupiter,tools.jackson.core:jackson-databind,org.mybatis.spring.boot:mybatis-spring-boot-starter'
```

B/C 后续新增的测试由各自运行；上述 3 项不包含它们尚未落盘的测试。

## D 可依赖的事实

- 请求与响应按 CONTRACTS.md v0.1；ID 用字符串，JSON camelCase，分页从 1 开始。
- 已有 csrf/me 控制器源码，但尚未启动 HTTP 服务或接入可登录账户。
- login/logout、题库作业、评测/导入/AI HTTP 流程尚不属于 A 的已完成内容；页面请明确使用模拟数据。
- PrivateTestCase / ProblemSnapshot 仅限服务器内部，不能作为学生响应。
- 编译正常不等于数据库、认证和部署已完成；各页面联调状态要分别记录。

## 下一轮 A 应继续

1. 在真实 MariaDB 测试环境验证 V1，随后按 B/C 的 INTEGRATION_REQUESTS.md 合并 V2+ 迁移。
2. 实现账户仓储、登录/退出、Session 保存与 CSRF 轮换，并补 HTTP 认证集成测试。
3. 实现对象级授权与 ProblemAccessPort / DraftImportPort，接入班级、题目版本和作业发布业务。
4. 接入 B/C 持久化与 D 页面；补 systemd / Nginx 及目标机部署验证。
