package com.cassist.judge.core;

import com.cassist.problem.ProblemCodePolicy;
import com.cassist.problem.ProblemSnapshot;
import java.util.Objects;

public final class FunctionSourceAssembler {
    private FunctionSourceAssembler() {}
    public static CompilationSource fromAuthorized(ProblemSnapshot snapshot, String source, String submittedLanguage) {
        Objects.requireNonNull(snapshot); Objects.requireNonNull(source);
        var p = snapshot.problem();
        if (!p.language().equals(submittedLanguage)) throw new IllegalArgumentException("Submission language does not match authorized version");
        var language = CompileLanguage.valueOf(p.language());
        ProblemCodePolicy.validate(p.problemType(), p.language(), p.functionSignature(), p.starterCode(), snapshot.judgeTemplate());
        JudgeProblemMapper.fromAuthorized(snapshot, source);
        if (source.isBlank()) throw new IllegalArgumentException("Source is empty");
        if ("PROGRAM".equals(p.problemType())) return CompilationSource.program(source, language);
        String template = snapshot.judgeTemplate();
        int marker = template.indexOf(ProblemCodePolicy.STUDENT_MARKER);
        String combined = "#line 1 \"judge_template\"\n" + template.substring(0, marker)
                + "\n#line 1 \"" + language.studentFile() + "\"\n" + source
                + "\n#line 1 \"judge_template\"\n" + template.substring(marker + ProblemCodePolicy.STUDENT_MARKER.length());
        return new CompilationSource(combined, language, true);
    }
}
