package com.cassist.judge.submission;

import com.cassist.common.BusinessException;
import com.cassist.identity.*;
import com.cassist.judge.core.*;
import com.cassist.judge.core.JudgeModels.Limits;
import com.cassist.problem.ProblemAccessPort;
import jakarta.annotation.PreDestroy;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TrialService {
    public record Input(@NotBlank @Size(max=100) String assignmentId, @NotBlank @Size(max=100) String problemId,
                        @NotBlank @Size(max=100) String problemVersionId, @NotBlank @Size(max=65536) String sourceCode,
                        @NotBlank @Size(max=10) String language, @NotNull @Size(max=65536) String stdin,
                        @Size(max=65536) String expectedOutput, @Size(max=65536) String driverCode) {
        @Override public String toString(){return "TrialInput[contents omitted]";}
    }
    public record View(String id,String status,JudgeEngine.TrialResult result) {}
    private static class Job {
        final String id=UUID.randomUUID().toString(); final String owner;
        final long created=System.nanoTime(); volatile JudgeEngine.TrialResult result;
        Job(String owner){this.owner=owner;}
        View view(){var value=result;return new View(id,value==null?"RUNNING":"COMPLETED",value);}
    }
    private final ProblemAccessPort problems;
    private final JudgeRuntime runtime;
    private final Map<String,Job> jobs=new LinkedHashMap<>();
    private final Set<String> active=new HashSet<>();
    private final ExecutorService executor=Executors.newFixedThreadPool(2);
    public TrialService(ProblemAccessPort problems,JudgeRuntime runtime){this.problems=problems;this.runtime=runtime;}
    public View start(Actor actor,Input input){
        if(actor.role()!=Role.STUDENT)throw error(HttpStatus.FORBIDDEN,"FORBIDDEN","请使用学生账号进行自主测试");
        var snapshot=problems.requireForSubmission(actor,input.assignmentId(),input.problemId(),input.problemVersionId(),Instant.now());
        var p=snapshot.problem();
        if(!p.language().equals(input.language()))throw invalid("测试语言须与题目一致");
        if(input.sourceCode()==null || input.sourceCode().isBlank() || bytes(input.sourceCode())>p.codeLimitBytes())throw invalid("代码为空或超过题目长度限制");
        if(input.stdin()==null || bytes(input.stdin())>65536 || input.expectedOutput()!=null && bytes(input.expectedOutput())>65536)throw invalid("输入和预期输出最多各 64 KiB");
        String source=input.sourceCode();
        if("FUNCTION".equals(p.problemType())){
            if(input.driverCode()==null || input.driverCode().isBlank())throw invalid("请填写用于调用函数的测试程序（包含 main）");
            source+="\n"+input.driverCode();
        }
        if(bytes(source)>65536)throw invalid("代码与测试程序合计最多 64 KiB");
        var compilation=CompilationSource.program(source,CompileLanguage.valueOf(input.language()));
        long cpu=Math.min(p.timeLimitMs(),3000),memory=Math.min(p.memoryLimitMb()*1_048_576L,268_435_456L);
        var limits=new Limits(cpu,Math.min(10000,Math.max(cpu*3,cpu+500)),memory,Math.min(memory,8_388_608L),1,65536);
        runtime.requireAvailable();
        Job job;
        synchronized(this){
            jobs.values().removeIf(j->j.result!=null && System.nanoTime()-j.created>600_000_000_000L);
            if(active.size()>=2 || active.contains(actor.id()))throw error(HttpStatus.TOO_MANY_REQUESTS,"TRIAL_BUSY","自主测试繁忙，请等待当前测试完成后重试");
            while(jobs.size()>=100){var oldest=jobs.values().stream().filter(j->j.result!=null).findFirst().orElseThrow();jobs.remove(oldest.id);}
            job=new Job(actor.id());active.add(actor.id());jobs.put(job.id,job);
        }
        try {
            executor.execute(()->{
                try {job.result=runtime.engine().trial(compilation,input.stdin(),limits,input.expectedOutput());}
                catch(RuntimeException e){job.result=new JudgeEngine.TrialResult(Verdict.SYSTEM_ERROR,"","","",null,null);}
                finally {synchronized(this){active.remove(actor.id());}}
            });
        } catch(RejectedExecutionException e){synchronized(this){active.remove(actor.id());jobs.remove(job.id);}throw error(HttpStatus.SERVICE_UNAVAILABLE,"TRIAL_UNAVAILABLE","自主测试暂不可用");}
        return job.view();
    }
    public synchronized View get(Actor actor,String id){
        var job=jobs.get(id);
        if(job==null || !job.owner.equals(actor.id()) || System.nanoTime()-job.created>600_000_000_000L)
            throw error(HttpStatus.NOT_FOUND,"NOT_FOUND","测试结果不存在或已过期，请重新运行");
        return job.view();
    }
    private static int bytes(String s){return s.getBytes(StandardCharsets.UTF_8).length;}
    private static BusinessException invalid(String message){return error(HttpStatus.BAD_REQUEST,"INVALID_TRIAL",message);}
    private static BusinessException error(HttpStatus status,String code,String message){return new BusinessException(status,code,message);}
    @PreDestroy public void close(){executor.shutdownNow();}
}
