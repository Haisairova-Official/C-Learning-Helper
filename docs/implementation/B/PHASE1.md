# B 第一轮交接：评测核心与协议适配

历史阶段记录；当前状态见 [PHASE2.md](PHASE2.md)。

日期：2026-11-06。状态：本轮可审阅交付完成，完整业务评测流程仍待后续集成。公共契约：v0.1。

## 已实现

- judge/core：EXACT 与 TOKENS 比较、BigDecimal 逐点计分、不可变题目快照、资源限额校验、总体 verdict 汇总。
- JudgeEngine：编译一次、逐用例运行、判断实际输出、区分基础设施错误、过滤编译诊断中的非学生文件信息、清理缓存产物。
- judge/protocol：固定 C17 argv、源码与标准输入 copyIn、编译/运行各自资源预算、可选二进制缓存输出与删除接口。源代码不会拼成 shell 命令。
- judge/http：JDK HttpClient + Jackson 3；本机来源校验、Bearer 认证、禁止跟随重定向、整个响应的截止时间、16 MiB 响应上限、错误信息不暴露响应体。
- JudgeProblemMapper：接入 A 的服务器专用 ProblemSnapshot，保留用例顺序、转换限制单位、检查题目代码长度，不把参考答案带入评测核心。
- deploy：原生 systemd 模板、独立 rootfs 挂载模板、认证配置示例、启动前检查和目标机验收步骤。

## 验证证据

环境：Arch Linux，内核 7.2.14-arch1-1，UID 1000，cgroup v2；JDK 27 使用 Java release 21 编译，宿主 GCC 16.2.1。未安装 go-judge，未访问实际 Rocky 8 服务器。Maven Wrapper 使用 A 创建的版本。

执行命令（backend 目录）：

```sh
./mvnw -q -Dtest=JudgeCoreTest,HttpGoJudgeTransportTest,JudgeProblemMapperTest test
```

结果：21 项测试，失败 0、错误 0、跳过 0。

| 测试文件 | 项数 | 核心覆盖 |
| --- | --- | --- |
| JudgeCoreTest | 13 | 换行、ASCII 空白、数字不隐式转换、正常退出但答案错误、部分分、编译失败、各执行异常映射、系统错误不计分、缓存清理、非法参数 |
| HttpGoJudgeTransportTest | 6 | 本机模拟 HTTP 服务、认证及 JSON、上游错误隐藏、多结果拒绝、响应头后仍受总超时限制、超大响应限制、配置校验 |
| JudgeProblemMapperTest | 2 | A 的授权快照转换、私有数据不进入诊断字符串、按 UTF-8 字节检查代码长度 |

根目录执行 sh -n deploy/judge/preflight.sh，通过语法检查。

没有真实编译/运行学生 C 代码，没有执行耗尽资源测试，没有修改宿主 cgroup、SELinux、systemd 或安装服务。上述测试使用模拟 transport 或本机临时 HTTP 服务，不能作为沙箱隔离验收证据。

## 跨 Agent 资料

- [A 的表结构和接入请求](INTEGRATION_REQUESTS.md)：submission、judge_task、case_result，以及领取租约 token、幂等和事务边界。
- [D 的 API 与状态示例](API_EXAMPLES.md)：提交 202、部分正确、编译失败和系统错误。
- [部署草案](../../../deploy/judge/README.md)：目标机待验证事项与官方依据。

## 后续 TODO

1. A 合并评测相关数据库迁移，完善真实 ProblemAccessPort 与身份授权服务。
2. B 实现提交与查询 Controller、持久任务队列、有限工作线程、租约续期/恢复、结果条件落库，以及与 A 的最高分统计接入。
3. 真实提交请求先检查作业访问/截止时间并持久化快照，后台执行再使用 loadForEvaluation；禁止客户端直接传入评测私有快照。
4. 将 HttpGoJudgeTransport 与 JudgeEngine 注册为受配置控制的服务，在 go-judge 尚未配置或隔离验收未完成时保持真实评测不可用，不自动回退宿主执行。
5. 在目标 Rocky 8 固定并验证 go-judge、GCC、rootfs 和资源限制；随后完成正确、错误、死循环、崩溃等真实受控用例。
6. 验证数据库并发、服务中断、重复请求、跨用户越权和完整教师/学生流程。

## 已知限制

- 尚无真实 /api/submissions 端点、数据库队列、历史查询或持久成绩；D 应继续使用明确标识的模拟适配。
- 真实 go-judge 版本和 GCC 尚未锁定；协议按官方当前文档及源码编写，必须在固定版本上验证。
- 第一版仅支持文本输出，非 UTF-8 二进制输出不支持。
- 编译资源超限映射为 COMPILATION_ERROR 并给出通用提示；运行阶段则有独立超限 verdict。
- 清理缓存失败会将本次评测标为 SYSTEM_ERROR。无法取得缓存 ID 的超时请求依赖服务器 TTL 回收，仍需在目标机检查实际行为。
- 执行引擎同步完成一次提交，最多 100 个用例；并发与总任务租约由下一阶段队列控制。
- 真实 rootfs 的路径、动态加载器、UID/GID、SELinux 和 cgroup 权限尚未验证，模板不是可直接宣称已安全上线的部署结果。
