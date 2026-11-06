package com.cassist.problem;

import java.nio.charset.StandardCharsets;

/** Shared definition checks; source and private harness are never included in exceptions. */
public final class ProblemCodePolicy {
    public static final String STUDENT_MARKER = "/*__STUDENT_CODE__*/";
    public static final int MAX_TEMPLATE_BYTES = 65_536;
    private ProblemCodePolicy() {}

    public static void validate(String problemType, String language, String signature, String starter, String template) {
        validateDraft(problemType, language, signature, starter, template);
        if ("FUNCTION".equals(problemType)) {
            if (signature == null || signature.isBlank()) invalid("函数题须填写公开函数接口");
            if (template == null || template.isBlank()) invalid("函数题须填写私有评测模板及学生代码占位标记");
        }
    }

    public static void validateDraft(String problemType, String language, String signature, String starter, String template) {
        if (!"PROGRAM".equals(problemType) && !"FUNCTION".equals(problemType)) invalid("题型须为 PROGRAM 或 FUNCTION");
        if (!"C17".equals(language) && !"CPP17".equals(language)) invalid("语言须为 C17 或 CPP17");
        if (bytes(signature) > 4096 || bytes(starter) > 65_536 || bytes(template) > MAX_TEMPLATE_BYTES)
            invalid("函数接口、起始代码或评测模板过长");
        if ("FUNCTION".equals(problemType)) {
            if (template != null && !template.isBlank() && (template.indexOf(STUDENT_MARKER) < 0
                    || template.indexOf(STUDENT_MARKER) != template.lastIndexOf(STUDENT_MARKER)))
                invalid("函数题评测模板须且只能包含一个学生代码占位标记");
        } else if (template != null && !template.isBlank()) {
            invalid("程序题不使用私有评测模板");
        }
    }
    private static int bytes(String value) { return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length; }
    private static void invalid(String message) { throw new IllegalArgumentException(message); }
}
