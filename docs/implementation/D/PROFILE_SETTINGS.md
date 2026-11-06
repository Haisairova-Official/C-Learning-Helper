# D 设置页阶段交接

日期：2026-11-06。依据 [公共接口约定](../../agents/PROFILE_SETTINGS.md)。

## 已完成

- `/settings` 设置页与所有角色的导航入口；侧栏显示已保存昵称/头像，昵称为空回退学校姓名。
- 当前账号的昵称、个人简介、AI 回复风格保存；登录名与学校姓名只读。风格为 CONCISE / GUIDED / DETAILED，默认 GUIDED，页面示例明确只描述表达风格。
- PNG/JPEG 头像上传与移除，前端校验非空、类型、512 KiB 大小，服务器负责实际格式、4096 像素限制和缩放重编码。头像变更立即保存且不覆盖尚未保存的昵称/简介输入。
- 同源头像地址白名单，生产拒绝外部 URL 和 Blob；显式开发模拟可使用内存 Blob，资料按模拟账号隔离。
- 初始加载、失败重试、保存失败、上传错误、头像加载失败、未保存状态和重复点击控制。
- 账号资料请求使用会话代次和账号标识，登录后旧请求不能覆盖新会话资料。登录过期仅清身份/资料，保留代码草稿；显式退出仍只清当前账号草稿。

## 修改文件

`frontend/src/api/{types,index,real,mock}.ts`、`auth.ts`、`router.ts`、`App.vue`、`styles.css`；新增 `views/SettingsView.vue`、`lib/profile.ts`、`tests/profile.test.ts`、`tests/profileState.test.ts`。同步 `frontend/README.md` 与 D 任务文件。

未改 WorkspaceView、ProblemNavigation、problemProgress；保留原 progress 字段和方法。

## 验证

- `npm run build`：通过 Vue/TypeScript 类型检查与生产构建。仍有现有 Monaco 大块体积提示，设置页单独懒加载。
- `npm test`：5 文件、16 测试全部通过，包含 7 项新增检查：Unicode 长度、图片限制、头像 URL 限制、multipart/DELETE CSRF、迟到会话隔离、保存失败保留数据、过期与显式退出的草稿差异。
- 环境 Node 20.20.2，全局 npm 12 有引擎提示，但上述命令正常退出 0。
- 未重启前后端。D 没有可用浏览器连接，未宣称截图/真实头像上传已验收；主 Agent 统一重启后完成真实接口与浏览器验证。

## 下一步

主 Agent 接入服务器 AI 风格并联调设置页。D 按新追加的函数题约定接管题库编辑、学生题型/语言/模板展示与动态语言提交，保留主 Agent 已完成的题目导航和进度处理。

## 追加：班级与独立学号

2026-11-06 ProfileView 追加只读 classNames/studentNumber，账号资料区域可进入设置；V7 与演示学号说明见 [账号资料卡交接](ACCOUNT_CARD.md)。仅本机代码与定向测试完成，真实迁移与浏览器由主 Agent 统一验收。
