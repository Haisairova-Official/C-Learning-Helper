import { reactive } from 'vue';
import { api, isMock } from './api';
import type { Analysis, AiReplyStyle, Problem } from './api/types';
import { poll } from './lib/poll';

export const workspaceAi = reactive({
  active: false,
  ready: false,
  enabled: false,
  busy: false,
  error: '',
  contextError: '',
  problemTitle: '',
  analysis: undefined as Analysis | undefined,
  style: 'GUIDED' as AiReplyStyle,
  provider: '',
  codeChanged: false,
});

let generation = 0;
let abort = new AbortController();

export function clearWorkspaceAi() {
  generation++;
  abort.abort();
  abort = new AbortController();
  Object.assign(workspaceAi, {
    active: false, ready: false, enabled: false, busy: false, error: '', contextError: '',
    problemTitle: '', analysis: undefined, style: 'GUIDED', provider: '', codeChanged: false,
  });
}

export async function analyseWorkspace(assignmentId: string, problem: Problem, sourceCode: string, style: AiReplyStyle) {
  if (!workspaceAi.ready || !workspaceAi.enabled || workspaceAi.busy || !sourceCode.trim()) return;
  const current = ++generation;
  abort.abort();
  abort = new AbortController();
  const signal = abort.signal;
  Object.assign(workspaceAi, { busy: true, error: '', analysis: undefined, style, codeChanged: false });
  try {
    const queued = await api.analyse({ assignmentId, problemId: problem.id, problemVersionId: problem.versionId, sourceCode });
    if (current !== generation) return;
    workspaceAi.analysis = { ...queued, summary: null, findings: [], errorCode: null, replyStyle: style };
    await poll(
      () => api.analysis(queued.id),
      result => ['COMPLETED', 'FAILED'].includes(result.status),
      result => {
        if (current !== generation) return;
        workspaceAi.analysis = result;
        workspaceAi.style = result.replyStyle || style;
      },
      signal,
      isMock ? 350 : 1200,
    );
  } catch (error) {
    if (current === generation && (error as Error).name !== 'AbortError') workspaceAi.error = (error as Error).message;
  } finally {
    if (current === generation) workspaceAi.busy = false;
  }
}
