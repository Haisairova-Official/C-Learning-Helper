import type { Language, Problem, TeacherProblem } from '../api/types';

export const studentCodeMarker = '/*__STUDENT_CODE__*/';
export const problemLanguage = (problem?: Pick<Problem, 'language'>): Language => problem?.language === 'CPP17' ? 'CPP17' : 'C17';
export const languageLabel = (language: Language) => language === 'CPP17' ? 'C++' : 'C';

export function initialCode(problem: Pick<Problem, 'problemType' | 'language' | 'starterCode'>, draft: string | null): string {
  if (draft !== null) return draft;
  if (problem.starterCode?.trim()) return problem.starterCode;
  if (problem.problemType === 'FUNCTION') return '// 请按左侧函数接口完成实现。\n// 只提交函数代码，无需编写 main 函数。\n';
  if (problemLanguage(problem) === 'CPP17') return '#include <iostream>\n\nint main() {\n    // 在这里编写你的程序\n\n    return 0;\n}\n';
  return '#include <stdio.h>\n\nint main(void) {\n    // 在这里编写你的程序\n\n    return 0;\n}\n';
}

export function functionProblemError(problem: Pick<TeacherProblem, 'problemType' | 'functionSignature' | 'judgeTemplate'>, allowIncomplete = false): string {
  if (problem.problemType !== 'FUNCTION') return '';
  if (!allowIncomplete && !problem.functionSignature?.trim()) return '函数题需要填写学生可见的函数接口。';
  if (allowIncomplete && !problem.judgeTemplate?.trim()) return '';
  if ((problem.judgeTemplate?.split(studentCodeMarker).length ?? 1) !== 2) return `教师评测主程序必须包含且仅包含一次 ${studentCodeMarker}。`;
  return '';
}

export function blankJudgeTemplate(language: Language): string {
  return `${language === 'CPP17' ? '#include <iostream>' : '#include <stdio.h>'}\n\n${studentCodeMarker}\n\n${language === 'CPP17' ? 'int main()' : 'int main(void)'} {\n    // 读取测试输入，调用待测函数，再输出用于比较的结果。\n    return 0;\n}\n`;
}
