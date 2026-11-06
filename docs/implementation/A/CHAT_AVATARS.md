# 题目讨论消息头像

日期：2026-11-06（Asia/Shanghai）。后端management_qa；前端frontend_finish；统一构建部署和公共契约由主 Agent负责。

## 实现

Message追加 `authorAvatarUrl: string | null`。无头像时null；有头像时为同源路径：

```text
/api/assignments/{assignmentId}/problems/{problemId}/chat/participants/{authorId}/avatar?v={UTC秒}-{纳秒}
```

版本来自作者的profile_updated_at。同作者在同房间的消息共用同一URL，不随消息ID变化；头像更新后随版本更新。消息查询只读取是否存在头像和更新时间，不把图片字节、简介或其他资料放进消息JSON。

新增GET上述头像路径，返回现有规范化PNG：

- 先执行与当前题目聊天室相同的authorize，包含作业成员/教师归属、随机抽题与学生聊天室开关检查。
- 再要求authorId确实在当前assignment及固定problem_version发过消息；发过消息但后来撤回仍算参与者。
- 作者不属于该房间、账号不存在或没有头像时均404。不能通过猜测用户ID读取任意账号头像。
- 仅返回PNG，`Cache-Control: private, no-store`、`Vary: Cookie`、`X-Content-Type-Options: nosniff`。
- 沿用管理历史的行为：关闭讨论后学生403，作业管理教师/admin仍可读取已有消息的头像。
- 没有迁移；本人 `/api/profile/avatar` 及管理员头像接口的权限未改变。

## 文件与验证

- `ProblemChatController.java`：Message字段、共享版本URL与受限PNG路由。
- `ChatAvatarControllerSecurityTest.java`：真实SecurityFilterChain，模拟TeachingService/JDBC，8项通过、0失败/0错误/0跳过。覆盖匿名、非成员、其他教师、未抽中题、关闭讨论、非参与者/无头像、私有缓存头和共享/更新/空头像URL；这些测试不代表真实数据库验证。
- `scripts/chat_avatar_check.py`：复用原合成聊天室和已有消息作者，临时更新头像后恢复；不发消息、不创建用户/作业、不调用AI或评测。已通过部署后的3组真实HTTP验证。

定向命令：`./mvnw -q -Dtest=ChatAvatarControllerSecurityTest test`。测试初次运行修正了测试类通配符导入冲突及Spring初始化JDBC模拟对象引发的计数误差，最终8项全部通过；业务校验未放宽。

主 Agent已统一构建部署，完整verify记录184项、179通过、5项环境跳过、0失败。本Agent随后执行 `CASSIST_BASE_URL=http://192.168.2.100/api python3 scripts/chat_avatar_check.py`，3组全部通过：

| 场景 | 实际结果 |
| --- | --- |
| 已有消息作者上传头像、同作者URL、再次更新头像 | 同作者同房间只有一个地址；更新后版本地址变化，消息原内容未改变 |
| 学生/教师/admin读取合法头像 | 200规范化PNG，private/no-store、Vary Cookie、nosniff均验证 |
| 匿名、非成员学生、非属主教师 | 分别401、403、403 |
| 未抽中题、作者未在该题发消息、未知作者 | 均404；未在当前题发消息的测试使用确实有头像的作者，避免把无图误当权限验证 |
| 关闭讨论 | 学生403/CHAT_DISABLED；该作业教师仍可读取历史消息头像 |
| 删除头像 | Message.authorAvatarUrl为null，直接读取404 |
| 恢复检查 | 原头像、其他资料、原作业开关与全部既有消息复核一致；未发送任何消息 |

测试头像仅涉及明确识别的合成作者：原来有头像时在内存保留规范化PNG并上传恢复，原来无头像时删除临时图；头像URL版本时间允许变化，图像字节与其他资料均恢复。代码中不保存真实密钥或真实用户图片。

没有剩余后端部署项。浏览器视觉验收由主 Agent执行并单独记录；本Agent不将HTTP通过称为浏览器验收。本Agent未重启服务或覆盖运行包，正式MariaDB/Rocky8验收仍不在本轮范围。
