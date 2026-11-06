# A 第二轮 HTTP 验收

日期：2026-11-06。环境：本机 JDK 27 / Spring Boot 4.1.2 / 项目私有 MySQL 9.7.1（正式 MariaDB 尚未实测）。

- [x] Anonymous identity request returns 401
- [x] Login requires CSRF
- [x] Incorrect password returns safe 401
- [x] Session login and session-id rotation
- [x] Authenticated identity persists
- [x] Student cannot access teacher problem DTO
- [x] Student assignment query works
- [x] Student public problem contains no answer or hidden cases
- [x] Teacher login works
- [x] Teacher can read private reference answer
- [x] Editing produces a new immutable version
- [x] Published assignment retains original version and statement
- [x] Publication rejects inconsistent testcase score total
- [x] Invalid pagination returns 400
- [x] Teacher class and member lists work
- [x] Teacher assignment results work
- [x] Logout invalidates session

说明：通过真实 HTTP、Session Cookie 与 CSRF token 执行；测试中编辑种子题产生新版本后已恢复其原内容，原作业始终保持原版本。后端数据库保存结果未使用内存替代。

## 跨教师对象权限补充

- [x] Other teacher cannot read private problem
- [x] Other teacher cannot read class roster
- [x] Other teacher cannot read assignment results

临时独立教师账号在验证后已删除，未改动既有账号。累计 20 项真实 HTTP 验收通过。

## 主 Agent 补充冷启动验收

- [x] 数据库冷启动后使用显式 serverRsaPublicKeyFile 完整认证成功。
- [x] 后台进程接管完成，数据库/后端/前端端口就绪。
- [x] 重启前 AC 提交记录保留，重启后新增隔离评测再次 AC。

以上由主 Agent 实际执行并回传，A 负责修复启动脚本并同步此记录；不是对正式 MariaDB 或 Rocky 8 的验证。
