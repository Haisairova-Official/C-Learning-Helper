package com.cassist.problem;

import com.cassist.identity.Actor;
import java.util.List;

/** A's implementation must check role/job ownership and commit atomically with idempotency. */
public interface DraftImportPort {
    DraftImportResult saveDrafts(Actor actor, String importJobId, List<DraftProblem> drafts, String title);

    record DraftImportResult(String problemSetId, List<String> problemIds) {
        public DraftImportResult { problemIds = List.copyOf(problemIds); }
    }
}
