package com.cassist.judge.protocol;

import java.util.Map;

public interface GoJudgeTransport {
    GoJudgeResult execute(Map<String, Object> request) throws JudgeTransportException;
    void deleteCachedFile(String fileId) throws JudgeTransportException;
}
