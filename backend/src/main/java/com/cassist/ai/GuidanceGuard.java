package com.cassist.ai;

import java.util.List;
import java.util.regex.Pattern;

import com.cassist.ai.AiProvider.*;

/** A conservative heuristic, not a proof of confidentiality or correctness. */
public final class GuidanceGuard {
    private static final Pattern CODE_OUTPUT = Pattern.compile("(?s)(```|~~~|#\\s*include\\s*[<\"]|\\b(?:int|void)\\s+main\\s*\\()");
    private static final Pattern FUNCTION_DEFINITION = Pattern.compile("(?s)\\b(?:bool|int|long|short|float|double|char|void|auto|unsigned|signed)\\s+[\\w:*&<>\\s]+\\([^{};]*\\)\\s*\\{");
    private final int maxCharacters;
    private final int maxFindings;

    public GuidanceGuard(int maxCharacters, int maxFindings) {
        if (maxCharacters < 1 || maxFindings < 1) throw new IllegalArgumentException("Invalid guidance limits");
        this.maxCharacters = maxCharacters;
        this.maxFindings = maxFindings;
    }

    public Guidance validate(Guidance response, AnalysisRequest request) throws ProviderException {
        if (response == null || response.summary() == null || response.summary().isBlank()
                || response.findings() == null || response.findings().size() > maxFindings)
            throw new ProviderException(Failure.INVALID_RESPONSE);
        StringBuilder combined = new StringBuilder(response.summary());
        for (Finding f : response.findings()) {
            if (f == null || blank(f.category()) || blank(f.reason()) || blank(f.hint()))
                throw new ProviderException(Failure.INVALID_RESPONSE);
            if ((f.startLine() == null) != (f.endLine() == null))
                throw new ProviderException(Failure.INVALID_RESPONSE);
            if (f.startLine() != null && (f.startLine() < 1 || f.endLine() < f.startLine()
                    || f.endLine() > request.code().lineCount()))
                throw new ProviderException(Failure.INVALID_RESPONSE);
            combined.append('\n').append(f.category()).append('\n').append(f.reason()).append('\n').append(f.hint());
            if (combined.length() > maxCharacters) throw new ProviderException(Failure.INVALID_RESPONSE);
        }
        if (combined.length() > maxCharacters) throw new ProviderException(Failure.INVALID_RESPONSE);
        String all = combined.toString();
        if (CODE_OUTPUT.matcher(all).find() || FUNCTION_DEFINITION.matcher(all).find())
            throw new ProviderException(Failure.UNSAFE_RESPONSE);
        String normalized = normalize(all);
        String reference = normalize(request.problem().referenceAnswer());
        if (reference.length() >= 12 && normalized.contains(reference))
            throw new ProviderException(Failure.UNSAFE_RESPONSE);
        for (String line : request.problem().referenceAnswer().split("\\R")) {
            String snippet = normalize(line);
            if (snippet.length() >= 24 && normalized.contains(snippet))
                throw new ProviderException(Failure.UNSAFE_RESPONSE);
        }
        return new Guidance(response.summary(), List.copyOf(response.findings()));
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String normalize(String value) { return value.replaceAll("\\s+", ""); }
}
