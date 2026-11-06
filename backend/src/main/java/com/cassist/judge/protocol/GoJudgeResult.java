package com.cassist.judge.protocol;

import java.util.Map;

/** Internal protocol response. Never serialize this object to a student-facing endpoint. */
public record GoJudgeResult(String status, Integer exitStatus, Long time, Long memory,
                            Long runTime, Map<String, String> files, Map<String, String> fileIds) {
    public GoJudgeResult {
        files = files == null ? Map.of() : Map.copyOf(files);
        fileIds = fileIds == null ? Map.of() : Map.copyOf(fileIds);
    }
    @Override public String toString() { return "GoJudgeResult[status=" + status + "]"; }
}
