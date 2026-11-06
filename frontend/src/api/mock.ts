import { newClientId } from '../lib/id';
import type { Actor, Api, Assignment, Candidate, ImportJob, Page, Problem, Role, Submission, SubmissionRequest, TeacherProblem, Analysis, Profile } from './types';
import { ApiError } from './http';
import { avatarValidation, profileValidation } from '../lib/profile';
import { functionProblemError, problemLanguage } from '../lib/problemCode';
import { importSourceType } from '../lib/importing';
const delay = () => new Promise(resolve => setTimeout(resolve, 180));
const copy = <T>(value: T): T => structuredClone(value);
const page = <T>(items: T[]): Page<T> => ({ items: copy(items), page: 1, pageSize: 100, total: items.length });
let actor: Actor | null = null;
try { const saved = JSON.parse(sessionStorage.getItem('cassist:demo-actor') || 'null'); if (saved && ['STUDENT', 'TEACHER', 'ADMIN'].includes(saved.role)) actor = saved; } catch { /* Invalid demo session is ignored. */ }
function requireActor(manager = false) { if (!actor) throw new ApiError('请先登录', 401, 'UNAUTHENTICATED'); if (manager && actor.role === 'STUDENT') throw new ApiError('无权访问教师资源', 403, 'FORBIDDEN'); return actor; }
const profiles = new Map<string, Profile>();
function currentProfile(): Profile {
  const user = requireActor();
  if (!profiles.has(user.id)) profiles.set(user.id, { username: user.id.replace(/^demo-/, ''), displayName: user.role === 'STUDENT' ? '演示学生' : user.role === 'ADMIN' ? '演示管理员' : '演示教师', nickname: '', bio: '', avatarUrl: null, aiReplyStyle: 'GUIDED', classNames: user.role === 'STUDENT' ? ['计算机 2026 级 1 班'] : [], studentNumber: user.role === 'STUDENT' ? 'DEMO001' : null, updatedAt: new Date().toISOString() });
  return profiles.get(user.id)!;
}
const publicProblems: Problem[] = [
  { id: 'p1', versionId: 'v1', title: '区间整数求和', statement: '输入两个整数 a 和 b，计算闭区间 [a, b] 中所有整数的和。\n请考虑区间中包含负数或只有一个整数的情况。', inputDescription: '一行输入两个整数 a、b，以空格分隔。−1000 ≤ a ≤ b ≤ 1000。', outputDescription: '输出一行，包含区间内所有整数的和。', samples: [{ input: '1 5', output: '15' }, { input: '-2 2', output: '0' }], timeLimitMs: 1000, memoryLimitMb: 128, codeLimitBytes: 16384, maxScore: 20, problemType: 'PROGRAM', language: 'C17', functionSignature: '', starterCode: '' },
  { id: 'p2', versionId: 'v2', title: '数字的位数', statement: '读取一个整数，输出它的十进制位数。负号不计入位数。', inputDescription: '一行输入一个整数 n，−100000 ≤ n ≤ 100000。', outputDescription: '输出该整数的位数。', samples: [{ input: '1024', output: '4' }], timeLimitMs: 1000, memoryLimitMb: 128, codeLimitBytes: 16384, maxScore: 20, problemType: 'PROGRAM', language: 'C17', functionSignature: '', starterCode: '' },
  { id: 'p3', versionId: 'v3', title: '最高温度', statement: '读取一周的温度，找出其中的最高温度。', inputDescription: '输入七个整数，以空格分隔。', outputDescription: '输出最高温度。', samples: [{ input: '18 20 19 23 21 17 20', output: '23' }], timeLimitMs: 1000, memoryLimitMb: 128, codeLimitBytes: 16384, maxScore: 20, problemType: 'PROGRAM', language: 'C17', functionSignature: '', starterCode: '' },
];
const classRows = [{ id: 'c1', name: '计算机 2026 级 1 班', memberCount: 3 }];
const classMembers = new Map([['c1', [{ id: 'u1', name: '演示学生甲', studentNumber: 'DEMO001' }, { id: 'u2', name: '演示学生乙', studentNumber: 'DEMO002' }, { id: 'u3', name: '演示学生丙', studentNumber: 'DEMO003' }]]]);
const now = Date.now();
let assignments: Assignment[] = [{ id: 'a1', title: '第三章 · 循环结构', className: '计算机 2026 级 1 班', opensAt: new Date(now - 86400000).toISOString(), closesAt: new Date(now + 7 * 86400000).toISOString(), problems: publicProblems, completedCount: 0, score: 0, maxScore: 60, state: 'PUBLISHED' }];
let teacherProblems: TeacherProblem[] = publicProblems.map(p => ({ ...copy(p), referenceAnswer: '', judgeTemplate: '', testCases: [], state: 'DRAFT' }));
const submissions = new Map<string, { owner: string; input: SubmissionRequest; created: number; result: Submission }>();
const analyses = new Map<string, { owner: string; created: number; value: Analysis }>();
const imports = new Map<string, { owner: string; created: number; value: ImportJob }>();
const replay = new Map<string, { signature: string; value: unknown }>();
function replayed<T>(key: string, input: unknown, create: () => T): T { const signature = JSON.stringify(input); const old = replay.get(key); if (old) { if (old.signature !== signature) throw new ApiError('相同请求编号对应不同内容', 409, 'IDEMPOTENCY_CONFLICT'); return copy(old.value as T); } const value = create(); replay.set(key, { signature, value }); return copy(value); }
function owned<T extends { owner: string }>(record: T | undefined): T { const user = requireActor(); if (!record) throw new ApiError('记录不存在', 404, 'NOT_FOUND'); if (record.owner !== user.id) throw new ApiError('无权访问此记录', 403, 'FORBIDDEN'); return record; }
function assignmentWithProgress(value: Assignment): Assignment {
  const user = requireActor();
  return { ...copy(value), problems: value.problems.map(p => {
    const rows = [...submissions.values()].filter(r => r.owner === user.id && r.input.assignmentId === value.id && r.input.problemVersionId === p.versionId);
    const finished = rows.filter(r => r.result.status === 'FINISHED' && r.result.score !== null);
    const pending = rows.some(r => ['QUEUED', 'COMPILING', 'RUNNING'].includes(r.result.status));
    const bestScore = finished.length ? Math.max(...finished.map(r => r.result.score!)) : null;
    const status = finished.some(r => r.result.verdict === 'ACCEPTED') ? 'ACCEPTED' : finished.some(r => r.result.verdict === 'PARTIAL_ACCEPTED') || (bestScore ?? 0) > 0 ? 'PARTIAL' : finished.length ? 'WRONG' : pending ? 'PENDING' : rows.length ? 'SYSTEM_ERROR' : 'UNSUBMITTED';
    return { ...copy(p), progress: { status, bestScore, pending } };
  }) };
}
async function hash(text: string) { return Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text)))).map(x => x.toString(16).padStart(2, '0')).join(''); }
function preview(text: string): Candidate[] {
  // Deliberately small UI simulator, not the Java Easy Parse implementation.
  const parts = text.split(/(?=^第\s*\d+\s*题[：:])/m).map(s => s.trim()).filter(Boolean);
  return parts.map((raw, index) => {
    const lines = raw.split('\n');
    const field = (name: string) => new RegExp(`^(?:${name})[：:]([^]*?)(?=^(?:题干|输入格式|输出格式|输入样例|输出样例|样例输入|样例输出|参考答案|解析|分数)[：:]|$(?![\\s\\S]))`, 'm').exec(raw)?.[1]?.trim() ?? '';
    const knownStart = lines.findIndex(l => /^(题干|输入格式|输出格式|输入样例|输出样例|样例输入|样例输出|参考答案|解析|分数)[：:]/.test(l));
    const sourceLocation = { startLine: 1, endLine: lines.length };
    return { problemType: 'PROGRAM', language: 'C17', functionSignature: '', starterCode: '', judgeTemplate: '', candidateId: `candidate-${index + 1}`, title: lines[0]?.replace(/^第\s*\d+\s*题[：:]\s*/, '') || '未命名题目', statement: field('题干') || '', inputDescription: field('输入格式'), outputDescription: field('输出格式'), samples: [{ key: '1', input: field('输入样例|样例输入') || null, output: field('输出样例|样例输出') || null }], referenceAnswer: field('参考答案'), privateNotes: field('解析'), unresolvedContent: knownStart < 0 ? raw : '', testCases: [], codeLimitBytes: 65536, maxScore: Number(field('分数')) || null, timeLimitMs: 1000, memoryLimitMb: 128, sourceLocation, issues: [{ code: 'DEMO_PARSER', severity: 'WARNING', field: 'all', sourceLocation, message: '以下为演示解析结果，请逐项核对。' }], needsReview: true };
  });
}
export const mockApi: Api = {
  async profile() { const profile = currentProfile(); await delay(); return copy(profile); },
  async saveProfile(input) { const profile = currentProfile(); await delay(); const problem = profileValidation(input); if (problem) throw new ApiError(problem, 400, 'VALIDATION_ERROR'); Object.assign(profile, { nickname: input.nickname.trim(), bio: input.bio, aiReplyStyle: input.aiReplyStyle, updatedAt: new Date().toISOString() }); return copy(profile); },
  async uploadAvatar(file) { const profile = currentProfile(); await delay(); const problem = avatarValidation(file); if (problem) throw new ApiError(problem, 400, 'INVALID_AVATAR'); if (profile.avatarUrl) URL.revokeObjectURL(profile.avatarUrl); profile.avatarUrl = URL.createObjectURL(file); profile.updatedAt = new Date().toISOString(); return copy(profile); },
  async removeAvatar() { const profile = currentProfile(); await delay(); if (profile.avatarUrl) URL.revokeObjectURL(profile.avatarUrl); profile.avatarUrl = null; profile.updatedAt = new Date().toISOString(); return copy(profile); },
  async aiProvider() { requireActor(); return { providerMode: 'MOCK' as const, configured: true }; },
  async me() { await delay(); return copy(requireActor()); },
  async login(username) { await delay(); const roles: Record<string, Role> = { student: 'STUDENT', teacher: 'TEACHER', admin: 'ADMIN' }; const role = roles[username]; if (!role) throw new ApiError('演示账户使用 student、teacher 或 admin', 401, 'INVALID_CREDENTIALS'); actor = { id: `demo-${username}`, role }; sessionStorage.setItem('cassist:demo-actor', JSON.stringify(actor)); return copy(actor); },
  async logout() { actor = null; sessionStorage.removeItem('cassist:demo-actor'); imports.clear(); },
  async assignments() { requireActor(); await delay(); return page(assignments.filter(a => a.state === 'PUBLISHED').map(assignmentWithProgress)); },
  async assignment(id) { requireActor(); await delay(); const value = assignments.find(a => a.id === id && a.state === 'PUBLISHED'); if (!value) throw new ApiError('作业不存在', 404, 'NOT_FOUND'); return assignmentWithProgress(value); },
  async problem(a, p) { requireActor(); await delay(); const assignment = assignments.find(x => x.id === a); const problem = publicProblems.find(x => x.id === p); if (!assignment?.problems.some(x => x.id === p) || !problem) throw new ApiError('题目不存在', 404, 'NOT_FOUND'); return copy(problem); },
  async submit(input, key) { const user = requireActor(); const target = publicProblems.find(p => p.id === input.problemId); if (!target || input.language !== problemLanguage(target)) throw new ApiError('提交语言必须与题目一致', 400, 'LANGUAGE_MISMATCH'); await delay(); return replayed(`${user.id}:submit:${key}`, input, () => { const id = newClientId(); const submittedAt = new Date().toISOString(); submissions.set(id, { owner: user.id, input: copy(input), created: Date.now(), result: { id, status: 'QUEUED', submittedAt, verdict: null, score: null, maxScore: 20, compileMessage: '', caseResults: [], sourceCode: input.sourceCode } }); return { id, status: 'QUEUED' as const, submittedAt }; }); },
  async submission(id) { await delay(); const r = owned(submissions.get(id)); const elapsed = Date.now() - r.created; r.result.status = elapsed < 600 ? 'QUEUED' : elapsed < 1200 ? 'COMPILING' : elapsed < 2000 ? 'RUNNING' : 'FINISHED'; if (r.result.status === 'FINISHED') { r.result.verdict = 'PARTIAL_ACCEPTED'; r.result.score = 10; r.result.caseResults = [{ caseIndex: 1, status: 'ACCEPTED', score: 10, timeMs: 2, memoryKb: 1024 }, { caseIndex: 2, status: 'WRONG_ANSWER', score: 0, timeMs: 2, memoryKb: 1024 }]; } return copy(r.result); },
  async submissions(a, p) { const user = requireActor(); await delay(); return page([...submissions.values()].filter(x => x.owner === user.id && x.input.assignmentId === a && x.input.problemId === p).map(x => x.result).reverse()); },
  async analyse(input) { const user = requireActor(); await delay(); const id = newClientId(); const codeHash = await hash(input.sourceCode); analyses.set(id, { owner: user.id, created: Date.now(), value: { id, status: 'QUEUED', codeHash, summary: null, findings: [], errorCode: null } }); return { id, status: 'QUEUED', codeHash }; },
  async analysis(id) { await delay(); const r = owned(analyses.get(id)); r.value.status = Date.now() - r.created < 1000 ? 'RUNNING' : 'COMPLETED'; if (r.value.status === 'COMPLETED') r.value.summary = '以下为示例指导，未分析当前代码。'; return copy(r.value); },
  async teacherProblems() { requireActor(true); await delay(); return page(teacherProblems); },
  async saveProblem(input) { requireActor(true); await delay(); const validation = functionProblemError(input, true); if (validation) throw new ApiError(validation, 400, 'INVALID_FUNCTION_PROBLEM'); const value = { ...copy(input), id: input.id || newClientId(), versionId: input.versionId || newClientId(), state: 'DRAFT' as const }; teacherProblems = [...teacherProblems.filter(p => p.id !== value.id), value]; return copy(value); },
  async parse(input) { const user = requireActor(true); if (importSourceType(input) === 'JSON') throw new ApiError('演示模式暂不支持 JSON 导入。', 400, 'DEMO_UNSUPPORTED'); await delay(); if (input instanceof File && !input.name.toLowerCase().endsWith('.txt')) throw new ApiError('演示模式仅支持文本与 TXT 导入。', 400, 'DEMO_UNSUPPORTED'); const text = typeof input === 'string' ? input : await input.text(); if (!text.trim()) throw new ApiError('请提供题目材料', 400, 'EMPTY_INPUT'); const id = newClientId(); imports.set(id, { owner: user.id, created: Date.now(), value: { id, status: 'QUEUED', parserVersion: 'ui-demo/0.1', candidates: preview(text), issues: [] } }); return { id, status: 'QUEUED' }; },
  async importJob(id) { requireActor(true); await delay(); const r = owned(imports.get(id)); if (r.value.status !== 'COMMITTED') r.value.status = Date.now() - r.created > 700 ? 'READY' : 'PARSING'; return copy(r.value); },
  async commit(id, title, candidates, key) { const user = requireActor(true); const r = owned(imports.get(id)); await delay(); return replayed(`${user.id}:commit:${key}`, { id, title, candidates }, () => { if (!title.trim() || !candidates.length) throw new ApiError('填写题目集名称并选择题目', 400, 'INVALID_INPUT'); const ids = candidates.map(c => { const p: TeacherProblem = { ...c, id: newClientId(), versionId: newClientId(), codeLimitBytes: c.codeLimitBytes, maxScore: c.maxScore ?? 0, samples: c.samples.filter(s => s.input !== null && s.output !== null).map(s => ({ input: s.input!, output: s.output!, explanation: s.explanation ?? undefined })), state: 'DRAFT' }; teacherProblems.push(p); return p.id; }); r.value.status = 'COMMITTED'; return { problemSetId: newClientId(), problemIds: ids }; }); },
  async classes() { requireActor(true); await delay(); return page(classRows); },
  async members(id) { requireActor(true); await delay(); return page(classMembers.get(id) ?? []); },
  async createClass(name) { requireActor(true); await delay(); const c = { id: newClientId(), name, memberCount: 0 }; classRows.push(c); classMembers.set(c.id, []); return copy(c); },
  async addMember(classId, studentId) { requireActor(true); await delay(); const c = classRows.find(x => x.id === classId); if (!c) throw new ApiError('班级不存在', 404, 'NOT_FOUND'); const rows = classMembers.get(classId)!; if (!rows.some(m => m.id === studentId)) rows.push({ id: studentId, name: '演示学生', studentNumber: studentId }); c.memberCount = rows.length; },
  async teacherAssignments() { requireActor(true); await delay(); return page(assignments); },
  async publish(input) { requireActor(true); await delay(); if (!input.title || !input.problemIds.length || new Date(input.closesAt) <= new Date(input.opensAt)) throw new ApiError('请检查标题、题目和起止时间', 400, 'INVALID_INPUT'); const selected = publicProblems.filter(p => input.problemIds.includes(p.id)); if (selected.length !== input.problemIds.length) throw new ApiError('演示模式仅支持发布预置题目。', 400, 'DRAFT_NOT_READY'); const a: Assignment = { id: newClientId(), title: input.title, className: '计算机 2026 级 1 班', opensAt: input.opensAt, closesAt: input.closesAt, problems: selected, completedCount: 0, score: 0, maxScore: selected.reduce((n, p) => n + p.maxScore, 0), state: 'PUBLISHED' }; assignments.push(a); return copy(a); },
  async results() { requireActor(true); await delay(); return page([{ studentId: 'u1', name: '演示学生甲', completedCount: 3, totalCount: 3, score: 60, maxScore: 60 }, { studentId: 'u2', name: '演示学生乙', completedCount: 2, totalCount: 3, score: 40, maxScore: 60 }, { studentId: 'u3', name: '演示学生丙', completedCount: 0, totalCount: 3, score: 0, maxScore: 60 }]); },
};
