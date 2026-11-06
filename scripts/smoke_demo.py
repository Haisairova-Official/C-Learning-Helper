#!/usr/bin/env python3
"""End-to-end checks against the explicit local demo. Creates an identifiable demo assignment."""
import datetime as dt
import hashlib
import http.cookiejar
import io
import json
import os
from pathlib import Path
import time
import urllib.error
import urllib.request
import uuid
import zipfile

BASE = os.environ.get('CASSIST_BASE_URL', 'http://127.0.0.1:8080/api')
PASSWORD = os.environ.get('CASSIST_DEMO_PASSWORD', 'CassistDemo2026!')
RESULTS = []


class Api:
    def __init__(self):
        self.client = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.token = None

    def call(self, method, path, data=None, key=None, expected=200, raw=None, content_type=None):
        headers = {}
        if method not in ('GET', 'HEAD'):
            if self.token is None:
                self.token = self.call('GET', '/auth/csrf')
            headers[self.token['headerName']] = self.token['token']
        if key:
            headers['Idempotency-Key'] = key
        body = raw
        if data is not None:
            body = json.dumps(data, ensure_ascii=False).encode()
            headers['Content-Type'] = 'application/json'
        elif content_type:
            headers['Content-Type'] = content_type
        req = urllib.request.Request(BASE + path, body, headers, method=method)
        try:
            response = self.client.open(req, timeout=20)
        except urllib.error.HTTPError as error:
            response = error
        payload = response.read()
        value = json.loads(payload) if payload else None
        assert response.status == expected, (method, path, response.status, value.get('code') if isinstance(value, dict) else '')
        return value

    def login(self, username):
        actor = self.call('POST', '/auth/login', {'username': username, 'password': PASSWORD})
        self.token = None
        return actor


def ok(name):
    RESULTS.append(name)
    print('PASS:', name, flush=True)


def await_submission(client, sid):
    for _ in range(160):
        result = client.call('GET', '/submissions/' + sid)
        if result['status'] in ('FINISHED', 'SYSTEM_ERROR'):
            return result
        time.sleep(.25)
    raise AssertionError('Submission did not finish within 40 seconds')


def main():
    teacher, student, other = Api(), Api(), Api()
    teacher.login('teacher')
    actor = student.login('student')
    other.login('student2')
    ok('Session 登录及 CSRF 轮换')
    student.call('GET', '/teacher/problems', expected=403)
    ok('学生无法读取教师题库')
    classes = teacher.call('GET', '/classes')['items']
    selected = None
    for classroom in classes:
        members = teacher.call('GET', '/classes/' + classroom['id'] + '/members')['items']
        if any(m['id'] == actor['id'] for m in members):
            selected = classroom['id']
            break
    assert selected, 'Demo student must belong to a teacher-managed class'
    source = '#include <stdio.h>\nint main(void){int a,b;if(scanf("%d%d",&a,&b)!=2)return 1;printf("%d\\n",a+b);return 0;}\n'
    text = ('第1题：两数求和（导入演示）\n题干：读取两个整数，输出它们的和。\n'
            '输入格式：一行两个整数，以空格分隔。\n输出格式：输出它们的和。\n'
            '输入样例：\n1 2\n输出样例：\n3\n分值：100\n参考答案：\n```c\n' + source + '```\n')
    job = teacher.call('POST', '/imports/parse', {'sourceType': 'TEXT', 'text': text}, expected=202)
    preview = teacher.call('GET', '/imports/' + job['id'])
    assert preview['status'] == 'READY' and len(preview['candidates']) == 1
    student.call('GET', '/imports/' + job['id'], expected=403)
    candidate = preview['candidates'][0]
    assert '#include' in candidate['referenceAnswer'] and '#include' not in candidate['statement']
    candidate['unresolvedContent'] = ''
    candidate['maxScore'] = 100
    candidate['testCases'] = [
        {'key': '1', 'input': '1 2\n', 'expectedOutput': '3\n', 'visibility': 'HIDDEN', 'score': 50, 'comparisonMode': 'TOKENS'},
        {'key': '2', 'input': '-3 1\n', 'expectedOutput': '-2\n', 'visibility': 'HIDDEN', 'score': 50, 'comparisonMode': 'TOKENS'},
    ]
    key = str(uuid.uuid4())
    commit_input = {'title': '导入演示 · 两数求和', 'candidates': [candidate]}
    committed = teacher.call('POST', '/imports/' + job['id'] + '/commit', commit_input, key, expected=201)
    replay = teacher.call('POST', '/imports/' + job['id'] + '/commit', commit_input, key, expected=201)
    assert replay == committed
    ok('真实文本解析、参考答案隔离、草稿入库和重复确认')

    now = dt.datetime.now(dt.timezone.utc)
    assignment = teacher.call('POST', '/teacher/assignments', {
        'title': '导入与评测体验', 'classId': selected, 'problemIds': committed['problemIds'],
        'opensAt': (now - dt.timedelta(minutes=2)).isoformat(), 'closesAt': (now + dt.timedelta(days=30)).isoformat(),
    }, expected=201)
    aid, pid = assignment['id'], committed['problemIds'][0]
    problem = student.call('GET', f'/student/assignments/{aid}/problems/{pid}')
    assert 'referenceAnswer' not in problem and 'testCases' not in problem and '#include' not in json.dumps(problem)
    ok('发布作业、学生授权可见、隐藏测试与答案不进入响应')
    common = {'assignmentId': aid, 'problemId': pid, 'problemVersionId': problem['versionId'], 'language': 'C17'}
    submitted_ids = []
    for label, code, expected in [
        ('正确程序', source, 'ACCEPTED'),
        ('错误输出', '#include <stdio.h>\nint main(void){puts("0");return 0;}', 'WRONG_ANSWER'),
        ('编译错误', 'this is not a C program', 'COMPILATION_ERROR'),
        ('运行超时', 'int main(void){for(;;){}return 0;}', 'TIME_LIMIT_EXCEEDED'),
    ]:
        request = dict(common, sourceCode=code)
        key = str(uuid.uuid4())
        accepted = student.call('POST', '/submissions', request, key, expected=202)
        replay = student.call('POST', '/submissions', request, key, expected=202)
        assert replay['id'] == accepted['id']
        result = await_submission(student, accepted['id'])
        assert result['verdict'] == expected, (label, result['status'], result['verdict'], result.get('compileMessage'))
        assert all(not any(k in case for k in ('input', 'expectedOutput', 'stdout', 'stderr')) for case in result['caseResults'])
        submitted_ids.append(accepted['id'])
        ok('真实隔离评测：' + label)
    other.call('GET', '/submissions/' + submitted_ids[0], expected=403)
    ok('他人提交记录不可越权读取')
    provider = student.call('GET', '/ai/analyses/provider')
    if provider['providerMode'] == 'MOCK':
        guidance = student.call('POST', '/ai/analyses', {k: v for k, v in dict(common, sourceCode=source).items() if k != 'language'}, expected=202)
        for _ in range(100):
            response = student.call('GET', '/ai/analyses/' + guidance['id'])
            if response['status'] in ('COMPLETED', 'FAILED'): break
            time.sleep(.1)
        assert response['status'] == 'COMPLETED' and response['providerMode'] == 'MOCK'
        assert '学校 AI 尚未接入' in response['summary'] and response['codeHash'] == hashlib.sha256(source.encode()).hexdigest()
        assert 'referenceAnswer' not in response and 'sourceCode' not in response
        other.call('GET', '/ai/analyses/' + guidance['id'], expected=403)
        ok('AI 入口、当前代码快照、明确模拟标识及记录权限')
    else:
        print('SKIP: real AI inference; use ai_live_check.py --run-live explicitly')
    rows = teacher.call('GET', '/teacher/assignments/' + aid + '/results')['items']
    row = next(r for r in rows if r['studentId'] == actor['id'])
    assert row['score'] == 100, row
    ok('教师成绩采用最高分')

    xml = '<?xml version="1.0"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>第1题：DOCX 导入验证</w:t></w:r></w:p><w:p><w:r><w:t>题干：读取整数并输出。</w:t></w:r></w:p></w:body></w:document>'
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, 'w', zipfile.ZIP_DEFLATED) as archive:
        archive.writestr('word/document.xml', xml)
    boundary = 'Cassist' + uuid.uuid4().hex
    upload = (f'--{boundary}\r\nContent-Disposition: form-data; name="sourceType"\r\n\r\nDOCX\r\n'
              f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="example.docx"\r\nContent-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document\r\n\r\n').encode() + buf.getvalue() + f'\r\n--{boundary}--\r\n'.encode()
    imported = teacher.call('POST', '/imports/parse', expected=202, raw=upload, content_type='multipart/form-data; boundary=' + boundary)
    assert teacher.call('GET', '/imports/' + imported['id'])['status'] == 'READY'
    ok('真实 DOCX 文件上传与解析')
    teacher.call('POST', '/auth/logout', {}, expected=204)
    teacher.call('GET', '/auth/me', expected=401)
    ok('退出后 Session 失效')
    report = {'passed': RESULTS, 'assignmentId': aid, 'problemId': pid, 'submissionIds': submitted_ids, 'schoolAi': 'explicit mock', 'database': 'local isolated MySQL compatibility instance; MariaDB target not yet verified'}
    out = Path(__file__).resolve().parents[1] / '.local' / 'smoke-result.json'
    out.parent.mkdir(exist_ok=True)
    out.write_text(json.dumps(report, ensure_ascii=False, indent=2))
    print('PASS:', len(RESULTS), 'checks; report:', out)


if __name__ == '__main__':
    main()
