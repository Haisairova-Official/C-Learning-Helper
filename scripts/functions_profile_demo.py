#!/usr/bin/env python3
"""Local demo integration for profiles, function imports and judging. Opt-in two AI calls."""
import argparse, datetime as dt, io, json, struct, time, uuid, zlib, urllib.request
from pathlib import Path
import smoke_demo as smoke
from ai_live_check import start_analysis

def png():
    def chunk(kind, data): return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',512,512,8,2,0,0,0))+chunk(b'IDAT',zlib.compress((b'\0'+bytes([20,140,145])*512)*512))+chunk(b'IEND',b'')

def upload(client, data, filename='test.png', kind='image/png', expected=200):
    boundary='cassist-'+uuid.uuid4().hex
    body=(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\nContent-Type: {kind}\r\n\r\n'.encode()+data+f'\r\n--{boundary}--\r\n'.encode())
    return client.call('POST','/profile/avatar',raw=body,content_type='multipart/form-data; boundary='+boundary,expected=expected)

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--run-live',action='store_true');args=parser.parse_args()
    smoke.BASE='http://127.0.0.1:5173/api'
    teacher, student, tester = smoke.Api(), smoke.Api(), smoke.Api()
    teacher.login('teacher'); student.login('student'); actor=tester.login('student2')
    original=tester.call('GET','/profile'); student_original=student.call('GET','/profile')
    original_avatar=None
    if original['avatarUrl']:
        original_avatar=tester.client.open(smoke.BASE+'/profile/avatar').read()
    report={'checks':[],'records':[],'ai':[]}
    def check(name): report['checks'].append(name); print('PASS:',name,flush=True)
    try:
        smoke.Api().call('GET','/profile',expected=401)
        updated=tester.call('PUT','/profile',{'nickname':'函数题验收🧪','bio':'独立演示测试账户，验证后恢复。','aiReplyStyle':'CONCISE'})
        assert updated['nickname']=='函数题验收🧪' and updated['username']==original['username']
        assert student.call('GET','/profile')==student_original
        tester.call('PUT','/profile',{'nickname':'x','bio':'','aiReplyStyle':'IGNORE_RULES'},expected=400)
        tester.call('PUT','/profile',{'nickname':'🧪'*33,'bio':'','aiReplyStyle':'GUIDED'},expected=400)
        check('profile current-user isolation and invalid preference/length rejection')
        picture=upload(tester,png());assert picture['avatarUrl'].startswith('/api/profile/avatar?')
        response=tester.client.open(smoke.BASE+'/profile/avatar');data=response.read()
        assert response.headers['Content-Type']=='image/png' and 'no-store' in response.headers['Cache-Control']
        assert data.startswith(b'\x89PNG') and struct.unpack('>II',data[16:24])==(256,256)
        upload(tester,b'<svg onload="alert(1)"/>',expected=400)
        tester.call('DELETE','/profile/avatar');tester.call('GET','/profile/avatar',expected=404)
        check('avatar normalized to PNG, spoofed image rejected, reset works')
        bundle=json.loads(Path('examples/pta/function-insert.json').read_text())
        job=teacher.call('POST','/imports/parse',{'sourceType':'TEXT','text':json.dumps(bundle,ensure_ascii=False)},expected=202)
        preview=teacher.call('GET','/imports/'+job['id']);assert len(preview['candidates'])==2,preview
        assert all(c['problemType']=='FUNCTION' and c['judgeTemplate'] for c in preview['candidates'])
        committed=teacher.call('POST','/imports/'+job['id']+'/commit',{'title':bundle['title'],'candidates':preview['candidates']},str(uuid.uuid4()),expected=201)
        ids=committed['problemIds']; drafts=[teacher.call('GET','/teacher/problems/'+pid) for pid in ids]
        assert [p['language'] for p in drafts]==['C17','CPP17']
        for p,expected in zip(drafts,bundle['problems']):
            assert p['judgeTemplate']==expected['judgeTemplate'] and p['starterCode']==expected['starterCode']
            assert p['codeLimitBytes']==expected['codeLimitBytes'] and p['samples'][0]['explanation']==expected['samples'][0]['explanation']
        check('JSON Easy Parse preserves both languages, interfaces, templates and private judges')
        classrooms=teacher.call('GET','/classes')['items']
        classroom=next(c for c in classrooms if any(m['id']==actor['id'] for m in teacher.call('GET','/classes/'+c['id']+'/members')['items']))
        now=dt.datetime.now(dt.timezone.utc)
        assignment=teacher.call('POST','/teacher/assignments',{'title':'函数题入门（C17 + C++17）','classId':classroom['id'],'problemIds':ids,'opensAt':(now-dt.timedelta(minutes=1)).isoformat(),'closesAt':(now+dt.timedelta(days=30)).isoformat()},expected=201)
        aid=assignment['id'];report.update(assignmentId=aid,problemIds=ids)
        for p in drafts:
            public=tester.call('GET',f"/student/assignments/{aid}/problems/{p['id']}")
            assert 'referenceAnswer' not in public and 'judgeTemplate' not in public and 'testCases' not in public
            common={'assignmentId':aid,'problemId':p['id'],'problemVersionId':p['versionId'],'language':p['language']}
            tester.call('POST','/submissions',dict(common,language='CPP17' if p['language']=='C17' else 'C17',sourceCode=p['referenceAnswer']),str(uuid.uuid4()),expected=400)
            accepted=tester.call('POST','/submissions',dict(common,sourceCode=p['referenceAnswer']),str(uuid.uuid4()),expected=202)
            result=smoke.await_submission(tester,accepted['id']);assert result['verdict']=='ACCEPTED' and result['score']==20,(p['language'],result)
            report['records'].append({'language':p['language'],'submissionId':result['id'],'verdict':result['verdict'],'score':result['score']})
        check('both function references pass all 10 real cases; language mismatch denied; public DTO has no private fields')
        view=student.call('GET','/student/assignments/'+aid)
        assert all(p['progress']['status']=='UNSUBMITTED' for p in view['problems'])
        check('demo student remains unsubmitted; separate test student owns acceptance checks')
        if args.run_live:
            p=drafts[1];code=p['starterCode'].replace('return false;', 'return true;')
            for style in ['CONCISE','DETAILED']:
                tester.call('PUT','/profile',{'nickname':original['nickname'],'bio':original['bio'],'aiReplyStyle':style})
                accepted=start_analysis(tester,{'assignmentId':aid,'problemId':p['id'],'problemVersionId':p['versionId'],'sourceCode':code})
                tester.call('PUT','/profile',{'nickname':original['nickname'],'bio':original['bio'],'aiReplyStyle':'GUIDED'})
                for _ in range(200):
                    result=tester.call('GET','/ai/analyses/'+accepted['id'])
                    if result['status'] in ['COMPLETED','FAILED']: break
                    time.sleep(.3)
                report['ai'].append(result)
                assert result['replyStyle']==style and result['providerMode']=='DEEPSEEK',result
                assert result['status']=='COMPLETED' and result['findings'],result
                publictext=json.dumps(result,ensure_ascii=False)
                assert '```' not in publictext and p['referenceAnswer'] not in publictext and 'judgeTemplate' not in publictext
                check('real AI '+style+' style snapshot and function guidance')
        report['url']=f'http://127.0.0.1:5173/assignments/{aid}/problems/{ids[0]}'
    finally:
        tester.call('PUT','/profile',{k:original[k] for k in ['nickname','bio','aiReplyStyle']})
        if original_avatar: upload(tester,original_avatar)
        else: tester.call('DELETE','/profile/avatar')
        Path('.local/functions-profile-result.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
        for client in (teacher,student,tester): client.call('POST','/auth/logout',expected=204)
    print(report['url'],flush=True)

if __name__=='__main__':main()
