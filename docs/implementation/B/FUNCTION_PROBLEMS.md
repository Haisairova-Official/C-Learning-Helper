# B 第三轮：函数题与 C++17

更新时间：2026-11-06 18:20（Asia/Shanghai）。当前阶段：实现及模块验证完成，可统一构建联调；服务重启、迁移与 HTTP 整体验收由主 Agent 执行。

## 字段与兼容

依照 [冻结契约](../../agents/FUNCTION_PROBLEMS.md)，题目版本增加 `problemType`（PROGRAM/FUNCTION）、`language`（C17/CPP17）、公开 `functionSignature`、公开 `starterCode` 及教师私有 `judgeTemplate`。学生 DTO 不含模板、答案或测试用例。新增 V6 迁移，已有版本默认为 PROGRAM+C17，不覆盖 A 的 V5。

StudentProblemView、TeacherProblemView、ProblemSnapshot、DraftProblem 保留旧 Java 构造器；旧 JSON 教师请求缺省 PROGRAM+C17。依据本轮导入需求，FUNCTION 草稿可缺接口/模板，发布和评测时须完整；非空模板在任何阶段都须且只含一个占位标记。PROGRAM 不接受非空私有模板。接口最多4096 UTF-8字节、起始代码和模板各最多65536字节。DraftProblem 与 saveDrafts 保留全部函数字段，导入器由 D 更新映射。

TeachingService 的读写和发布快照完整保存这些字段；主 Agent 的 ProblemProgress 聚合原样保留，仍按当前学生、作业、题目版本统计。

## 评测与信息边界

- 新提交先通过原 ProblemAccessPort 身份/作业/版本/截止权限，再检查请求语言与该版本一致。未授权版本不会进入组装或队列。
- FunctionSourceAssembler 只将学生原代码插入唯一 `/*__STUDENT_CODE__*/`；不插入参考答案。模板和组装代码仅存在于评测内部上下文；submission.source_code、哈希与详情继续保存原学生代码。
- 固定工具链 C17 使用 `/usr/bin/gcc -std=c17`、CPP17 使用 `/usr/bin/g++ -std=c++17`；输入内容不进入 shell、不提供自选参数。编译和运行仍经过现有 go-judge 及资源限制。
- `#line` 将学生代码定位为 student.c/student.cpp。函数题诊断只提取数字行/列并配固定中文说明；即使 GCC 在学生位置引用私有声明也不会回传原诊断文字。模板/链接错误只显示通用说明。程序题保持现有路径过滤诊断。
- 模板属于教师私有评测程序，应只包含调用/检查框架，不应包含真实密钥。此机制不是将同一编译单元内的函数变成互不访问的安全边界；运行时数据仍由现有隔离执行与不公开标准输出保护。

## 修改文件

- problem：StudentProblemView、TeacherProblemView、ProblemSnapshot、DraftProblem、TeachingService、ProblemCodePolicy。
- judge/core：CompileLanguage、CompilationSource、FunctionSourceAssembler、JudgeEngine；judge/protocol/GoJudgeRequests；SubmissionService、JudgeWorker。
- migration：V6__function_problems_cpp17.sql。
- 测试：FunctionProblemTest、FunctionSubmissionAuthorizationTest、FunctionJudgeIntegrationTest；原评测核心回归。
- 部署：preflight.sh 检查 g++；README 要求 C++ 标准库；verify_functions_local.py 复用现有服务执行实际隔离验证。

## 验证与交接

| 场景 | 方式 | 当前结果 |
| --- | --- | --- |
| 新增字段/旧构造器/旧JSON/公开DTO/占位校验/草稿例外/安全诊断 | FunctionProblemTest | 7项通过 |
| 语言不匹配/未授权版本/只持久化原代码 | FunctionSubmissionAuthorizationTest | 3项通过（mock仓储/权限边界） |
| 既有比较/协议/HTTP/快照映射 | 原21项评测测试 | 全部通过 |
| C17、CPP17函数正确/错误/编译失败 | FunctionJudgeIntegrationTest，真实go-judge | 6项通过，分别为AC/WA/CE |
| C17程序回归、C++17标准库与结构化绑定 | 同上 | 2项通过，均为AC |
| V6迁移与真实HTTP/页面/AI上下文 | 主 Agent 统一重启后验收 | 未执行 |

本机 g++ 为 GCC 16.2.1，真实编译和二进制执行全部由当前 go-judge v1.14.0 完成；测试执行前确认其 seccomp 与 cpu/memory/pids 控制器就绪。31项定向测试于18:20:04通过，8项真实隔离测试于18:20:20通过，失败/错误/跳过均为0。证据在 backend/target/surefire-reports/com.cassist.judge.FunctionProblemTest.txt、FunctionSubmissionAuthorizationTest.txt、FunctionJudgeIntegrationTest.txt及原有三类测试报告。后续普通全量测试会将显式启用的集成测试报告覆盖为跳过，本文保留此次实际执行记录。没有重启后端/数据库，也未在宿主运行学生代码。

运行核心测试：backend 下 `./mvnw -B -ntp -Dtest=JudgeCoreTest,JudgeProblemMapperTest,HttpGoJudgeTransportTest,FunctionProblemTest,FunctionSubmissionAuthorizationTest test`。

运行实际隔离测试：项目根目录 `python3 deploy/judge/verify_functions_local.py`。脚本只从本机受限凭据文件加载环境变量，不输出凭据。默认普通 Maven 测试会跳过真实服务测试；需显式 CASSIST_JUDGE_INTEGRATION=true。

官方依据：[GCC C++17 支持](https://gcc.gnu.org/projects/cxx-status.html#cxx17)、[GCC #line 说明](https://gcc.gnu.org/onlinedocs/cpp/Line-Control.html)。Rocky 8 的 G++/libstdc++ 安装根目录仍需目标服务器单独验收。

## 下一步

主 Agent 完成全模块构建后统一迁移/重启，建立 C17 与 CPP17 函数题并发布，复验学生公开接口、提交详情、历史、错误语言、非法占位标记和未授权版本；AI 模块及前端由其负责人接入本轮公开字段。B 不修改 AI、前端或 V5。
