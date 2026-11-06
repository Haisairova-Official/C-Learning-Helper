#!/usr/bin/env python3
"""Verify assignment edits on existing synthetic fixtures and restore their settings.
No accounts/assignments are created; no accepted code or paid AI request is sent.
"""
import datetime as dt
import json
from pathlib import Path
import urllib.error
import urllib.request
import uuid
from management_demo import Client
from smoke_demo import BASE

FIELDS=('title','opensAt','closesAt','aiEnabled','chatEnabled')
STATE=Path(__file__).resolve().parents[1]/'.local/management-check.json'

def editable(view):return {key:view[key] for key in FIELDS}
def frozen(view):return {key:value for key,value in view.items() if key not in FIELDS}

def main():
    data=json.loads(STATE.read_text());tag=data['tag']
    users={u['username'].rsplit('_',1)[-1]:u for u in data['users']}
    assert all(u['username'].startswith('check_'+tag+'_') for u in users.values())
    teacher=Client(users['teacher']['username']);other=Client(users['other']['username'])
    student=Client(users['s1']['username']);admin=Client(users['admin']['username'])
    views=[teacher.call('GET','/teacher/assignments/'+aid) for aid in data['assignments']]
    full=next(v for v in views if v['id']==data['chatAssignment'])
    per=next(v for v in views if v['selectionMode']=='PER_STUDENT_RANDOM')
    path='/teacher/assignments/'+full['id'];original=editable(full)
    Client().call('PUT',path,original,expected=401)
    student.call('PUT',path,original,expected=403);other.call('PUT',path,original,expected=403)
    teacher.call('PUT','/teacher/assignments/'+str(uuid.uuid4()),original,expected=404)
    request=urllib.request.Request(BASE+path,json.dumps(original).encode(),{'Content-Type':'application/json'},method='PUT')
    try:teacher.client.open(request,timeout=10);raise AssertionError('Missing CSRF accepted')
    except urllib.error.HTTPError as error:assert error.code==403
    print('PASS: anonymous, student, other teacher, missing assignment and missing CSRF rejected')
    invalid=[dict(original,title=' '),dict(original,title='题'*201),dict(original,opensAt=None),dict(original,closesAt=None),
             dict(original,closesAt=original['opensAt']),dict(original,opensAt=original['closesAt'],closesAt=original['opensAt']),
             dict(original,aiEnabled=None),dict(original,chatEnabled=None),dict(original,opensAt='not-a-time')]
    for value in invalid:teacher.call('PUT',path,value,expected=400)
    assert teacher.call('GET',path)==full
    print('PASS: invalid name, times and missing switches rejected without changing the assignment')
    now=dt.datetime.now(dt.timezone.utc)
    for before in (full,per):
        aid=before['id'];endpoint='/teacher/assignments/'+aid;restore=editable(before)
        public='/student/assignments/'+aid
        student_before=student.call('GET',public)
        results=teacher.call('GET',endpoint+'/results')
        if before['selectionMode']=='PER_STUDENT_RANDOM':assert any(row['score']>0 for row in results['items'])
        problem=student_before['problems'][0]
        room=f"/assignments/{aid}/problems/{problem['id']}/chat"
        mutes=f'/assignments/{aid}/chat/mutes'
        old_mutes=teacher.call('GET',mutes);temporary_mute=not any(v['studentId']==users['s2']['id'] for v in old_mutes)
        if temporary_mute:teacher.call('POST',mutes,{'studentId':users['s2']['id']},expected=204)
        try:
            muted=teacher.call('GET',mutes);messages=teacher.call('GET',room)
            change=dict(restore,title='参数修改验证 '+tag,opensAt=(now-dt.timedelta(hours=2)).isoformat(),
                        closesAt=(now+dt.timedelta(days=1)).isoformat(),aiEnabled=True,chatEnabled=False)
            updated=teacher.call('PUT',endpoint,change)
            assert frozen(updated)==frozen(before) and updated['title']==change['title']
            assert updated['aiEnabled'] and not updated['chatEnabled']
            assert dt.datetime.fromisoformat(updated['opensAt'].replace('Z','+00:00'))==now-dt.timedelta(hours=2)
            assert dt.datetime.fromisoformat(updated['closesAt'].replace('Z','+00:00'))==now+dt.timedelta(days=1)
            assert frozen(student.call('GET',public))==frozen(student_before)
            assert teacher.call('GET',endpoint+'/results')==results
            assert teacher.call('GET',room)==messages and teacher.call('GET',mutes)==muted
            student.call('GET',room,expected=403)
            student.call('POST',room,{'body':'disabled discussion check'},str(uuid.uuid4()),expected=403)
            # Disable AI before issuing a request: it must fail before any provider call.
            disabled=dict(change,aiEnabled=False,chatEnabled=True)
            admin.call('PUT',endpoint,disabled)
            ai={'assignmentId':aid,'problemId':problem['id'],'problemVersionId':problem['versionId'],'sourceCode':'int main(void){return 0;}'}
            assert student.call('POST','/ai/analyses',ai,expected=403)['code']=='AI_DISABLED'
            assert student.call('GET',room)['items']==messages['items']
            assert teacher.call('GET',mutes)==muted
            submission=dict(ai,language='C17')
            teacher.call('PUT',endpoint,dict(disabled,closesAt=(now-dt.timedelta(hours=1)).isoformat()))
            assert student.call('POST','/submissions',submission,str(uuid.uuid4()),expected=409)['code']=='ASSIGNMENT_CLOSED'
            teacher.call('PUT',endpoint,dict(disabled,opensAt=(now+dt.timedelta(hours=1)).isoformat(),closesAt=(now+dt.timedelta(hours=2)).isoformat()))
            assert student.call('GET',public+'/problems/'+problem['id'],expected=403)['code']=='ASSIGNMENT_NOT_OPEN'
        finally:
            admin.call('PUT',endpoint,restore)
            if temporary_mute:teacher.call('DELETE',mutes+'/'+users['s2']['id'],expected=204)
        assert teacher.call('GET',endpoint)==before
        assert student.call('GET',public)==student_before
        assert teacher.call('GET',endpoint+'/results')==results
        assert teacher.call('GET',mutes)==old_mutes
        print('PASS:',before['selectionMode'],'teacher/admin edits take effect; versions, selection, class, scores, messages and mutes preserved; settings restored')

if __name__=='__main__':main()
