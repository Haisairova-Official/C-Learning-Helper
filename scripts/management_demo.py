#!/usr/bin/env python3
"""Real HTTP regression for local demo. Creates separate, named synthetic fixtures; no paid AI calls."""
import concurrent.futures as futures
import datetime as dt
import http.cookiejar
import json
import os
from pathlib import Path
import time
import urllib.request
import uuid
from smoke_demo import Api, PASSWORD, ok, RESULTS, await_submission

class Client(Api):
    def __init__(self, username=None):
        super().__init__()
        self.client=urllib.request.build_opener(urllib.request.ProxyHandler({}),urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        if username: self.login(username)

def main():
    tag=uuid.uuid4().hex[:8]
    admin=Client('admin')
    state={'tag':tag,'users':[],'assignments':[],'classes':[],'libraries':[]}
    statefile=Path(__file__).resolve().parents[1]/'.local/management-check.json'
    def save():
        statefile.write_text(json.dumps(state,ensure_ascii=False,indent=2));statefile.chmod(0o600)
    def create(suffix,role='STUDENT',number=''):
        value=admin.call('POST','/admin/users',{'username':f'check_{tag}_{suffix}','displayName':f'验证{suffix}','role':role,'studentNumber':number,'password':PASSWORD})
        state['users'].append(value);save();return value
    owner=create('teacher','TEACHER'); outsider=create('other','TEACHER'); s1=create('s1',number='S'+tag+'1'); extraAdmin=create('admin','ADMIN')
    teacher=Client(owner['username']);other=Client(outsider['username']);student=Client(s1['username'])
    for client in (teacher,student):
        client.call('GET','/admin/users',expected=403)
        client.call('POST','/admin/users',{'username':'forbidden','role':'ADMIN','displayName':'x','password':PASSWORD},expected=403)
        client.call('PUT','/admin/users/'+s1['id'],{'displayName':'x'},expected=403)
    Client(extraAdmin['username']).call('GET','/admin/users')
    assert 'password' not in json.dumps(admin.call('GET','/admin/users'))
    edited=admin.call('PUT','/admin/users/'+s1['id'],{'displayName':s1['displayName'],'studentNumber':s1['studentNumber'],'nickname':'验证昵称','bio':'验证简介','aiReplyStyle':'DETAILED'})
    assert student.call('GET','/profile')['nickname']=='验证昵称'
    admin.call('POST','/admin/users',{'username':s1['username'],'displayName':'重复','role':'STUDENT','studentNumber':'','password':PASSWORD},expected=400)
    admin.call('POST','/admin/users',{'username':'new_'+tag,'displayName':'重复学号','role':'STUDENT','studentNumber':s1['studentNumber'],'password':PASSWORD},expected=400)
    ok('管理员创建三种角色、修改资料、唯一性及学生/教师越权拦截')
    # Explicitly omit CSRF, while retaining authenticated cookies.
    req=urllib.request.Request(__import__('smoke_demo').BASE+'/admin/users',json.dumps({'username':'no_csrf'}).encode(),{'Content-Type':'application/json'},method='POST')
    try: admin.client.open(req);raise AssertionError('Missing CSRF accepted')
    except urllib.error.HTTPError as e: assert e.code==403
    ok('新增管理写接口保持 CSRF 校验')
    classroom=teacher.call('POST','/classes',{'name':'联调验证班 '+tag},expected=201);cid=classroom['id'];state['classes'].append(cid);save()
    path=f'/classes/{cid}/students/import'
    username='check_'+tag+'_s2';number='S'+tag+'2'
    raw=f"学生名单\n姓名,学号,账号\n{s1['displayName']},{s1['studentNumber']},\n验证s2,{number},{username}"
    preview=teacher.call('POST',path+'/preview',{'text':raw});assert [r['action'] for r in preview['rows']]==['EXISTING','NEW']
    assert admin.call('GET','/admin/users?q='+username)['total']==0
    student.call('POST',path+'/preview',{'text':raw},expected=403);other.call('POST',path+'/preview',{'text':raw},expected=403)
    rows=[{k:r[k] for k in ('rowNumber','studentNumber','name','username','className')} for r in preview['rows']]
    bad=rows+[dict(rows[-1],rowNumber=4)]
    teacher.call('POST',path+'/commit',{'rows':bad,'initialPassword':PASSWORD},str(uuid.uuid4()),expected=400)
    assert admin.call('GET','/admin/users?q='+username)['total']==0
    key=str(uuid.uuid4());payload={'rows':rows,'initialPassword':PASSWORD}
    parallel=[Client(owner['username']) for _ in range(2)]
    with futures.ThreadPoolExecutor(2) as pool: committed=list(pool.map(lambda c:c.call('POST',path+'/commit',payload,key),parallel))
    assert committed[0]==committed[1] and committed[0]['created']==1 and committed[0]['added']==2
    teacher.call('POST',path+'/commit',dict(payload,initialPassword='DifferentPassword1!'),key,expected=409)
    imported=admin.call('GET','/admin/users?q='+username)['items'][0];state['users'].append(imported);save();student2=Client(username)
    assert Client(s1['username']).call('GET','/profile')['nickname']=='验证昵称'
    again=teacher.call('POST',path+'/commit',dict(payload,initialPassword='RosterDifferent2026!'),str(uuid.uuid4()));assert again['created']==0 and again['unchanged']==2
    Client(s1['username']);Client(username)
    for login_name in (s1['username'],username):
        Client().call('POST','/auth/login',{'username':login_name,'password':'RosterDifferent2026!'},expected=401)
    ok('名单只读预览、原子失败、并发幂等、新建入班、已有资料和密码保留')
    library=teacher.call('POST','/teacher/libraries',{'title':'联调验证题库 '+tag});lid=library['id'];state['libraries'].append(lid);save()
    student.call('GET','/teacher/libraries',expected=403);other.call('GET','/teacher/libraries/'+lid,expected=403)
    code='#include <stdio.h>\nint main(void){int a,b;scanf("%d%d",&a,&b);printf("%d\\n",a+b);return 0;}\n'
    problem={'title':'验证求和','statement':'读取两个整数，输出和。','inputDescription':'两个整数','outputDescription':'和','samples':[{'input':'1 2\n','output':'3\n'}],'referenceAnswer':code,'testCases':[{'input':'1 2\n','expectedOutput':'3\n','visibility':'HIDDEN','score':10,'comparisonMode':'TOKENS'}],'timeLimitMs':1000,'memoryLimitMb':128,'codeLimitBytes':65536,'maxScore':10,'problemType':'PROGRAM','language':'C17','functionSignature':'','starterCode':'','judgeTemplate':'','state':'DRAFT'}
    problems=[teacher.call('POST',f'/teacher/libraries/{lid}/problems',dict(problem,title='验证求和 '+str(i))) for i in range(1,5)]
    assert len(teacher.call('GET','/teacher/libraries/'+lid)['problems'])==4
    now=dt.datetime.now(dt.timezone.utc)
    base={'classId':cid,'libraryId':lid,'problemIds':[],'opensAt':(now-dt.timedelta(minutes=1)).isoformat(),'closesAt':(now+dt.timedelta(days=2)).isoformat(),'aiEnabled':False,'chatEnabled':True,'drawCount':2}
    assignments={}
    for mode in ('FULL','SHARED_RANDOM','PER_STUDENT_RANDOM'):
        a=teacher.call('POST','/teacher/assignments',dict(base,title='联调验证 '+mode+' '+tag,selectionMode=mode),expected=201)
        assignments[mode]=a;state['assignments'].append(a['id']);save()
        v1=student.call('GET','/student/assignments/'+a['id']);v2=student2.call('GET','/student/assignments/'+a['id'])
        assert len(v1['problems'])==(4 if mode=='FULL' else 2)
        assert v1['maxScore']==(40 if mode=='FULL' else 20)
        assert v1==student.call('GET','/student/assignments/'+a['id'])
        assert len({p['id'] for p in v1['problems']})==len(v1['problems'])
        if mode!='PER_STUDENT_RANDOM':assert v1['problems']==v2['problems']
        else:
            hidden=next(p for p in problems if p['id'] not in {x['id'] for x in v1['problems']})
            student.call('GET',f"/student/assignments/{a['id']}/problems/{hidden['id']}",expected=404)
            data={'assignmentId':a['id'],'problemId':hidden['id'],'problemVersionId':hidden['versionId'],'sourceCode':code,'language':'C17'}
            student.call('POST','/submissions',data,str(uuid.uuid4()),expected=404)
    ok('题库层级、三种出题模式、随机持久化、分值及未抽中题目访问/提交拦截')
    late=create('late',number='S'+tag+'3');teacher.call('POST',f'/classes/{cid}/members',{'studentId':late['id']},expected=204)
    clients=[Client(late['username']) for _ in range(4)];aid=assignments['PER_STUDENT_RANDOM']['id']
    with futures.ThreadPoolExecutor(4) as pool: views=list(pool.map(lambda c:c.call('GET','/student/assignments/'+aid),clients))
    assert all(v['problems']==views[0]['problems'] for v in views) and len(views[0]['problems'])==2
    p=views[0]['problems'][0];accepted=clients[0].call('POST','/submissions',{'assignmentId':aid,'problemId':p['id'],'problemVersionId':p['versionId'],'sourceCode':code,'language':'C17'},str(uuid.uuid4()),expected=202)
    assert await_submission(clients[0],accepted['id'])['verdict']=='ACCEPTED'
    result=next(r for r in teacher.call('GET',f'/teacher/assignments/{aid}/results')['items'] if r['studentId']==late['id'])
    assert result['totalCount']==2 and result['maxScore']==20 and result['score']==10
    ok('晚入班并发抽题保持一致、真实隔离评测及随机作业成绩')
    full=assignments['FULL'];aid=full['id'];pid=full['problems'][0]['id'];vid=full['problems'][0]['versionId']
    teacher.call('PUT',f'/teacher/libraries/{lid}/problems/{pid}',dict(problems[0],title='题库新版标题'))
    snapshot=student.call('GET',f'/student/assignments/{aid}/problems/{pid}');assert snapshot['versionId']==vid and snapshot['title']!='题库新版标题'
    assert 'referenceAnswer' not in snapshot and 'testCases' not in snapshot
    ai={'assignmentId':aid,'problemId':pid,'problemVersionId':vid,'sourceCode':code}
    assert student.call('POST','/ai/analyses',ai,expected=403)['code']=='AI_DISABLED'
    room=f'/assignments/{aid}/problems/{pid}/chat';mutes=f'/assignments/{aid}/chat/mutes';key=str(uuid.uuid4())
    message=student.call('POST',room,{'body':'这是独立联调消息。'},key)
    assert student.call('POST',room,{'body':'这是独立联调消息。'},key)['id']==message['id']
    student.call('POST',room,{'body':'不同内容'},key,expected=409)
    assert student2.call('GET',room)['items'][0]['body']=='这是独立联调消息。'
    student.call('DELETE',room+'/messages/'+message['id'],expected=403);other.call('DELETE',room+'/messages/'+message['id'],expected=403)
    teacher.call('DELETE',room+'/messages/'+message['id'],expected=204)
    retracted=student.call('GET',room)['items'][0];assert retracted['body']=='' and retracted['retracted']
    teacher.call('POST',mutes,{'studentId':s1['id']},expected=204)
    assert student.call('GET',room)['muted']
    assert student.call('POST',room,{'body':'禁言时发言'},str(uuid.uuid4()),expected=403)['code']=='CHAT_MUTED'
    room2=f"/assignments/{aid}/problems/{full['problems'][1]['id']}/chat"
    student.call('POST',room2,{'body':'切题绕过禁言'},str(uuid.uuid4()),expected=403)
    student.call('GET',mutes,expected=403);other.call('GET',mutes,expected=403)
    assert teacher.call('GET',mutes)[0]['studentId']==s1['id']
    student.call('DELETE',mutes+'/'+s1['id'],expected=403);other.call('DELETE',mutes+'/'+s1['id'],expected=403)
    admin.call('DELETE',mutes+'/'+s1['id'],expected=204);assert not teacher.call('GET',mutes)
    time.sleep(2.1)
    student.call('POST',room,{'body':'解禁后可正常发言。'},str(uuid.uuid4()))
    teacher.call('POST',mutes,{'studentId':s1['id']},expected=204);teacher.call('DELETE',mutes+'/'+s1['id'],expected=204)
    ok('按题聊天、幂等、撤回隐藏原文、跨题禁言、被禁言列表、教师/管理员解禁及越权拦截')
    closed=teacher.call('POST','/teacher/assignments',dict(base,title='联调验证 聊天关闭 '+tag,selectionMode='FULL',chatEnabled=False),expected=201);state['assignments'].append(closed['id']);save()
    closedroom=f"/assignments/{closed['id']}/problems/{pid}/chat"
    student.call('GET',closedroom,expected=403)
    for client in (student,teacher):client.call('POST',closedroom,{'body':'不允许发言'},str(uuid.uuid4()),expected=403)
    teacher.call('GET',closedroom)
    ok('AI 关闭不调用模型，聊天关闭不能通过直接请求发言，教师保留历史管理入口')
    state.update({'chatAssignment':aid,'chatProblem':pid,'checks':RESULTS});save()
    print('Completed',len(RESULTS),'scenario groups; fixtures:',statefile)

if __name__=='__main__':main()
