package com.cassist.judge.core;

import java.util.Objects;

/** Internal ephemeral compiler input. Never persist or serialize as student sourceCode. */
public record CompilationSource(String content, CompileLanguage language, boolean hasPrivateTemplate) {
    public CompilationSource { Objects.requireNonNull(content); Objects.requireNonNull(language); }
    public static CompilationSource program(String source, CompileLanguage language) {
        return new CompilationSource(source, language, false);
    }
    @Override public String toString() { return "CompilationSource[language=" + language + ",hasPrivateTemplate=" + hasPrivateTemplate + "]"; }
}
