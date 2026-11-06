package com.cassist.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Exact current-editor content; do not normalize after hashing. */
public record CodeSnapshot(String sourceCode, String codeHash, int lineCount) {
    public static CodeSnapshot capture(String sourceCode, int maxBytes) {
        if (maxBytes < 1) throw new IllegalArgumentException("Invalid code limit");
        if (sourceCode == null || sourceCode.isBlank()) throw new IllegalArgumentException("EMPTY_CODE");
        byte[] bytes = sourceCode.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxBytes) throw new IllegalArgumentException("CODE_TOO_LARGE");
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            int lines = sourceCode.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1).length;
            return new CodeSnapshot(sourceCode, hash, lines);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    @Override public String toString() { return "CodeSnapshot[codeHash=" + codeHash + ", lineCount=" + lineCount + "]"; }
}
