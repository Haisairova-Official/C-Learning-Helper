package com.cassist.ai.web;

import com.cassist.ai.*;
import com.cassist.common.*;
import com.cassist.identity.*;
import com.cassist.problem.ProblemAccessPort;
import java.util.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/analyses")
public class AiAnalysisController {
    private final CurrentActor actors;
    private final JsonJobStore jobs;
    private final JdbcTemplate jdbc;
    private final ProblemAccessPort problems;
    private final AiRuntime runtime;
    private final ProfileService profiles;
    public AiAnalysisController(CurrentActor actors,JsonJobStore jobs,JdbcTemplate jdbc,ProblemAccessPort problems,AiRuntime runtime,ProfileService profiles) {
        this.actors=actors;this.jobs=jobs;this.jdbc=jdbc;this.problems=problems;
        this.runtime=runtime; this.profiles=profiles;
    }
    /** Single backend deployment: do not leave interrupted work polling forever or bill an automatic replay. */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterrupted() {
        jdbc.update("UPDATE ai_analysis SET status='FAILED', payload_json=JSON_SET(payload_json, '$.response.status', 'FAILED', '$.response.errorCode', 'INTERRUPTED'), updated_at=CURRENT_TIMESTAMP(6) WHERE status IN ('QUEUED','RUNNING')");
    }
    public record Request(String assignmentId,String problemId,String problemVersionId,String sourceCode,String submissionId) {
        @Override public String toString() {return "AiRequest[problemId="+problemId+"]";}
    }
    public record Analysis(String id,String status,String codeHash,String summary,List<AiProvider.Finding> findings,String errorCode,String providerMode,String promptVersion,String replyStyle) {}
    public record ProviderInfo(String providerMode,boolean configured) {}
    @GetMapping("/provider")
    public ProviderInfo provider() { actors.require(); return new ProviderInfo(runtime.mode(),!"DISABLED".equals(runtime.mode())); }
    private record Stored(Analysis response,String assignmentId,String problemId,String problemVersionId,String sourceCode,String addressName) {
        @Override public String toString() {return "StoredAiAnalysis[id="+response.id()+"]";}
    }
    @PostMapping
    public ResponseEntity<Map<String,String>> create(@RequestBody Request request) {
        Actor actor=actors.require();
        if(request.assignmentId()==null || request.problemId()==null || request.problemVersionId()==null || request.sourceCode()==null)
            throw new BusinessException(HttpStatus.BAD_REQUEST,"INVALID_ANALYSIS","请选择题目并提供当前代码");
        var problem=problems.requireForStudent(actor,request.assignmentId(),request.problemId(),request.problemVersionId());
        requireAiEnabled(request.assignmentId());
        CodeSnapshot snapshot;
        try {snapshot=CodeSnapshot.capture(request.sourceCode(),Math.toIntExact(Math.min(65536,problem.problem().codeLimitBytes())));}
        catch(IllegalArgumentException e) {throw new BusinessException(HttpStatus.BAD_REQUEST,"INVALID_CODE","代码为空或超过题目限制");}
        if(request.submissionId()!=null && !request.submissionId().isBlank()) {
            Integer owned=jdbc.queryForObject("SELECT COUNT(*) FROM submission WHERE id=? AND student_id=? AND assignment_id=? AND problem_id=? AND problem_version_id=?",Integer.class,
                request.submissionId(),actor.id(),request.assignmentId(),request.problemId(),request.problemVersionId());
            if(owned==null || owned==0) throw new BusinessException(HttpStatus.FORBIDDEN,"FORBIDDEN","无权使用该提交记录");
        }
        AiReplyStyle style=AiReplyStyle.valueOf(profiles.getAiReplyStyle(actor.id()));
        String addressName=style==AiReplyStyle.TINO?profiles.getAiAddress(actor):"";
        String id=UUID.randomUUID().toString();
        Analysis initial=new Analysis(id,"RUNNING",snapshot.codeHash(),null,List.of(),null,runtime.mode(),GuidancePrompt.VERSION,style.name());
        runtime.submit(actor.id(),()->jobs.create("ai_analysis",id,actor,"RUNNING",stored(initial,request,addressName)),
                ()->complete(id,actor,request,snapshot,style,addressName));
        return ResponseEntity.accepted().body(Map.of("id",id,"status","RUNNING","codeHash",snapshot.codeHash()));
    }
    private void complete(String id,Actor actor,Request request,CodeSnapshot snapshot,AiReplyStyle style,String addressName) {
        Analysis response;
        try {
            var result=runtime.analyze(actor,request.assignmentId(),request.problemId(),request.problemVersionId(),request.sourceCode(),style,addressName);
            response=new Analysis(id,"COMPLETED",result.codeHash(),result.guidance().summary(),result.guidance().findings(),null,result.providerMode().name(),result.promptVersion(),style.name());
        } catch(AiProvider.ProviderException e) {
            response=new Analysis(id,"FAILED",snapshot.codeHash(),null,List.of(),e.failure().name(),runtime.mode(),GuidancePrompt.VERSION,style.name());
        } catch(RuntimeException e) {
            response=new Analysis(id,"FAILED",snapshot.codeHash(),null,List.of(),"UNAVAILABLE",runtime.mode(),GuidancePrompt.VERSION,style.name());
        }
        jobs.save("ai_analysis",id,response.status(),stored(response,request,addressName));
    }
    @GetMapping("/{id}")
    public Analysis get(@PathVariable String id) {
        Actor actor=actors.require();Stored stored=jobs.read("ai_analysis",id,actor,Stored.class);
        problems.requireForStudent(actor,stored.assignmentId(),stored.problemId(),stored.problemVersionId());
        requireAiEnabled(stored.assignmentId());return stored.response();
    }
    private void requireAiEnabled(String assignmentId) {
        Boolean enabled=jdbc.queryForObject("SELECT ai_enabled FROM assignment WHERE id=?",Boolean.class,assignmentId);
        if(!Boolean.TRUE.equals(enabled)) throw new BusinessException(HttpStatus.FORBIDDEN,"AI_DISABLED","该作业未开放 AI 辅助");
    }
    private Stored stored(Analysis response,Request request,String addressName) {return new Stored(response,request.assignmentId(),request.problemId(),request.problemVersionId(),request.sourceCode(),addressName);}
}
