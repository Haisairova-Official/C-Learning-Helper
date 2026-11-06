#!/usr/bin/env python3
"""Integration checks against the local demo; uses bounded go-judge execution only."""
import http.cookiejar,json,os,time,urllib.request,urllib.error,uuid,pathlib
base=os.environ.get('DEMO_BASE_URL','http://127.0.0.1:8080')
password=os.environ.get('DEMO_TEST_PASSWORD','CassistDemo2026!')
class Client:
    def __init__(self,username):
        self.op=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.csrf=None
        self.csrf=self.call('/api/auth/csrf')[1]
        assert self.call('/api/auth/login',{'username':username,'password':password})[0]==200
        self.csrf=self.call('/api/auth/csrf')[1]
    def call(self,path,body=None,key=None):
        headers={'Content-Type':'application/json'}
        if self.csrf:headers[self.csrf['headerName']]=self.csrf['token']
        if key:headers['Idempotency-Key']=key
        request=urllib.request.Request(base+path,data=None if body is None else json.dumps(body).encode(),headers=headers)
        try:
            with self.op.open(request,timeout=15) as response:return response.status,json.load(response)
        except urllib.error.HTTPError as e:return e.code,json.load(e)
student=Client('student');other=Client('student2');teacher=Client('teacher')
assignments=student.call('/api/student/assignments')[1]['items']
assignment=next(a for a in assignments if any(p['title']=='两数之和' for p in a['problems']))
problem=next(p for p in assignment['problems'] if p['title']=='两数之和')
common={'assignmentId':assignment['id'],'problemId':problem['id'],'problemVersionId':problem['versionId'],'language':'C17'}
fixtures=[
 ('correct','#include <stdio.h>\nint main(void){long long a,b;if(scanf("%lld%lld",&a,&b)!=2)return 1;printf("%lld\\n",a+b);return 0;}','ACCEPTED'),
 ('wrong','#include <stdio.h>\nint main(void){puts("wrong");return 0;}','WRONG_ANSWER'),
 ('compile-error','int main(void){ this is invalid C; }','COMPILATION_ERROR'),
 ('runtime-error','#include <signal.h>\nint main(void){raise(SIGSEGV);}','RUNTIME_ERROR'),
 ('time-limit','int main(void){volatile unsigned long x=0;for(;;)x++;}','TIME_LIMIT_EXCEEDED'),
 ('output-limit','#include <stdio.h>\nint main(void){for(;;)puts("bounded output test");}','OUTPUT_LIMIT_EXCEEDED'),
 ('memory-limit','#include <stdlib.h>\nint main(void){for(;;){volatile char *p=malloc(1048576);if(!p)return 2;for(int i=0;i<1048576;i+=4096)p[i]=1;}}','MEMORY_LIMIT_EXCEEDED')
]
records=[];first_id=None;first_key=None;first_body=None
for name,source,expected in fixtures:
    body={**common,'sourceCode':source};key=str(uuid.uuid4())
    code,accepted=student.call('/api/submissions',body,key)
    assert code==202,(name,code,accepted)
    sid=accepted['id'];states=[accepted['status']]
    for _ in range(300):
        code,result=student.call('/api/submissions/'+sid)
        assert code==200,(code,result)
        if result['status'] not in states:states.append(result['status'])
        if result['status'] in ['FINISHED','SYSTEM_ERROR']:break
        time.sleep(.1)
    else:raise AssertionError('evaluation timeout')
    assert result['verdict']==expected,(name,result)
    for case in result['caseResults']:
        assert set(case)=={'caseIndex','status','score','timeMs','memoryKb'},case
    records.append({'scenario':name,'submissionId':sid,'verdict':result['verdict'],'score':result['score'],'states':states})
    print(name,result['verdict'],result['score'])
    if first_id is None:first_id=sid;first_key=key;first_body=body
code,replay=student.call('/api/submissions',first_body,first_key)
assert code==202 and replay['id']==first_id
code,conflict=student.call('/api/submissions',{**first_body,'sourceCode':'int main(void){return 0;}'},first_key)
assert code==409 and conflict['code']=='IDEMPOTENCY_CONFLICT'
assert other.call('/api/submissions/'+first_id)[0]==403
assert teacher.call('/api/submissions/'+first_id)[0]==200
code,history=student.call('/api/submissions?assignmentId='+assignment['id'])
assert code==200 and any(r['id']==first_id for r in history['items'])
assert all(r['sourceCode'] is None for r in history['items'])
report=pathlib.Path('docs/implementation/B/PHASE2_API_RESULTS.md')
report.write_text('# 第二轮真实 API 验证结果\n\n日期：2026-11-06；本地演示服务 + 私有 SQL 数据库 + go-judge v1.14.0。\n\n| 场景 | 实际结果 | 分数 | 观察到的状态 |\n| --- | --- | --- | --- |\n'+''.join(f"| {r['scenario']} | {r['verdict']} | {r['score']} | {' → '.join(r['states'])} |\n" for r in records)+'\n已通过：相同幂等键重放返回同一记录、不同内容返回 409、其他学生查询返回 403、授权教师可查看、历史记录查询及列表不回传代码、逐测试点响应没有隐藏输入输出。\n\n这些结果来自真实 HTTP 与受限 C 执行，不是模拟响应。目标 Rocky 8 尚未验收。\n',encoding='utf-8')
print('idempotency, authorization, history and hidden-data boundaries: PASS')
