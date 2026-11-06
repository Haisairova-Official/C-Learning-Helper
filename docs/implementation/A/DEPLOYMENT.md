# A 部署与本机启动说明

## 当前本机演示

真实持久化使用项目私有 MySQL 9.7.1，数据保存在 backend/.local/mysql-data，仅监听 127.0.0.1:33077；这不等同于已完成正式 MariaDB 验收。应用通过 MariaDB Connector/J 连接，Flyway V1–V4 已在该演示库执行通过。

在 backend/ 中执行：

```sh
python3 scripts/start-demo-db.py
```

该命令仅用于已经有 MySQL mysqld 的本机演示环境。首次创建私有目录、随机数据库密码与权限为 0600 的配置；已存在的数据不会重新初始化。端口被占用时退出，不停止现有进程。它保持前台运行，另一个终端启动应用：

```sh
./mvnw -B -ntp verify
python3 scripts/run-demo.py
```

run-demo.py 读取 backend/.local/demo.env，并在存在时读取 /tmp/cassist-judge-当前用户ID/judge.env。应用监听 127.0.0.1:8080，日志写入 backend/.local/backend.log，PID 写入 backend/.local/backend.pid。Java 以 UTC 时区运行，Session 使用本地 HTTP 允许的 Cookie 配置。

演示账户 teacher、student、student2、admin 的密码均为 CassistDemo2026!，只在显式 demo profile 中初始化；首次启动创建演示班和“两数之和”作业。再次启动保留数据库数据和已经创建的作业，不重置账号密码。AI 在 demo profile 中明确为模拟模式，学校真实 AI 尚未接入。

停机时对属于本项目 PID 文件记录的进程发送正常终止信号；先核对进程命令与工作目录，不按端口杀死未知服务。重新启动应用后需重新登录，提交、题目和导入数据仍保存在数据库。

## Rocky Linux 8 正式部署准备

1. 安装并验证 Java 21、受维护 MariaDB、GCC 和 go-judge；此处尚未在目标机器实际执行。
2. 创建专用系统用户 cassist、工作目录 /opt/c-assist/backend 和可写目录 /var/lib/c-assist，部署构建后的 JAR 为 c-assist-backend.jar。
3. 创建 MariaDB 数据库 c_assist，字符集 utf8mb4；配置专用应用账号，仅授权该数据库。当前启动时由 Flyway 迁移，需要相应建表/改表权限。
4. 将 deploy/config/backend/backend.env.example 复制为 /etc/c-assist/backend.env，填写实际数据库与评测凭据，权限 0600，所有者为服务账户或受限部署管理者。
5. 使用 deploy/systemd/c-assist-backend.service 模板，先核对 /usr/bin/java 实际指向 Java 21。正式环境不启用 demo profile；正式账号应通过独立受控初始化流程提供，当前该管理流程尚待完善。
6. 构建前端，将 dist 放到 /opt/c-assist/frontend/dist；填好 Nginx 模板的学校域名与实际 TLS 证书后检查配置并启用。
7. 按 B 的评测部署说明验证 cgroup、文件与网络隔离、时间/内存/进程/输出限制；仅允许后端调用评测服务。
8. 执行登录、题目导入、作业发布、实际评测、AI 模式与权限检查，再开放给用户。

部署模板尚未在 Rocky 8 验证，不应直接表述为生产部署完成。

## 备份与恢复

使用正式 MariaDB 自带的 mariadb-dump 对 c_assist 做一致性备份，凭据从受限选项文件读取，避免密码写进命令行或仓库。先在独立测试数据库恢复并核对 Flyway 版本、用户和作业数量，再规划正式恢复。

示例（路径和账号文件由部署者提供）：

```sh
mariadb-dump --defaults-extra-file=/etc/c-assist/backup.cnf --single-transaction c_assist > c_assist-backup.sql
mariadb --defaults-extra-file=/etc/c-assist/restore-test.cnf c_assist_restore < c_assist-backup.sql
```

恢复示例指向单独的 c_assist_restore，不能在未确认目标和备份的情况下覆盖现有库。


## 私有 MySQL 冷启动认证

MySQL 的 caching_sha2_password 在服务重启后需要完整认证，先前暖缓存连接不能证明重启后可用。run-demo.py 现对私有 33077 实例显式传入 serverRsaPublicKeyFile，指向 backend/.local/mysql-data/public_key.pem；通过 Hikari JDBC 属性传递原始 Unicode 路径，不做 URL 百分号编码，不读取私钥，也不放宽公钥自动获取。该选项依据 [MariaDB Connector/J 官方说明](https://mariadb.com/docs/connectors/mariadb-connector-j/about-mariadb-connector-j)。

本机持久后台运行已由主 Agent 的 scripts/local_app.py 接管。主 Agent 于 2026-11-06 实测数据库与后端冷启动成功，三个应用端口就绪，重启前的 AC 记录保留，新提交经真实隔离评测再次 AC；RSA 公钥路径修复已通过实际完整认证。统一脚本支持 start / stop / restart / status，避免依赖会随子 Agent 结束清理的交互工具会话。
