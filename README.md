# C-Learning Helper · C 语言教学辅助系统

当前为可在本机运行的初步版本。已接通登录、教师题库与作业、Easy Parse 导入、学生在线编程、真实隔离评测、提交历史和成绩查询。

## 许可证

本项目原创代码采用 **GNU Affero General Public License v3.0 only**（`AGPL-3.0-only`），完整条款见 [LICENSE](LICENSE)。第三方脚本、依赖、题目来源与角色素材的说明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 直接试用

本机地址：[打开系统](http://127.0.0.1/)。当前局域网地址：[局域网入口](http://192.168.2.100/)（2026-11-06 实测；使用 HTTP，设备 IP 改变后需使用新地址）。80 端口由已安装的专用 systemd socket 转到前端 5173，后端继续走 `/api` 代理；5173 也可直接访问。

| 角色 | 演示账号 |
| --- | --- |
| 教师 | teacher |
| 学生 | student |
| 管理员 | admin |
| 权限测试用的第二学生 | student2 |

以上本机演示账户密码均为 **CassistDemo2026!**。这些账户仅由 demo profile 初始化，正式部署不应启用该 profile。

## 启动与停止

在项目根目录运行：

```sh
python3 scripts/local_app.py start
python3 scripts/local_app.py status
python3 scripts/local_app.py stop
```

需要重启时使用：

```sh
python3 scripts/local_app.py restart
```

启动器将数据库、后端和前端作为独立进程启动，避免开发 Agent 结束后页面失联。只管理 PID 与程序路径能够确认为本项目的进程，不替换其他服务。前端按用户要求监听 `0.0.0.0:5173`，允许局域网设备访问；`/api` 仍由前端代理到本机后端。后端 8080、评测 15050、演示数据库 33077 继续只绑定本机回环地址。

这个入口面向当前 Linux 开发机，依赖已安装的 Java、Node.js、Go、GCC/G++、mysqld 和可用的用户级隔离/资源控制。首次缺少构建产物时会构建后端并安装前端锁定依赖；不负责全局安装软件或修改操作系统设置。

代码修改后重新构建：

```sh
cd backend
./mvnw -B -ntp verify
```

前端在 frontend 目录执行 npm run build。正式部署步骤见 [部署说明](docs/implementation/A/DEPLOYMENT.md)。

## 建议体验顺序

1. 用 teacher 登录，在“Easy Parse 导入”粘贴题目材料或上传 TXT/DOCX/JSON。
2. 检查题干、样例、参考答案和警告，修正归属不明确的内容，确认导入为草稿。
3. 在题库补齐计分测试用例、参考答案和资源限制，把题目发布给班级。
4. 用 student 登录，进入作业，用网页编辑器提交 C / C++ 完整程序或指定函数。
5. 查看真实评测结果、测试点得分、提交历史；用教师账号查看成绩。
6. 点击 AI 排错指导；当前本机已接入 DeepSeek，使用题干、教师参考答案和当前代码生成有限指导，结果经后端检查后展示。

已准备“第一次练习：输入与输出”和“导入与评测体验”等合成演示数据。测试脚本还会创建标明用途的验收记录。

## 已实现的主要功能

- 角色与对象级权限、Session、CSRF、登录退出。
- 教师题库、参考答案、公开样例、隐藏测试点、题目版本及发布快照。
- 班级成员管理、作业发布、学生完成情况和最高分统计。
- 文本/TXT/DOCX 的 Easy Parse，原文定位、警告、预览修正、幂等保存草稿。
- Monaco 编辑器、标签页内草稿、作业集上一题/下一题导航、可点击目录及四色完成状态、真实提交/轮询、逐点评分与提交历史。
- go-judge 隔离编译与执行、有限队列、租约恢复、资源限制和错误分类；单独提示输出格式不符，仅缺最后换行时正常通过。
- AI 提示词、代码快照、输出约束、权限及记录接口；DeepSeek 真实异步分析、并发限制和错误反馈已接入。
- 管理员手动创建账号、编辑个人资料与头像；班级名单 Easy Parse 支持预览、修正、自动创建学生及统一初始密码，保留已有账号密码。
- 题库到题目的管理层级；作业支持全量、统一随机、每人随机，并固定每名学生的题目与版本。
- 作业可设置 AI / 讨论开关；点击已发布作业名称可编辑五项参数，日期显示到分钟。逐题聊天室显示作者头像，支持教师与管理员撤回、作业范围禁言及名单解禁。
- 长题干下编辑区保持可见；AI 按钮在代码旁，回复在左侧对话框；Tino 使用已有素材和按账号解锁的第四种风格。

## 当前环境与未完成范围

- 正式数据库选型仍为 **MariaDB**。本机已有 MySQL 二进制，因此演示使用独立 MySQL 实例验证业务及兼容 SQL，未替换或操作其他数据库。生产 MariaDB 迁移仍需实测。
- 当前构建宿主是 JDK 27，编译目标为 Java 21；Rocky 8、JDK 21 实际运行及正式服务器隔离配置尚待验收。
- 用户已提供 DeepSeek 凭据，本机启用外部 DeepSeek 服务；它不是学校私有部署。密钥只存于后端私有配置，使用与验收见 [DeepSeek 接入说明](docs/implementation/C/DEEPSEEK.md)。未指定真实模式的 demo profile 仍可显式模拟；真实模式凭据缺失不会回退模拟。
- PDF/OCR、多文件题、特殊裁判、查重及监考尚未纳入这个初版。
- 正式账号开通与学校身份接入仍需完善。

## 验证与交接

- [第二轮功能与验收](docs/ITERATION_02.md)
- [最新继续入口](docs/NEXT_SESSION.md)
- [用户、名单、题库与讨论管理](docs/implementation/A/USER_MANAGEMENT.md)
- [Tino 人格与称呼](docs/implementation/C/TINO.md)
- [作答布局与真实页面验证](docs/implementation/D/WORKSPACE_AI.md)
- [本机服务稳定性修复](docs/implementation/A/SERVICE_STABILITY.md)
- [PTA 题目蓝本总入口](docs/implementation/C/PTA_COLLECTION.md)
- [初版验收记录](docs/INITIAL_RELEASE.md)
- [非法操作与边界测试](docs/SECURITY_CHECKS.md)
- [真实 AI 接入与验收](docs/implementation/C/DEEPSEEK.md)
- [作业集切题功能](docs/implementation/D/NAVIGATION.md)
- [Agent 任务入口](docs/agents/README.md)
- [运行与调度记录](docs/agents/RUN_STATUS.md)
- [公共接口契约](docs/agents/CONTRACTS.md)

演示环境的可重复检查：

```sh
python3 scripts/smoke_demo.py
python3 scripts/security_demo.py
```

这些脚本会使用演示账号并创建有明确名称的测试题目/提交记录，不要直接用于正式教学数据。

普通回归脚本在真实 AI 模式下跳过模型推理。真实 AI 验收需显式运行 `python3 scripts/ai_live_check.py --run-live`，会进行四次真实 API 调用。
