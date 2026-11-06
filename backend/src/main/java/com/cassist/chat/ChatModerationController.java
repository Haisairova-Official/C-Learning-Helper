package com.cassist.chat;

import com.cassist.common.BusinessException;
import com.cassist.identity.*;
import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/assignments/{assignmentId}/chat/mutes")
public class ChatModerationController {
    private final JdbcTemplate jdbc;private final CurrentActor actors;private final AccessPolicy access;
    public ChatModerationController(JdbcTemplate jdbc,CurrentActor actors,AccessPolicy access){this.jdbc=jdbc;this.actors=actors;this.access=access;}
    public record MutedStudent(String studentId,String name,String studentNumber,Instant mutedAt) {}
    public record Mute(String studentId) {}
    private Actor manager(String assignmentId){Actor actor=actors.requireProblemManager();access.requireAssignmentManage(actor,assignmentId);return actor;}
    @GetMapping public ResponseEntity<List<MutedStudent>> list(@PathVariable String assignmentId) {
        manager(assignmentId);
        var rows=jdbc.query("SELECT u.id,u.display_name,u.student_number,m.muted_at FROM assignment_chat_mute m JOIN app_user u ON u.id=m.student_id WHERE m.assignment_id=? ORDER BY m.muted_at DESC",(rs,n)->new MutedStudent(rs.getString(1),rs.getString(2),rs.getString(3),rs.getObject(4,LocalDateTime.class).toInstant(ZoneOffset.UTC)),assignmentId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).header("Vary","Cookie").body(rows);
    }
    @PostMapping @Transactional public ResponseEntity<Void> mute(@PathVariable String assignmentId,@RequestBody Mute input) {
        Actor actor=manager(assignmentId);
        int count=jdbc.queryForObject("SELECT COUNT(*) FROM app_user u WHERE u.id=? AND u.role='STUDENT' AND EXISTS(SELECT 1 FROM assignment_class ac JOIN class_member cm ON cm.class_id=ac.class_id WHERE ac.assignment_id=? AND cm.student_id=u.id)",Integer.class,input.studentId(),assignmentId);
        if(count==0) throw new BusinessException(HttpStatus.BAD_REQUEST,"INVALID_STUDENT","该学生不属于当前作业班级");
        jdbc.queryForObject("SELECT id FROM app_user WHERE id=? FOR UPDATE",String.class,input.studentId());
        jdbc.update("INSERT INTO assignment_chat_mute(assignment_id,student_id,muted_by,muted_at) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE muted_by=VALUES(muted_by),muted_at=VALUES(muted_at)",assignmentId,input.studentId(),actor.id(),LocalDateTime.now(ZoneOffset.UTC));
        return ResponseEntity.noContent().build();
    }
    @DeleteMapping("/{studentId}") @Transactional public ResponseEntity<Void> unmute(@PathVariable String assignmentId,@PathVariable String studentId) {
        manager(assignmentId);
        jdbc.update("DELETE FROM assignment_chat_mute WHERE assignment_id=? AND student_id=?",assignmentId,studentId);
        return ResponseEntity.noContent().build();
    }
}
