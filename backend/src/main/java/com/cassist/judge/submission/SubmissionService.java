package com.cassist.judge.submission;

import com.cassist.common.*;
import com.cassist.identity.*;
import com.cassist.problem.ProblemAccessPort;
import com.cassist.judge.core.JudgeProblemMapper;
import com.cassist.judge.core.FunctionSourceAssembler;
import com.cassist.judge.submission.SubmissionModels.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class SubmissionService {
    private final SubmissionRepository repository;
    private final ProblemAccessPort problems;
    private final AccessPolicy access;
    private final JudgeRuntime runtime;
    public SubmissionService(SubmissionRepository repository,ProblemAccessPort problems,AccessPolicy access,JudgeRuntime runtime){
        this.repository=repository;this.problems=problems;this.access=access;this.runtime=runtime;
    }
    public Accepted submit(Actor actor,CreateRequest request,String key) {
        if(actor.role()!=Role.STUDENT)throw new BusinessException(HttpStatus.FORBIDDEN,"FORBIDDEN","请使用学生身份提交作业");
        if(key==null || !key.matches("[A-Za-z0-9-]{16,128}"))throw bad("请求编号格式不正确");
        if(!"C17".equals(request.language()) && !"CPP17".equals(request.language()))throw bad("语言须为 C17 或 CPP17");
        String hash=hash(request.assignmentId(),request.problemId(),request.problemVersionId(),request.language(),request.sourceCode());
        var old=repository.findByKey(actor.id(),key);
        if(old.isPresent()) {
            access.requireSubmissionRead(actor,old.get().studentId(),old.get().assignmentId());
            if(!old.get().requestHash().equals(hash))throw new BusinessException(HttpStatus.CONFLICT,"IDEMPOTENCY_CONFLICT","相同请求编号对应不同提交内容");
            return accepted(old.get());
        }
        Instant receivedAt=Instant.now();
        var snapshot=problems.requireForSubmission(actor,request.assignmentId(),request.problemId(),request.problemVersionId(),receivedAt);
        if(!snapshot.problem().language().equals(request.language()))throw bad("提交语言须与当前作业题目版本一致");
        try{FunctionSourceAssembler.fromAuthorized(snapshot,request.sourceCode(),request.language());}
        catch(IllegalArgumentException e){throw bad("代码长度或题目评测配置不符合要求");}
        runtime.requireAvailable();
        return accepted(repository.create(actor.id(),request,key,hash,hash(request.sourceCode()),snapshot.problem().maxScore(),receivedAt));
    }
    public View get(Actor actor,String id) {
        Row row=repository.find(id).orElseThrow(()->new BusinessException(HttpStatus.NOT_FOUND,"NOT_FOUND","提交记录不存在"));
        access.requireSubmissionRead(actor,row.studentId(),row.assignmentId());
        return view(row,true);
    }
    public PageResult<View> list(Actor actor,String studentId,String assignmentId,String problemId,int page,int size){
        if(page<1 || size<1 || size>100)throw bad("分页参数不正确");
        String student=studentId;
        if(actor.canManageProblems() && assignmentId!=null && studentId==null) {
            access.requireAssignmentManage(actor,assignmentId);
        } else {
            student=student==null?actor.id():student;
            if(!student.equals(actor.id()) && assignmentId==null)throw bad("查询其他学生的提交需要指定作业");
            if(assignmentId!=null)access.requireSubmissionRead(actor,student,assignmentId);
            else if(!student.equals(actor.id()))throw new BusinessException(HttpStatus.FORBIDDEN,"FORBIDDEN","无权查看该提交列表");
        }
        var result=repository.list(student,assignmentId,problemId,page,size);
        return new PageResult<>(result.items().stream().map(row->view(row,false)).toList(),page,size,result.total());
    }
    private View view(Row r,boolean detail){return new View(r.id(),r.studentId(),r.assignmentId(),r.problemId(),r.versionId(),r.language(),
        detail?r.sourceCode():null,r.status(),r.verdict(),r.score(),r.maxScore(),r.compileMessage()==null?"":r.compileMessage(),
        detail?repository.cases(r.id()).stream().map(CaseView::of).toList():java.util.List.of(),r.submittedAt(),r.finishedAt());}
    private Accepted accepted(Row r){return new Accepted(r.id(),r.status(),r.submittedAt());}
    private static BusinessException bad(String message){return new BusinessException(HttpStatus.BAD_REQUEST,"INVALID_SUBMISSION",message);}
    static String hash(String... values){
        try{MessageDigest d=MessageDigest.getInstance("SHA-256");for(String v:values){byte[] b=v.getBytes(StandardCharsets.UTF_8);d.update(ByteBuffer.allocate(4).putInt(b.length).array());d.update(b);}return HexFormat.of().formatHex(d.digest());}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
