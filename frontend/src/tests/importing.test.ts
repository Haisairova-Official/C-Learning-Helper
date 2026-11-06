import { describe, expect, it } from 'vitest';
import type { Candidate } from '../api/types';
import { candidateBlocker, candidateDraftWarning, importSourceType, normalizeCandidate } from '../lib/importing';

const candidate: Candidate = { candidateId: '1', title: '函数练习', statement: '', inputDescription: '', outputDescription: '', samples: [], referenceAnswer: '', privateNotes: '', unresolvedContent: '', testCases: [], maxScore: 20, timeLimitMs: 1000, memoryLimitMb: 128, codeLimitBytes: 16384, sourceLocation: { startLine: 1, endLine: 3 }, issues: [], needsReview: true, problemType: 'FUNCTION', language: 'CPP17', functionSignature: '', starterCode: '', judgeTemplate: '' };
describe('结构化函数题导入', () => {
  it('识别粘贴 JSON 和文件格式，普通文本继续使用原解析器', () => {
    expect(importSourceType('\ufeff  {"problems":[]}')).toBe('JSON'); expect(importSourceType('\n[]')).toBe('JSON');
    expect(importSourceType('第1题：函数')).toBe('TEXT');
    expect(importSourceType(new File(['{}'], 'package.JSON'))).toBe('JSON');
    expect(importSourceType(new File([''], '练习.docx'))).toBe('DOCX');
  });
  it('缺接口和模板可暂存但显示待修正，不改变题型或语言', () => {
    expect(candidateBlocker(candidate)).toBe(''); expect(candidateDraftWarning(candidate)).toContain('待修正草稿');
    const normalized = normalizeCandidate(candidate);
    expect(normalized.problemType).toBe('FUNCTION'); expect(normalized.language).toBe('CPP17'); expect(normalized.codeLimitBytes).toBe(16384);
  });
  it('阻止错误的非空模板及无效配置，不把私有模板转入题干', () => {
    const invalid = { ...candidate, judgeTemplate: 'private harness without marker' };
    expect(candidateBlocker(invalid)).toContain('仅包含一次');
    expect(normalizeCandidate(invalid).statement).toBe('');
    expect(candidateBlocker({ ...candidate, problemType: 'PROGRAM', judgeTemplate: 'private' })).toContain('程序题不使用');
    expect(candidateBlocker({ ...candidate, testCases: [{ input: null, expectedOutput: '', score: 1, visibility: 'HIDDEN', comparisonMode: 'EXACT' }] })).toContain('补齐');
  });
  it('旧候选仍默认 C17 程序题，并保留已识别的代码与样例说明', () => {
    const old = { candidateId: 'legacy', samples: [{ key: '1', input: '', output: '', explanation: '说明' }] } as Candidate;
    const value = normalizeCandidate(old);
    expect(value.problemType).toBe('PROGRAM'); expect(value.language).toBe('C17'); expect(value.codeLimitBytes).toBe(65536);
    expect(value.samples[0]?.explanation).toBe('说明');
  });
});
