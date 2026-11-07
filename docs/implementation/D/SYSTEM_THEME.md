# 跟随系统配色

日期：2026-11-07（Asia/Shanghai）。负责人：主 Agent，D 前端范围。状态：实现及本机验证完成。契约：v0.2，无接口或数据库变更。

## 行为与边界

- 启动时读取 `matchMedia('(prefers-color-scheme: dark)')`，监听偏好变化，实时应用到全站与 Monaco。检测缺失、抛错或未匹配深色时使用原有浅色青色配色。
- Windows 和 Linux 的本机主题由浏览器对接；网页不直接调用 Windows 原生主题 API 或读取 Linux 配置文件。浏览器有单独外观偏好时，以它实际向网页报告的偏好为准。
- 本次适配浅色／深色，强调色仍为项目青色，不提取壁纸颜色或任意系统自定义调色板。
- 不保存账号主题偏好、不修改电脑主题、不新增产品页面说明。原有浅色颜色逐项保留在变量回退值中；深色保留灰／红／黄／绿完成状态的含义。
- 直接切换 Monaco 主题，不重建编辑器或代码模型，保留当前输入、光标及撤销历史；Tino 使用既有素材。

依据：[MDN prefers-color-scheme](https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/At-rules/@media/prefers-color-scheme)、[Element Plus 深色模式](https://element-plus.org/en-US/guide/dark-mode.html)。

## 修改文件

- `frontend/src/theme.ts`：响应式主题、启动检测、现代／旧版事件监听和清理。
- `frontend/src/main.ts`、`frontend/index.html`：组件挂载前初始化、Element Plus 深色变量、原生控件配色提示。
- `frontend/src/theme.css`：深色调色板、组件库变量、表格／按钮／日期选中状态对比度。
- `frontend/src/styles.css` 及题目导航、聊天、导入、设置、题库、作业、作答组件样式：语义颜色变量及原浅色回退值。
- `frontend/src/components/CodeEditor.vue`：初始与实时 `vs` / `vs-dark` 主题切换。
- `frontend/src/tests/theme.test.ts`：8 项系统偏好与兼容性验证。

## 验证

| 场景 | 执行方式 | 实际结果 | 限制 |
| --- | --- | --- | --- |
| 初始浅／深色、双向切换、旧版监听、清理、缺失／抛错／未知偏好 | Vitest | 全部 42 项通过，主题新增 8 项 | 媒体查询为测试替身 |
| 类型检查与生产构建 | `npm run build` | 通过 | 既有 npm/Node 兼容及 Monaco 包体积提示仍存在 |
| 原有浅色颜色 | 将新变量恢复为 fallback，与 Git HEAD 样式逐项比对 | 所有原样式完全一致 | 不含新增深色文件及原生 color-scheme 行为 |
| 网页与编辑器同步 | 独立标签页模拟媒体查询 change | 深→浅→深正常，Monaco 同步 vs / vs-dark | 未改电脑主题 |
| 编辑内容及撤销 | 验证账号代码末尾输入注释，切换后撤销 | 输入保留，撤销恢复原有 89 字节骨架 | 未提交评测或调用模型 |
| 登录、作业、设置、AI 侧栏、聊天区域、四色题号 | 实际页面及计算样式 | 深色背景、文字、状态色及 Tino 正常 | 聊天为已有空房间，无发送行为 |
| 管理表格、作业编辑弹窗、日期选择器 | 教师验证账号打开既有作业 | 表格／弹窗／浮层均为深色；选中日期数字对比正常 | 取消弹窗，未保存参数 |
| 正式入口无模拟检测 | 新标签打开正常页面 | 当前浏览器偏好自动应用深色，卡片 rgb(25,35,47) | 未实测其他 Windows/Linux 实体设备 |

截图：`.local/system-theme-dark.png`（设置页）、`.local/system-theme-dialog.png`（日期浮层）。临时模拟入口已移出前端到 `.local/theme-preview.html`，不参与生产构建或 Git 提交。验证账号已退出，未修改用户资料、真实作答、系统主题、后端和服务监听配置。

## 下一步

实现无阻塞。Windows 和各 Linux 桌面环境中，系统设置→浏览器的实际通知行为仍需对应设备实测。浏览器不提供深色偏好时，保留原浅色。无需后端部署或数据库迁移。
