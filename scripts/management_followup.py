#!/usr/bin/env python3
"""Recheck existing synthetic management fixtures; never creates accounts or calls AI.
Mutations are limited to idempotent existing-member import, same-title rename,
and a temporary avatar on an identified fixture whose original avatar is absent.
--check-legacy-create additionally creates up to two draft questions once and records
their IDs in the private fixture state. Use it only after deploying the compatibility fix.
"""
import argparse
import base64
import concurrent.futures as futures
import json
from pathlib import Path
import urllib.request
import uuid
from management_demo import Client
from smoke_demo import BASE

STATE=Path(__file__).resolve().parents[1]/'.local/management-check.json'

def main():
    args=argparse.ArgumentParser();args.add_argument('--check-legacy-create',action='store_true');options=args.parse_args()
    data=json.loads(STATE.read_text());tag=data['tag']
    users={u['username'].rsplit('_',1)[-1]:u for u in data['users']}
    assert all(u['username'].startswith('check_'+tag+'_') for u in users.values())
    admin=Client('admin');teacher=Client(users['teacher']['username']);student=Client(users['s1']['username']);other=Client(users['other']['username'])
    cid=data['classes'][0];lid=data['libraries'][0]
    rows=[{'rowNumber':i,'studentNumber':users[s]['studentNumber'],'name':users[s]['displayName'],'username':users[s]['username'],'className':''} for i,s in enumerate(('s1','s2'),1)]
    path=f'/classes/{cid}/students/import'
    before=[admin.call('GET','/admin/users/'+users[s]['id']) for s in ('s1','s2')]
    result=teacher.call('POST',path+'/commit',{'rows':rows,'initialPassword':'RosterDifferent2026!'},str(uuid.uuid4()))
    assert result['created']==0 and result['added']==0 and result['unchanged']==2
    for s in ('s1','s2'):
        Client(users[s]['username'])
        Client().call('POST','/auth/login',{'username':users[s]['username'],'password':'RosterDifferent2026!'},expected=401)
    after=[admin.call('GET','/admin/users/'+users[s]['id']) for s in ('s1','s2')]
    assert before==after
    print('PASS: different supplied import password does not replace either existing password or profile')
    lib=teacher.call('GET','/teacher/libraries/'+lid)
    teacher.call('PUT','/teacher/libraries/'+lid,{'title':lib['title']},expected=204)
    teacher.call('POST',f'/teacher/libraries/{lid}/members',[p['id'] for p in lib['problems']],expected=204)
    assert teacher.call('GET','/teacher/libraries/'+lid)==lib
    print('PASS: library rename/membership use 204; repeated membership does not duplicate questions')
    per=next(a for aid in data['assignments'] if (a:=teacher.call('GET','/teacher/assignments/'+aid))['selectionMode']=='PER_STUDENT_RANDOM')
    public=student.call('GET','/student/assignments/'+per['id'])
    hidden=next(p for p in per['problems'] if p['id'] not in {v['id'] for v in public['problems']})
    room=f"/assignments/{per['id']}/problems/{hidden['id']}/chat"
    student.call('GET',room,expected=404)
    student.call('POST',room,{'body':'unassigned room access check'},str(uuid.uuid4()),expected=404)
    mutes=f"/assignments/{data['chatAssignment']}/chat/mutes"
    teacher.call('POST',mutes,{'studentId':users['teacher']['id']},expected=400)
    other.call('DELETE',mutes+'/'+users['s2']['id'],expected=403)
    print('PASS: unassigned random question cannot be used as a chat backdoor; only students can be muted')
    target=users['other']['id'];endpoint='/admin/users/'+target+'/avatar'
    assert admin.call('GET','/admin/users/'+target)['avatarUrl'] is None,'Avatar fixture already changed; will not overwrite'
    png=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=')
    boundary='cassist-'+uuid.uuid4().hex
    body=(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="test.png"\r\nContent-Type: image/png\r\n\r\n'.encode()+png+f'\r\n--{boundary}--\r\n'.encode())
    try:
        student.call('POST',endpoint,raw=body,content_type='multipart/form-data; boundary='+boundary,expected=403)
        teacher.call('POST',endpoint,raw=body,content_type='multipart/form-data; boundary='+boundary,expected=403)
        updated=admin.call('POST',endpoint,raw=body,content_type='multipart/form-data; boundary='+boundary)
        assert updated['avatarUrl'].startswith('/api/admin/users/')
        response=admin.client.open(urllib.request.Request(BASE+endpoint),timeout=10)
        assert response.status==200 and response.headers.get_content_type()=='image/png' and response.read().startswith(b'\x89PNG')
        assert 'no-store' in response.headers.get('Cache-Control','')
        student.call('GET',endpoint,expected=403);teacher.call('GET',endpoint,expected=403)
    finally:
        admin.call('DELETE',endpoint)
    assert admin.call('GET','/admin/users/'+target)['avatarUrl'] is None
    print('PASS: administrator avatar upload/read/delete and privacy headers; fixture restored')
    if options.check_legacy_create:
        default_id=users['teacher']['id'].replace('-','')
        ids=data.get('legacyProblemIds',[])
        if not ids:
            template=lib['problems'][0]
            clients=[Client(users['teacher']['username']) for _ in range(2)]
            with futures.ThreadPoolExecutor(2) as pool:
                created=list(pool.map(lambda pair:pair[1].call('POST','/teacher/problems',dict(template,title=f'兼容入口验证 {tag} {pair[0]+1}',state='DRAFT'),expected=201),enumerate(clients)))
            ids=[p['id'] for p in created];data['legacyProblemIds']=ids
            STATE.write_text(json.dumps(data,ensure_ascii=False,indent=2));STATE.chmod(0o600)
        default=teacher.call('GET','/teacher/libraries/'+default_id)
        assert set(ids).issubset({p['id'] for p in default['problems']})
        assert len({p['id'] for p in default['problems']})==len(default['problems'])
        assert all(p['id'] not in ids for p in teacher.call('GET','/teacher/libraries/'+lid)['problems'])
        other.call('GET','/teacher/libraries/'+default_id,expected=403)
        student.call('POST','/teacher/problems',lib['problems'][0],expected=403)
        print('PASS: concurrent legacy create returns 201 and puts owned questions in the default library only')

if __name__=='__main__':main()
