# 原生安装与启动交接

## 入口

首次安装见 [仓库首页](../../../README.md)。从项目根目录运行。Windows PowerShell 使用 `.\install.bat`、`.\run.bat`。重启电脑后直接运行启动入口。修改源码后先停止服务，再重新安装构建。

- Linux 使用发行版包管理器安装缺少的系统依赖，按需下载项目专用 JDK、Node 和 Go。构建 go-judge、网页入口、Spring Boot 应用和静态网页。普通用户无法获得完整评测隔离时，安装器尝试配置独立 systemd 评测服务。
- Windows x64 使用系统 PowerShell 引导项目私有 Python，下载按 SHA-256 固定的 JDK、Node、Go、MariaDB 和 WinLibs GCC。Visual C++ 运行库缺失时校验微软签名后请求安装。首次安装需要联网。依赖放在 `.local/windows`，数据库独立初始化，服务以当前用户运行。
- 网页默认监听全部 IPv4 接口的 80 端口。数据库、后端和评测在回环地址上运行。已占用的端口会报错，可用 `--port 9000` 更改对外端口。已有专用 systemd 80 端口转发的旧开发机继续走原转发。
- Windows 私有数据和日志在 `.local/windows/private`。Linux 数据库在 `backend/.local`，启动日志在 `.local`。安装后可配置后端 AI 环境变量；现有 Linux 私有配置沿用。初始密码、数据库和运行目录均由 Git 排除。

## 评测

Linux 使用 seccomp 及 CPU、内存和进程数控制器。原生 Windows 构建在 go-judge v1.14.0 的 Job Object 基础上增加每次执行独立的 AppContainer，使用空能力列表、限制继承的标准流句柄、在恢复线程前检查实际进程令牌。学生端沿用同一提交接口。Windows 安装时会分别编译、缓存、运行和清理一个 C 与一个 C++ 程序，检查真实输出。通过后记录评测器摘要，启动时核对该摘要。

Windows 适配尚需在真实主机验收。上游将原生 Windows 执行后端标为实验性支持。[go-judge 说明](https://github.com/criyle/go-judge/tree/v1.14.0) · [Microsoft AppContainer 文档](https://learn.microsoft.com/en-us/windows/win32/secauthz/implementing-an-appcontainer)

## 验证记录

| 场景 | 已有结果 |
| --- | --- |
| 本机 Linux 启动 | 首次启动、重复启动、80 和自选端口的首页、深链接及 CSRF API 返回成功 |
| Linux 环境检查 | 本机原生隔离评测中的 C、C++ 编译检查通过 |
| 空 MariaDB | 独立实例的 12 个迁移、4 个演示账号、管理员登录、重启后保留数据与凭据通过 |
| 后端回归 | Maven 记录 184 项，179 通过、5 项环境跳过 |
| Windows | 网页入口和适配后的评测器交叉编译为 x64 PE。已加入原生安装自检，尚无 Windows 真机安装、提交或安全验收结果 |
| Rocky 8 | 新机安装、systemd 隔离和目标服务器验收待实测 |
