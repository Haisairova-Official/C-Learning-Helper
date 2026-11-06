package com.cassist.ai;

import java.net.URI;
import java.time.Duration;

/** Server-side configuration only. Credentials belong in a secret provider, not this record or responses. */
public record AiProviderSettings(AiProvider.ProviderMode mode, URI endpoint, String model,
        Duration connectTimeout, Duration responseTimeout, int maxConcurrentRequests,
        int maxCodeBytes, int maxResponseCharacters) {
    public AiProviderSettings {
        if (mode == null || connectTimeout == null || responseTimeout == null
                || connectTimeout.isNegative() || connectTimeout.isZero()
                || responseTimeout.isNegative() || responseTimeout.isZero()
                || maxConcurrentRequests < 1 || maxCodeBytes < 1 || maxResponseCharacters < 1)
            throw new IllegalArgumentException("Invalid AI provider configuration");
        if (mode != AiProvider.ProviderMode.MOCK && (endpoint == null || model == null || model.isBlank()))
            throw new IllegalArgumentException("Real provider requires endpoint and model configuration");
        if (endpoint != null && (!ListOfSchemes.allowed(endpoint.getScheme())
                || endpoint.getHost() == null || endpoint.getUserInfo() != null))
            throw new IllegalArgumentException("Invalid AI endpoint");
    }

    private static final class ListOfSchemes {
        static boolean allowed(String scheme) { return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme); }
    }

    @Override public String toString() { return "AiProviderSettings[mode=" + mode + ", model=" + model + "]"; }
}
