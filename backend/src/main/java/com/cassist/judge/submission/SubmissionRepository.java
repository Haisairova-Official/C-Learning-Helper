package com.cassist.judge.submission;

import com.cassist.common.BusinessException;
import com.cassist.common.PageResult;
import com.cassist.judge.core.JudgeModels.*;
import com.cassist.judge.core.Verdict;
import com.cassist.judge.submission.SubmissionModels.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.util.*;

@Repository
public class SubmissionRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    public SubmissionRepository(JdbcTemplate jdbc, TransactionTemplate tx) { this.jdbc=jdbc; this.tx=tx; }
    private static Instant instant(ResultSet r,String column) throws SQLException {
        LocalDateTime value=r.getObject(column,LocalDateTime.class);
        return value==null?null:value.toInstant(ZoneOffset.UTC);
    }
    private static final RowMapper<Row> ROW=(r,n)->new Row(r.getString("id"),r.getString("student_id"),
            r.getString("assignment_id"),r.getString("problem_id"),r.getString("problem_version_id"),
            r.getString("source_code"),r.getString("language"),r.getString("status"),r.getString("verdict"),
            r.getBigDecimal("score"),r.getBigDecimal("max_score"),r.getString("compile_message"),
            r.getString("request_hash"),instant(r,"submitted_at"),instant(r,"finished_at"));
    public Optional<Row> find(String id) { return jdbc.query("SELECT * FROM submission WHERE id=?",ROW,id).stream().findFirst(); }
    public Optional<Row> findByKey(String student,String key) {
        return jdbc.query("SELECT * FROM submission WHERE student_id=? AND idempotency_key=?",ROW,student,key).stream().findFirst();
    }
    public Row create(String student,CreateRequest request,String key,String hash,String sourceHash,BigDecimal maxScore,Instant receivedAt) {
        return tx.execute(status->{
            // Serializes submissions for this user, including idempotency and active-task admission.
            jdbc.queryForObject("SELECT id FROM app_user WHERE id=? FOR UPDATE",String.class,student);
            Optional<Row> previous=findByKey(student,key);
            if(previous.isPresent()) {
                if(!previous.get().requestHash().equals(hash)) throw new BusinessException(HttpStatus.CONFLICT,"IDEMPOTENCY_CONFLICT","相同请求编号对应不同提交内容");
                return previous.get();
            }
            Integer active=jdbc.queryForObject("SELECT COUNT(*) FROM submission WHERE student_id=? AND status IN ('QUEUED','COMPILING','RUNNING')",Integer.class,student);
            if(active!=null && active>=5) throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,"TOO_MANY_SUBMISSIONS","正在评测的提交较多，请稍后再试");
            String id=UUID.randomUUID().toString();
            jdbc.update("INSERT INTO submission(id,student_id,assignment_id,problem_id,problem_version_id,source_code,source_hash,language,status,max_score,idempotency_key,request_hash,submitted_at) VALUES (?,?,?,?,?,?,?,?,'QUEUED',?,?,?,?)",
                    id,student,request.assignmentId(),request.problemId(),request.problemVersionId(),request.sourceCode(),sourceHash,request.language(),maxScore,key,hash,LocalDateTime.ofInstant(receivedAt,ZoneOffset.UTC));
            jdbc.update("INSERT INTO judge_task(id,submission_id,state,attempt,max_attempts,available_at,created_at,updated_at) VALUES (?,?,'QUEUED',0,3,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",UUID.randomUUID().toString(),id);
            return find(id).orElseThrow();
        });
    }
    public PageResult<Row> list(String student,String assignment,String problem,int page,int pageSize) {
        StringBuilder where=new StringBuilder(" WHERE 1=1"); var args=new ArrayList<Object>();
        if(student!=null){where.append(" AND student_id=?");args.add(student);}
        if(assignment!=null){where.append(" AND assignment_id=?");args.add(assignment);}
        if(problem!=null){where.append(" AND problem_id=?");args.add(problem);}
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM submission"+where,Long.class,args.toArray());
        args.add(pageSize);args.add((long)(page-1)*pageSize);
        List<Row> rows=jdbc.query("SELECT * FROM submission"+where+" ORDER BY submitted_at DESC,id DESC LIMIT ? OFFSET ?",ROW,args.toArray());
        return new PageResult<>(rows,page,pageSize,total==null?0:total);
    }
    public List<CaseResult> cases(String id) {
        return jdbc.query("SELECT * FROM case_result WHERE submission_id=? ORDER BY case_index",(r,n)->
            new CaseResult(r.getInt("case_index"),Verdict.valueOf(r.getString("verdict")),r.getBigDecimal("score"),
                ceil(r.getLong("cpu_time_ns"),1_000_000),ceil(r.getLong("memory_bytes"),1024),
                r.getLong("cpu_time_ns"),r.getLong("wall_time_ns"),r.getLong("memory_bytes")),id);
    }
    private static long ceil(long n,long divisor){return n/divisor+(n%divisor==0?0:1);}
    public Optional<Task> claim(String owner) {
        return tx.execute(status->{
            List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,submission_id,attempt FROM judge_task WHERE state='QUEUED' AND available_at<=UTC_TIMESTAMP(6) ORDER BY available_at,created_at LIMIT 1 FOR UPDATE SKIP LOCKED");
            if(rows.isEmpty())return Optional.empty();
            var row=rows.getFirst();String id=(String)row.get("id"),submission=(String)row.get("submission_id"),token=UUID.randomUUID().toString();
            int attempt=((Number)row.get("attempt")).intValue()+1;
            jdbc.update("UPDATE judge_task SET state='RUNNING',attempt=?,lease_owner=?,lease_token=?,lease_until=TIMESTAMPADD(SECOND,120,UTC_TIMESTAMP(6)),updated_at=UTC_TIMESTAMP(6) WHERE id=?",attempt,owner,token,id);
            jdbc.update("UPDATE submission SET status='COMPILING',verdict=NULL,score=NULL,finished_at=NULL WHERE id=?",submission);
            return Optional.of(new Task(id,submission,token,attempt));
        });
    }
    public boolean renew(Task task) {
        return jdbc.update("UPDATE judge_task SET lease_until=TIMESTAMPADD(SECOND,120,UTC_TIMESTAMP(6)),updated_at=UTC_TIMESTAMP(6) WHERE id=? AND lease_token=? AND state='RUNNING' AND lease_until>UTC_TIMESTAMP(6)",task.id(),task.token())==1;
    }
    public void markRunning(Task task) {
        jdbc.update("UPDATE submission s JOIN judge_task t ON s.id=t.submission_id SET s.status='RUNNING' WHERE t.id=? AND t.lease_token=? AND t.state='RUNNING' AND t.lease_until>UTC_TIMESTAMP(6)",task.id(),task.token());
    }
    public void finish(Task task,Outcome outcome,ProblemSnapshot snapshot) {
        tx.executeWithoutResult(status->{
            var owned=jdbc.queryForList("SELECT max_attempts FROM judge_task WHERE id=? AND lease_token=? AND state='RUNNING' AND lease_until>UTC_TIMESTAMP(6) FOR UPDATE",task.id(),task.token());
            if(owned.isEmpty())return;
            int max=((Number)owned.getFirst().get("max_attempts")).intValue();
            if(outcome.systemError() && task.attempt()<max) {
                jdbc.update("UPDATE judge_task SET state='QUEUED',available_at=TIMESTAMPADD(SECOND,?,UTC_TIMESTAMP(6)),lease_owner=NULL,lease_token=NULL,lease_until=NULL,last_error_code=?,updated_at=UTC_TIMESTAMP(6) WHERE id=?",Math.min(30,task.attempt()*3),outcome.errorCode(),task.id());
                jdbc.update("UPDATE submission SET status='QUEUED',verdict=NULL,score=NULL WHERE id=?",task.submissionId());return;
            }
            jdbc.update("DELETE FROM case_result WHERE submission_id=?",task.submissionId());
            if(snapshot!=null) for(CaseResult result:outcome.caseResults()) {
                String testId=snapshot.cases().stream().filter(c->c.index()==result.caseIndex()).findFirst().orElseThrow().id();
                jdbc.update("INSERT INTO case_result(id,submission_id,case_index,test_case_id,verdict,score,cpu_time_ns,wall_time_ns,memory_bytes) VALUES (?,?,?,?,?,?,?,?,?)",
                    UUID.randomUUID().toString(),task.submissionId(),result.caseIndex(),testId,result.status().name(),result.score(),result.cpuTimeNs(),result.wallTimeNs(),result.memoryBytes());
            }
            jdbc.update("UPDATE submission SET status=?,verdict=?,score=?,compile_message=?,finished_at=UTC_TIMESTAMP(6) WHERE id=?",
                outcome.systemError()?"SYSTEM_ERROR":"FINISHED",outcome.verdict().name(),outcome.score(),outcome.compileMessage(),task.submissionId());
            jdbc.update("UPDATE judge_task SET state=?,lease_owner=NULL,lease_token=NULL,lease_until=NULL,last_error_code=?,updated_at=UTC_TIMESTAMP(6) WHERE id=?",
                outcome.systemError()?"FAILED":"DONE",outcome.errorCode(),task.id());
        });
    }
    public void recoverExpired() {
        tx.executeWithoutResult(status->{
            var rows=jdbc.queryForList("SELECT id,submission_id,attempt,max_attempts FROM judge_task WHERE state='RUNNING' AND lease_until<=UTC_TIMESTAMP(6) LIMIT 20 FOR UPDATE SKIP LOCKED");
            for(var row:rows){
                boolean exhausted=((Number)row.get("attempt")).intValue()>=((Number)row.get("max_attempts")).intValue();
                jdbc.update("UPDATE judge_task SET state=?,available_at=UTC_TIMESTAMP(6),lease_owner=NULL,lease_token=NULL,lease_until=NULL,last_error_code='WORKER_LEASE_EXPIRED',updated_at=UTC_TIMESTAMP(6) WHERE id=?",exhausted?"FAILED":"QUEUED",row.get("id"));
                jdbc.update("UPDATE submission SET status=?,verdict=?,score=NULL,finished_at=? WHERE id=?",exhausted?"SYSTEM_ERROR":"QUEUED",exhausted?"SYSTEM_ERROR":null,
                    exhausted?LocalDateTime.now(ZoneOffset.UTC):null,row.get("submission_id"));
            }
        });
    }
}
