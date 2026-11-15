<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue';
import type { Problem, Verdict } from '../api/types';
import { post, request } from '../api/http';
import { poll } from '../lib/poll';
import { label } from '../lib/format';
const props=defineProps<{problem:Problem;assignmentId:string;code:string}>();
interface Trial {id:string;status:'RUNNING'|'COMPLETED';result:null|{verdict:Verdict;stdout:string;stderr:string;compileMessage:string;timeMs:number|null;memoryKb:number|null}}
const stdin=ref(''),expected=ref(''),compare=ref(false),driver=ref(''),busy=ref(false),error=ref(''),trial=ref<Trial>();
let abort=new AbortController();let destroyed=false;let lastInput='';let lastCompared=false;
const signature=computed(()=>JSON.stringify([props.code,stdin.value,compare.value,expected.value,driver.value]));
const stale=computed(()=>!!trial.value && signature.value!==lastInput);
const resultTitle=computed(()=>trial.value?.result?.verdict==='ACCEPTED'?(lastCompared?'输出一致':'运行完成'):label(trial.value?.result?.verdict||''));
function sample(index:number){const s=props.problem.samples[index];if(s){stdin.value=s.input;expected.value=s.output;compare.value=true;}}
async function readResult(id:string){
  abort.abort();abort=new AbortController();
  await poll(signal=>request<Trial>(`/trials/${encodeURIComponent(id)}`,{signal}),v=>v.status==='COMPLETED',v=>{trial.value=v;},abort.signal,1000,300);
}
async function run(){
 if(busy.value)return;
 error.value='';
 if(!props.code.trim()){error.value='请先编写代码';return;}
 const bytes=(s:string)=>new TextEncoder().encode(s).length;
 if(bytes(props.code)>props.problem.codeLimitBytes){error.value='代码超过题目长度限制';return;}
 if(bytes(stdin.value)>65536 || compare.value && bytes(expected.value)>65536){error.value='输入和预期输出最多各 64 KiB';return;}
 if(props.problem.problemType==='FUNCTION' && !driver.value.trim()){error.value='请填写包含 main 的测试程序';return;}
 busy.value=true;trial.value=undefined;lastInput=signature.value;lastCompared=compare.value;
 try{
   trial.value=await post<Trial>('/trials',{assignmentId:props.assignmentId,problemId:props.problem.id,problemVersionId:props.problem.versionId,sourceCode:props.code,language:props.problem.language,stdin:stdin.value,expectedOutput:compare.value?expected.value:null,driverCode:driver.value});
   if(destroyed)return;
   if(trial.value.status!=='COMPLETED')await readResult(trial.value.id);
 }catch(e){if((e as Error).name!=='AbortError')error.value=(e as Error).message;}finally{busy.value=false;}
}
async function retry(){if(!trial.value || busy.value)return;busy.value=true;error.value='';try{await readResult(trial.value.id);}catch(e){if((e as Error).name!=='AbortError')error.value=(e as Error).message;}finally{busy.value=false;}}
onBeforeUnmount(()=>{destroyed=true;abort.abort();});
</script>
<template><div class="trial-panel"><div v-if="problem.samples.length" class="sample-actions"><el-button v-for="(_,i) in problem.samples" :key="i" size="small" :disabled="busy" @click="sample(i)">填入样例 {{i+1}}</el-button></div><el-form label-position="top"><el-form-item v-if="problem.problemType==='FUNCTION'" label="测试程序（包含 main）"><el-input v-model="driver" type="textarea" :rows="6" :disabled="busy" placeholder="编写 main，调用上方函数并打印结果。测试程序会接在函数代码之后。"/><span class="muted">可在测试程序中加入所需的头文件；不会影响正式提交的函数代码。</span></el-form-item><el-form-item label="标准输入"><el-input v-model="stdin" type="textarea" :rows="4" :disabled="busy" placeholder="输入程序需要读取的测试数据，也可以留空"/></el-form-item><el-checkbox v-model="compare" :disabled="busy">对照预期输出</el-checkbox><el-form-item v-if="compare" label="预期输出"><el-input v-model="expected" type="textarea" :rows="4" :disabled="busy" placeholder="填写期望结果，也支持空输出"/><span class="muted">按文本比较；允许 CRLF/LF 与末尾一个换行的差异。</span></el-form-item></el-form><el-button type="primary" :loading="busy" @click="run">运行测试</el-button><el-alert v-if="error" class="mt" :title="error" type="error" :closable="false"/><el-button v-if="error && trial?.status==='RUNNING'" class="mt" :loading="busy" @click="retry">重新查询测试结果</el-button><p v-if="stale" class="muted">代码或测试数据已更改，下方为上次运行结果。</p><div v-if="trial?.result" class="trial-result"><div class="section-bar"><strong>{{resultTitle}}</strong><span v-if="trial.result.timeMs!==null">{{trial.result.timeMs}} ms · {{trial.result.memoryKb}} KiB</span></div><p v-if="trial.result.verdict==='SYSTEM_ERROR'" role="alert">测试服务暂不可用，请稍后重试。</p><pre v-if="trial.result.compileMessage" class="diagnostic">{{trial.result.compileMessage}}</pre><template v-else-if="trial.result.verdict!=='SYSTEM_ERROR'"><h3>实际输出</h3><pre class="diagnostic trial-output">{{trial.result.stdout || '（无输出）'}}</pre><template v-if="trial.result.stderr"><h3>标准错误</h3><pre class="diagnostic trial-output">{{trial.result.stderr}}</pre></template></template></div></div></template>
<style scoped>.trial-panel p{line-height:1.7}.sample-actions{display:flex;gap:8px;flex-wrap:wrap;margin:12px 0}.sample-actions .el-button{margin:0}.trial-panel :deep(textarea){font-family:monospace;white-space:pre}.trial-result{margin-top:20px}.trial-result h3{font-size:13px;margin:16px 0 8px}.trial-output{white-space:pre;overflow:auto;max-height:280px;min-height:44px}.trial-panel .muted{font-size:12px}</style>
