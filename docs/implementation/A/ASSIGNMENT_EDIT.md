# 已发布作业参数修改

日期：2026-11-06（Asia/Shanghai）。后端：management_qa；前端：frontend_finish；统一构建部署及公共契约：主 Agent。

## 用户确认

作业发布页点击作业名称可修改参数。用户确认只修改名称、开放时间、截止时间、AI与聊天室开关；题目、抽题方式和数量固定。

## 实现与接口

`PUT /api/teacher/assignments/{id}`，JSON：

```json
{
  "title": "数组练习",
  "opensAt": "2026-11-06T01:00:00Z",
  "closesAt": "2026-11-13T15:59:00Z",
  "aiEnabled": true,
  "chatEnabled": false
}
```

成功200返回现有AssignmentView；Session与CSRF规则保留。仅该作业的拥有教师或ADMIN可编辑，学生和其他教师403，资源不存在404。

名称去首尾空白后非空且不超过200个Unicode code point；两个时间与两个Boolean必填。UTC存储时间按数据库DATETIME(6)保留微秒，校验截止严格晚于开放；数据库可表达范围外的时间或截到微秒后相等的时间返回400。错误内容不包含SQL或私有题目数据。

事务仅更新assignment表的title、opens_at、closes_at、ai_enabled、chat_enabled五列；不修改题目版本、抽题绑定、班级、成绩、历史消息或禁言。没有数据库迁移。并发保存按数据库事务串行写入，后提交的值覆盖先前值，当前未增加版本号冲突提示。

修改后，学生后续读取和请求按新参数执行。关闭讨论仍保留教师历史管理入口；关闭AI阻止后续受理，但不取消已受理的模型请求；变更截止时间不取消已受理的评测。时间限制沿用现有受理规则。

## 修改文件

- `TeachingController.java`：新增PUT路由，使用当前受信任身份。
- `TeachingService.java`：AssignmentUpdate输入与事务更新方法。
- `AssignmentUpdateServiceTest.java`：实际AccessPolicy配合模拟JDBC，验证属主/admin、拒绝分支和参数边界。
- `AssignmentUpdateControllerSecurityTest.java`：真实SecurityFilterChain验证401/403、CSRF、请求解析及200响应。
- `scripts/assignment_edit_check.py`：复用原独立合成资料，验证FULL与每人随机作业，finally恢复参数及临时禁言。

## 验证与当前状态（已部署通过）

- 已执行 `./mvnw -q -Dtest=AssignmentUpdateServiceTest,AssignmentUpdateControllerSecurityTest test`：22项通过，0失败/0错误/0跳过（服务18项、控制器4项）。这些为单元/控制器测试，不是数据库集成。
- 主 Agent 已统一构建部署；本Agent未打包或重启。
- 已执行 `CASSIST_BASE_URL=http://192.168.2.100/api python3 scripts/assignment_edit_check.py`：4组真实HTTP场景全部通过，见下表。
- 脚本不新建账号/作业，不执行已受理程序或付费AI；只在明确关闭AI时检查拒绝。临时禁言仅合成s2账号，已有禁言不删除；每份作业参数在finally恢复，并核对原抽题、班级、成绩和消息。
- 正式MariaDB、Rocky Linux 8与生产JDK21不因本次本机测试而视为验收。

| 场景 | 实际结果 |
| --- | --- |
| 匿名、学生、跨教师、缺少CSRF、不存在作业 | 分别401、403、403、403、404；无参数修改 |
| 空白/超长名称、时间缺失/无效/相等/逆序、缺少开关 | 9种请求均400，完整作业响应与修改前一致 |
| FULL作业的教师和管理员修改 | 返回200，名称与微秒时间正确，新开关反映在学生后续请求；原题目版本/班级/成绩/历史消息/禁言不变 |
| PER_STUDENT_RANDOM作业修改 | 返回200，原学生抽题、题序、总分及已有非零成绩不变；原题目版本/班级/消息/禁言不变 |
| 关闭AI和讨论、修改截止与开放时间 | AI请求403/AI_DISABLED，讨论读写403；截止已过时提交409/ASSIGNMENT_CLOSED，尚未开放时题目403/ASSIGNMENT_NOT_OPEN；没有受理推理或评测 |
| 测试恢复 | 两份合成作业恢复原5字段；完整教师/学生视图与成绩恢复核对一致；临时s2禁言删除后与原列表一致 |

没有创建新的账号或作业，没有修改真实用户资料，未读取或输出密钥。本轮无剩余后端部署项。日期显示的前端中文格式由frontend_finish维护，不改变UTC接口契约。

主 Agent 更新CONTRACTS/DECISIONS，覆盖此前“没有发布后修改开关接口”的历史限制；本专项未直接修改这些公共文件。
