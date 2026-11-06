package com.cassist.judge.protocol;

/** Stable safe code only; do not expose upstream bodies or URLs. */
public final class JudgeTransportException extends Exception {
    private final String code;
    public JudgeTransportException(String code) { super(code); this.code = code; }
    public String code() { return code; }
}
