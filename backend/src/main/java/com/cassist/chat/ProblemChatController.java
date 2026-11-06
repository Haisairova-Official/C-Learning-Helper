package com.cassist.chat;

import com.cassist.common.BusinessException;
import com.cassist.identity.*;
import com.cassist.problem.*;
import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/assignments/{assignmentId}/problems/{problemId}/chat")
public class ProblemChatController {
    private final JdbcTemplate jdbc;private final CurrentActor actors;private final TeachingService teaching;private final AssignmentSelectionService selection;
    public ProblemChatController(JdbcTemplate jdbc,CurrentActor actors,TeachingService teaching,AssignmentSelectionService selection){this.jdbc=jdbc;this.actors=actors;this.teaching=teaching;this.selection=selection;}
    public record Message(String id,String authorId,String authorName,Role role,String body,Instant createdAt,boolean retracted,String authorAvatarUrl) {}
    public record Messages(List<Message> items,String nextCursor,boolean muted) {}
    public record Post(String body) {}
    private String authorize(Actor actor,String assignmentId,String problemId) {
        String version=teaching.studentProblem(actor,assignmentId,problemId).versionId();
        if(actor.role()==Role.STUDENT && !selection.options(assignmentId).chatEnabled()) throw new BusinessException(HttpStatus.FORBIDDEN,"CHAT_DISABLED","该作业未开放聊天室");
        return version;
    }
    private static final String SELECT="SELECT m.id,m.author_id,COALESCE(NULLIF(u.nickname,''),u.display_name) AS author_name,u.role,CASE WHEN m.retracted_at IS NULL THEN m.body ELSE '' END AS body,m.created_at,m.retracted_at IS NOT NULL AS retracted,u.avatar_png IS NOT NULL AS has_avatar,u.profile_updated_at FROM problem_chat_message m JOIN app_user u ON u.id=m.author_id ";
    private Message map(java.sql.ResultSet rs,String assignmentId,String problemId) throws java.sql.SQLException {
        String authorId=rs.getString(2),avatarUrl=null;
        if(rs.getBoolean(8)) {
            var updated=rs.getObject(9,LocalDateTime.class);
            avatarUrl="/api/assignments/"+assignmentId+"/problems/"+problemId+"/chat/participants/"+authorId+"/avatar?v="+updated.toEpochSecond(ZoneOffset.UTC)+"-"+updated.getNano();
        }
        return new Message(rs.getString(1),authorId,rs.getString(3),Role.valueOf(rs.getString(4)),rs.getString(5),rs.getObject(6,LocalDateTime.class).toInstant(ZoneOffset.UTC),rs.getBoolean(7),avatarUrl);
    }
    @GetMapping public ResponseEntity<Messages> read(@PathVariable String assignmentId,@PathVariable String problemId,@RequestParam(defaultValue="0") long after) {
        Actor actor=actors.require();String version=authorize(actor,assignmentId,problemId);
        if(after<0) throw new BusinessException(HttpStatus.BAD_REQUEST,"INVALID_CURSOR","消息位置不正确");
        var messages=jdbc.query(SELECT+"WHERE m.assignment_id=? AND m.problem_version_id=? AND m.id>? ORDER BY m.id "+(after==0?"DESC":"ASC")+" LIMIT 50",(rs,n)->map(rs,assignmentId,problemId),assignmentId,version,after);
        if(after==0) Collections.reverse(messages);
        String cursor=messages.isEmpty()?Long.toString(after):messages.getLast().id();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).header("Vary","Cookie").body(new Messages(messages,cursor,isMuted(assignmentId,actor.id())));
    }
    @GetMapping("/participants/{authorId}/avatar")
    public ResponseEntity<byte[]> avatar(@PathVariable String assignmentId,@PathVariable String problemId,@PathVariable String authorId) {
        Actor actor=actors.require();String version=authorize(actor,assignmentId,problemId);
        var avatars=jdbc.query("SELECT u.avatar_png FROM app_user u WHERE u.id=? AND u.avatar_png IS NOT NULL AND EXISTS(SELECT 1 FROM problem_chat_message m WHERE m.author_id=u.id AND m.assignment_id=? AND m.problem_version_id=?)",
                (rs,n)->rs.getBytes(1),authorId,assignmentId,version);
        if(avatars.isEmpty()) AccessPolicy.notFound();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).header("Vary","Cookie")
                .header("X-Content-Type-Options","nosniff").contentType(MediaType.IMAGE_PNG).body(avatars.getFirst());
    }
    private boolean isMuted(String assignmentId,String studentId) {return jdbc.queryForObject("SELECT COUNT(*) FROM assignment_chat_mute WHERE assignment_id=? AND student_id=?",Integer.class,assignmentId,studentId)>0;}
    @DeleteMapping("/messages/{messageId}") @Transactional
    public ResponseEntity<Void> retract(@PathVariable String assignmentId,@PathVariable String problemId,@PathVariable long messageId) {
        Actor actor=actors.requireProblemManager();String version=authorize(actor,assignmentId,problemId);
        int changed=jdbc.update("UPDATE problem_chat_message SET retracted_by=?,retracted_at=COALESCE(retracted_at,?) WHERE id=? AND assignment_id=? AND problem_version_id=?",actor.id(),LocalDateTime.now(ZoneOffset.UTC),messageId,assignmentId,version);
        if(changed==0) AccessPolicy.notFound();return ResponseEntity.noContent().build();
    }
    @PostMapping @Transactional public ResponseEntity<Message> post(@PathVariable String assignmentId,@PathVariable String problemId,@RequestHeader("Idempotency-Key") String key,@RequestBody Post input) {
        Actor actor=actors.require();String version=authorize(actor,assignmentId,problemId);
        if(!selection.options(assignmentId).chatEnabled()) throw new BusinessException(HttpStatus.FORBIDDEN,"CHAT_DISABLED","该作业未开放聊天室");
        String body=input.body()==null?"":input.body().strip();
        if(body.isEmpty() || body.codePointCount(0,body.length())>1000 || body.indexOf('\0')>=0 || !key.matches("[A-Za-z0-9-]{16,128}")) throw new BusinessException(HttpStatus.BAD_REQUEST,"INVALID_MESSAGE","消息需为 1 至 1000 个字符");
        jdbc.queryForObject("SELECT id FROM app_user WHERE id=? FOR UPDATE",String.class,actor.id());
        if(!jdbc.queryForList("SELECT student_id FROM assignment_chat_mute WHERE assignment_id=? AND student_id=? FOR UPDATE",String.class,assignmentId,actor.id()).isEmpty()) throw new BusinessException(HttpStatus.FORBIDDEN,"CHAT_MUTED","你在本作业中已被禁言");
        var previous=jdbc.queryForList("SELECT id,assignment_id,problem_version_id,body FROM problem_chat_message WHERE author_id=? AND client_key=? FOR UPDATE",actor.id(),key);
        String id;
        if(!previous.isEmpty()) {
            var p=previous.getFirst();
            if(!assignmentId.equals(p.get("assignment_id")) || !version.equals(p.get("problem_version_id")) || !body.equals(p.get("body"))) throw new BusinessException(HttpStatus.CONFLICT,"IDEMPOTENCY_CONFLICT","该消息已使用不同内容发送");
            id=p.get("id").toString();
        } else {
            var times=jdbc.query("SELECT created_at FROM problem_chat_message WHERE author_id=? ORDER BY created_at DESC LIMIT 1 FOR UPDATE",(rs,n)->rs.getObject(1,LocalDateTime.class),actor.id());
            var now=LocalDateTime.now(ZoneOffset.UTC);
            if(!times.isEmpty() && times.getFirst().plusSeconds(2).isAfter(now)) throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,"CHAT_RATE_LIMIT","发送过于频繁，请稍后重试");
            jdbc.update("INSERT INTO problem_chat_message(assignment_id,problem_version_id,author_id,client_key,body,created_at) VALUES(?,?,?,?,?,?)",assignmentId,version,actor.id(),key,body,now);
            id=jdbc.queryForObject("SELECT id FROM problem_chat_message WHERE author_id=? AND client_key=?",String.class,actor.id(),key);
        }
        Message message=jdbc.query(SELECT+"WHERE m.id=?",(rs,n)->map(rs,assignmentId,problemId),id).getFirst();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).header("Vary","Cookie").body(message);
    }
}
