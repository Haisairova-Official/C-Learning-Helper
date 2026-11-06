package com.cassist.ai;

import java.util.List;

/** Explicit developer fixture. Not a fallback for an unavailable school service. */
public final class MockAiProvider implements AiProvider {
    @Override public ProviderMode mode() { return ProviderMode.MOCK; }

    @Override public Guidance analyze(AnalysisRequest request) {
        return new Guidance("模拟响应：学校 AI 尚未接入，本结果只用于验证界面与接口，不代表代码分析。", List.of());
    }
}
