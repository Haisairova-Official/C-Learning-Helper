#!/usr/bin/env python3
"""Opt-in two AI style checks against the existing independently authored function demo."""
import argparse,json,time
from pathlib import Path
import smoke_demo as smoke
from ai_live_check import start_analysis

def main():
    p=argparse.ArgumentParser();p.add_argument('--run-live',action='store_true');args=p.parse_args()
    if not args.run_live:p.error('Two real inference requests require --run-live')
    smoke.BASE='http://127.0.0.1:5173/api'
    report=json.loads(Path('.local/functions-profile-result.json').read_text())
    test=smoke.Api();test.login('student2');profile=test.call('GET','/profile')
    bundle=json.loads(Path('examples/pta/function-insert.json').read_text())
    definition=bundle['problems'][1];aid=report['assignmentId'];pid=report['problemIds'][1]
    public=test.call('GET',f'/student/assignments/{aid}/problems/{pid}')
    code=definition['starterCode'].replace('return false;','return true;')
    results=[]
    try:
        for style in ['CONCISE','DETAILED']:
            test.call('PUT','/profile',{'nickname':profile['nickname'],'bio':profile['bio'],'aiReplyStyle':style})
            accepted=start_analysis(test,{'assignmentId':aid,'problemId':pid,'problemVersionId':public['versionId'],'sourceCode':code})
            test.call('PUT','/profile',{'nickname':profile['nickname'],'bio':profile['bio'],'aiReplyStyle':'GUIDED'})
            for _ in range(200):
                result=test.call('GET','/ai/analyses/'+accepted['id'])
                if result['status'] in ['COMPLETED','FAILED']:break
                time.sleep(.3)
            results.append(result)
            assert result['replyStyle']==style and result['providerMode']=='DEEPSEEK'
            assert result['status']=='COMPLETED' and result['findings'],(style,result['status'],result['errorCode'])
            serialized=json.dumps(result,ensure_ascii=False)
            assert '```' not in serialized and definition['referenceAnswer'] not in serialized and 'judgeTemplate' not in serialized
            print(style,result['status'],result['summary'],flush=True)
            for finding in result['findings']:print('Hint:',finding['hint'],flush=True)
    finally:
        test.call('PUT','/profile',{k:profile[k] for k in ['nickname','bio','aiReplyStyle']})
        test.call('POST','/auth/logout',expected=204)
        Path('.local/ai-style-result.json').write_text(json.dumps(results,ensure_ascii=False,indent=2)+'\n')

if __name__=='__main__':main()
