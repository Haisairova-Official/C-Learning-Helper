# Rocky Linux 8 评测服务部署草案

状态：正式 Rocky 8 配置仍为待验收模板。本地 Arch Linux 已通过 start_local.py 启动 go-judge v1.14.0 并完成真实评测和受控隔离验证；详见 docs/implementation/B/PHASE2.md。

## 目录与运行方式

- /usr/local/libexec/go-judge：经校验并固定版本的官方二进制。
- /usr/local/libexec/c-assist-judge-preflight.sh：本目录 preflight.sh。
- /etc/c-assist-judge/mount.yaml：独立编译 rootfs 挂载配置。
- /etc/c-assist-judge/judge.env：仅 root 可读的认证配置，ES_AUTH_TOKEN 不得为空。
- /var/lib/c-assist-judge/rootfs：单独准备的 Rocky 8 GCC/标准库环境，不放业务或数据库文件。
- systemd 单元使用 deploy/systemd/c-assist-judge.service，由管理员在目标机审核安装。

rootfs 需要提供固定版本的 GCC、G++、汇编器、链接器、C/C++ 标准库（含 libstdc++）、开发头文件和动态加载器。可以在单独的 Rocky 8 安装根目录中通过发行版包管理器准备，并记录包版本及校验信息；不要把宿主 /usr、/etc、/home 或数据库目录直接替换到模板中。服务管理员需要核对 /bin、/lib 等符号链接及 uid/gid 20000 不与宿主业务用户重合。

## 必须完成的目标机检查

1. 核对内核、cgroup 挂载及服务创建资源组的权限；服务带 no-fallback，资源组创建失败时停止启动。
2. 核对 systemd 使用的绝对路径、SELinux 规则和 rootfs 读取权限，按具体拒绝原因配置必要规则。
3. 确认只监听 127.0.0.1:5050，后端使用 Bearer token；学生网页不能直接访问评测服务。
4. 在隔离环境内分别运行正常 C17、C++17 程序题与函数题样例，验证编译、运行、stdout、产物缓存和删除；C++17 要覆盖标准库链接。
5. 受控验证时间、墙钟、内存、进程数、输出、临时文件限制；禁止用未经隔离的宿主进程模拟这些攻击场景。
6. 验证沙箱无法读取业务配置/数据库文件、无法访问宿主或校园网、无法看到其他提交的文件。
7. 验证 go-judge 不可用、任务中断与重启后的恢复；记录实测并发与内存峰值再决定 parallelism。

未完成这些验证前，不能将此模板视为安全验收通过。启动前检查脚本只检查部分静态条件，不替代隔离测试。

## 实现选择

编译命令固定为 /usr/bin/gcc -std=c17 或 /usr/bin/g++ -std=c++17 的参数数组，指定 O2、常见警告和无颜色诊断；语言来自已授权题目版本，源码通过 copyIn 传入，不进入 shell。运行仅执行缓存产物 main。函数题由后端将原学生代码插入私有模板的唯一占位处，并加入 #line 定位；原代码单独保存，教师模板不进入学生响应，函数编译反馈只保留数字行列与通用说明。

编译预算为 CPU 10 秒、墙钟 20 秒、内存 512 MiB、16 个进程；运行预算来自题目快照并限制在系统支持范围，默认最多 1 个进程及 64 KiB 标准输出/错误。两个输出收集器分别限额，临时目录分别有容量上限。全服务并发另由 parallelism 限制。

临时二进制使用可选缓存输出 main?，编译失败时不把“没有产物”误认为文件系统故障；完成或错误后主动删除已知缓存 ID。请求超时但未取得 ID 时，由服务端 file-timeout 清理残留。删除失败会记录系统错误。

当前实现只支持文本输出比较；包含非 UTF-8 二进制输出的题目不在第一版支持范围。

## 凭据与日志

认证值通过受限的 EnvironmentFile 传入；不得出现在命令行示例、交接或学生响应里。模板使用 silent 以减少上游信息日志；部署时还需要检查所固定版本的启动日志没有输出 token。上游当前实现的部分信息日志会记录认证参数，因此必须进行该项核查。

## 官方依据

- [go-judge API](https://docs.goj.ac/api)：执行协议与计量单位。
- [执行示例](https://docs.goj.ac/example)：编译产物缓存、复用与删除。
- [配置说明](https://docs.goj.ac/configuration)：监听、认证、并发、资源组失败行为与网络共享选项。
- [独立 RootFS](https://docs.goj.ac/rootfs)：独立编译运行文件系统。
- [上游 model.go](https://github.com/criyle/go-judge/blob/master/cmd/go-judge/model/model.go)：copyOutCached 同样支持问号可选后缀。
- [GCC C++17 支持](https://gcc.gnu.org/projects/cxx-status.html#cxx17)：显式使用 -std=c++17。
- [GCC 行号控制](https://gcc.gnu.org/onlinedocs/cpp/Line-Control.html)：生成文件通过 #line 标注原学生代码位置。

具体 go-judge / GCC 版本尚未锁定，需在 Rocky 8 验证后记录，不使用未验证的“最新版”作为验收结论。

## 本地演示辅助脚本

- start_local.py：普通用户启动固定go-judge版本，检查cgroup与seccomp，不修改宿主安全设置。
- probe_local.py：小型良性C编译与文件/网络隔离探针。
- verify_demo_api.py：通过真实API验证各评测结果、幂等与权限。
- verify_isolation_api.py：对唯一自建canary和本机监听器执行受控非法操作测试；细节见SECURITY_TESTS.md。
- verify_functions_local.py：复用当前 go-judge，运行 C17/CPP17 函数 AC/WA/CE 与两种程序题回归；不重启应用/数据库，不向宿主直接运行提交代码。

这些脚本用于本地受控开发环境，不替代目标机部署验收。整体本地应用启停由scripts/local_app.py协调。
