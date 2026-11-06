# 输出格式提示与末尾换行容错

日期：2026-11-06。负责人：主 Agent，B/D 范围。用户要求格式不符时有提示，只有最后缺一个换行符时不判错。

## 已实现行为

| 模式与输出差异 | 结果 |
| --- | --- |
| EXACT，只差单个末尾 LF 或 CRLF | ACCEPTED，正常给分 |
| EXACT，CRLF 与 LF 行尾差异 | ACCEPTED |
| EXACT，额外空格、制表符、换行位置或空行，ASCII 分词完全一致 | PRESENTATION_ERROR，该测试点零分 |
| EXACT，数值/文本不同、额外标签、缺失或多出 token | WRONG_ANSWER |
| TOKENS，仅 ASCII 空白差异 | 沿用原规则，ACCEPTED |

不做数值强制转换；`1` 与 `1.0` 不相等。精确模式只容忍一个末尾行终止符，不通过 trim 忽略所有尾部空白。

总结果：全部通过为 ACCEPTED；有通过得分且存在失败为 PARTIAL_ACCEPTED；零分且只有格式失败为 PRESENTATION_ERROR；零分且还有其他内容/运行失败则保留对应失败。历史提交不自动重判。

前端显示“输出格式不符”，并提示核对空格、换行及多余空行，说明仅缺末尾换行不影响通过。隐藏测试的输入、预期输出、实际输出继续不进入学生响应。

## 修改文件

- `OutputComparator.java`：规范化与三种比较结果。
- `JudgeEngine.java`、`Verdict.java`：持久结果与混合结果汇总；只 ACCEPTED 得分。
- `JudgeCoreTest.java`：末尾换行、其他空白、内容差异、部分得分及隐藏内容保护。
- `frontend/src/api/types.ts`、`lib/format.ts`、`views/WorkspaceView.vue`：状态与通用检查提示。
- `scripts/format_check.py`：复用独立验证班级，另建格式验证题库和作业，所有学生代码经实际 go-judge 编译执行。

数据库已有40字符结果字段，新增状态无需迁移；契约已同步。

## 验证进度

- 统一后端 verify：154条测试记录、149通过、5环境跳过，零失败。
- 前端34项测试及构建通过；之后仅移除重复CSS，待与作业编辑窗口一起最终构建。
- 真实隔离编译/HTTP与页面检查待统一部署，完成后在文末追加，不把比较器测试替代实际编译。

## 主 Agent 实际集成验收

统一部署后 `scripts/format_check.py` 的11组真实提交全部通过：C正常/缺末尾换行AC，额外空格/空行/换行位置PE，数值错误/额外文字WA，混合通过与格式失败5/10分；C++缺末尾换行AC、多空格PE；TOKENS空白宽容AC。所有编译执行经现有go-judge；HTTP重新查询保留结果，测试点响应严格只有原5个公开字段。

独立学生浏览器另提交含多余空格的C程序，实际显示“输出格式不符”、0/10分、两个格式失败测试点以及核对空白和末尾换行容错提示。截图 `.local/output-format.png`。没有重判旧提交。
