package com.cassist.judge.core;

/** Fixed server-owned toolchains; no student supplied command, option or path. */
public enum CompileLanguage {
    C17("/usr/bin/gcc", "-std=c17", "main.c", "student.c"),
    CPP17("/usr/bin/g++", "-std=c++17", "main.cpp", "student.cpp");
    private final String compiler, standard, sourceFile, studentFile;
    CompileLanguage(String compiler, String standard, String sourceFile, String studentFile) {
        this.compiler=compiler; this.standard=standard; this.sourceFile=sourceFile; this.studentFile=studentFile;
    }
    public String compiler() { return compiler; }
    public String standard() { return standard; }
    public String sourceFile() { return sourceFile; }
    public String studentFile() { return studentFile; }
}
