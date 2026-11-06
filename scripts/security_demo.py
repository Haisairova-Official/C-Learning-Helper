#!/usr/bin/env python3
"""Non-destructive negative API checks against the local demo only."""
import io
import json
from pathlib import Path
import urllib.parse
import uuid
import zipfile
from smoke_demo import Api, PASSWORD

checks=[]
def passed(name):
    checks.append(name)
    print('PASS:', name, flush=True)

def multipart(data, filename, kind):
    boundary='CassistSecurity'+uuid.uuid4().hex
    body=(f'--{boundary}\r\nContent-Disposition: form-data; name="sourceType"\r\n\r\n{kind}\r\n'
          f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\nContent-Type: application/octet-stream\r\n\r\n').encode()+data+f'\r\n--{boundary}--\r\n'.encode()
    return body,'multipart/form-data; boundary='+boundary

def main():
    teacher, student, other, anonymous=Api(),Api(),Api(),Api()
    teacher.login('teacher'); actor=student.login('student'); other.login('student2')
    anonymous.call('GET','/teacher/problems',expected=401)
    anonymous.call('GET','/submissions',expected=401)
    passed('匿名用户无法访问教师题库或提交记录')
    student.call('GET','/teacher/problems',expected=403)
    student.call('POST','/imports/parse',{'sourceType':'TEXT','text':'第1题：无权限导入'},expected=403)
    student.call('POST','/classes',{'name':'越权创建班级'},expected=403)
    passed('学生不能读取教师答案、导入题目或创建班级')
    teacher_problem=teacher.call('GET','/teacher/problems')['items'][0]
    pid=teacher_problem['id']
    student.call('PUT','/teacher/problems/'+pid,teacher_problem,expected=403)
    passed('学生不能直接调用教师题目修改接口')
    teacher.token={'headerName':'X-CSRF-TOKEN','token':'deliberately-invalid'}
    teacher.call('POST','/imports/parse',{'sourceType':'TEXT','text':'第1题：跨站请求'},expected=403)
    teacher.token=None
    passed('已登录教师的错误 CSRF token 被拒绝')
    injected=Api()
    result=injected.call('POST','/auth/login',{'username':'student','password':PASSWORD,'role':'ADMIN','userId':'demo-admin'})
    assert result['role']=='STUDENT' and result['id']==actor['id']
    passed('伪造角色与用户ID不能升级权限')
    response=anonymous.call('POST','/auth/login',{'username':"' OR '1'='1",'password':'not-a-password'},expected=401)
    assert not any(key in response for key in ['stackTrace','sql','password','exception'])
    passed('SQL 注入式账号被当作普通输入，错误不泄露内部信息')
    imported=teacher.call('POST','/imports/parse',{'sourceType':'TEXT','text':'第1题：安全验收草稿\n题干：输出一个整数。\n参考答案：\n```c\nint main(void){return 0;}\n```'},expected=202)
    other.call('GET','/imports/'+imported['id'],expected=403)
    candidate=teacher.call('GET','/imports/'+imported['id'])['candidates'][0]
    candidate['unresolvedContent']='用途未确认的私有代码'
    teacher.call('POST','/imports/'+imported['id']+'/commit',{'title':'不应被保存','candidates':[candidate]},str(uuid.uuid4()),expected=400)
    passed('导入预览受权限保护，归属不明确内容不能直接公开入库')
    upload,ctype=multipart(b'not-an-allowed-format','script.exe','TXT')
    teacher.call('POST','/imports/parse',expected=400,raw=upload,content_type=ctype)
    passed('伪装类型的上传文件被拒绝')
    data=io.BytesIO()
    with zipfile.ZipFile(data,'w',zipfile.ZIP_DEFLATED) as z:
        z.writestr('word/document.xml','<!DOCTYPE x [<!ENTITY leak SYSTEM "file:///nonexistent-cassist-security-canary">]><x>&leak;</x>')
    upload,ctype=multipart(data.getvalue(),'external-entity.docx','DOCX')
    teacher.call('POST','/imports/parse',expected=400,raw=upload,content_type=ctype)
    passed('DOCX 外部实体声明被拒绝，不能通过解析器读取文件')
    data=io.BytesIO()
    with zipfile.ZipFile(data,'w',zipfile.ZIP_DEFLATED) as z:
        z.writestr('word/document.xml',' '*(9*1024*1024))
    upload,ctype=multipart(data.getvalue(),'expanded.docx','DOCX')
    teacher.call('POST','/imports/parse',expected=400,raw=upload,content_type=ctype)
    passed('DOCX 高压缩率超限内容被拒绝')
    assignment=student.call('GET','/student/assignments')['items'][0]
    problem=assignment['problems'][0]
    base={'assignmentId':assignment['id'],'problemId':problem['id'],'problemVersionId':problem['versionId'],'sourceCode':'int main(void){return 0;}'}
    fake=dict(base,problemVersionId='unpublished-forged-version',language='C17')
    student.call('POST','/submissions',fake,str(uuid.uuid4()),expected=404)
    passed('伪造题目版本无法进入评测队列')
    student.call('POST','/submissions',dict(base,language='C17',sourceCode='x'*70000),str(uuid.uuid4()),expected=400)
    passed('超长学生代码在提交前被拒绝')
    history=student.call('GET','/submissions?assignmentId='+urllib.parse.quote(assignment['id'])+'&problemId='+urllib.parse.quote(problem['id']))['items']
    assert history
    other.call('GET','/submissions/'+history[0]['id'],expected=403)
    passed('更改提交ID不能读取他人代码和成绩')
    teacher.call('POST','/auth/logout',{},expected=204)
    teacher.call('GET','/teacher/problems',expected=401)
    passed('退出后的旧会话不能继续读取教师数据')
    destination=Path(__file__).resolve().parents[1]/'.local'/'security-api-result.json'
    destination.parent.mkdir(exist_ok=True)
    destination.write_text(json.dumps({'passed':checks,'scope':'local demo HTTP API; no real school model inference'},ensure_ascii=False,indent=2))
    print('PASS:',len(checks),'negative checks; report:',destination)

if __name__=='__main__': main()
