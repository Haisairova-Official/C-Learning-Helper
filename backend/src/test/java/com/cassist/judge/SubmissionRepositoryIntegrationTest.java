package com.cassist.judge;

import com.cassist.judge.core.JudgeModels.Outcome;
import com.cassist.judge.core.Verdict;
import com.cassist.judge.submission.SubmissionModels.Task;
import com.cassist.judge.submission.SubmissionRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Uses only fresh UUID-owned fixtures; requires the separately running local demo worker. */
@EnabledIfEnvironmentVariable(named="CASSIST_INTEGRATION_DB",matches="true")
class SubmissionRepositoryIntegrationTest {
    private JdbcTemplate jdbc;
    private SubmissionRepository repository;
    private String submissionId,taskId,token;
    private BigDecimal maxScore;
    @BeforeEach void setup(){
        var ds=new DriverManagerDataSource(System.getenv("DB_URL"),System.getenv("DB_USERNAME"),System.getenv("DB_PASSWORD"));
        jdbc=new JdbcTemplate(ds);jdbc.execute("SET time_zone='+00:00'");
        repository=new SubmissionRepository(jdbc,new TransactionTemplate(new DataSourceTransactionManager(ds)));
        submissionId=UUID.randomUUID().toString();taskId=UUID.randomUUID().toString();token=UUID.randomUUID().toString();
        var base=jdbc.queryForList("SELECT id,max_score FROM submission WHERE status='FINISHED' AND verdict='ACCEPTED' ORDER BY submitted_at DESC LIMIT 1").getFirst();
        maxScore=(BigDecimal)base.get("max_score");
        jdbc.update("INSERT INTO submission(id,student_id,assignment_id,problem_id,problem_version_id,source_code,source_hash,language,status,max_score,idempotency_key,request_hash,submitted_at) SELECT ?,student_id,assignment_id,problem_id,problem_version_id,source_code,source_hash,language,'RUNNING',max_score,?,request_hash,UTC_TIMESTAMP(6) FROM submission WHERE id=?",submissionId,UUID.randomUUID().toString(),base.get("id"));
    }
    private void createTask(int attempts,int leaseSeconds){
        jdbc.update("INSERT INTO judge_task(id,submission_id,state,attempt,max_attempts,available_at,lease_owner,lease_token,lease_until,created_at,updated_at) VALUES (?,?,'RUNNING',?,3,UTC_TIMESTAMP(6),'integration-fixture',?,TIMESTAMPADD(SECOND,?,UTC_TIMESTAMP(6)),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",taskId,submissionId,attempts,token,leaseSeconds);
    }
    @AfterEach void cleanup(){
        jdbc.update("DELETE FROM case_result WHERE submission_id=?",submissionId);
        jdbc.update("DELETE FROM judge_task WHERE submission_id=?",submissionId);
        jdbc.update("DELETE FROM submission WHERE id=?",submissionId);
    }
    @Test void oldWorkerCannotOverwriteAnotherLease(){
        createTask(1,120);
        repository.finish(new Task(taskId,submissionId,"stale-token",1),new Outcome(Verdict.ACCEPTED,maxScore,maxScore,"",List.of(),null),null);
        assertEquals("RUNNING",repository.find(submissionId).orElseThrow().status());
        assertNull(repository.find(submissionId).orElseThrow().score());
        repository.finish(new Task(taskId,submissionId,token,1),new Outcome(Verdict.ACCEPTED,maxScore,maxScore,"",List.of(),null),null);
        assertEquals("FINISHED",repository.find(submissionId).orElseThrow().status());
    }
    @Test void expiredLeaseIsRecoveredAndActuallyEvaluated() throws Exception {
        createTask(1,-5);
        for(int i=0;i<150;i++){
            var row=repository.find(submissionId).orElseThrow();
            if(row.status().equals("FINISHED")){
                assertEquals("ACCEPTED",row.verdict());assertEquals(0,maxScore.compareTo(row.score()));
                assertEquals(2,jdbc.queryForObject("SELECT attempt FROM judge_task WHERE id=?",Integer.class,taskId));return;
            }
            assertNotEquals("SYSTEM_ERROR",row.status());Thread.sleep(100);
        }
        fail("The running demo worker did not recover and evaluate the expired fixture");
    }
    @Test void exhaustedExpiredLeaseBecomesSystemErrorWithoutScore() throws Exception {
        createTask(3,-5);
        for(int i=0;i<80;i++){
            var row=repository.find(submissionId).orElseThrow();
            if(row.status().equals("SYSTEM_ERROR")){
                assertNull(row.score());assertEquals("SYSTEM_ERROR",row.verdict());
                assertEquals("FAILED",jdbc.queryForObject("SELECT state FROM judge_task WHERE id=?",String.class,taskId));return;
            }
            Thread.sleep(100);
        }
        fail("The running demo worker did not finalize an exhausted lease");
    }
}
