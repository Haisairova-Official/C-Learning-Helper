package com.cassist.identity;

import com.cassist.problem.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("demo")
@ConditionalOnProperty(name="app.demo.enabled",havingValue="true")
public class DemoDataInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;private final PasswordEncoder encoder;private final TeachingService teaching;
    private final Environment environment;

    public DemoDataInitializer(JdbcTemplate jdbc, PasswordEncoder encoder, TeachingService teaching, Environment environment) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.teaching = teaching;
        this.environment = environment;
    }

    @Override @Transactional
    public void run(ApplicationArguments arguments){
        seedUser("demo-teacher","teacher","演示教师",Role.TEACHER);
        seedUser("demo-student","student","演示学生",Role.STUDENT);
        seedUser("demo-student2","student2","另一位学生",Role.STUDENT);
        seedUser("demo-admin","admin","演示管理员",Role.ADMIN);
        seedStudentNumber("demo-student", "20260001");
        seedStudentNumber("demo-student2", "20260002");
        var teacher=new Actor("demo-teacher",Role.TEACHER);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM classroom WHERE id='demo-class'",Integer.class)==0){
            jdbc.update("INSERT INTO classroom(id,name,teacher_id,created_at) VALUES('demo-class','C 语言演示班','demo-teacher',?)",LocalDateTime.now(ZoneOffset.UTC));
        }
        teaching.addMember(teacher,"demo-class","demo-student");
        teaching.addMember(teacher,"demo-class","demo-student2");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM assignment WHERE owner_id='demo-teacher'",Integer.class)>0)return;
        String reference="#include <stdio.h>\nint main(void) { long long a, b; if (scanf(\"%lld %lld\", &a, &b) != 2) return 1; printf(\"%lld\\n\", a + b); return 0; }\n";
        var problem=teaching.saveProblem(teacher,null,new TeacherProblemView(null,null,"两数之和","读取两个整数，输出它们的和。输入范围为 -1000000 到 1000000。","一行两个整数，以空格分隔。","输出两个整数的和。",List.of(new Sample("2 3\n","5\n",null)),1000L,128L,65536L,new BigDecimal("100"),reference,List.of(
                new PrivateTestCase(null,"2 3\n","5\n",PrivateTestCase.Visibility.PUBLIC,new BigDecimal("40"),PrivateTestCase.ComparisonMode.TOKENS),
                new PrivateTestCase(null,"-8 8\n","0\n",PrivateTestCase.Visibility.HIDDEN,new BigDecimal("30"),PrivateTestCase.ComparisonMode.TOKENS),
                new PrivateTestCase(null,"1000000 999999\n","1999999\n",PrivateTestCase.Visibility.HIDDEN,new BigDecimal("30"),PrivateTestCase.ComparisonMode.TOKENS)),"PUBLISHED"));
        teaching.publish(teacher,new TeachingService.AssignmentRequest("第一次练习：输入与输出","demo-class",List.of(problem.id()),Instant.now().minusSeconds(86400),Instant.now().plusSeconds(86400L*30)));
    }

    private void seedUser(String id,String username,String displayName,Role role){
        if(jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE id=?",Integer.class,id)==0)
            jdbc.update("INSERT INTO app_user(id,username,display_name,password_hash,role,enabled,created_at) VALUES(?,?,?,?,?,TRUE,?)",id,username,displayName,encoder.encode(environment.getProperty("app.demo.password." + username, "CassistDemo2026!")),role.name(),LocalDateTime.now(ZoneOffset.UTC));
    }

    /** Explicit demo fixtures, not real school numbers; existing assigned values always win. */
    private void seedStudentNumber(String id, String number) {
        jdbc.update("UPDATE app_user SET student_number=? WHERE id=? AND role='STUDENT' AND (student_number IS NULL OR TRIM(student_number)='')", number, id);
    }
}
