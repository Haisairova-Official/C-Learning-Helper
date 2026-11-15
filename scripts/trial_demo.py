#!/usr/bin/env python3
"""Explicit local integration check; creates reusable synthetic fixtures, no formal submissions or AI calls."""
import urllib.request,urllib.error,http.cookiejar,json,shlex,time
from pathlib import Path
root=Path(__file__).resolve().parents[1];base='http://127.0.0.1/api'
class Client:
 def __init__(self):self.op=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
 def req(self,path,body=None,status=200,csrf=True):
  headers={}
  if body is not None:
   headers['Content-Type']='application/json'
   if csrf:
    t=self.req('/auth/csrf');headers[t['headerName']]=t['token']
  req=urllib.request.Request(base+path,data=None if body is None else json.dumps(body).encode(),headers=headers)
  try:
   with self.op.open(req,timeout=20) as r:actual=r.status;raw=r.read()
  except urllib.error.HTTPError as e:actual=e.code;raw=e.read()
  assert actual==status,(path,actual,status,raw[:200] if actual>=500 else '')
  return json.loads(raw) if raw else None
 def login(self,name):
  self.req('/auth/login',{'username':name,'password':accounts['APP_DEMO_PASSWORD_'+name.upper()]})
accounts={}
for line in (root/'backend/.local/initial-accounts.env').read_text().splitlines():
 if line and not line.startswith('#'):
  k,v=line.removeprefix('export ').split('=',1);accounts[k]=shlex.split(v)[0]
import secrets
from datetime import datetime,timezone,timedelta
admin=Client();admin.login('admin')
fixture=root/'backend/.local/trial-check.json'
if fixture.exists():
 data=json.loads(fixture.read_text())
else:
 suffix=secrets.token_hex(5);data={'users':[]}
 for index in range(2):
  name='trial-check-'+suffix+'-'+str(index);password='Trial1'+secrets.token_hex(15)
  user=admin.req('/admin/users',{'username':name,'displayName':'自主测试验收账号'+str(index),'role':'STUDENT','studentNumber':'','password':password})
  data['users'].append({'username':name,'password':password,'id':user['id']})
 classroom=admin.req('/classes',{'name':'自主测试验收班-'+suffix},201)
 for user in data['users']:admin.req('/classes/'+classroom['id']+'/members',{'studentId':user['id']},204)
 ids=[]
 for language in ['C17','CPP17']:
  for kind in ['PROGRAM','FUNCTION']:
   template='#include <stdio.h>\n/*__STUDENT_CODE__*/\nint main(){puts("PRIVATE_TRIAL_TEMPLATE");}' if kind=='FUNCTION' else ''
   source='int add(int a,int b){return a+b;}' if kind=='FUNCTION' else '#include <stdio.h>\nint main(){puts("3");}'
   problem=admin.req('/teacher/problems',{'title':'自主测试验收 '+language+' '+kind,'statement':'独立合成题，验证自主测试输入输出。','inputDescription':'两个整数','outputDescription':'和','samples':[{'input':'1 2\n','output':'3\n'}],'referenceAnswer':source,'testCases':[{'input':'1 2\n','expectedOutput':'3\n','visibility':'HIDDEN','score':1,'comparisonMode':'EXACT'}],'timeLimitMs':1000,'memoryLimitMb':128,'codeLimitBytes':65536,'maxScore':1,'problemType':kind,'language':language,'functionSignature':'int add(int a,int b);' if kind=='FUNCTION' else '', 'starterCode':source, 'judgeTemplate':template},201)
   ids.append(problem['id'])
 now=datetime.now(timezone.utc)
 assignment=admin.req('/teacher/assignments',{'title':'自主测试功能验收-'+suffix,'classId':classroom['id'],'problemIds':ids,'opensAt':(now-timedelta(minutes=1)).isoformat(),'closesAt':(now+timedelta(days=30)).isoformat()},201)
 data['assignmentId']=assignment['id']
 import os
 fd=os.open(fixture,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
 with os.fdopen(fd,'w') as stream:json.dump(data,stream)
s=Client();u=data['users'][0];s.req('/auth/login',{'username':u['username'],'password':u['password']})
other=Client();u=data['users'][1];other.req('/auth/login',{'username':u['username'],'password':u['password']});anon=Client()
a_list=s.req('/student/assignments'); a_list=a_list['items'] if isinstance(a_list,dict) else a_list
available=[]
from datetime import datetime,timezone
for a in a_list:
 if datetime.fromisoformat(a['opensAt'].replace('Z','+00:00'))<=datetime.now(timezone.utc)<datetime.fromisoformat(a['closesAt'].replace('Z','+00:00')):
  detail=s.req('/student/assignments/'+a['id'])
  for item in detail['problems']:
   p=s.req('/student/assignments/'+a['id']+'/problems/'+item['id']);available.append((a,p))
assert available,'no open problem'
a,p=available[0]
body={'assignmentId':a['id'],'problemId':p['id'],'problemVersionId':p['versionId'],'sourceCode':'#include <stdio.h>\nint main(){int a,b;if(scanf("%d%d",&a,&b)!=2)return 1;printf("%d\\n",a+b);fputs("test stderr",stderr);}', 'language':p['language'],'stdin':'20 22\n','expectedOutput':'42\n','driverCode':''}
if p['problemType']=='FUNCTION':body['driverCode']=body.pop('sourceCode');body['sourceCode']='int trial_placeholder(){return 0;}'
before=s.req('/submissions?assignmentId='+a['id']+'&problemId='+p['id']);score_before=s.req('/student/assignments/'+a['id'])
def run(b):
 job=s.req('/trials',b,202)
 for _ in range(200):
  v=s.req('/trials/'+job['id'])
  if v['status']=='COMPLETED':return job,v['result']
  time.sleep(.15)
 raise AssertionError('trial timed out')
anon.req('/trials',body,401);s.req('/trials',body,403,csrf=False)
job,r=run(body);assert r['verdict']=='ACCEPTED' and r['stdout']=='42\n' and r['stderr']=='test stderr',r
other.req('/trials/'+job['id'],status=404)
_,r=run(dict(body,expectedOutput='43'));assert r['verdict']=='WRONG_ANSWER',r
_,r=run(dict(body,expectedOutput=None));assert r['verdict']=='ACCEPTED',r
bad=dict(body,sourceCode='int main( broken',driverCode='int trial(){return 0;}' if p['problemType']=='FUNCTION' else '')
_,r=run(bad);assert r['verdict']=='COMPILATION_ERROR' and r['compileMessage'],r
loop=dict(body,sourceCode='int main(){for(;;){}}',driverCode='int trial(){return 0;}' if p['problemType']=='FUNCTION' else '',expectedOutput=None)
_,r=run(loop);assert r['verdict']=='TIME_LIMIT_EXCEEDED',r
s.req('/trials',dict(body,stdin='中'*22000),400)
s.req('/trials',dict(body,problemVersionId='not-authorized'),404)
after=s.req('/submissions?assignmentId='+a['id']+'&problemId='+p['id']);score_after=s.req('/student/assignments/'+a['id'])
assert before==after and score_before==score_after,'formal submissions/scores changed'
assert s.req('/ai/analyses/provider')=={'providerMode':'DEEPSEEK','configured':True}
print('PASS: real sandbox custom stdin/stdout/stderr, expected/no expected, wrong answer, compile failure, infinite-loop time limit, UTF8 input size, unauthorized version, login/CSRF/owner scope; formal submissions/scores unchanged; DeepSeek preserved.')
print('Tested problem language/type:',p['language'],p['problemType'])
print('Available problem variants:',sorted(set((p['language'],p['problemType']) for a,p in available)))

for a,p in available[1:]:
 variant=dict(body,assignmentId=a['id'],problemId=p['id'],problemVersionId=p['versionId'],language=p['language'])
 if p['problemType']=='FUNCTION':
  variant['sourceCode']='int trial_sum(int a,int b){return a+b;}'
  variant['driverCode']='#include <stdio.h>\nint main(){int a,b;scanf("%d%d",&a,&b);printf("%d\\n",trial_sum(a,b));}'
 else:
  variant['sourceCode']='#include <stdio.h>\nint main(){int a,b;scanf("%d%d",&a,&b);printf("%d\\n",a+b);}'
  variant['driverCode']=''
 _,r=run(variant);assert r['verdict']=='ACCEPTED' and r['stdout']=='42\n',r
 assert 'PRIVATE_TRIAL_TEMPLATE' not in str(r)
 print('PASS additional language/type:',p['language'],p['problemType'])
