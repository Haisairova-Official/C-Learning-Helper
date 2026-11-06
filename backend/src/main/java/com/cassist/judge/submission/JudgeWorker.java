package com.cassist.judge.submission;

import com.cassist.judge.core.JudgeModels;
import com.cassist.judge.core.JudgeModels.Outcome;
import com.cassist.judge.core.JudgeProblemMapper;
import com.cassist.judge.core.FunctionSourceAssembler;
import com.cassist.judge.core.Verdict;
import com.cassist.judge.submission.SubmissionModels.*;
import com.cassist.problem.ProblemAccessPort;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.*;

@Component
public final class JudgeWorker {
    private static final Logger log=LoggerFactory.getLogger(JudgeWorker.class);
    private final SubmissionRepository repository; private final ProblemAccessPort problems; private final JudgeRuntime runtime;
    private final String owner=UUID.randomUUID().toString();
    private final Map<String,Task> active=new ConcurrentHashMap<>();
    private final Semaphore slots=new Semaphore(2);
    private final ExecutorService workers=Executors.newFixedThreadPool(2,Thread.ofPlatform().daemon().name("judge-worker-",0).factory());
    public JudgeWorker(SubmissionRepository repository,ProblemAccessPort problems,JudgeRuntime runtime){this.repository=repository;this.problems=problems;this.runtime=runtime;}
    @Scheduled(initialDelay=1000,fixedDelay=1000) public void tick(){
        if(!runtime.enabled())return;
        try{
            for(Task task:active.values())repository.renew(task);
            repository.recoverExpired();
            while(slots.tryAcquire()){
                Optional<Task> claim;
                try{claim=repository.claim(owner);}catch(RuntimeException e){slots.release();throw e;}
                if(claim.isEmpty()){slots.release();break;}
                Task task=claim.get();active.put(task.id(),task);
                workers.submit(()->execute(task));
            }
        }catch(RuntimeException e){log.warn("Judge scheduler temporarily unavailable: {}",e.getClass().getSimpleName());}
    }
    private void execute(Task task){
        Row row=null;JudgeModels.ProblemSnapshot snapshot=null;
        try{
            row=repository.find(task.submissionId()).orElseThrow();
            var authorized=problems.loadForEvaluation(row.versionId());
            snapshot=JudgeProblemMapper.fromAuthorized(authorized,row.sourceCode());
            var compilation=FunctionSourceAssembler.fromAuthorized(authorized,row.sourceCode(),row.language());
            Outcome outcome=runtime.engine().judge(compilation,snapshot,()->repository.markRunning(task));
            repository.finish(task,outcome,snapshot);
        }catch(RuntimeException e){
            log.warn("Judge worker failed for task {}: {}",task.id(),e.getClass().getSimpleName());
            if(row!=null)try{repository.finish(task,new Outcome(Verdict.SYSTEM_ERROR,null,row.maxScore(),"",List.of(),"JUDGE_WORKER_FAILURE"),snapshot);}
            catch(RuntimeException storageFailure){log.warn("Judge result persistence deferred to lease recovery: {}",task.id());}
        }finally{active.remove(task.id());slots.release();}
    }
    @PreDestroy public void close(){workers.shutdownNow();}
}

@Configuration
@EnableScheduling
class JudgeSchedulingConfiguration {}
