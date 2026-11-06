# 首道函数题：教师样例交接

## 当前状态

- Agent：A；日期：2026-11-06（Asia/Shanghai）；阶段：主 Agent 已完成双语言样例导入、发布与真实 AC 验证。
- 交付：[function-insert.json](../../../examples/pta/function-insert.json)。含两份独立编写的教师题目草稿，分别使用 C17、CPP17，字段符合 [函数题契约](../../agents/FUNCTION_PROBLEMS.md) 与 TeacherProblemView。
- 题意来自已采集的 [PTA 首题蓝本](../C/PTA_BLUEPRINT.md)。这里的题干措辞、函数接口、答案、裁判程序和数据均为独立改编，不是 PTA 原题全文、官方参考答案或隐藏用例的复制。
- A 初次交付只生成文件并核对期望输出，没有直接在宿主执行 C/C++。随后主 Agent 已通过 JSON 导入、作业发布和 go-judge 验证，C17、CPP17 各10个用例全部 AC；统一验收记录由主 Agent 汇总至 [第二轮验收](../../ITERATION_02.md)。
- 演示作业 ID：`9631ff1d-3af8-4a7a-b3bb-3c5dd4f5f54b`；首题 problem ID：`57e6f93d-d101-47d8-a081-0408851e0277`。此结论仅涵盖这里独立编写的两份语言版本，不代表其余29道函数题或190道编程题已入库。

## 函数与约束

两份题都要求向严格递减且无重复的序列插入整数。容量满或值已存在时返回失败，序列及长度不变；成功保持顺序。容量0–128，元素为有符号32位整数；仅提交函数体对应实现，不能提交 main 或重复结构体定义。

| 语言 | 公开接口 | 数据模型 |
| --- | --- | --- |
| C17 | `int insert_descending(OrderedArray *list, int value)` | 公开 struct 含 data[128]、size、capacity；成功1/失败0 |
| CPP17 | `bool insert_descending(std::vector<int>& values, int capacity, int value)` | 引用修改 vector，显式 capacity 表示逻辑最大长度；成功true/失败false |

CPP17 的逻辑容量不等于 vector::capacity()。公开 functionSignature 说明数据类型和返回约定；starterCode 只有待补全函数及默认失败返回。评测模板提供必要 include、C结构体、输入/输出和 main，各有且只有一个 `/*__STUDENT_CODE__*/` 标记。参考答案仅为函数实现，未拼接主程序。

## 计分与输入输出

每种语言均为20分、400 ms、64 MB、16 KiB代码上限，10个用例各2分，比较模式 EXACT。两个公开用例同时出现在 samples 和 PUBLIC 测试点，其余8个为 HIDDEN。

| 用例序号 | 覆盖内容 | 可见性 |
| --- | --- | --- |
| 1 | 空序列且容量足够 | HIDDEN |
| 2 | 零容量空序列，失败且仍为空 | HIDDEN |
| 3 | 首部插入 | HIDDEN |
| 4 | 中部插入 | PUBLIC |
| 5 | 尾部插入 | HIDDEN |
| 6 | 容量足够但值重复，保持内容和长度 | PUBLIC |
| 7 | 容量已满但值不重复，保持内容和长度 | HIDDEN |
| 8 | 全负整数与中部插入 | HIDDEN |
| 9 | 插入 INT_MAX，原序列含 INT_MIN | HIDDEN |
| 10 | 插入 INT_MIN，原序列含 INT_MAX | HIDDEN |

评测输入：第一行 capacity n，第二行n个元素（n=0时为空行），第三行插入值。输出第一行为 `OK 长度` 或 `FAIL 长度`，第二行为当前序列，无尾随空格；空序列的第二行仍需换行。全部期望输出在教师 JSON 中，不复制进前端或公开题目样例。

## 导入方式与私有边界

文件顶层是教师数据包 `{schemaVersion,title,source,problems}`，其中 problems 每一项可直接作为 `POST /api/teacher/problems` 的 JSON 请求体。A 初次交付时按逐项保存准备，后续主 Agent 已验证 JSON 导入流程；当前批量入口和真实验收以 [第二轮验收](../../ITERATION_02.md) 为准。顶层 source 是改编来源说明，不发给逐题保存接口。

以下步骤供复用样例时参考；当前首题的实际导入与 AC 已由主 Agent 完成。

1. 使用已应用 V6 的服务，确保 FUNCTION/CPP17 字段可持久保存。
2. 使用教师会话和当前 CSRF token，逐项发送 problems 中的草稿对象；记录返回 id/versionId。
3. 在隔离评测环境检查答案 AC、错误函数 WA、语法错误 CE；再按教师编辑接口将 state 更新为 PUBLISHED，并给演示班发布作业。
4. 学生 GET 返回题面、样例、functionSignature、starterCode、problemType 和 language。不可返回或将 JSON 本身公开挂载；referenceAnswer、judgeTemplate 与 HIDDEN testCases 仅留教师/评测内部。

## 实际验证与下一步

| 验证 | 实际结果 | 限制 |
| --- | --- | --- |
| UTF-8 JSON 解析、两个完整教师草稿、FUNCTION/C17/CPP17 字段 | 通过 | 文件级验证 |
| 每模板唯一占位、接口与模板字节上限、函数参考答案无 main | 通过 | 静态核对 |
| 每题10用例、分值总和20 | 通过 | 静态核对 |
| 10份手写预期输出与独立 set/sort 规则核对 | 全部一致 | 可信 Python oracle；没有执行参考 C/C++ 代码 |
| JSON 导入、发布、真实 go-judge 编译与 AC | 主 Agent 已验证两语言各10用例全部 AC | 仅上述独立改编首题的双语言版本 |
| WA/CE、学生字段隔离等其他集成 | 以主 Agent 第二轮验收为准 | A 未重新执行或据 AC 结果推定这些场景 |

后续扩充 PTA 目录时继续独立编写答案及测试，保留教师私有资料边界，不将公开裁判程序当作官方答案。已完成的首题验收与其余题目的蓝本采集分别记录。
