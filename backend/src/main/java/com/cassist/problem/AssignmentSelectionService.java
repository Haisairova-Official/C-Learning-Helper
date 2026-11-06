package com.cassist.problem;

import com.cassist.common.BusinessException;
import com.cassist.identity.*;
import java.security.SecureRandom;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssignmentSelectionService {
    private final JdbcTemplate jdbc;private final AccessPolicy access;private static final SecureRandom RANDOM=new SecureRandom();
    public AssignmentSelectionService(JdbcTemplate jdbc,AccessPolicy access){this.jdbc=jdbc;this.access=access;}
    public record Options(String selectionMode,Integer drawCount,boolean aiEnabled,boolean chatEnabled) {}
    public Options options(String id) {
        var rows=jdbc.query("SELECT selection_mode,draw_count,ai_enabled,chat_enabled FROM assignment WHERE id=?",(rs,n)->new Options(rs.getString(1),(Integer)rs.getObject(2),rs.getBoolean(3),rs.getBoolean(4)),id);
        if(rows.isEmpty()) AccessPolicy.notFound();return rows.getFirst();
    }
    public static <T> List<T> draw(List<T> source,int count) {
        if(count<1 || count>source.size()) throw new BusinessException(HttpStatus.BAD_REQUEST,"INVALID_DRAW","抽取题数应在 1 与候选题数之间");
        List<T> shuffled=new ArrayList<>(source);Collections.shuffle(shuffled,RANDOM);return List.copyOf(shuffled.subList(0,count));
    }
    @Transactional public List<String> versions(Actor actor,String assignmentId) {
        access.requireAssignmentRead(actor,assignmentId);
        var options=options(assignmentId);
        if(actor.role()!=Role.STUDENT || !options.selectionMode().equals("PER_STUDENT_RANDOM")) return pool(assignmentId);
        jdbc.queryForObject("SELECT id FROM assignment WHERE id=? FOR UPDATE",String.class,assignmentId);
        var existing=assigned(actor.id(),assignmentId);if(!existing.isEmpty()) return existing;
        var chosen=draw(pool(assignmentId),options.drawCount());int position=0;
        for(String version:chosen) jdbc.update("INSERT INTO assignment_student_problem(assignment_id,student_id,problem_version_id,position) VALUES(?,?,?,?)",assignmentId,actor.id(),version,++position);
        return chosen;
    }
    private List<String> assigned(String studentId,String assignmentId) {return jdbc.queryForList("SELECT problem_version_id FROM assignment_student_problem WHERE assignment_id=? AND student_id=? ORDER BY position FOR UPDATE",String.class,assignmentId,studentId);}
    private List<String> pool(String assignmentId) {return jdbc.queryForList("SELECT problem_version_id FROM assignment_problem WHERE assignment_id=? ORDER BY position",String.class,assignmentId);}
}
