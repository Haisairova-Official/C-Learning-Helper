package com.cassist.roster;

import com.cassist.common.BusinessException;
import com.cassist.identity.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import static com.cassist.identity.UserManagementService.*;

@Service
public class StudentImportService {
    private final JdbcTemplate jdbc; private final AccessPolicy access; private final PasswordEncoder passwords; private final ObjectMapper json;
    public StudentImportService(JdbcTemplate jdbc,AccessPolicy access,PasswordEncoder passwords,ObjectMapper json) { this.jdbc=jdbc;this.access=access;this.passwords=passwords;this.json=json; }
    public record Candidate(int rowNumber,String studentNumber,String name,String username,String className,String action,List<String> issues) {}
    public record Preview(List<Candidate> rows,List<String> warnings) {}
    public record Commit(List<RosterParser.Row> rows,String initialPassword) { @Override public String toString(){return "Commit[credentials omitted]";} }
    public record Result(int created,int added,int unchanged,List<String> studentIds) {}
    private record Match(String id,String username,String name,String number,String role,boolean enabled) {}
    public Preview preview(Actor actor,String classId,RosterParser.Parsed parsed) {
        access.requireClassManage(actor,classId);
        return new Preview(inspect(classId,parsed.rows()),parsed.warnings());
    }
    public void authorize(Actor actor,String classId) { access.requireClassManage(actor,classId); }
    private List<Candidate> inspect(String classId,List<RosterParser.Row> rows) {
        if(rows==null || rows.isEmpty() || rows.size()>200) invalid("请选择 1 至 200 名学生");
        String className=jdbc.queryForObject("SELECT name FROM classroom WHERE id=?",String.class,classId);
        Set<String> numbers=new HashSet<>(),usernames=new HashSet<>(); List<Candidate> candidates=new ArrayList<>();
        for(RosterParser.Row row:rows) {
            if(row==null) invalid("学生记录不能为空");
            String number=text(row.studentNumber()),name=text(row.name()),username=text(row.username()),hint=text(row.className());
            List<String> issues=new ArrayList<>(); String action="NEW";
            try { validateNumber(number);if(number.isEmpty()) invalid("请填写学号");validateName(name);if(!username.isEmpty()) validateUsername(username); }
            catch(BusinessException e) { issues.add(e.getMessage()); }
            if(!numbers.add(number.toLowerCase(Locale.ROOT))) issues.add("名单内学号重复");
            if(!hint.isEmpty() && !hint.equals(className)) issues.add("名单班级与所选班级不一致");
            List<Match> matches=number.isEmpty()?List.of():jdbc.query("SELECT id,username,display_name,student_number,role,enabled FROM app_user WHERE student_number=?",
                    (rs,n)->new Match(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getBoolean(6)),number);
            if(!matches.isEmpty()) {
                Match match=matches.getFirst();
                if(!match.role().equals("STUDENT") || !match.enabled()) issues.add("该学号无法作为可用学生账号导入");
                if(!match.name().equals(name)) issues.add("学号对应的姓名不一致，请核对");
                if(!username.isEmpty() && !username.equalsIgnoreCase(match.username())) issues.add("学号对应的登录账号不一致");
                username=match.username();
                action=jdbc.queryForObject("SELECT COUNT(*) FROM class_member WHERE class_id=? AND student_id=?",Integer.class,classId,match.id())>0?"ALREADY_MEMBER":"EXISTING";
            } else {
                if(username.isEmpty()) username=number;
                try { validateUsername(username); } catch(BusinessException e) {issues.add(e.getMessage());}
                if(jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE username=?",Integer.class,username)>0) issues.add("登录账号已被使用，请更换账号或核对学号");
            }
            if(!usernames.add(username.toLowerCase(Locale.ROOT))) issues.add("名单内登录账号重复");
            candidates.add(new Candidate(row.rowNumber(),number,name,username,hint,issues.isEmpty()?action:"INVALID",issues));
        }
        return candidates;
    }
    @Transactional public Result commit(Actor actor,String classId,String key,Commit input) {
        access.requireClassManage(actor,classId);
        if(key==null || !key.matches("[A-Za-z0-9-]{16,128}")) invalid("无效的操作标识");
        if(input==null) invalid("请提供学生名单");
        validatePassword(input.initialPassword());
        if(input.rows()==null || input.rows().isEmpty() || input.rows().size()>200) invalid("请选择 1 至 200 名学生");
        jdbc.queryForObject("SELECT id FROM app_user WHERE id=? FOR UPDATE",String.class,actor.id());
        jdbc.queryForObject("SELECT id FROM classroom WHERE id=? FOR UPDATE",String.class,classId);
        String hash=digest(json.writeValueAsString(List.of(classId,input.rows())));
        var receipts=jdbc.queryForList("SELECT request_hash,initial_password_hash,result_json FROM student_import_receipt WHERE owner_id=? AND operation_key=? FOR UPDATE",actor.id(),key);
        if(!receipts.isEmpty()) {
            var receipt=receipts.getFirst();
            if(!hash.equals(receipt.get("request_hash")) || !passwords.matches(input.initialPassword(),(String)receipt.get("initial_password_hash"))) throw new BusinessException(HttpStatus.CONFLICT,"IDEMPOTENCY_CONFLICT","该操作已使用不同内容完成，请重新核对");
            return json.readValue((String)receipt.get("result_json"),Result.class);
        }
        List<Candidate> candidates=inspect(classId,input.rows());
        for(Candidate c:candidates) if(!c.issues().isEmpty()) invalid("第 "+c.rowNumber()+" 行："+String.join("；",c.issues()));
        String passwordHash=passwords.encode(input.initialPassword());
        int created=0,added=0,unchanged=0;List<String> ids=new ArrayList<>();
        for(Candidate c:candidates) {
            String id;
            if(c.action().equals("NEW")) {
                id=UUID.randomUUID().toString();
                jdbc.update("INSERT INTO app_user(id,username,display_name,student_number,password_hash,role,created_at) VALUES(?,?,?,?,?,'STUDENT',?)",id,c.username(),c.name(),c.studentNumber(),passwordHash,LocalDateTime.now(ZoneOffset.UTC)); created++;
            } else id=jdbc.queryForObject("SELECT id FROM app_user WHERE student_number=?",String.class,c.studentNumber());
            if(c.action().equals("ALREADY_MEMBER")) unchanged++;
            else { jdbc.update("INSERT INTO class_member(class_id,student_id,joined_at) VALUES(?,?,?)",classId,id,LocalDateTime.now(ZoneOffset.UTC)); added++; }
            ids.add(id);
        }
        Result result=new Result(created,added,unchanged,ids);
        jdbc.update("INSERT INTO student_import_receipt(owner_id,operation_key,class_id,request_hash,initial_password_hash,result_json,created_at) VALUES(?,?,?,?,?,?,?)",actor.id(),key,classId,hash,passwordHash,json.writeValueAsString(result),LocalDateTime.now(ZoneOffset.UTC));
        return result;
    }
    private String digest(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch(Exception e) { throw new IllegalStateException("Hash unavailable"); } }
}
