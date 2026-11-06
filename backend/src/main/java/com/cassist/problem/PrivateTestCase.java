package com.cassist.problem;

import java.math.BigDecimal;

/** Server-internal data. Never return this object to a student endpoint. */
public record PrivateTestCase(String id, String input, String expectedOutput, Visibility visibility,
        BigDecimal score, ComparisonMode comparisonMode) {
    public enum Visibility { PUBLIC, HIDDEN }
    public enum ComparisonMode { EXACT, TOKENS }
}
