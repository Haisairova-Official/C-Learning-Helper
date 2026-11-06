#!/usr/bin/env python3
"""Create a four-colour demo using real judging; no AI requests or changes to existing submissions."""
import datetime as dt
import json
from pathlib import Path
import uuid
import smoke_demo as smoke


def main():
    smoke.BASE = 'http://127.0.0.1:5173/api'
    teacher, student, other = smoke.Api(), smoke.Api(), smoke.Api()
    teacher.login('teacher'); actor = student.login('student'); other.login('student2')
    original = json.loads(Path('.local/navigation-demo.json').read_text())
    before = student.call('GET', '/student/assignments/' + original['assignmentId'])
    ids = original['problemIds'][:3]
    fourth = teacher.call('GET', '/teacher/problems/' + ids[-1])
    fourth['id'] = None; fourth['versionId'] = None; fourth['state'] = 'DRAFT'
    fourth['title'] = '目录状态体验：待完成的练习'
    fourth['referenceAnswer'] = '#include <stdio.h>\nint main(void){int a,b;if(scanf("%d%d",&a,&b)!=2)return 1;printf("%d\\n",a+b);return 0;}'
    for case in fourth['testCases']: case['id'] = str(uuid.uuid4())
    created = teacher.call('POST', '/teacher/problems', fourth, expected=201); ids.append(created['id'])
    classes = teacher.call('GET', '/classes')['items']
    classroom = next(c for c in classes if any(m['id'] == actor['id'] for m in teacher.call('GET', '/classes/'+c['id']+'/members')['items']))
    now = dt.datetime.now(dt.timezone.utc)
    assignment = teacher.call('POST', '/teacher/assignments', {
        'title': '目录状态体验（4题）', 'classId': classroom['id'], 'problemIds': ids,
        'opensAt': (now-dt.timedelta(minutes=1)).isoformat(), 'closesAt': (now+dt.timedelta(days=30)).isoformat(),
    }, expected=201)
    aid = assignment['id']
    initial = student.call('GET', '/student/assignments/'+aid)
    assert all(p['progress']['status'] == 'UNSUBMITTED' for p in initial['problems'])
    correct = '#include <stdio.h>\nint main(void){int a,b;if(scanf("%d%d",&a,&b)!=2)return 1;printf("%d\\n",a+b);return 0;}'
    records = []
    for index, code, verdict in [(0,'#include <stdio.h>\nint main(void){puts("999");return 0;}', 'WRONG_ANSWER'),
                                 (1,'#include <stdio.h>\nint main(void){puts("3");return 0;}', 'PARTIAL_ACCEPTED'),
                                 (2,correct,'ACCEPTED'),
                                 (2,'#include <stdio.h>\nint main(void){puts("999");return 0;}', 'WRONG_ANSWER')]:
        p = initial['problems'][index]
        accepted = student.call('POST', '/submissions', {'assignmentId': aid, 'problemId': p['id'], 'problemVersionId': p['versionId'], 'sourceCode': code, 'language': 'C17'}, str(uuid.uuid4()), expected=202)
        result = smoke.await_submission(student, accepted['id'])
        assert result['verdict'] == verdict, (index, result['verdict'])
        records.append({'id': result['id'], 'verdict': result['verdict'], 'score': result['score']})
    actual = student.call('GET', '/student/assignments/'+aid)
    statuses = [p['progress']['status'] for p in actual['problems']]
    assert statuses == ['WRONG', 'PARTIAL', 'ACCEPTED', 'UNSUBMITTED'], statuses
    assert actual['problems'][2]['progress']['bestScore'] == 100
    assert all(p['progress']['status'] == 'UNSUBMITTED' for p in other.call('GET', '/student/assignments/'+aid)['problems'])
    assert all(p['progress'] is None for p in teacher.call('GET', '/teacher/assignments/'+aid)['problems'])
    # The user may keep submitting in the original assignment while this script runs.
    # Check our known submission IDs rather than expecting their live score to stay unchanged.
    created_ids = {record['id'] for record in records}
    for p in before['problems']:
        original_rows = student.call('GET', '/submissions?assignmentId='+original['assignmentId']+'&problemId='+p['id']+'&pageSize=100')['items']
        assert not created_ids.intersection(r['id'] for r in original_rows)
    assert 'referenceAnswer' not in json.dumps(actual) and 'testCases' not in json.dumps(actual)
    url = f'http://127.0.0.1:5173/assignments/{aid}/problems/{ids[-1]}'
    report = {'assignmentId': aid, 'problemIds': ids, 'statuses': statuses, 'records': records, 'url': url,
              'bestPreserved': True, 'studentIsolation': True, 'assignmentIsolation': True, 'teacherProgressNull': True}
    Path('.local/progress-demo-result.json').write_text(json.dumps(report, ensure_ascii=False, indent=2))
    for client in (teacher, student, other): client.call('POST', '/auth/logout', expected=204)
    print('PASS: four real statuses, best preserved, student and assignment isolation')
    print(url)


if __name__ == '__main__': main()
