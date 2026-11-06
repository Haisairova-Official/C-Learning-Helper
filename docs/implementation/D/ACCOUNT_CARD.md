# 左下角账号资料：班级、学号与设置入口

日期：2026-11-06。用户新增要求：昵称下显示“班级 · 学号”；点击头像昵称区域进入设置，退出按钮独立。

## 实现与数据来源

- ProfileView 追加只读 `classNames: string[]`、`studentNumber: string | null`。当前学生班级通过 `class_member.student_id = CurrentActor.id` 关联 classroom 获取，按名称/ID 排序；教师/管理员返回空班级与空学号，侧栏保留角色及登录名。
- 原 schema 没有独立学号。主 Agent 明确授权新增 V7：`app_user.student_number VARCHAR(32) NULL`。不从 username、用户 ID 或昵称推导学号。
- ProfileService 只读该独立字段；TeachingService.members 仅将原 username 错误映射替换为 student_number，其余逻辑不变。
- demo profile 初始化为 `demo-student` / `demo-student2` 补入 `20260001` / `20260002`，仅在原学号为空时执行。这两个值是明确的演示数据，并非用户真实学校学号。正式账号未知时保持 null。
- 学生侧栏显示首班及学号；多班时显示首个与总数，悬停显示完整班级列表。未知字段明确显示“未加入班级 / 学号未录入”，不会把登录名冒充学号。
- 整块头像昵称是 `/settings` RouterLink；退出按钮为链接外的兄弟元素并阻止点击冒泡。窄屏头像仍可进入设置。
- 不修改学校姓名、角色或登录名。未执行任何现有昵称/头像更新；用户已保存的 Test User 和头像保持原数据。

## 修改文件

后端 `identity/ProfileView.java`、`ProfileService.java`、`DemoDataInitializer.java`，`problem/TeachingService.java` 仅成员查询 SELECT 列；V7 迁移和三项测试类。

前端 `api/types.ts`、`api/mock.ts`、`auth.ts`、`lib/profile.ts`、`App.vue`、`styles.css` 及相关测试。

## 验证与交接

- ProfileServiceTest 10 项、ProfileControllerSecurityTest 6 项、DemoStudentNumberTest 1 项，共 17 项通过。验证当前身份范围、真实独立学号、无学号不回退用户名、昵称头像读取保留、演示更新的空值条件与只更新学号。
- 前端 25 项测试通过，覆盖多班摘要/完整列表、缺失占位与教师显示；类型检查与生产构建通过。
- 未重启服务、未自行应用 V7。Maven 已结束并释放 target；由主 Agent 统一 verify/restart 后实际验收班级与学号显示、设置点击、退出行为及既有头像昵称保留。
