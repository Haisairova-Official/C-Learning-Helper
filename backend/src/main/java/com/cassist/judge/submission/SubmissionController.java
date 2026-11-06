package com.cassist.judge.submission;

import com.cassist.common.PageResult;
import com.cassist.identity.CurrentActor;
import com.cassist.judge.submission.SubmissionModels.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/submissions")
public final class SubmissionController {
    private final CurrentActor actors; private final SubmissionService submissions;
    public SubmissionController(CurrentActor actors,SubmissionService submissions){this.actors=actors;this.submissions=submissions;}
    @PostMapping public ResponseEntity<Accepted> submit(@Valid @RequestBody CreateRequest request,
            @RequestHeader("Idempotency-Key") String key){return ResponseEntity.accepted().body(submissions.submit(actors.require(),request,key));}
    @GetMapping("/{id}") public View get(@PathVariable String id){return submissions.get(actors.require(),id);}
    @GetMapping public PageResult<View> list(@RequestParam(required=false) String studentId,
            @RequestParam(required=false) String assignmentId,@RequestParam(required=false) String problemId,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize){
        return submissions.list(actors.require(),studentId,assignmentId,problemId,page,pageSize);
    }
}
