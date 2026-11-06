package com.cassist.problem;

import com.cassist.common.*;
import com.cassist.identity.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProblemLibraryService {
    private final JdbcTemplate jdbc; private final AccessPolicy access; private final TeachingService teaching;
    public ProblemLibraryService(JdbcTemplate jdbc,AccessPolicy access,TeachingService teaching) {this.jdbc=jdbc;this.access=access;this.teaching=teaching;}
    public record Library(String id,String title,int problemCount) {}
    public record Detail(String id,String title,List<TeacherProblemView> problems) {}
    public void authorize(Actor actor,String id) {
        access.requireManager(actor);
        var owners=jdbc.queryForList("SELECT owner_id FROM problem_library WHERE id=?",String.class,id);
        if(owners.isEmpty()) AccessPolicy.notFound(); access.requireOwner(actor,owners.getFirst());
    }
    public PageResult<Library> list(Actor actor,int page,int size,String q) {
        access.requireManager(actor); if(page<1 || size<1 || size>100 || q.length()>200) invalid("查询范围不正确");
        String where=actor.role()==Role.ADMIN?" WHERE LOCATE(?,l.title)>0":" WHERE LOCATE(?,l.title)>0 AND l.owner_id=?";
        List<Object> args=new ArrayList<>(List.of(q));if(actor.role()!=Role.ADMIN) args.add(actor.id());
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM problem_library l"+where,Long.class,args.toArray());
        args.add(size);args.add((long)(page-1)*size);
        var rows=jdbc.query("SELECT l.id,l.title,(SELECT COUNT(*) FROM library_problem lp WHERE lp.library_id=l.id) AS count FROM problem_library l"+where+" ORDER BY l.updated_at DESC,l.id LIMIT ? OFFSET ?",(rs,n)->new Library(rs.getString(1),rs.getString(2),rs.getInt(3)),args.toArray());
        return new PageResult<>(rows,page,size,total);
    }
    public Detail detail(Actor actor,String id) {
        authorize(actor,id);String title=jdbc.queryForObject("SELECT title FROM problem_library WHERE id=?",String.class,id);
        var ids=jdbc.queryForList("SELECT problem_id FROM library_problem WHERE library_id=? ORDER BY position,problem_id",String.class,id);
        return new Detail(id,title,ids.stream().map(p->teaching.teacherProblem(actor,p)).toList());
    }
    @Transactional public Library create(Actor actor,String title) {
        access.requireManager(actor); title=validTitle(title);String id=UUID.randomUUID().toString();var now=LocalDateTime.now(ZoneOffset.UTC);
        jdbc.update("INSERT INTO problem_library(id,owner_id,title,created_at,updated_at) VALUES(?,?,?,?,?)",id,actor.id(),title,now,now);return new Library(id,title,0);
    }
    @Transactional public void rename(Actor actor,String id,String title) {authorize(actor,id);jdbc.update("UPDATE problem_library SET title=?,updated_at=? WHERE id=?",validTitle(title),LocalDateTime.now(ZoneOffset.UTC),id);}
    @Transactional public TeacherProblemView saveUncategorized(Actor actor,TeacherProblemView input) {
        access.requireManager(actor);
        String libraryId=actor.id().replace("-","");
        var now=LocalDateTime.now(ZoneOffset.UTC);
        // The legacy create endpoint uses the same per-owner library ID as migration V9.
        jdbc.update("INSERT INTO problem_library(id,owner_id,title,created_at,updated_at) VALUES(?,?,'未分类题目',?,?) ON DUPLICATE KEY UPDATE id=id",libraryId,actor.id(),now,now);
        String owner=jdbc.queryForObject("SELECT owner_id FROM problem_library WHERE id=? FOR UPDATE",String.class,libraryId);
        if(!actor.id().equals(owner)) invalid("题库归属不一致");
        var problem=teaching.saveProblem(actor,null,input);
        addInternal(libraryId,problem.id());return problem;
    }
    @Transactional public TeacherProblemView save(Actor actor,String libraryId,String problemId,TeacherProblemView input) {
        authorize(actor,libraryId);
        jdbc.queryForObject("SELECT id FROM problem_library WHERE id=? FOR UPDATE",String.class,libraryId);
        if(problemId!=null && jdbc.queryForObject("SELECT COUNT(*) FROM library_problem WHERE library_id=? AND problem_id=?",Integer.class,libraryId,problemId)==0) AccessPolicy.notFound();
        String libraryOwner=jdbc.queryForObject("SELECT owner_id FROM problem_library WHERE id=?",String.class,libraryId);
        var problem=teaching.saveProblem(problemId==null?new Actor(libraryOwner,Role.TEACHER):actor,problemId,input);
        addInternal(libraryId,problem.id());return problem;
    }
    @Transactional public void add(Actor actor,String id,List<String> problemIds) {
        authorize(actor,id);if(problemIds==null || problemIds.isEmpty() || problemIds.size()>100) invalid("请选择 1 至 100 道题目");
        jdbc.queryForObject("SELECT id FROM problem_library WHERE id=? FOR UPDATE",String.class,id);
        String owner=jdbc.queryForObject("SELECT owner_id FROM problem_library WHERE id=?",String.class,id);
        for(String problemId:problemIds) {var p=teaching.teacherProblem(actor,problemId);if(!owner.equals(jdbc.queryForObject("SELECT owner_id FROM problem WHERE id=?",String.class,p.id()))) invalid("只能加入同一管理者的题目");addInternal(id,p.id());}
    }
    private void addInternal(String id,String problemId) {
        int position=jdbc.queryForObject("SELECT COALESCE(MAX(position),0)+1 FROM library_problem WHERE library_id=?",Integer.class,id);
        jdbc.update("INSERT INTO library_problem(library_id,problem_id,position) VALUES(?,?,?) ON DUPLICATE KEY UPDATE position=position",id,problemId,position);
        jdbc.update("UPDATE problem_library SET updated_at=? WHERE id=?",LocalDateTime.now(ZoneOffset.UTC),id);
    }
    private String validTitle(String value) {if(value==null || value.isBlank() || value.length()>200) invalid("题库名称需为 1 至 200 字");return value.strip();}
    private static void invalid(String message) {throw new BusinessException(HttpStatus.BAD_REQUEST,"INVALID_LIBRARY",message);}
}
