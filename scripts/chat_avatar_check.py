#!/usr/bin/env python3
"""Read-only chat verification with a temporary synthetic avatar and restored switch.
Never posts a chat message, creates an account, runs student code or calls AI.
"""
import base64
import json
from pathlib import Path
import urllib.request
import uuid
from management_demo import Client
from smoke_demo import BASE

STATE=Path(__file__).resolve().parents[1]/'.local/management-check.json'
PNG=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=')

def binary(client,path):
    if path.startswith('/api/'):path=path[4:]
    response=client.client.open(urllib.request.Request(BASE+path),timeout=10)
    value=response.read()
    assert response.status==200 and response.headers.get_content_type()=='image/png'
    assert value.startswith(b'\x89PNG')
    assert 'private' in response.headers.get('Cache-Control','') and 'no-store' in response.headers.get('Cache-Control','')
    assert response.headers.get('Vary')=='Cookie' and response.headers.get('X-Content-Type-Options')=='nosniff'
    return value

def upload(admin,path,png):
    boundary='avatar-check-'+uuid.uuid4().hex
    body=(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="check.png"\r\nContent-Type: image/png\r\n\r\n'.encode()+png+f'\r\n--{boundary}--\r\n'.encode())
    return admin.call('POST',path,raw=body,content_type='multipart/form-data; boundary='+boundary)

def content(messages):
    return {**messages,'items':[{k:v for k,v in item.items() if k!='authorAvatarUrl'} for item in messages['items']]}

def main():
    state=json.loads(STATE.read_text());tag=state['tag']
    users={u['username'].rsplit('_',1)[-1]:u for u in state['users']}
    assert all(u['username'].startswith('check_'+tag+'_') for u in users.values())
    admin=Client(users['admin']['username']);teacher=Client(users['teacher']['username'])
    viewer=Client(users['s2']['username']);other=Client(users['other']['username']);outsider=Client('student2')
    members=teacher.call('GET','/classes/'+state['classes'][0]+'/members')['items']
    assert outsider.call('GET','/auth/me')['id'] not in {v['id'] for v in members},'Expected a nonmember demo account'
    aid=state['chatAssignment'];pid=state['chatProblem']
    assignment_path='/teacher/assignments/'+aid
    assignment=teacher.call('GET',assignment_path)
    restore={k:assignment[k] for k in ('title','opensAt','closesAt','aiEnabled','chatEnabled')}
    assert restore['chatEnabled'],'Do not enable a discussion that was intentionally closed'
    room=f'/assignments/{aid}/problems/{pid}/chat'
    before=viewer.call('GET',room)
    assert before['items'] and all('authorAvatarUrl' in m for m in before['items'])
    fixture_ids={u['id'] for u in users.values()}
    author=next(m['authorId'] for m in before['items'] if m['authorId'] in fixture_ids)
    avatar=room+'/participants/'+author+'/avatar'
    admin_avatar='/admin/users/'+author+'/avatar'
    profile=admin.call('GET','/admin/users/'+author)
    original_png=binary(admin,admin_avatar) if profile['avatarUrl'] else None
    absent_room=None
    for problem in assignment['problems']:
        candidate=f"/assignments/{aid}/problems/{problem['id']}/chat"
        rows=teacher.call('GET',candidate)['items']
        if len(rows)<50 and not any(m['authorId']==author for m in rows):absent_room=candidate;break
    assert absent_room,'A fixture room without this author is required'
    try:
        upload(admin,admin_avatar,PNG)
        messages=viewer.call('GET',room)
        urls={m['authorAvatarUrl'] for m in messages['items'] if m['authorId']==author}
        assert len(urls)==1
        url=urls.pop();assert url and url.startswith('/api'+avatar+'?v=')
        binary(viewer,url);binary(teacher,url);binary(admin,url)
        assert content(messages)==content(before)
        upload(admin,admin_avatar,PNG)
        changed=viewer.call('GET',room)
        new_urls={m['authorAvatarUrl'] for m in changed['items'] if m['authorId']==author}
        assert len(new_urls)==1 and next(iter(new_urls))!=url
        print('PASS: message avatar URLs share one author/room address, refresh on change and return private PNG only')
        Client().call('GET',avatar,expected=401)
        outsider.call('GET',avatar,expected=403);other.call('GET',avatar,expected=403)
        viewer.call('GET',absent_room+'/participants/'+author+'/avatar',expected=404)
        viewer.call('GET',room+'/participants/'+str(uuid.uuid4())+'/avatar',expected=404)
        viewer.call('GET',admin_avatar,expected=403)
        per=next(v for a in state['assignments'] if (v:=teacher.call('GET','/teacher/assignments/'+a))['selectionMode']=='PER_STUDENT_RANDOM')
        drawn=viewer.call('GET','/student/assignments/'+per['id'])
        hidden=next(p for p in per['problems'] if p['id'] not in {x['id'] for x in drawn['problems']})
        viewer.call('GET',f"/assignments/{per['id']}/problems/{hidden['id']}/chat/participants/{author}/avatar",expected=404)
        teacher.call('PUT',assignment_path,dict(restore,chatEnabled=False))
        assert viewer.call('GET',avatar,expected=403)['code']=='CHAT_DISABLED'
        binary(teacher,avatar)
        teacher.call('PUT',assignment_path,restore)
        admin.call('DELETE',admin_avatar)
        no_avatar=viewer.call('GET',room)
        assert all(m['authorAvatarUrl'] is None for m in no_avatar['items'] if m['authorId']==author)
        viewer.call('GET',avatar,expected=404)
        print('PASS: anonymous/nonmember/other teacher/unassigned question/closed discussion denied; no room message or no avatar returns 404')
    finally:
        try:teacher.call('PUT',assignment_path,restore)
        finally:
            if original_png is None:admin.call('DELETE',admin_avatar)
            else:upload(admin,admin_avatar,original_png)
    assert teacher.call('GET',assignment_path)==assignment
    after=viewer.call('GET',room);assert content(after)==content(before)
    restored=admin.call('GET','/admin/users/'+author)
    assert {k:v for k,v in restored.items() if k!='avatarUrl'}=={k:v for k,v in profile.items() if k!='avatarUrl'}
    if original_png is None:
        assert restored['avatarUrl'] is None
    else:assert binary(admin,admin_avatar)==original_png
    print('PASS: original avatar, profile, assignment switch and all existing messages restored; no messages sent')

if __name__=='__main__':main()
