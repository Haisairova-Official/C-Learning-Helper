# C-Learning Helper · C 语言教学辅助系统

当前为可在本机运行的初步版本。已接通登录、教师题库与作业、Easy Parse 导入、学生在线编程、真实隔离评测、提交历史和成绩查询。

## 安装与启动

**Windows 10/11 x64**　首次双击根目录的 `install.bat`。看到 C 和 C++ 自检通过后，双击 `run.bat`，打开 [本机课堂](http://localhost/)。安装器使用 Windows 原生程序和 AppContainer，无需 WSL。

**Linux**　在项目根目录执行两条命令。安装系统依赖时可能要求输入 sudo 密码。

```sh
./install.sh
./run
```

两个平台默认监听 `0.0.0.0:80`。从局域网访问时，在浏览器输入 `http://服务器的局域网IP/`。网页通过同源 `/api` 代理连接后端。

| 操作 | Windows 命令提示符 | Linux |
| --- | --- | --- |
| 改用 9000 端口 | `run.bat --port 9000` | `./run --port 9000` |
| 查看运行状态 | `run.bat status` | `./run status` |
| 停止 | `run.bat stop` | `./run stop` |
| 检查安装 | `install.bat --check` | `./install.sh --check` |

新安装的演示账号为 `admin`、`teacher`、`student` 和 `student2`，各有独立随机密码。Windows 的初始密码文件位于 `.local/windows/private/instance/initial-accounts.env`，Linux 位于 `backend/.local/initial-accounts.env`。已有数据库沿用原密码。

Linux 已在开发机验证网页、接口和隔离编译。Windows 网页入口与评测器已交叉编译，Windows 整机安装及课堂负载仍待实测。平台依赖、数据位置与自检说明见 [安装交接](docs/implementation/A/INSTALL_RUN.md)。正式服务器配置见 [部署说明](docs/implementation/A/DEPLOYMENT.md)。

## 许可证

本项目原创代码采用 **GNU Affero General Public License v3.0 only**（`AGPL-3.0-only`）。[完整条款](LICENSE) · [第三方材料说明](THIRD_PARTY_NOTICES.md)

## 建议体验顺序

1. 用 teacher 登录，在“Easy Parse 导入”粘贴题目材料或上传 TXT/DOCX/JSON。
2. 检查题干、样例、参考答案和警告，修正归属不明确的内容，确认导入为草稿。
3. 在题库补齐计分测试用例、参考答案和资源限制，把题目发布给班级。
4. 用 student 登录，进入作业，用网页编辑器提交 C / C++ 完整程序或指定函数。
5. 查看真实评测结果、测试点得分、提交历史；用教师账号查看成绩。
6. 配置 AI 后点击排错指导，使用题干、教师参考答案和当前代码生成有限指导，结果经后端检查后展示。

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

- 正式数据库选型仍为 **MariaDB**。本机已有 MySQL 二进制，因此演示使用独立 MySQL 实例验证业务及兼容 SQL，未替换或操作其他数据库。已另用干净 MariaDB 实例验证 12 个迁移、登录和重启后的数据保留。
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
