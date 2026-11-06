# 第二轮真实 API 验证结果

日期：2026-11-06；本地演示服务 + 私有 SQL 数据库 + go-judge v1.14.0。

| 场景 | 实际结果 | 分数 | 观察到的状态 |
| --- | --- | --- | --- |
| correct | ACCEPTED | 100.0 | QUEUED → COMPILING → FINISHED |
| wrong | WRONG_ANSWER | 0.0 | QUEUED → FINISHED |
| compile-error | COMPILATION_ERROR | 0.0 | QUEUED → FINISHED |
| runtime-error | RUNTIME_ERROR | 0.0 | QUEUED → RUNNING → FINISHED |
| time-limit | TIME_LIMIT_EXCEEDED | 0.0 | QUEUED → COMPILING → RUNNING → FINISHED |
| output-limit | OUTPUT_LIMIT_EXCEEDED | 0.0 | QUEUED → RUNNING → FINISHED |
| memory-limit | MEMORY_LIMIT_EXCEEDED | 0.0 | QUEUED → COMPILING → RUNNING → FINISHED |

已通过：相同幂等键重放返回同一记录、不同内容返回 409、其他学生查询返回 403、授权教师可查看、历史记录查询及列表不回传代码、逐测试点响应没有隐藏输入输出。

这些结果来自真实 HTTP 与受限 C 执行，不是模拟响应。目标 Rocky 8 尚未验收。
