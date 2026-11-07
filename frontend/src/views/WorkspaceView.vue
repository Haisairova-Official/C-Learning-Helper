<script setup lang="ts">
import { newClientId } from '../lib/id';
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { api, isMock } from '../api';
import type { Assignment, Problem, Submission } from '../api/types';
import { actor, profile } from '../auth';
import { analyseWorkspace, clearWorkspaceAi, workspaceAi } from '../aiWorkspace';
import { readDraft, saveDraft } from '../lib/drafts';
import { poll } from '../lib/poll';
import { dateTime, label } from '../lib/format';
import { initialCode, languageLabel, problemLanguage } from '../lib/problemCode';
import CodeEditor from '../components/CodeEditor.vue';
import AsyncState from '../components/AsyncState.vue';
import ProblemNavigation from '../components/ProblemNavigation.vue';
import ProblemChat from '../components/ProblemChat.vue';
const route = useRoute(); const router = useRouter(); const assignmentId = String(route.params.assignmentId); const problemId = String(route.params.problemId);
const historyError = ref('');
const assignment = ref<Assignment>(); const navigationError = ref(''); const switchingProblem = ref(false);
const problem = ref<Problem>(); const loading = ref(true); const error = ref(''); const code = ref(''); const saveLabel = ref(''); const history = ref<Submission[]>([]);
const result = ref<Submission>(); const panel = ref('result'); const submitting = ref(false); const judgeError = ref('');
const submittedCode = ref(''); const selectedHistory = ref<Submission>(); const historyOpen = ref(false);
let analysedCode = '';
clearWorkspaceAi();
workspaceAi.active = true;
workspaceAi.style = profile.value?.aiReplyStyle || 'GUIDED';
api.aiProvider().then(value => { if(!destroyed) workspaceAi.provider=value.providerMode; }).catch(()=>{});
watch(profile,value=>{if(!workspaceAi.analysis&&!workspaceAi.busy)workspaceAi.style=value?.aiReplyStyle||'GUIDED';});
const language = computed(() => problemLanguage(problem.value));
const isFunction = computed(() => problem.value?.problemType === 'FUNCTION');
const sourceFile = computed(() => `${isFunction.value ? 'solution' : 'main'}.${language.value === 'CPP17' ? 'cpp' : 'c'}`);
const byteCount = computed(() => new TextEncoder().encode(code.value).length);
let judgeAbort = new AbortController(); let saveTimer: ReturnType<typeof setTimeout> | undefined;
let requestKey = ''; let requestCode = ''; let destroyed = false;
let navigationTimer: ReturnType<typeof setTimeout> | undefined; let navigationRequest = 0;
async function load() { loading.value = true; error.value = ''; try { const p = await api.problem(assignmentId, problemId); if (destroyed) return; problem.value = p; workspaceAi.problemTitle=p.title; code.value = initialCode(p, readDraft(actor.value!.id, assignmentId, p.versionId)); await loadHistory(); } catch (e) { error.value = (e as Error).message; } finally { loading.value = false; } }
async function loadNavigation() {
  if (destroyed) return;
  clearTimeout(navigationTimer);
  const request = ++navigationRequest;
  navigationError.value = '';
  try {
    const value = await api.assignment(assignmentId);
    if (destroyed || request !== navigationRequest) return;
    assignment.value = value; workspaceAi.enabled=value.aiEnabled!==false; workspaceAi.ready=true; workspaceAi.contextError='';
    navigationTimer = setTimeout(refreshNavigation, value.problems.some(p => p.progress?.pending || !p.progress) ? 1500 : 15000);
  } catch (e) { if (!destroyed && request === navigationRequest) { navigationError.value = (e as Error).message; if(!workspaceAi.ready)workspaceAi.contextError=navigationError.value; navigationTimer = setTimeout(refreshNavigation, 15000); } }
}
function refreshNavigation() {
  if (destroyed) return;
  if (document.visibilityState !== 'hidden') void loadNavigation();
  else navigationTimer = setTimeout(refreshNavigation, 15000);
}
onMounted(() => { window.addEventListener('focus', refreshNavigation); document.addEventListener('visibilitychange', refreshNavigation); });
async function goToProblem(targetId: string) {
  if (switchingProblem.value || !problem.value || !actor.value || targetId === problemId || !assignment.value?.problems.some(p => p.id === targetId)) return;
  clearTimeout(saveTimer);
  if (!saveDraft(actor.value.id, assignmentId, problem.value.versionId, code.value)) {
    saveLabel.value = '草稿保存失败，请复制备份';
    ElMessage.error('草稿保存失败，请先复制代码备份后再切题。');
    return;
  }
  switchingProblem.value = true;
  try { await router.push(`/assignments/${encodeURIComponent(assignmentId)}/problems/${encodeURIComponent(targetId)}`); }
  catch { ElMessage.error('切换题目失败，请重试。'); }
  finally { switchingProblem.value = false; }
}
async function loadHistory() { historyError.value = ''; try { const rows = await api.submissions(assignmentId, problemId); if (!destroyed) history.value = rows.items; } catch (e) { if (!destroyed) historyError.value = (e as Error).message; } }
watch(code, value => { workspaceAi.codeChanged=!!analysedCode&&value!==analysedCode; clearTimeout(saveTimer); saveLabel.value = '正在保存…'; saveTimer = setTimeout(() => { if (problem.value && actor.value) saveLabel.value = saveDraft(actor.value.id, assignmentId, problem.value.versionId, value) ? '已保存至当前标签页' : '草稿保存失败，请复制备份'; }, 400); });
function input() { if (!problem.value || !code.value.trim()) throw new Error('请先编写代码。'); if (byteCount.value > problem.value.codeLimitBytes) throw new Error('代码超过题目长度限制。'); return { assignmentId, problemId, problemVersionId: problem.value.versionId, sourceCode: code.value }; }
async function analyse(){if(workspaceAi.busy || !workspaceAi.ready || !workspaceAi.enabled)return;try{const data=input();analysedCode=data.sourceCode;await analyseWorkspace(assignmentId,problem.value!,data.sourceCode,profile.value?.aiReplyStyle||'GUIDED');}catch(e){workspaceAi.error=(e as Error).message;}}
async function watchResult(id: string) { judgeAbort.abort(); judgeAbort = new AbortController(); await poll(() => api.submission(id), r => ['FINISHED', 'SYSTEM_ERROR'].includes(r.status), r => { result.value = r; }, judgeAbort.signal, isMock ? 350 : 1200); await Promise.all([loadHistory(), loadNavigation()]); }
async function submit() { judgeError.value = ''; submitting.value = true; panel.value = 'result'; try { const data = input(); if (requestCode !== data.sourceCode || !requestKey) { requestCode = data.sourceCode; requestKey = newClientId(); } submittedCode.value = data.sourceCode; const queued = await api.submit({ ...data, language: language.value }, requestKey); result.value = { ...queued, verdict: null, score: null, maxScore: problem.value!.maxScore, compileMessage: '', caseResults: [] }; void loadNavigation(); await watchResult(queued.id); requestKey = ''; } catch (e) { if ((e as Error).name !== 'AbortError') judgeError.value = (e as Error).message; } finally { submitting.value = false; } }
async function retryResult() { if (!result.value) return; submitting.value = true; judgeError.value = ''; try { await watchResult(result.value.id); } catch (e) { if ((e as Error).name !== 'AbortError') judgeError.value = (e as Error).message; } finally { submitting.value = false; } }
async function inspectHistory(row: Submission) { try { selectedHistory.value = await api.submission(row.id); historyOpen.value = true; } catch (e) { ElMessage.error((e as Error).message); } }
onMounted(load); onMounted(loadNavigation); onBeforeUnmount(() => { destroyed = true; clearWorkspaceAi(); clearTimeout(navigationTimer); window.removeEventListener('focus', refreshNavigation); document.removeEventListener('visibilitychange', refreshNavigation); judgeAbort.abort(); clearTimeout(saveTimer); if (problem.value && actor.value) saveDraft(actor.value.id, assignmentId, problem.value.versionId, code.value); });
</script>
<template><div class="workspace page"><div class="workspace-heading"><RouterLink to="/" class="back-link">← 返回作业</RouterLink><span class="muted">在线编程 · {{ languageLabel(language) }}{{ isFunction ? ' · 函数题' : '' }}</span></div><ProblemNavigation v-if="assignment" :assignment="assignment" :current-id="problemId" :busy="loading || switchingProblem" @navigate="goToProblem" /><div v-if="navigationError" class="navigation-error"><el-alert title="目录或完成状态更新失败，当前题目仍可作答。" type="warning" :closable="false" /><el-button link @click="loadNavigation">重新加载题目导航</el-button></div><AsyncState :loading="loading" :error="error" @retry="load"><div v-if="problem" class="workspace-grid" :class="{'with-chat': assignment?.chatEnabled}"><section class="statement-panel panel"><div class="statement-title"><h1>{{ problem.title }}</h1><div class="constraint-row"><span>{{ problem.maxScore }} 分</span><span>{{ problem.timeLimitMs }} ms</span><span>{{ problem.memoryLimitMb }} MB</span></div></div><p class="pre-wrap">{{ problem.statement }}</p><div v-if="isFunction" class="function-contract"><h3>函数接口</h3><pre>{{ problem.functionSignature }}</pre><p>仅提交指定函数的实现，无需编写 main 函数。</p></div><h3>输入格式</h3><p class="pre-wrap">{{ problem.inputDescription }}</p><h3>输出格式</h3><p class="pre-wrap">{{ problem.outputDescription }}</p><section v-for="(s, i) in problem.samples" :key="i" class="sample-pair"><h3>样例 {{ i + 1 }}</h3><div><span>输入</span><pre>{{ s.input }}</pre></div><div><span>输出</span><pre>{{ s.output }}</pre></div><p v-if="s.explanation" class="muted">{{ s.explanation }}</p></section></section><section class="coding-column"><div class="editor-panel panel"><div class="editor-toolbar"><strong><span class="file-dot"></span>{{ sourceFile }}</strong><span>{{ languageLabel(language) }} <span class="separator">·</span> {{ language === 'CPP17' ? 'G++' : 'GCC' }}</span></div><CodeEditor v-model="code" :language="language" /><div class="editor-status"><span>{{ saveLabel }}</span><span :class="{ danger: byteCount > problem.codeLimitBytes }">{{ byteCount }} / {{ problem.codeLimitBytes }} 字节</span></div><p v-if="workspaceAi.enabled" class="ai-provider-note"><template v-if="workspaceAi.provider==='DEEPSEEK'">AI 服务：DeepSeek · </template>分析时将发送当前代码、题目信息<template v-if="workspaceAi.style==='TINO'">及称呼</template>。</p><div class="editor-actions"><el-button v-if="workspaceAi.enabled" :loading="workspaceAi.busy" :disabled="!code.trim() || byteCount > problem.codeLimitBytes" @click="analyse">AI 排错</el-button><el-button type="primary" :loading="submitting" :disabled="!code.trim() || byteCount > problem.codeLimitBytes" @click="submit">提交评测</el-button></div></div><div class="feedback-panel panel"><el-tabs v-model="panel"><el-tab-pane label="评测结果" name="result"><p v-if="isMock" class="demo-inline">固定演示结果，与当前代码正确性无关。</p><el-alert v-if="judgeError" :title="judgeError" type="error" :closable="false" /><el-button v-if="judgeError && result" class="mt" @click="retryResult" :loading="submitting">重新查询结果</el-button><div v-if="result" :key="`${result.id}:${result.status}:${result.verdict}`" class="result-reveal"><div class="result-heading"><el-tag :type="result.verdict === 'ACCEPTED' ? 'success' : result.status === 'SYSTEM_ERROR' ? 'danger' : 'warning'">{{ label(result.verdict || result.status) }}</el-tag><strong>{{ result.score === null ? '—' : result.score }}<small> / {{ result.maxScore }} 分</small></strong></div><p v-if="code !== submittedCode" class="muted">此结果对应先前提交的代码，编辑后的代码尚未评测。</p><p v-if="result.status === 'SYSTEM_ERROR'" role="alert">评测服务暂不可用，本次不判定为答案错误。</p><p v-if="result.caseResults.some(c => c.status === 'PRESENTATION_ERROR')" class="format-feedback" role="status">部分测试点的输出内容一致，但格式不符。请检查空格、换行和多余空行；仅缺少最后一个换行符不影响通过。</p><pre v-if="result.compileMessage" class="diagnostic">{{ result.compileMessage }}</pre><el-table v-if="result.caseResults.length" :data="result.caseResults" size="small"><el-table-column prop="caseIndex" label="测试点" width="75" /><el-table-column label="结果"><template #default="{ row }"><span :class="row.status === 'ACCEPTED' ? 'success' : 'warning'">{{ label(row.status) }}</span></template></el-table-column><el-table-column label="耗时" width="75"><template #default="{ row }">{{ row.timeMs }} ms</template></el-table-column><el-table-column label="内存" width="95"><template #default="{ row }">{{ row.memoryKb }} KiB</template></el-table-column><el-table-column prop="score" label="得分" width="65" /></el-table></div><div v-else-if="!judgeError" class="feedback-empty"><span>⌁</span><p>{{ isFunction ? '完成函数，提交后查看评测结果' : '编写程序，提交后查看评测结果' }}</p></div></el-tab-pane><el-tab-pane :label="`提交记录 (${history.length})`" name="history"><el-alert v-if="historyError" :title="historyError" type="error" :closable="false" /><el-button v-if="historyError" @click="loadHistory">重新加载记录</el-button><el-empty v-if="!history.length" description="还没有提交记录" :image-size="50" /><el-table v-else :data="history" size="small"><el-table-column label="时间"><template #default="{ row }">{{ row.submittedAt ? dateTime(row.submittedAt) : '—' }}</template></el-table-column><el-table-column label="结果"><template #default="{ row }">{{ label(row.verdict || row.status) }}</template></el-table-column><el-table-column label="得分" width="60"><template #default="{ row }">{{ row.score ?? '—' }}</template></el-table-column><el-table-column width="80"><template #default="{ row }"><el-button link type="primary" @click="inspectHistory(row)">查看代码</el-button></template></el-table-column></el-table></el-tab-pane></el-tabs></div></section><ProblemChat v-if="assignment?.chatEnabled" :assignment-id="assignmentId" :problem-id="problemId" :enabled="true"/></div></AsyncState><el-dialog v-model="historyOpen" title="历史提交 · 只读" width="700px"><pre class="diagnostic">{{ selectedHistory?.sourceCode ?? '暂无可查看的代码。' }}</pre></el-dialog></div></template>

<style scoped>

.workspace{max-width:1800px}.coding-column{position:sticky;top:16px;max-height:calc(100dvh - 32px);grid-template-rows:auto minmax(0,1fr)}.coding-column .feedback-panel{overflow:auto;min-height:120px}.editor-panel :deep(.code-editor){height:clamp(160px,40dvh,430px)}.workspace-grid.with-chat{grid-template-columns:minmax(200px,.85fr) minmax(330px,1.3fr) minmax(245px,.8fr)}
@media(max-width:1200px){.workspace-grid.with-chat{grid-template-columns:minmax(240px,.85fr) minmax(370px,1.3fr)}.workspace-grid.with-chat>.chat-panel{grid-column:1/-1}}
@media(max-width:1000px){.workspace-grid,.workspace-grid.with-chat{grid-template-columns:minmax(0,1fr)}.statement-panel{max-height:450px;overflow:auto}}
.function-contract{background:var(--accent-soft, #f1f7f7);border:1px solid var(--accent-border, #deeceb);border-radius:7px;padding:17px;margin:20px 0}.function-contract h3{margin-top:0}.function-contract pre{font-size:12px;color:var(--text-secondary, #3e6d77);line-height:1.8}.function-contract p{font-size:11px;color:var(--text-muted, #81959d);margin:10px 0 0}.ai-provider-note{margin:0;padding:8px 17px;color:var(--text-secondary, #6b7c87);font-size:11px}

.statement-panel { animation: workspace-arrive .36s ease-out both; }
.coding-column { animation: workspace-arrive .42s .05s ease-out both; }
.result-reveal { animation: feedback-arrive .3s ease-out both; }
:deep(.editor-actions .el-button) { transition: transform .18s, box-shadow .22s, background-color .22s; }
:deep(.editor-actions .el-button:hover:not(:disabled)) { transform: translateY(-2px); box-shadow: 0 5px 12px #1c7f8520; }
:deep(.editor-actions .el-button:active:not(:disabled)) { transform: translateY(0) scale(.97); }
@keyframes workspace-arrive { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }
@keyframes feedback-arrive { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: translateY(0); } }
@media (prefers-reduced-motion: reduce) { *, :deep(*) { animation: none !important; transition: none !important; } }
</style>

<style scoped>
.format-feedback { margin: 12px 0; padding: 12px 14px; border-left: 3px solid var(--warning-border, #c38d28); background: var(--warning-soft, #fbf6e9); color: var(--warning-text, #795b20); line-height: 1.7; }

@media(max-width:1000px){.coding-column{position:static;max-height:none;display:grid;grid-template-rows:auto auto}.coding-column .editor-panel{position:sticky;top:0;z-index:5}.coding-column .feedback-panel{overflow:visible}.editor-panel :deep(.code-editor){height:280px}}
@media(min-width:1001px) and (max-height:600px){.coding-column{max-height:calc(100dvh - 16px);top:8px}.coding-column .feedback-panel{min-height:80px}.editor-panel :deep(.code-editor){height:140px}}
</style>
