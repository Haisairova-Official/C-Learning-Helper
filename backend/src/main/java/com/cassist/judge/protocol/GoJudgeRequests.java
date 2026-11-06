package com.cassist.judge.protocol;

import com.cassist.judge.core.JudgeModels.Limits;
import com.cassist.judge.core.CompilationSource;
import com.cassist.judge.core.CompileLanguage;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public final class GoJudgeRequests {
    public static final int MAX_SOURCE_BYTES = 65_536;
    public static final int MAX_INPUT_BYTES = 1_048_576;
    public static final Limits COMPILE_LIMITS = new Limits(10_000, 20_000, 536_870_912L, 67_108_864, 16, 65_536);
    private static final Pattern FILE_ID = Pattern.compile("[A-Za-z0-9_-]{1,128}");
    private GoJudgeRequests() {}

    public static Map<String, Object> compile(String source) {
        return compile(CompilationSource.program(source, CompileLanguage.C17));
    }

    public static Map<String, Object> compile(CompilationSource source) {
        Objects.requireNonNull(source);
        int maxBytes = source.hasPrivateTemplate() ? MAX_SOURCE_BYTES * 3 : MAX_SOURCE_BYTES;
        if (source.content().isBlank() || source.content().getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new IllegalArgumentException("Source code empty or too large");
        }
        var language = source.language();
        Map<String, Object> cmd = command(COMPILE_LIMITS, "");
        cmd.put("args", List.of(language.compiler(), language.standard(), "-O2", "-Wall", "-Wextra",
                "-fdiagnostics-color=never", "-fno-diagnostics-show-caret", language.sourceFile(), "-o", "main", "-lm"));
        cmd.put("copyIn", Map.of(language.sourceFile(), Map.of("content", source.content())));
        // Optional copy-out preserves compilation failure instead of creating a missing-binary File Error.
        cmd.put("copyOutCached", List.of("main?"));
        cmd.put("copyOutMax", 16_777_216);
        return Map.of("cmd", List.of(Map.copyOf(cmd)));
    }

    public static Map<String, Object> run(String binaryFileId, String input, Limits limits) {
        requireFileId(binaryFileId); Objects.requireNonNull(input); Objects.requireNonNull(limits);
        if (input.getBytes(StandardCharsets.UTF_8).length > MAX_INPUT_BYTES) throw new IllegalArgumentException("Test input too large");
        Map<String, Object> cmd = command(limits, input);
        cmd.put("args", List.of("./main"));
        cmd.put("copyIn", Map.of("main", Map.of("fileId", binaryFileId)));
        cmd.put("copyOutMax", limits.outputBytes());
        return Map.of("cmd", List.of(Map.copyOf(cmd)));
    }

    public static void requireFileId(String id) {
        if (id == null || !FILE_ID.matcher(id).matches()) throw new IllegalArgumentException("Invalid cached file identifier");
    }

    private static Map<String, Object> command(Limits limits, String input) {
        Map<String, Object> cmd = new LinkedHashMap<>();
        cmd.put("env", List.of("PATH=/usr/bin:/bin", "LC_ALL=C"));
        cmd.put("files", List.of(Map.of("content", input), Map.of("name", "stdout", "max", limits.outputBytes()),
                Map.of("name", "stderr", "max", limits.outputBytes())));
        cmd.put("cpuLimit", Math.multiplyExact(limits.cpuTimeMs(), 1_000_000));
        cmd.put("clockLimit", Math.multiplyExact(limits.wallTimeMs(), 1_000_000));
        cmd.put("memoryLimit", limits.memoryBytes());
        cmd.put("stackLimit", limits.stackBytes());
        cmd.put("procLimit", limits.processLimit());
        cmd.put("copyOut", List.of("stdout", "stderr"));
        return cmd;
    }
}
