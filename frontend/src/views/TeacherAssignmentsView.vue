<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { api } from '../api';
import { management, type Library } from '../api/management';
import type { Assignment, Classroom, TeacherProblem } from '../api/types';
import AsyncState from '../components/AsyncState.vue';
const rows=ref<Assignment[]>([]),classes=ref<Classroom[]>([]),libraries=ref<Library[]>([]),problems=ref<TeacherProblem[]>([]),loading=ref(true),error=ref(''),open=ref(false),saving=ref(false),poolLoading=ref(false),poolError=ref('');
const localTime=(timestamp:number)=>{const d=new Date(timestamp);return new Date(timestamp-d.getTimezoneOffset()*60000).toISOString().slice(0,16);};
const form=ref({title:'',classId:'',libraryId:'',selectionMode:'FULL' as 'FULL'|'SHARED_RANDOM'|'PER_STUDENT_RANDOM',drawCount:1,aiEnabled:true,chatEnabled:false,opensAt:localTime(Date.now()-60000),closesAt:localTime(Date.now()+7*86400000)});
const assignmentTime=(value:string)=>new Intl.DateTimeFormat('zh-CN',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).format(new Date(value));
const modeNames:Record<string,string>={FULL:'全量',SHARED_RANDOM:'统一随机',PER_STUDENT_RANDOM:'每人随机'};
const invalid=computed(()=>!form.value.title.trim()||!form.value.classId||!form.value.libraryId||!problems.value.length||poolLoading.value||!!poolError.value||(form.value.selectionMode!=='FULL'&&(form.value.drawCount<1||form.value.drawCount>problems.value.length))||(form.value.selectionMode==='PER_STUDENT_RANDOM'&&new Set(problems.value.map(p=>p.maxScore)).size>1));
async function load(){loading.value=true;error.value='';try{const [a,c,l]=await Promise.all([api.teacherAssignments(),api.classes(),management.libraries()]);rows.value=a.items;classes.value=c.items;libraries.value=l.items;}catch(e){error.value=(e as Error).message;}finally{loading.value=false;}}
let poolRequest=0;
async function loadPool(){const token=++poolRequest;poolLoading.value=true;poolError.value='';problems.value=[];try{const library=await management.library(form.value.libraryId);if(token===poolRequest){problems.value=library.problems;form.value.drawCount=Math.min(Math.max(1,form.value.drawCount),Math.max(1,library.problems.length));}}catch(e){if(token===poolRequest)poolError.value=(e as Error).message;}finally{if(token===poolRequest)poolLoading.value=false;}}
async function publish(){if(invalid.value)return;if(!form.value.opensAt||!form.value.closesAt||+new Date(form.value.closesAt)<=+new Date(form.value.opensAt))return ElMessage.warning('截止时间应晚于开放时间');saving.value=true;try{await api.publish({...form.value,problemIds:problems.value.map(p=>p.id),opensAt:new Date(form.value.opensAt).toISOString(),closesAt:new Date(form.value.closesAt).toISOString()});open.value=false;ElMessage.success('作业已发布');await load();}catch(e){ElMessage.error((e as Error).message);}finally{saving.value=false;}}
const editOpen=ref(false),editLoading=ref(false),editSaving=ref(false),editLoadError=ref(''),editSaveError=ref(''),editingId=ref('');
const currentAssignment=ref<Assignment>();
const editForm=ref({title:'',opensAt:'',closesAt:'',aiEnabled:true,chatEnabled:false});
let editRequest=0,destroyed=false;
const minuteLocalTime=(value:string)=>{const timestamp=Date.parse(value);if(!Number.isFinite(timestamp))return '';const d=new Date(timestamp);return new Date(timestamp-d.getTimezoneOffset()*60000).toISOString().slice(0,16);};
const editErrors=computed(()=>{
  const value=editForm.value,current=currentAssignment.value;
  const opens=Date.parse(current&&value.opensAt===minuteLocalTime(current.opensAt)?current.opensAt:value.opensAt),closes=Date.parse(current&&value.closesAt===minuteLocalTime(current.closesAt)?current.closesAt:value.closesAt);
  return {
    title:!value.title.trim()?'请填写作业名称。':Array.from(value.title.trim()).length>200?'作业名称最多 200 字。':'',
    opensAt:!Number.isFinite(opens)?'请选择有效的开放时间。':'',
    closesAt:!Number.isFinite(closes)?'请选择有效的截止时间。':Number.isFinite(opens)&&closes<=opens?'截止时间应晚于开放时间。':'',
  };
});
const editInvalid=computed(()=>!currentAssignment.value||editLoading.value||Object.values(editErrors.value).some(Boolean));
const editChanged=computed(()=>{
  const current=currentAssignment.value,value=editForm.value;
  return !!current&&(value.title.trim()!==current.title||value.opensAt!==minuteLocalTime(current.opensAt)||value.closesAt!==minuteLocalTime(current.closesAt)||value.aiEnabled!==(current.aiEnabled!==false)||value.chatEnabled!==!!current.chatEnabled);
});
async function loadEditing(){
  const request=++editRequest,assignmentId=editingId.value;
  currentAssignment.value=undefined;editLoading.value=true;editLoadError.value='';editSaveError.value='';
  try{
    const value=await management.assignment(assignmentId);
    if(destroyed||request!==editRequest||!editOpen.value)return;
    currentAssignment.value=value;
    editForm.value={title:value.title,opensAt:minuteLocalTime(value.opensAt),closesAt:minuteLocalTime(value.closesAt),aiEnabled:value.aiEnabled!==false,chatEnabled:!!value.chatEnabled};
  }catch(e){if(!destroyed&&request===editRequest)editLoadError.value=(e as Error).message;}
  finally{if(request===editRequest)editLoading.value=false;}
}
function startEdit(assignmentId:string){editingId.value=assignmentId;editOpen.value=true;void loadEditing();}
function closeEdit(){editRequest++;currentAssignment.value=undefined;editingId.value='';editLoading.value=false;editSaving.value=false;editSaveError.value='';}
async function saveEdit(){
  if(editInvalid.value||!editChanged.value||editSaving.value)return;
  const request=editRequest,assignmentId=editingId.value,value=editForm.value,current=currentAssignment.value!;
  editSaving.value=true;editSaveError.value='';
  try{
    const updated=await management.editAssignment(assignmentId,{title:value.title.trim(),opensAt:value.opensAt===minuteLocalTime(current.opensAt)?current.opensAt:new Date(value.opensAt).toISOString(),closesAt:value.closesAt===minuteLocalTime(current.closesAt)?current.closesAt:new Date(value.closesAt).toISOString(),aiEnabled:value.aiEnabled,chatEnabled:value.chatEnabled});
    if(destroyed||request!==editRequest||!editOpen.value)return;
    rows.value=rows.value.map(row=>row.id===updated.id?updated:row);
    editOpen.value=false;ElMessage.success('作业设置已保存');
  }catch(e){if(!destroyed&&request===editRequest)editSaveError.value=(e as Error).message;}
  finally{if(request===editRequest)editSaving.value=false;}
}
onBeforeUnmount(()=>{destroyed=true;editRequest++;});
onMounted(load);
</script>
<template><div class="page"><div class="page-heading"><h1>作业发布</h1><el-button type="primary" @click="open=true">发布作业</el-button></div><AsyncState :loading="loading" :error="error" :empty="!rows.length" empty-text="暂无已发布的作业。" @retry="load"><div class="panel table-panel"><el-table :data="rows"><el-table-column label="作业名称" min-width="200"><template #default="{row}"><button type="button" class="assignment-title" :aria-label="`修改作业：${row.title}`" @click="startEdit(row.id)">{{row.title}}</button></template></el-table-column><el-table-column prop="className" label="班级" min-width="140"/><el-table-column label="出题方式" width="110"><template #default="{row}">{{modeNames[row.selectionMode||'FULL']}}</template></el-table-column><el-table-column label="题数" width="70"><template #default="{row}">{{row.questionCount??row.problems.length}}</template></el-table-column><el-table-column label="截止时间" min-width="145"><template #default="{row}">{{assignmentTime(row.closesAt)}}</template></el-table-column><el-table-column label="辅助功能" width="145"><template #default="{row}"><span>{{row.aiEnabled!==false?'AI 开启':'AI 关闭'}} / {{row.chatEnabled?'聊天开启':'聊天关闭'}}</span></template></el-table-column><el-table-column label="操作" width="100"><template #default="{row}"><RouterLink :to="`/teacher/assignments/${row.id}/chat`"><el-button link type="primary">讨论管理</el-button></RouterLink></template></el-table-column></el-table></div></AsyncState><el-dialog v-model="open" title="发布课程作业" width="720px" :close-on-click-modal="false"><el-form label-position="top" :disabled="saving"><el-form-item label="作业名称"><el-input v-model="form.title" placeholder="例如：第三章 · 循环结构" maxlength="200"/></el-form-item><div class="two-column"><el-form-item label="发布班级"><el-select v-model="form.classId" placeholder="选择班级"><el-option v-for="c in classes" :key="c.id" :value="c.id" :label="c.name"/></el-select></el-form-item><el-form-item label="题库"><el-select v-model="form.libraryId" placeholder="选择题库" @change="loadPool"><el-option v-for="l in libraries" :key="l.id" :value="l.id" :label="`${l.title}（${l.problemCount} 题）`"/></el-select></el-form-item></div><el-alert v-if="poolError" :title="poolError" type="error" :closable="false"/><el-form-item label="出题方式"><el-radio-group v-model="form.selectionMode"><el-radio-button value="FULL">全量</el-radio-button><el-radio-button value="SHARED_RANDOM">统一随机</el-radio-button><el-radio-button value="PER_STUDENT_RANDOM">每人随机</el-radio-button></el-radio-group></el-form-item><div class="draw-settings"><span v-if="form.selectionMode==='FULL'">共 {{problems.length}} 道题目</span><template v-else><label>每人 {{form.selectionMode==='SHARED_RANDOM'?'使用统一抽取的':'抽取'}} <el-input-number v-model="form.drawCount" :min="1" :max="Math.max(1,problems.length)" size="small"/> 题</label><span class="muted">抽题结果固定，重新进入不会换题。</span></template></div><el-alert v-if="form.selectionMode==='PER_STUDENT_RANDOM'&&new Set(problems.map(p=>p.maxScore)).size>1" title="每人随机时，题库内题目分值需一致。" type="warning" :closable="false"/><el-collapse v-if="problems.length" class="mb"><el-collapse-item :title="`查看候选题目（${problems.length}）`"><div class="pool-list"><p v-for="p in problems" :key="p.id">{{p.title}} <span>{{p.maxScore}} 分</span></p></div></el-collapse-item></el-collapse><div class="two-column"><el-form-item label="开放时间（当前设备时区）"><el-date-picker v-model="form.opensAt" type="datetime" format="YYYY/MM/DD HH:mm" value-format="YYYY-MM-DDTHH:mm" date-format="YYYY/MM/DD" time-format="HH:mm" :aria-label="'开放时间'"/></el-form-item><el-form-item label="截止时间（当前设备时区）"><el-date-picker v-model="form.closesAt" type="datetime" format="YYYY/MM/DD HH:mm" value-format="YYYY-MM-DDTHH:mm" date-format="YYYY/MM/DD" time-format="HH:mm" :aria-label="'截止时间'"/></el-form-item></div><div class="two-column"><el-form-item label="允许 AI 辅助"><el-switch v-model="form.aiEnabled"/></el-form-item><el-form-item label="允许题目聊天"><el-switch v-model="form.chatEnabled"/></el-form-item></div></el-form><template #footer><el-button :disabled="saving" @click="open=false">取消</el-button><el-button type="primary" :disabled="invalid" :loading="saving" @click="publish">确认发布</el-button></template></el-dialog>
<el-dialog v-model="editOpen" title="修改作业设置" width="680px" :close-on-click-modal="false" :close-on-press-escape="!editSaving" :show-close="!editSaving" @close="closeEdit">
  <AsyncState :loading="editLoading" :error="editLoadError" @retry="loadEditing">
    <template v-if="currentAssignment">
      <dl class="assignment-details">
        <div><dt>班级</dt><dd>{{currentAssignment.className}}</dd></div>
        <div><dt>出题方式</dt><dd>{{modeNames[currentAssignment.selectionMode||'FULL']}}</dd></div>
        <div><dt>题数</dt><dd>{{currentAssignment.questionCount??currentAssignment.problems.length}} 题</dd></div>
      </dl>
      <el-alert v-if="editSaveError" :title="editSaveError" type="error" :closable="false"/>
      <el-form label-position="top" :disabled="editSaving" @submit.prevent="saveEdit">
        <el-form-item label="作业名称" :error="editErrors.title"><el-input v-model="editForm.title" aria-label="作业名称"/></el-form-item>
        <div class="two-column">
          <el-form-item label="开放时间（当前设备时区）" :error="editErrors.opensAt"><el-date-picker v-model="editForm.opensAt" type="datetime" format="YYYY/MM/DD HH:mm" value-format="YYYY-MM-DDTHH:mm" date-format="YYYY/MM/DD" time-format="HH:mm" aria-label="开放时间"/></el-form-item>
          <el-form-item label="截止时间（当前设备时区）" :error="editErrors.closesAt"><el-date-picker v-model="editForm.closesAt" type="datetime" format="YYYY/MM/DD HH:mm" value-format="YYYY-MM-DDTHH:mm" date-format="YYYY/MM/DD" time-format="HH:mm" aria-label="截止时间"/></el-form-item>
        </div>
        <div class="two-column">
          <el-form-item label="允许 AI 辅助"><el-switch v-model="editForm.aiEnabled" aria-label="允许 AI 辅助"/></el-form-item>
          <el-form-item label="允许题目聊天"><el-switch v-model="editForm.chatEnabled" aria-label="允许题目聊天"/></el-form-item>
        </div>
      </el-form>
    </template>
  </AsyncState>
  <template #footer><el-button :disabled="editSaving" @click="editOpen=false">取消</el-button><el-button type="primary" :loading="editSaving" :disabled="editInvalid||!editChanged" @click="saveEdit">保存修改</el-button></template>
</el-dialog></div></template>
<style scoped>.draw-settings{display:flex;gap:12px;align-items:center;flex-wrap:wrap;font-size:12px;margin-bottom:20px}.draw-settings .el-input-number{width:120px;margin:0 6px}.pool-list{max-height:180px;overflow:auto}.pool-list p{margin:4px 0;font-size:12px}.pool-list span{float:right;color:var(--text-secondary, #7c8e9a)}.assignment-title{border:0;background:transparent;padding:4px 0;color:var(--accent, #177f84);text-align:left;line-height:1.6;overflow-wrap:anywhere}.assignment-title:hover{text-decoration:underline}.assignment-details{display:grid;grid-template-columns:2fr 1fr 1fr;gap:16px;margin:0 0 24px;padding:16px;background:var(--surface-soft, #f5f8fa);border-radius:7px}.assignment-details dt{font-size:12px;color:var(--text-muted, #81919e);margin-bottom:6px}.assignment-details dd{margin:0;overflow-wrap:anywhere}@media(max-width:500px){.assignment-details{grid-template-columns:1fr;gap:12px}}
.two-column :deep(.el-date-editor.el-input){width:100%}
</style>
