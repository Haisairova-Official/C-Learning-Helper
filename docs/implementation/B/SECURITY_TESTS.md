# 受控非法操作测试

日期：2026-11-06。通过真实 /api/submissions 执行，使用已验证的 go-judge v1.14.0、cgroup v2 与 seccomp；没有在宿主直接执行学生程序。

## 方法

新建 backend/.local 下独享随机目录及唯一 canary 文件；所有文件攻击仅针对该文件，测试后清理。每段程序在确认限制生效时才输出题目要求的正确结果，所以本次 ACCEPTED 表示限制检查已执行并通过；普通答案错误不算隔离成功。逐次独立检查宿主 canary 存在且内容完全相同，并检查专用进程名没有残留。

| 测试 | 实际结果 | canary 完整 | 残留标记进程 |
| --- | --- | --- | --- |
| file-read-write-delete | ACCEPTED（限制检查通过） | True | 0 |
| system-canary-write | ACCEPTED（限制检查通过） | True | 0 |
| bounded-process-create | ACCEPTED（限制检查通过） | True | 0 |
| local-network-connect | ACCEPTED（限制检查通过） | True | 0 |

## 已验证范围与限制

- 直接读取、写入、删除唯一宿主 canary 均未成功；宿主文件每轮保持原样。
- system 尝试通过 shell 写入同一 canary 没有成功，宿主文件保持原样。此结果由运行进程数限制与文件隔离共同作用，不能解释为所有 system 调用均被禁用。
- 最多进行 32 次非递归 fork 尝试，若产生子进程会立即退出并由父进程回收；实际 32 次均因 EAGAIN/EPERM 失败，符合运行 procLimit=1，结束后无专用标记进程。
- 网络仅尝试连接本机临时监听器一个端口；沙箱连接失败，宿主监听器接收数为 0，没有对外扫描。
- 复用 PHASE2_API_RESULTS.md 已有 CPU、内存与输出超限证据，本轮没有重复耗尽测试。
- 本轮只验证这些明确场景，不能据此宣称所有沙箱逃逸、系统调用或侧信道风险均已排除。目标 Rocky 8 仍需独立验收。
