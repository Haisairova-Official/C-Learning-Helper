import { describe, expect, it } from 'vitest';
import type { Problem } from '../api/types';
import { blankJudgeTemplate, functionProblemError, initialCode, problemLanguage, studentCodeMarker } from '../lib/problemCode';

const functionProblem: Pick<Problem, 'problemType' | 'language' | 'starterCode'> = { problemType: 'FUNCTION', language: 'CPP17', starterCode: 'int calculate(int value) {\n    // TODO\n}\n' };
describe('函数题起始代码与教师评测框架', () => {
  it('用户草稿优先于新模板，主动清空的草稿也不会被恢复为示例', () => {
    expect(initialCode(functionProblem, '// 已经开始作答')).toBe('// 已经开始作答');
    expect(initialCode(functionProblem, '')).toBe('');
    expect(initialCode(functionProblem, null)).toBe(functionProblem.starterCode);
  });
  it('无模板的函数题不生成 main，实现语言固定来自题目', () => {
    const code = initialCode({ ...functionProblem, starterCode: '' }, null);
    expect(code).not.toMatch(/int\s+main\s*\(/); expect(code).toContain('只提交函数');
    expect(problemLanguage(functionProblem)).toBe('CPP17');
  });
  it('旧题兼容 C17，C++ 完整程序使用对应默认模板', () => {
    const legacy = {} as Pick<Problem, 'problemType' | 'language' | 'starterCode'>;
    expect(problemLanguage(legacy)).toBe('C17'); expect(initialCode(legacy, null)).toContain('#include <stdio.h>');
    expect(initialCode({ problemType: 'PROGRAM', language: 'CPP17', starterCode: '' }, null)).toContain('#include <iostream>');
  });
  it('函数题必须同时有公开接口和唯一的私有插入位置', () => {
    const valid = { problemType: 'FUNCTION' as const, functionSignature: 'int calculate(int value);', judgeTemplate: blankJudgeTemplate('CPP17') };
    expect(functionProblemError(valid)).toBe('');
    expect(functionProblemError({ ...valid, functionSignature: '  ' })).toContain('函数接口');
    expect(functionProblemError({ ...valid, judgeTemplate: 'int main() {}' })).toContain('仅包含一次');
    expect(functionProblemError({ ...valid, judgeTemplate: valid.judgeTemplate + studentCodeMarker })).toContain('仅包含一次');
    expect(functionProblemError({ ...valid, problemType: 'PROGRAM', judgeTemplate: '' })).toBe('');
    expect(functionProblemError({ ...valid, functionSignature: '', judgeTemplate: '' }, true)).toBe('');
    expect(functionProblemError({ ...valid, judgeTemplate: 'invalid private driver' }, true)).toContain('仅包含一次');
  });
});
