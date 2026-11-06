#!/usr/bin/env python3
"""Bounded isolation probes via the real submission API; only an owned canary may be touched."""
import http.cookiejar,json,os,pathlib,socket,tempfile,threading,time,urllib.error,urllib.request,uuid,shutil
base=os.environ.get('DEMO_BASE_URL','http://127.0.0.1:8080')
password=os.environ.get('DEMO_TEST_PASSWORD','CassistDemo2026!')
op=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
csrf=None
def call(path,body=None,key=None):
    headers={'Content-Type':'application/json'}
    if csrf:headers[csrf['headerName']]=csrf['token']
    if key:headers['Idempotency-Key']=key
    request=urllib.request.Request(base+path,data=None if body is None else json.dumps(body).encode(),headers=headers)
    try:
        with op.open(request,timeout=15) as response:return response.status,json.load(response)
    except urllib.error.HTTPError as e:return e.code,json.load(e)
csrf=call('/api/auth/csrf')[1]
assert call('/api/auth/login',{'username':'student','password':password})[0]==200
csrf=call('/api/auth/csrf')[1]
assignment=next(a for a in call('/api/student/assignments')[1]['items'] if any(p['title']=='两数之和' for p in a['problems']))
problem=next(p for p in assignment['problems'] if p['title']=='两数之和')
owned=pathlib.Path(tempfile.mkdtemp(prefix='security-canary-',dir='backend/.local')).resolve()
canary=owned/'marker.txt'; original=('CASSIST_CANARY_'+uuid.uuid4().hex).encode();canary.write_bytes(original)
marker='csb-'+uuid.uuid4().hex[:8]
listener=socket.socket();listener.bind(('127.0.0.1',0));listener.listen(4);listener.settimeout(.2)
port=listener.getsockname()[1];connections=[];stop=threading.Event()
def listen():
    while not stop.is_set():
        try:
            c,_=listener.accept();connections.append(True);c.close()
        except socket.timeout:pass
        except OSError:break
thread=threading.Thread(target=listen,daemon=True);thread.start()
path_literal=json.dumps(str(canary),ensure_ascii=False)
# Each probe prints the legitimate sum only if its expected restriction is observed.
# Therefore AC means the restriction checks ran successfully for every grading input; WA alone is never counted as safe.
checks={
'file-read-write-delete':f'''FILE *f=fopen({path_literal},"r"); if(f){{fclose(f);ok=0;}}
f=fopen({path_literal},"w"); if(f){{fputs("CANARY_CHANGED",f);fclose(f);ok=0;}}
if(remove({path_literal})==0)ok=0;''',
'system-canary-write':f'''int rc=system("printf CANARY_CHANGED > '{canary}'");
if(rc==0)ok=0; FILE *f=fopen({path_literal},"r"); if(f){{fclose(f);ok=0;}}''',
'bounded-process-create':'''int spawned=0,denied=0;for(int i=0;i<32;i++){errno=0;pid_t p=fork();
if(p==0){_exit(0);}if(p>0)spawned++;else if(errno==EAGAIN||errno==EPERM)denied++;}
while(waitpid(-1,NULL,0)>0){} if(spawned!=0||denied!=32)ok=0;''',
'local-network-connect':f'''int s=socket(AF_INET,SOCK_STREAM,0);if(s>=0){{struct sockaddr_in a={{0}};
a.sin_family=AF_INET;a.sin_port=htons({port});inet_pton(AF_INET,"127.0.0.1",&a.sin_addr);
if(connect(s,(struct sockaddr*)&a,sizeof(a))==0)ok=0;close(s);}}'''
}
prefix='''#define _POSIX_C_SOURCE 200809L
#include <stdio.h>
#include <stdlib.h>
#include <errno.h>
#include <unistd.h>
#include <sys/wait.h>
#include <sys/prctl.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
'''
results=[]
def marker_pids():
    found=[]
    for item in pathlib.Path('/proc').iterdir():
        if item.name.isdigit():
            try:
                if (item/'comm').read_text().strip()==marker:found.append(int(item.name))
            except (FileNotFoundError,PermissionError,ProcessLookupError):pass
    return found
try:
    for name,check in checks.items():
        source=prefix+f'''int main(void){{prctl(PR_SET_NAME,"{marker}",0,0,0);long long a,b;
if(scanf("%lld%lld",&a,&b)!=2)return 2;int ok=1;
{check}
if(ok)printf("%lld\\n",a+b);else puts("ISOLATION_CHECK_FAILED");return 0;}}
'''
        body={'assignmentId':assignment['id'],'problemId':problem['id'],'problemVersionId':problem['versionId'],'language':'C17','sourceCode':source}
        code,accepted=call('/api/submissions',body,str(uuid.uuid4()));assert code==202,(name,code,accepted)
        sid=accepted['id']
        for _ in range(300):
            code,result=call('/api/submissions/'+sid);assert code==200
            if result['status'] in ['FINISHED','SYSTEM_ERROR']:break
            time.sleep(.1)
        else:raise AssertionError('probe evaluation timed out')
        intact=canary.is_file() and canary.read_bytes()==original
        assert intact,'Owned host canary changed; stop probes'
        assert result['verdict']=='ACCEPTED',(name,result)
        time.sleep(.1)
        remaining=marker_pids();assert not remaining,remaining
        results.append((name,result['verdict'],intact,len(remaining)))
        print(name,'restriction checks passed; canary unchanged; no marked process remains')
    assert not connections,'A sandbox reached the host listener'
    report=pathlib.Path('docs/implementation/B/SECURITY_TESTS.md')
    report.write_text('# 受控非法操作测试\n\n日期：2026-11-06。通过真实 /api/submissions 执行，使用已验证的 go-judge v1.14.0、cgroup v2 与 seccomp；没有在宿主直接执行学生程序。\n\n## 方法\n\n新建 backend/.local 下独享随机目录及唯一 canary 文件；所有文件攻击仅针对该文件，测试后清理。每段程序在确认限制生效时才输出题目要求的正确结果，所以本次 ACCEPTED 表示限制检查已执行并通过；普通答案错误不算隔离成功。逐次独立检查宿主 canary 存在且内容完全相同，并检查专用进程名没有残留。\n\n| 测试 | 实际结果 | canary 完整 | 残留标记进程 |\n| --- | --- | --- | --- |\n'+''.join(f'| {name} | {verdict}（限制检查通过） | {intact} | {remaining} |\n' for name,verdict,intact,remaining in results)+'\n## 已验证范围与限制\n\n- 直接读取、写入、删除唯一宿主 canary 均未成功；宿主文件每轮保持原样。\n- system 尝试通过 shell 写入同一 canary 没有成功，宿主文件保持原样。此结果由运行进程数限制与文件隔离共同作用，不能解释为所有 system 调用均被禁用。\n- 最多进行 32 次非递归 fork 尝试，若产生子进程会立即退出并由父进程回收；实际 32 次均因 EAGAIN/EPERM 失败，符合运行 procLimit=1，结束后无专用标记进程。\n- 网络仅尝试连接本机临时监听器一个端口；沙箱连接失败，宿主监听器接收数为 0，没有对外扫描。\n- 复用 PHASE2_API_RESULTS.md 已有 CPU、内存与输出超限证据，本轮没有重复耗尽测试。\n- 本轮只验证这些明确场景，不能据此宣称所有沙箱逃逸、系统调用或侧信道风险均已排除。目标 Rocky 8 仍需独立验收。\n',encoding='utf-8')
finally:
    stop.set();listener.close();thread.join(timeout=1)
    # Delete only this script's own disposable canary directory.
    shutil.rmtree(owned)
