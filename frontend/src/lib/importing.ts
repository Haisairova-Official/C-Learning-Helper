import type { Candidate } from '../api/types';
import { functionProblemError } from './problemCode';

export function importSourceType(input: string | File): 'TEXT' | 'TXT' | 'DOCX' | 'JSON' {
  if (typeof input !== 'string') return input.name.toLowerCase().endsWith('.json') ? 'JSON' : input.name.toLowerCase().endsWith('.docx') ? 'DOCX' : 'TXT';
  const start = input.trimStart(); return start.startsWith('{') || start.startsWith('[') ? 'JSON' : 'TEXT';
}

export function normalizeCandidate(candidate: Candidate): Candidate {
  return { ...candidate, codeLimitBytes: candidate.codeLimitBytes ?? 65536, problemType: candidate.problemType ?? 'PROGRAM', language: candidate.language ?? 'C17', functionSignature: candidate.functionSignature ?? '', starterCode: candidate.starterCode ?? '', judgeTemplate: candidate.judgeTemplate ?? '' };
}

export function candidateBlocker(candidate: Candidate): string {
  if (!['PROGRAM', 'FUNCTION'].includes(candidate.problemType)) return '请选择有效题型。';
  if (!['C17', 'CPP17'].includes(candidate.language)) return '请选择 C 或 C++。';
  if (candidate.problemType === 'PROGRAM' && candidate.judgeTemplate.trim()) return '程序题不使用评测主程序，请确认题型或将模板移至导入记录的私有备注。';
  if (candidate.testCases.some(test => test.input === null || test.expectedOutput === null)) return '请补齐测试点输入和预期输出。';
  return functionProblemError(candidate, true);
}

export function candidateDraftWarning(candidate: Candidate): string {
  if (candidate.problemType !== 'FUNCTION') return '';
  const missing = [!candidate.functionSignature.trim() ? '函数接口' : '', !candidate.judgeTemplate.trim() ? '私有评测主程序' : ''].filter(Boolean);
  return missing.length ? `缺少${missing.join('、')}，可保存为待修正草稿，补齐后才能发布。` : '';
}
