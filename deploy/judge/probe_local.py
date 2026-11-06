#!/usr/bin/env python3
"""Run a bounded, benign compilation and verify file/network isolation in local go-judge."""
import json, os, pathlib, urllib.request
runtime=pathlib.Path('/tmp')/f'cassist-judge-{os.getuid()}'
settings=dict(line.split('=',1) for line in (runtime/'judge.env').read_text().splitlines())
base=settings['JUDGE_BASE_URL']; token=settings['JUDGE_TOKEN']
sentinel=runtime/'host-only-sentinel.txt'; sentinel.write_text('isolation marker')
source=r'''#include <stdio.h>
#include <unistd.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
int main(void) {
  FILE *f=fopen("HOST_SENTINEL", "r");
  int files_blocked=(f==NULL); if(f) fclose(f);
  int s=socket(AF_INET,SOCK_STREAM,0), network_blocked=1;
  if(s>=0){struct sockaddr_in a={0};a.sin_family=AF_INET;a.sin_port=htons(15050);
    inet_pton(AF_INET,"127.0.0.1",&a.sin_addr);network_blocked=connect(s,(struct sockaddr*)&a,sizeof(a))<0;close(s);}
  printf("files_blocked=%d network_blocked=%d\n",files_blocked,network_blocked);
  return files_blocked&&network_blocked?0:9;
}'''.replace('HOST_SENTINEL',str(sentinel))
def request(path,payload=None,method=None):
    data=json.dumps(payload).encode() if payload is not None else None
    r=urllib.request.Request(base+path,data=data,method=method,headers={'Authorization':'Bearer '+token,'Content-Type':'application/json'})
    with urllib.request.urlopen(r,timeout=25) as response:
        return json.load(response) if method!='DELETE' else None
compile_cmd={'args':['/usr/bin/gcc','-std=c17','-O2','main.c','-o','main'],
 'env':['PATH=/usr/bin:/bin','LC_ALL=C'],'files':[{'content':''},{'name':'stdout','max':65536},{'name':'stderr','max':65536}],
 'cpuLimit':10_000_000_000,'clockLimit':20_000_000_000,'memoryLimit':536870912,'procLimit':16,
 'copyIn':{'main.c':{'content':source}},'copyOutCached':['main?'],'copyOutMax':16777216}
binary=None
try:
    result=request('/run',{'cmd':[compile_cmd]})[0]
    if result['status']!='Accepted': raise RuntimeError('Bounded compilation failed: '+result.get('files',{}).get('stderr',''))
    binary=result['fileIds']['main']
    run={'args':['./main'],'env':['PATH=/usr/bin:/bin','LC_ALL=C'],'files':[{'content':''},{'name':'stdout','max':65536},{'name':'stderr','max':65536}],
         'cpuLimit':1_000_000_000,'clockLimit':3_000_000_000,'memoryLimit':67108864,'procLimit':1,'copyIn':{'main':{'fileId':binary}}}
    result=request('/run',{'cmd':[run]})[0]
    print(json.dumps({'status':result['status'],'output':result.get('files',{}).get('stdout',''),'timeNs':result.get('time'),'memoryBytes':result.get('memory')},ensure_ascii=False))
    assert result['status']=='Accepted' and result['files']['stdout']=='files_blocked=1 network_blocked=1\n'
finally:
    if binary: request('/file/'+binary,method='DELETE')
    sentinel.unlink(missing_ok=True)
