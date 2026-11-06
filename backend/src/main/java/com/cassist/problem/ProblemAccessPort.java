package com.cassist.problem;

import com.cassist.identity.Actor;
import java.time.Instant;

/** Implemented by A's assignment/problem services; modules must not bypass authorization. */
public interface ProblemAccessPort {
    ProblemSnapshot requireForStudent(Actor actor, String assignmentId, String problemId, String versionId);

    ProblemSnapshot requireForSubmission(Actor actor, String assignmentId, String problemId,
                                         String versionId, Instant receivedAt);

    /** Worker-only access to a previously authorized, persisted submission's version. */
    ProblemSnapshot loadForEvaluation(String versionId);
}
