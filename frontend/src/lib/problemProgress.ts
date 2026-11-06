import type { AssignmentProblem } from '../api/types';

export function progressTone(problem: AssignmentProblem): string {
  const tones: Record<string, string> = { ACCEPTED: 'correct', PARTIAL: 'partial', WRONG: 'wrong' };
  return tones[problem.progress?.status ?? ''] ?? 'neutral';
}

export function progressLabel(problem: AssignmentProblem): string {
  const labels: Record<string, string> = { UNSUBMITTED: '未提交', PENDING: '评测中', SYSTEM_ERROR: '评测服务异常', WRONG: '全部错误', PARTIAL: '部分正确', ACCEPTED: '全部正确' };
  const progress = problem.progress;
  const label = progress ? labels[progress.status] : '状态待更新';
  return label + (progress?.pending && progress.status !== 'PENDING' ? ' · 新提交评测中' : '');
}

export function problemHint(problem: AssignmentProblem, index: number): string {
  const score = problem.progress?.bestScore;
  return `第 ${index + 1} 题：${problem.title} · ${progressLabel(problem)}` + (score != null ? ` · 最高 ${score} / ${problem.maxScore} 分` : '');
}
