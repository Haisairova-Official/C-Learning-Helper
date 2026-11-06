<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { api } from '../api';
import { actor, profile } from '../auth';
import { workspaceAi, analyseWorkspace, clearWorkspaceAi } from '../aiWorkspace';
import type { Assignment, Problem } from '../api/types';
import { readDraft } from '../lib/drafts';
import AsyncState from '../components/AsyncState.vue';
const route=useRoute(),assignments=ref<Assignment[]>([]),assignment=ref<Assignment>(),assignmentId=ref(''),problemId=ref(''),problem=ref<Problem>(),code=ref(''),loading=ref(true),error=ref('');
const byteCount=computed(()=>new TextEncoder().encode(code.value).length);
const allowed=computed(()=>assignment.value?.aiEnabled!==false);
const problemLink=computed(()=>`/assignments/${encodeURIComponent(assignmentId.value)}/problems/${encodeURIComponent(problemId.value)}`);
let version=0,destroyed=false;
const providerMode=ref('');
function reset(){clearWorkspaceAi();workspaceAi.active=true;workspaceAi.style=profile.value?.aiReplyStyle||'GUIDED';workspaceAi.provider=providerMode.value;}
reset();
watch(()=>profile.value?.aiReplyStyle,style=>{if(!workspaceAi.analysis&&!workspaceAi.busy)workspaceAi.style=style||'GUIDED';});
async function selectProblem(){
  const current=++version;reset();loading.value=true;error.value='';problem.value=undefined;code.value='';
  workspaceAi.enabled=allowed.value;
  try{if(!problemId.value){workspaceAi.ready=true;return;}const value=await api.problem(assignmentId.value,problemId.value);if(current===version&&!destroyed){problem.value=value;workspaceAi.ready=true;workspaceAi.problemTitle=value.title;code.value=actor.value?readDraft(actor.value.id,assignmentId.value,value.versionId)||'':'';}}
  catch(e){if(current===version&&!destroyed){error.value=(e as Error).message;workspaceAi.contextError=error.value;}}finally{if(current===version)loading.value=false;}
}
async function selectAssignment(preferred=''){
  const current=++version;reset();loading.value=true;error.value='';assignment.value=undefined;problem.value=undefined;code.value='';
  try{const value=await api.assignment(assignmentId.value);if(current!==version||destroyed)return;assignment.value=value;problemId.value=value.problems.some(p=>p.id===preferred)?preferred:value.problems[0]?.id||'';await selectProblem();}
  catch(e){if(current===version&&!destroyed){error.value=(e as Error).message;workspaceAi.contextError=error.value;loading.value=false;}}
}
async function load(){
  const current=++version;reset();loading.value=true;error.value='';
  void api.aiProvider().then(provider=>{if(!destroyed){providerMode.value=provider.providerMode;workspaceAi.provider=provider.providerMode;}}).catch(()=>{});
  try{
    const values=(await api.assignments()).items;
    if(current!==version||destroyed)return;
    const requested=String(route.query.assignmentId||'');
    if(requested&&!values.some(a=>a.id===requested)){
      const requestedAssignment=await api.assignment(requested);
      if(current!==version||destroyed)return;
      values.unshift(requestedAssignment);
    }
    assignments.value=values;assignmentId.value=requested||values[0]?.id||'';
    if(assignmentId.value)await selectAssignment(String(route.query.problemId||''));
    else{loading.value=false;workspaceAi.ready=true;}
  }catch(e){if(current===version&&!destroyed){error.value=(e as Error).message;workspaceAi.contextError=error.value;loading.value=false;}}
}
async function analyse(){if(problem.value)await analyseWorkspace(assignmentId.value,problem.value,code.value,profile.value?.aiReplyStyle||'GUIDED');}
onMounted(load);onBeforeUnmount(()=>{destroyed=true;version++;clearWorkspaceAi();});
</script>
<template><div class="page ai-page"><div class="page-heading"><h1>AI 辅助</h1></div><div class="panel ai-selectors"><el-form label-position="top"><div class="two-column"><el-form-item label="作业"><el-select v-model="assignmentId" placeholder="选择作业" @change="selectAssignment()"><el-option v-for="a in assignments" :key="a.id" :value="a.id" :label="a.title"/></el-select></el-form-item><el-form-item label="题目"><el-select v-model="problemId" :disabled="!assignment || loading" placeholder="选择题目" @change="selectProblem"><el-option v-for="p in assignment?.problems||[]" :key="p.id" :value="p.id" :label="p.title"/></el-select></el-form-item></div></el-form></div><AsyncState :loading="loading" :error="error" :empty="!assignmentId" empty-text="暂无可选择的作业" @retry="load"><el-alert v-if="!allowed" title="教师未开放本作业的 AI 辅助。" type="info" :closable="false"/><template v-else-if="problem"><section class="panel ai-context"><div class="section-bar"><h2>{{problem.title}}</h2><RouterLink :to="problemLink"><el-button>返回作答</el-button></RouterLink></div><el-collapse><el-collapse-item title="当前代码"><pre v-if="code" class="diagnostic">{{code}}</pre><p v-else class="muted">请先进入题目编写代码。</p></el-collapse-item></el-collapse><div class="ai-actions"><p class="muted"><template v-if="workspaceAi.provider==='DEEPSEEK'">AI 服务：DeepSeek · </template>分析时将发送当前代码、题目信息<template v-if="workspaceAi.style==='TINO'">及称呼</template>。</p><el-button type="primary" :loading="workspaceAi.busy" :disabled="!code.trim() || byteCount > problem.codeLimitBytes" @click="analyse">分析当前代码</el-button></div></section></template></AsyncState></div></template>
<style scoped>.ai-page{max-width:1100px}.ai-selectors{margin-bottom:20px}.ai-selectors .el-form-item{margin-bottom:0}.ai-context,.ai-guidance{margin-bottom:20px}.ai-actions{display:flex;align-items:center;justify-content:space-between;gap:18px;margin-top:18px}.ai-actions p{margin:0}.ai-guidance{min-height:180px}@media(max-width:600px){.ai-selectors .el-form-item{margin-bottom:15px}.ai-actions{align-items:stretch;flex-direction:column}}</style>
