# 多 Agent 开发任务入口

更新日期：2026-11-07

当前阶段：管理扩展、Tino、输出格式提示、作业参数编辑与聊天室头像已完成本机验证。最新状态见 [RUN_STATUS.md](RUN_STATUS.md)，下次入口 [NEXT_SESSION.md](../NEXT_SESSION.md)，本轮验收 [ITERATION_02.md](../ITERATION_02.md)。初步成品已完成本机联调。A/B/D 已完成并交接，主 Agent 已完成 C 的第二轮接入、整体联调、非法操作测试及随后提供的 DeepSeek 真实 AI 接入。学校私有部署、正式 MariaDB 与 Rocky 8 验收仍待对应环境。试用方式见 [项目首页](../../README.md)，结果见 [初版验收](../INITIAL_RELEASE.md)；调度见 [RUN_STATUS.md](RUN_STATUS.md)，各模块遗留项见对应交接文件。

GitHub仓库已建立：[C-Learning-Helper](https://github.com/Haisairova-Official/C-Learning-Helper)（私有，main，AGPLv3）。首次入库与敏感信息排除记录见 [GitHub交接](../implementation/A/GITHUB_REPOSITORY.md)。

## 分工

| Agent | 工作范围 | TODO 与交接文件 |
| --- | --- | --- |
| A | 基础权限、班级、题库作业、数据库、公共契约 | [AGENT_A.md](AGENT_A.md) |
| B | 编译评测、任务恢复、go-judge、Rocky 8 评测部署 | [AGENT_B.md](AGENT_B.md) |
| C | Easy Parse 批量导入、学校 AI 排错指导 | [AGENT_C.md](AGENT_C.md) |
| D | 学生端、教师端、管理员端网页及前端联调 | [AGENT_D.md](AGENT_D.md) |

## 每个 Agent 都需要阅读

- [PROJECT_BRIEF.md](PROJECT_BRIEF.md)：用户需求、技术选择、第一版范围和待确认项。
- [CONTRACTS.md](CONTRACTS.md)：数据和接口约定草案；A 维护，其他 Agent 提出变更请求。
- [DECISIONS.md](DECISIONS.md)：已定方向与待定方案。
- [INTEGRATION.md](INTEGRATION.md)：依赖顺序、集成验收、目标服务器验证。
- [HANDOFF_TEMPLATE.md](HANDOFF_TEMPLATE.md)：统一交接格式。

## 分发给 Agent 的任务文本

将下面这段中的字母替换为 A、B、C 或 D：

> 你负责本项目 Agent X 的工作。先阅读 docs/agents/README.md，若本地有 AGENTS.md 也一并阅读，再阅读公共需求、接口契约、决策记录及 docs/agents/AGENT_X.md。按 TODO 完成职责内的实现与验证，并持续更新自己的 Markdown 进度和交接。公共契约尚未冻结的部分使用适配接口或模拟数据推进，将依赖和建议写入交接文件。完成时给出实际改动、测试结果及未完成事项。

各 Agent 必须能访问同一份项目文件，或从同一份契约基线开始。如果使用独立工作副本，合并代码时也应同步交接文档。

## 并行顺序

1. A 优先初始化工程、确定兼容版本、审查契约及建立数据库基线。
2. 同期 B 验证隔离执行能力；C 制作导入样例和 AI 模拟适配器；D 根据契约制作页面与模拟数据。
3. A 发布契约基线后，B、C、D 接入真实公共接口。
4. 四条工作线共同完成“导入题目—发布作业—提交评测—AI 指导—查看成绩”的联调。

当前聊天或指定主 Agent 负责协调与最终集成；A 负责公共技术基线。第一轮开发保持四条工作线即可。

## Markdown 的使用方式

- 需求与接口放在公共文档中，任务状态放在各 Agent 文件中，不仅留在聊天里。
- 交接要附具体路径、字段或复现步骤，不用“已经处理”“应该可用”代替证据。
- 公共接口变更先写入自己的“跨 Agent 请求”，由 A 更新 CONTRACTS.md 和 DECISIONS.md 后再通知相关 Agent。
- 联调缺陷集中记录到 INTEGRATION.md，由集成负责人维护；各模块负责人修复后提交验证结果。
- 收到影响本模块的变更时，在自己的交接中注明已采用的契约版本。
