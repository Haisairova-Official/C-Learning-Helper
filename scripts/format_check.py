#!/usr/bin/env python3
"""Check output comparison through the real isolated judge, using separate test fixtures."""
import datetime as dt
import json
from pathlib import Path
import uuid
from management_demo import Client
from smoke_demo import await_submission

STATE = Path('.local/format-check.json')

def main():
    management = json.loads(Path('.local/management-check.json').read_text())
    teacher = Client(next(u['username'] for u in management['users'] if u['username'].endswith('_teacher')))
    student = Client(next(u['username'] for u in management['users'] if u['username'].endswith('_s2')))
    def source(body):
        return '#include <stdio.h>\nint main(void){int a,b;if(scanf("%d%d",&a,&b)!=2)return 1;' + body + 'return 0;}\n'
    if STATE.exists():
        state = json.loads(STATE.read_text())
    else:
        state = {'problems':{}, 'submissions':[]}
        library = teacher.call('POST','/teacher/libraries',{'title':'输出格式验证 '+management['tag']})
        state['libraryId'] = library['id']
        for key,language,mode in [('c','C17','EXACT'),('cpp','CPP17','EXACT'),('tokens','C17','TOKENS')]:
            problem = teacher.call('POST',f"/teacher/libraries/{library['id']}/problems",{
                'title':'整数原样输出 · '+key,'statement':'读取两个整数，按原顺序输出。',
                'inputDescription':'两个空格分隔的整数。','outputDescription':'一行两个整数，中间一个空格。',
                'samples':[{'input':'1 2\n','output':'1 2\n'}],
                'referenceAnswer':source('printf("%d %d\\n",a,b);'),
                'testCases':[{'input':value,'expectedOutput':value,'visibility':'HIDDEN','score':5,'comparisonMode':mode} for value in ('1 2\n','5 8\n')],
                'timeLimitMs':1000,'memoryLimitMb':128,'codeLimitBytes':65536,'maxScore':10,
                'problemType':'PROGRAM','language':language,'state':'DRAFT',
                'functionSignature':'','starterCode':'','judgeTemplate':''})
            state['problems'][key] = {k:problem[k] for k in ('id','versionId','language')}
        now=dt.datetime.now(dt.timezone.utc)
        assignment=teacher.call('POST','/teacher/assignments',{
            'title':'输出格式验证 '+management['tag'],'classId':management['classes'][0],
            'libraryId':library['id'],'problemIds':[],'selectionMode':'FULL','aiEnabled':False,'chatEnabled':False,
            'opensAt':(now-dt.timedelta(minutes=1)).isoformat(),'closesAt':(now+dt.timedelta(days=2)).isoformat()},expected=201)
        state['assignmentId']=assignment['id']
        STATE.write_text(json.dumps(state,ensure_ascii=False,indent=2));STATE.chmod(0o600)
    cases=[
        ('c','normal','printf("%d %d\\n",a,b);','ACCEPTED',10),
        ('c','missing-final-newline','printf("%d %d",a,b);','ACCEPTED',10),
        ('c','extra-space','printf("%d  %d\\n",a,b);','PRESENTATION_ERROR',0),
        ('c','extra-blank-line','printf("%d %d\\n\\n",a,b);','PRESENTATION_ERROR',0),
        ('c','wrong-line-break','printf("%d\\n%d\\n",a,b);','PRESENTATION_ERROR',0),
        ('c','wrong-value','printf("%d %d\\n",a+1,b);','WRONG_ANSWER',0),
        ('c','extra-text','printf("result: %d %d\\n",a,b);','WRONG_ANSWER',0),
        ('c','partial-format','if(a==1)printf("%d %d\\n",a,b);else printf("%d  %d\\n",a,b);','PARTIAL_ACCEPTED',5),
        ('cpp','missing-final-newline','printf("%d %d",a,b);','ACCEPTED',10),
        ('cpp','extra-space','printf("%d  %d\\n",a,b);','PRESENTATION_ERROR',0),
        ('tokens','whitespace-tolerance','printf("  %d\\t%d\\n\\n",a,b);','ACCEPTED',10),
    ]
    for key,name,body,verdict,score in cases:
        p=state['problems'][key]
        job=student.call('POST','/submissions',{'assignmentId':state['assignmentId'],'problemId':p['id'],
            'problemVersionId':p['versionId'],'language':p['language'],'sourceCode':source(body)},str(uuid.uuid4()),expected=202)
        result=await_submission(student,job['id'])
        state['submissions'].append({'case':key+'/'+name,'id':job['id'],'verdict':result['verdict'],'score':result['score']})
        STATE.write_text(json.dumps(state,ensure_ascii=False,indent=2))
        assert result['status']=='FINISHED' and result['verdict']==verdict and result['score']==score,(name,result['verdict'],result['score'])
        assert all(set(c)=={'caseIndex','status','score','timeMs','memoryKb'} for c in result['caseResults'])
        assert student.call('GET','/submissions/'+job['id'])==result
        if name=='partial-format':assert [c['status'] for c in result['caseResults']]==['ACCEPTED','PRESENTATION_ERROR']
        print('PASS',key,name,verdict,score,flush=True)

if __name__=='__main__': main()
