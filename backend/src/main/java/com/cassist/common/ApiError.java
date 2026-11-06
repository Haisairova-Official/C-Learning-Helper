package com.cassist.common;

import java.util.Map;

public record ApiError(String code, String message, String traceId, Map<String, String> fieldErrors) {
    public ApiError {
        fieldErrors = fieldErrors == null ? Map.of() : Map.copyOf(fieldErrors);
    }
}
