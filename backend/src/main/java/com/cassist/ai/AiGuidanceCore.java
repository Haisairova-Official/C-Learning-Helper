package com.cassist.ai;

import java.util.Objects;
import java.util.concurrent.Semaphore;

import com.cassist.ai.AiProvider.*;
import com.cassist.identity.AiReplyStyle;

/** Controller/job layer must authorize the actor and load the matching problem version first. */
public final class AiGuidanceCore {
    private final AiProvider provider;
    private final AiProviderSettings settings;
    private final GuidanceGuard guard;
    private final Semaphore permits;
    private final String systemInstruction;

    public AiGuidanceCore(AiProvider provider, AiProviderSettings settings) {
        this.provider = Objects.requireNonNull(provider);
        this.settings = Objects.requireNonNull(settings);
        if (provider.mode() != settings.mode()) throw new IllegalArgumentException("AI provider mode mismatch");
        this.guard = new GuidanceGuard(settings.maxResponseCharacters(), 12);
        this.permits = new Semaphore(settings.maxConcurrentRequests());
        this.systemInstruction = GuidancePrompt.load();
    }

    public Result analyzeAuthorized(ProblemContext authorizedProblem, String currentCode) throws ProviderException {
        return analyzeAuthorized(authorizedProblem, currentCode, AiReplyStyle.GUIDED);
    }

    public Result analyzeAuthorized(ProblemContext authorizedProblem, String currentCode, AiReplyStyle style) throws ProviderException {
        return analyzeAuthorized(authorizedProblem, currentCode, style, "");
    }

    public Result analyzeAuthorized(ProblemContext authorizedProblem, String currentCode, AiReplyStyle style, String addressName) throws ProviderException {
        CodeSnapshot snapshot = CodeSnapshot.capture(currentCode, settings.maxCodeBytes());
        AnalysisRequest request = new AnalysisRequest(systemInstruction + GuidancePrompt.styleInstruction(Objects.requireNonNull(style)),
                GuidancePrompt.VERSION, authorizedProblem, snapshot, style == AiReplyStyle.TINO ? addressName : "");
        if (!permits.tryAcquire()) throw new ProviderException(Failure.UNAVAILABLE);
        try {
            Guidance approved = guard.validate(provider.analyze(request), request);
            return new Result(snapshot.codeHash(), GuidancePrompt.VERSION, provider.mode(), approved);
        } finally {
            permits.release();
        }
    }

    /** Public result contains neither source code nor reference answer. */
    public record Result(String codeHash, String promptVersion, ProviderMode providerMode, Guidance guidance) { }
}
