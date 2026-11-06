package com.cassist.judge.core;

import java.util.Objects;

/** Text output comparison with one optional final newline; never coerces numeric tokens. */
public final class OutputComparator {
    private OutputComparator() {}

    public static Verdict compare(String expected, String actual, ComparisonMode mode) {
        if (matches(expected, actual, mode)) return Verdict.ACCEPTED;
        return mode == ComparisonMode.EXACT && matches(expected, actual, ComparisonMode.TOKENS)
                ? Verdict.PRESENTATION_ERROR : Verdict.WRONG_ANSWER;
    }

    public static boolean matches(String expected, String actual, ComparisonMode mode) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(actual, "actual");
        Objects.requireNonNull(mode, "mode");
        if (mode == ComparisonMode.EXACT) {
            return withoutFinalNewline(expected).equals(withoutFinalNewline(actual));
        }
        int a = 0, b = 0;
        while (true) {
            while (a < expected.length() && isSpace(expected.charAt(a))) a++;
            while (b < actual.length() && isSpace(actual.charAt(b))) b++;
            if (a == expected.length() || b == actual.length()) {
                return a == expected.length() && b == actual.length();
            }
            int ae = a, be = b;
            while (ae < expected.length() && !isSpace(expected.charAt(ae))) ae++;
            while (be < actual.length() && !isSpace(actual.charAt(be))) be++;
            if (ae - a != be - b || !expected.regionMatches(a, actual, b, ae - a)) return false;
            a = ae;
            b = be;
        }
    }

    private static String withoutFinalNewline(String value) {
        String normalized = value.replace("\r\n", "\n");
        return normalized.endsWith("\n") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private static boolean isSpace(char c) { return c == ' ' || c >= '\t' && c <= '\r'; }
}
