package com.cassist.problem;

import com.cassist.common.BusinessException;
import com.cassist.common.PageResult;
import com.cassist.identity.AccessPolicy;
import com.cassist.identity.Actor;
import com.cassist.identity.Role;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class TeachingService implements ProblemAccessPort, DraftImportPort {
    private final JdbcTemplate jdbc;
    private final AccessPolicy access;
    private final ObjectMapper json;
    private final AssignmentSelectionService selection;

    public TeachingService(JdbcTemplate jdbc, AccessPolicy access, ObjectMapper json, AssignmentSelectionService selection) {
        this.jdbc = jdbc; this.access = access; this.json = json; this.selection = selection;
    }

    public PageResult<TeacherProblemView> teacherProblems(Actor actor, int page, int pageSize) {
        access.requireManager(actor); pagination(page, pageSize);
        String condition = actor.role() == Role.ADMIN ? "1=1" : "owner_id=?";
        List<Object> args = new ArrayList<>(); if (actor.role() != Role.ADMIN) args.add(actor.id());
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM problem WHERE " + condition, Long.class, args.toArray());
        args.add(pageSize); args.add((page - 1) * pageSize);
        var ids = jdbc.queryForList("SELECT id FROM problem WHERE " + condition + " ORDER BY updated_at DESC, id LIMIT ? OFFSET ?", String.class, args.toArray());
        return new PageResult<>(ids.stream().map(id -> teacherProblem(actor, id)).toList(), page, pageSize, total);
    }

    public TeacherProblemView teacherProblem(Actor actor, String id) {
        access.requireManager(actor);
        var owners = jdbc.queryForList("SELECT owner_id FROM problem WHERE id=?", String.class, id);
        if (owners.isEmpty()) AccessPolicy.notFound();
        access.requireOwner(actor, owners.getFirst());
        return teacherVersion(latestVersionId(id));
    }

    @Transactional
    public TeacherProblemView saveProblem(Actor actor, String id, TeacherProblemView input) {
        access.requireManager(actor);
        validateDraft(input);
        boolean create = id == null;
        if (create) {
            id = uuid();
            jdbc.update("INSERT INTO problem(id,owner_id,title,status,created_at,updated_at) VALUES(?,?,?,'DRAFT',?,?)",
                    id, actor.id(), input.title().trim(), now(), now());
        } else {
            var owners = jdbc.queryForList("SELECT owner_id FROM problem WHERE id=? FOR UPDATE", String.class, id);
            if (owners.isEmpty()) AccessPolicy.notFound();
            access.requireOwner(actor, owners.getFirst());
        }
        String state = "PUBLISHED".equals(input.state()) ? "PUBLISHED" : "DRAFT";
        if (state.equals("PUBLISHED")) validatePublish(input);
        Integer version = jdbc.queryForObject("SELECT COALESCE(MAX(version_number),0)+1 FROM problem_version WHERE problem_id=?", Integer.class, id);
        String versionId = uuid();
        jdbc.update("INSERT INTO problem_version(id,problem_id,version_number,title,statement,input_description,output_description,reference_answer,time_limit_ms,memory_limit_mb,code_limit_bytes,max_score,created_at,problem_type,language,function_signature,starter_code,judge_template) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                versionId, id, version, input.title().trim(), text(input.statement()), text(input.inputDescription()),
                text(input.outputDescription()), text(input.referenceAnswer()), time(input), memory(input), codeLimit(input), input.maxScore(), now(),
                input.problemType(), input.language(), input.functionSignature(), input.starterCode(), input.judgeTemplate());
        int index = 0;
        for (Sample sample : input.samples()) {
            jdbc.update("INSERT INTO problem_sample(id,problem_version_id,sample_index,input_text,output_text,explanation) VALUES(?,?,?,?,?,?)",
                    uuid(), versionId, ++index, text(sample.input()), text(sample.output()), sample.explanation());
        }
        index = 0;
        for (PrivateTestCase test : input.testCases()) {
            jdbc.update("INSERT INTO test_case(id,problem_version_id,case_index,input_text,expected_output,visibility,score,comparison_mode) VALUES(?,?,?,?,?,?,?,?)",
                    uuid(), versionId, ++index, test.input(), test.expectedOutput(),
                    test.visibility() == null ? "HIDDEN" : test.visibility().name(), test.score(),
                    test.comparisonMode() == null ? "EXACT" : test.comparisonMode().name());
        }
        jdbc.update("UPDATE problem SET title=?,status=?,updated_at=? WHERE id=?", input.title().trim(), state, now(), id);
        return teacherVersion(versionId);
    }

    private String latestVersionId(String problemId) {
        var versions = jdbc.queryForList("SELECT id FROM problem_version WHERE problem_id=? ORDER BY version_number DESC LIMIT 1", String.class, problemId);
        if (versions.isEmpty()) AccessPolicy.notFound();
        return versions.getFirst();
    }

    private TeacherProblemView teacherVersion(String versionId) {
        var views = jdbc.query("SELECT pv.*,p.status FROM problem_version pv JOIN problem p ON p.id=pv.problem_id WHERE pv.id=?",
                (rs, n) -> new TeacherProblemView(rs.getString("problem_id"), rs.getString("id"), rs.getString("title"),
                        rs.getString("statement"), rs.getString("input_description"), rs.getString("output_description"), samples(versionId),
                        rs.getLong("time_limit_ms"), rs.getLong("memory_limit_mb"), rs.getLong("code_limit_bytes"), rs.getBigDecimal("max_score"),
                        rs.getString("reference_answer"), testCases(versionId), rs.getString("status"),
                        rs.getString("problem_type"), rs.getString("language"), rs.getString("function_signature"),
                        rs.getString("starter_code"), rs.getString("judge_template")), versionId);
        if (views.isEmpty()) AccessPolicy.notFound();
        return views.getFirst();
    }

    private List<Sample> samples(String versionId) {
        return jdbc.query("SELECT input_text,output_text,explanation FROM problem_sample WHERE problem_version_id=? ORDER BY sample_index",
                (rs, n) -> new Sample(rs.getString(1), rs.getString(2), rs.getString(3)), versionId);
    }

    private List<PrivateTestCase> testCases(String versionId) {
        return jdbc.query("SELECT id,input_text,expected_output,visibility,score,comparison_mode FROM test_case WHERE problem_version_id=? ORDER BY case_index",
                (rs, n) -> new PrivateTestCase(rs.getString(1), rs.getString(2), rs.getString(3),
                        PrivateTestCase.Visibility.valueOf(rs.getString(4)), rs.getBigDecimal(5), PrivateTestCase.ComparisonMode.valueOf(rs.getString(6))), versionId);
    }

    @Override
    public ProblemSnapshot loadForEvaluation(String versionId) {
        TeacherProblemView view = teacherVersion(versionId);
        StudentProblemView student = new StudentProblemView(view.id(), view.versionId(), view.title(), view.statement(),
                view.inputDescription(), view.outputDescription(), view.samples(), view.timeLimitMs(), view.memoryLimitMb(),
                view.codeLimitBytes(), view.maxScore() == null ? BigDecimal.ZERO : view.maxScore(),
                view.problemType(), view.language(), view.functionSignature(), view.starterCode());
        return new ProblemSnapshot(student, view.referenceAnswer(), view.testCases(), view.judgeTemplate());
    }

    @Override
    public ProblemSnapshot requireForStudent(Actor actor, String assignmentId, String problemId, String versionId) {
        access.requireAssignmentRead(actor, assignmentId);
        var opens = jdbc.query("SELECT opens_at FROM assignment WHERE id=?", (rs, n) -> rs.getObject(1, LocalDateTime.class), assignmentId);
        if (opens.isEmpty()) AccessPolicy.notFound();
        if (actor.role() == Role.STUDENT && now().isBefore(opens.getFirst()))
            throw new BusinessException(HttpStatus.FORBIDDEN, "ASSIGNMENT_NOT_OPEN", "作业尚未开放");
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM assignment_problem ap JOIN problem_version pv ON pv.id=ap.problem_version_id WHERE ap.assignment_id=? AND pv.problem_id=? AND pv.id=?", Integer.class, assignmentId, problemId, versionId);
        if (count == null || count == 0 || !selection.versions(actor,assignmentId).contains(versionId)) AccessPolicy.notFound();
        return loadForEvaluation(versionId);
    }

    @Override
    public ProblemSnapshot requireForSubmission(Actor actor, String assignmentId, String problemId, String versionId, Instant receivedAt) {
        if (actor.role() != Role.STUDENT) AccessPolicy.forbidden();
        ProblemSnapshot snapshot = requireForStudent(actor, assignmentId, problemId, versionId);
        var bounds = jdbc.query("SELECT opens_at,closes_at FROM assignment WHERE id=?", (rs, n) ->
                new LocalDateTime[]{rs.getObject(1, LocalDateTime.class), rs.getObject(2, LocalDateTime.class)}, assignmentId).getFirst();
        LocalDateTime acceptedAt = LocalDateTime.ofInstant(receivedAt, ZoneOffset.UTC);
        if (acceptedAt.isBefore(bounds[0]) || !acceptedAt.isBefore(bounds[1]))
            throw new BusinessException(HttpStatus.CONFLICT, "ASSIGNMENT_CLOSED", "当前不在作业提交时间内");
        return snapshot;
    }

    public StudentProblemView studentProblem(Actor actor, String assignmentId, String problemId) {
        access.requireAssignmentRead(actor, assignmentId);
        var versions = jdbc.queryForList("SELECT pv.id FROM assignment_problem ap JOIN problem_version pv ON pv.id=ap.problem_version_id WHERE ap.assignment_id=? AND pv.problem_id=?", String.class, assignmentId, problemId);
        if (versions.isEmpty()) AccessPolicy.notFound();
        return requireForStudent(actor, assignmentId, problemId, versions.getFirst()).problem();
    }

    @Override
    @Transactional
    public DraftImportResult saveDrafts(Actor actor, String importJobId, List<DraftProblem> drafts, String title) {
        access.requireManager(actor);
        if (drafts == null || drafts.isEmpty() || drafts.size() > 100) invalid("请选择 1 至 100 道题目");
        if (title == null || title.isBlank() || title.length() > 300) invalid("题目集标题不能为空且不超过 300 字符");
        var owners = jdbc.queryForList("SELECT owner_id FROM import_job WHERE id=? FOR UPDATE", String.class, importJobId);
        if (owners.isEmpty()) AccessPolicy.notFound();
        access.requireOwner(actor, owners.getFirst());
        String hash = digest(json.writeValueAsString(List.of(title, drafts)));
        var receipts = jdbc.queryForList("SELECT request_hash,problem_set_id,problem_ids_json FROM draft_import_receipt WHERE import_job_id=?", importJobId);
        if (!receipts.isEmpty()) {
            var row = receipts.getFirst();
            if (!hash.equals(row.get("request_hash"))) throw new BusinessException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "该导入任务已用不同内容保存");
            return new DraftImportResult((String) row.get("problem_set_id"), Arrays.asList(json.readValue((String) row.get("problem_ids_json"), String[].class)));
        }
        String setId = uuid();
        jdbc.update("INSERT INTO problem_set(id,owner_id,title,created_at) VALUES(?,?,?,?)", setId, actor.id(), title, now());
        jdbc.update("INSERT INTO problem_library(id,owner_id,title,created_at,updated_at) VALUES(?,?,?,?,?)",setId,actor.id(),title,now(),now());
        List<String> ids = new ArrayList<>(); int position = 0;
        for (DraftProblem draft : drafts) {
            var view = saveProblem(actor, null, new TeacherProblemView(null, null, draft.title(), draft.statement(),
                    draft.inputDescription(), draft.outputDescription(), draft.samples(), draft.timeLimitMs(), draft.memoryLimitMb(),
                    draft.codeLimitBytes(), draft.maxScore(), draft.referenceAnswer(), draft.testCases(), "DRAFT",
                    draft.problemType(), draft.language(), draft.functionSignature(), draft.starterCode(), draft.judgeTemplate()));
            ids.add(view.id());
            jdbc.update("INSERT INTO problem_set_item(problem_set_id,problem_version_id,position) VALUES(?,?,?)", setId, view.versionId(), ++position);
            jdbc.update("INSERT INTO library_problem(library_id,problem_id,position) VALUES(?,?,?)",setId,view.id(),position);
        }
        jdbc.update("INSERT INTO draft_import_receipt(import_job_id,owner_id,request_hash,problem_set_id,problem_ids_json,created_at) VALUES(?,?,?,?,?,?)",
                importJobId, actor.id(), hash, setId, json.writeValueAsString(ids), now());
        return new DraftImportResult(setId, ids);
    }

    public PageResult<ClassView> classes(Actor actor, int page, int pageSize) {
        pagination(page, pageSize);
        String condition = actor.role() == Role.ADMIN ? "1=1" : actor.role() == Role.TEACHER ? "c.teacher_id=?" : "EXISTS(SELECT 1 FROM class_member cm WHERE cm.class_id=c.id AND cm.student_id=?)";
        List<Object> args = new ArrayList<>(); if (actor.role() != Role.ADMIN) args.add(actor.id());
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM classroom c WHERE " + condition, Long.class, args.toArray());
        args.add(pageSize); args.add((page-1)*pageSize);
        var rows = jdbc.query("SELECT c.id,c.name,(SELECT COUNT(*) FROM class_member cm WHERE cm.class_id=c.id) AS member_count FROM classroom c WHERE " + condition + " ORDER BY c.created_at,c.id LIMIT ? OFFSET ?",
                (rs, n) -> new ClassView(rs.getString(1), rs.getString(2), rs.getInt(3)), args.toArray());
        return new PageResult<>(rows, page, pageSize, total);
    }

    public PageResult<MemberView> members(Actor actor, String classId, int page, int pageSize) {
        access.requireClassManage(actor, classId); pagination(page, pageSize);
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM class_member WHERE class_id=?", Long.class, classId);
        var rows = jdbc.query("SELECT u.id,u.display_name,u.student_number FROM class_member cm JOIN app_user u ON u.id=cm.student_id WHERE cm.class_id=? ORDER BY u.username LIMIT ? OFFSET ?",
                (rs,n) -> new MemberView(rs.getString(1),rs.getString(2),rs.getString(3)), classId,pageSize,(page-1)*pageSize);
        return new PageResult<>(rows,page,pageSize,total);
    }

    @Transactional
    public ClassView createClass(Actor actor, String name) {
        access.requireManager(actor);
        if (name == null || name.isBlank() || name.length()>200) invalid("请输入有效班级名称");
        String id = uuid(); jdbc.update("INSERT INTO classroom(id,name,teacher_id,created_at) VALUES(?,?,?,?)", id,name.trim(),actor.id(),now());
        return new ClassView(id,name.trim(),0);
    }

    @Transactional
    public void addMember(Actor actor, String classId, String studentId) {
        access.requireClassManage(actor,classId);
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE id=? AND role='STUDENT' AND enabled=TRUE",Integer.class,studentId);
        if (count==null || count==0) invalid("学生账号不存在");
        jdbc.update("INSERT INTO class_member(class_id,student_id,joined_at) VALUES(?,?,?) ON DUPLICATE KEY UPDATE joined_at=joined_at",classId,studentId,now());
    }

    @Transactional
    public AssignmentView publish(Actor actor, AssignmentRequest request) {
        access.requireClassManage(actor,request.classId());
        if (request.title()==null || request.title().isBlank() || request.title().length()>300) invalid("请输入作业标题");
        if (request.opensAt()==null || request.closesAt()==null || !request.closesAt().isAfter(request.opensAt())) invalid("截止时间须晚于开放时间");
        List<String> problemIds=request.problemIds();
        if(request.libraryId()!=null && !request.libraryId().isBlank()) {
            var owners=jdbc.queryForList("SELECT owner_id FROM problem_library WHERE id=?",String.class,request.libraryId());
            if(owners.isEmpty()) AccessPolicy.notFound(); access.requireOwner(actor,owners.getFirst());
            problemIds=jdbc.queryForList("SELECT problem_id FROM library_problem WHERE library_id=? ORDER BY position,problem_id",String.class,request.libraryId());
        }
        if(problemIds==null || problemIds.isEmpty() || problemIds.size()>100 || problemIds.stream().distinct().count()!=problemIds.size()) invalid("请选择包含 1 至 100 道题目的题库");
        List<TeacherProblemView> problems=problemIds.stream().map(id->teacherProblem(actor,id)).toList();
        problems.forEach(this::validatePublish);
        String mode=request.selectionMode()==null?"FULL":request.selectionMode();
        if(!List.of("FULL","SHARED_RANDOM","PER_STUDENT_RANDOM").contains(mode)) invalid("请选择有效的出题方式");
        int drawCount=mode.equals("FULL")?problems.size():request.drawCount()==null?0:request.drawCount();
        if(drawCount<1 || drawCount>problems.size()) invalid("抽取题数应在 1 与候选题数之间");
        if(mode.equals("PER_STUDENT_RANDOM") && problems.stream().map(p->p.maxScore().stripTrailingZeros()).distinct().count()!=1) invalid("每人随机时，题库内题目分值需一致");
        if(mode.equals("SHARED_RANDOM")) problems=AssignmentSelectionService.draw(problems,drawCount);
        String setId=uuid(),id=uuid();
        jdbc.update("INSERT INTO problem_set(id,owner_id,title,created_at) VALUES(?,?,?,?)",setId,actor.id(),request.title(),now());
        jdbc.update("INSERT INTO assignment(id,problem_set_id,owner_id,title,opens_at,closes_at,created_at) VALUES(?,?,?,?,?,?,?)",id,setId,actor.id(),request.title(),utc(request.opensAt()),utc(request.closesAt()),now());
        jdbc.update("UPDATE assignment SET selection_mode=?,draw_count=?,ai_enabled=?,chat_enabled=? WHERE id=?",mode,drawCount,request.aiEnabled()==null || request.aiEnabled(),Boolean.TRUE.equals(request.chatEnabled()),id);
        jdbc.update("INSERT INTO assignment_class(assignment_id,class_id) VALUES(?,?)",id,request.classId());
        int index=0;
        for (var p:problems) {
            jdbc.update("INSERT INTO problem_set_item(problem_set_id,problem_version_id,position) VALUES(?,?,?)",setId,p.versionId(),++index);
            jdbc.update("INSERT INTO assignment_problem(assignment_id,problem_version_id,position) VALUES(?,?,?)",id,p.versionId(),index);
            jdbc.update("UPDATE problem SET status='PUBLISHED' WHERE id=?",p.id());
        }
        if(mode.equals("PER_STUDENT_RANDOM")) {
            for(String studentId:jdbc.queryForList("SELECT student_id FROM class_member WHERE class_id=?",String.class,request.classId())) selection.versions(new Actor(studentId,Role.STUDENT),id);
        }
        return assignmentView(actor,id);
    }

    @Transactional
    public AssignmentView updateAssignment(Actor actor,String id,AssignmentUpdate request) {
        access.requireAssignmentManage(actor,id);
        if(request==null || request.title()==null || request.title().isBlank()) invalid("请输入作业名称");
        String title=request.title().strip();
        if(title.codePointCount(0,title.length())>200) invalid("作业名称不能超过 200 字");
        if(request.opensAt()==null || request.closesAt()==null) invalid("请选择开放时间和截止时间");
        Instant earliest=Instant.parse("1000-01-01T00:00:00Z"),latest=Instant.parse("+10000-01-01T00:00:00Z");
        if(request.opensAt().isBefore(earliest) || !request.opensAt().isBefore(latest)
                || request.closesAt().isBefore(earliest) || !request.closesAt().isBefore(latest)) invalid("作业时间超出支持范围");
        var opensAt=utc(request.opensAt().truncatedTo(ChronoUnit.MICROS));
        var closesAt=utc(request.closesAt().truncatedTo(ChronoUnit.MICROS));
        if(!closesAt.isAfter(opensAt)) invalid("截止时间须晚于开放时间");
        if(request.aiEnabled()==null || request.chatEnabled()==null) invalid("请选择 AI 和聊天室开关");
        jdbc.update("UPDATE assignment SET title=?,opens_at=?,closes_at=?,ai_enabled=?,chat_enabled=? WHERE id=?",
                title,opensAt,closesAt,request.aiEnabled(),request.chatEnabled(),id);
        return assignmentView(actor,id);
    }

    public PageResult<AssignmentView> assignments(Actor actor, boolean teacher, int page, int pageSize) {
        pagination(page,pageSize); if (teacher) access.requireManager(actor);
        String condition = teacher ? actor.role()==Role.ADMIN ? "1=1" : "a.owner_id=?" : "EXISTS(SELECT 1 FROM assignment_class ac JOIN class_member cm ON cm.class_id=ac.class_id WHERE ac.assignment_id=a.id AND cm.student_id=?)";
        List<Object> args=new ArrayList<>(); if (!(teacher && actor.role()==Role.ADMIN)) args.add(actor.id());
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM assignment a WHERE "+condition,Long.class,args.toArray());
        args.add(pageSize);args.add((page-1)*pageSize);
        var ids=jdbc.queryForList("SELECT a.id FROM assignment a WHERE "+condition+" ORDER BY a.created_at DESC,a.id LIMIT ? OFFSET ?",String.class,args.toArray());
        return new PageResult<>(ids.stream().map(id->assignmentView(actor,id)).toList(),page,pageSize,total);
    }

    public AssignmentView assignmentView(Actor actor,String id) {
        access.requireAssignmentRead(actor,id);
        var row=jdbc.queryForMap("SELECT title,opens_at,closes_at FROM assignment WHERE id=?",id);
        var classNames=jdbc.queryForList("SELECT c.name FROM classroom c JOIN assignment_class ac ON ac.class_id=c.id WHERE ac.assignment_id=? ORDER BY c.name",String.class,id);
        var progress=actor.role()==Role.STUDENT ? problemProgress(actor.id(),id) : Map.<String,ProblemProgress>of();
        var options=selection.options(id);
        var visibleVersions=selection.versions(actor,id);
        var problems=jdbc.query("SELECT pv.problem_id,pv.id,pv.title,pv.max_score FROM assignment_problem ap JOIN problem_version pv ON pv.id=ap.problem_version_id WHERE ap.assignment_id=? ORDER BY ap.position",
                (rs,n)->new ProblemSummary(rs.getString(1),rs.getString(2),rs.getString(3),rs.getBigDecimal(4),
                        actor.role()==Role.STUDENT ? progress.getOrDefault(rs.getString(2),ProblemProgress.empty()) : null),id);
        var times=jdbc.query("SELECT opens_at,closes_at FROM assignment WHERE id=?",(rs,n)->new Instant[]{rs.getObject(1,LocalDateTime.class).toInstant(ZoneOffset.UTC),rs.getObject(2,LocalDateTime.class).toInstant(ZoneOffset.UTC)},id).getFirst();
        int poolSize=problems.size();
        Map<String,ProblemSummary> byVersion=new HashMap<>();for(var p:problems) byVersion.put(p.versionId(),p);
        problems=visibleVersions.stream().map(byVersion::get).filter(Objects::nonNull).toList();
        int questionCount=options.selectionMode().equals("PER_STUDENT_RANDOM")?options.drawCount():problems.size();
        BigDecimal maximum=options.selectionMode().equals("PER_STUDENT_RANDOM")?problems.getFirst().maxScore().multiply(BigDecimal.valueOf(questionCount)):problems.stream().map(ProblemSummary::maxScore).reduce(BigDecimal.ZERO,BigDecimal::add);
        Score score=actor.role()==Role.STUDENT ? score(actor.id(),id) : new Score(0,BigDecimal.ZERO);
        return new AssignmentView(id,(String)row.get("title"),String.join("、",classNames),times[0],times[1],problems,score.completed(),score.score(),maximum,"PUBLISHED",options.selectionMode(),questionCount,actor.role()==Role.STUDENT?questionCount:poolSize,options.aiEnabled(),options.chatEnabled());
    }

    public PageResult<ResultView> results(Actor actor,String assignmentId,int page,int pageSize) {
        access.requireAssignmentManage(actor,assignmentId);pagination(page,pageSize);
        var ids=jdbc.queryForList("SELECT DISTINCT cm.student_id FROM assignment_class ac JOIN class_member cm ON cm.class_id=ac.class_id WHERE ac.assignment_id=? ORDER BY cm.student_id",String.class,assignmentId);
        var assignment=assignmentView(actor,assignmentId);
        var rows=ids.stream().skip((long)(page-1)*pageSize).limit(pageSize).map(id->{
            String name=jdbc.queryForObject("SELECT display_name FROM app_user WHERE id=?",String.class,id);Score score=score(id,assignmentId);
            return new ResultView(id,name,score.completed(),assignment.questionCount(),score.score(),assignment.maxScore());
        }).toList();
        return new PageResult<>(rows,page,pageSize,ids.size());
    }

    private Score score(String studentId,String assignmentId) {
        var rows=jdbc.query("SELECT problem_id,MAX(score) AS best,MAX(CASE WHEN verdict='ACCEPTED' THEN 1 ELSE 0 END) AS completed FROM submission WHERE student_id=? AND assignment_id=? AND status='FINISHED' AND score IS NOT NULL GROUP BY problem_id",
                (rs,n)->new Score(rs.getInt("completed"),rs.getBigDecimal("best")),studentId,assignmentId);
        return rows.stream().reduce(new Score(0,BigDecimal.ZERO),(a,b)->new Score(a.completed()+b.completed(),a.score().add(b.score())));
    }

    private Map<String,ProblemProgress> problemProgress(String studentId,String assignmentId) {
        var rows=jdbc.query("""
                SELECT problem_version_id, COUNT(*) AS attempts,
                    SUM(CASE WHEN status='FINISHED' AND score IS NOT NULL THEN 1 ELSE 0 END) AS judged,
                    MAX(CASE WHEN status='FINISHED' AND verdict='ACCEPTED' AND score IS NOT NULL THEN 1 ELSE 0 END) AS accepted,
                    MAX(CASE WHEN status='FINISHED' AND verdict='PARTIAL_ACCEPTED' AND score IS NOT NULL THEN 1 ELSE 0 END) AS partial,
                    SUM(CASE WHEN status IN ('QUEUED','COMPILING','RUNNING') THEN 1 ELSE 0 END) AS pending,
                    MAX(CASE WHEN status='FINISHED' THEN score ELSE NULL END) AS best
                FROM submission WHERE student_id=? AND assignment_id=? GROUP BY problem_version_id
                """,(rs,n)->Map.entry(rs.getString("problem_version_id"),ProblemProgress.summarize(
                        rs.getInt("attempts"),rs.getInt("judged"),rs.getInt("accepted"),rs.getInt("partial"),
                        rs.getInt("pending"),rs.getBigDecimal("best"))),studentId,assignmentId);
        Map<String,ProblemProgress> progress=new HashMap<>();
        for(var row:rows) progress.put(row.getKey(),row.getValue());
        return progress;
    }

    private void validateDraft(TeacherProblemView p) {
        try { ProblemCodePolicy.validateDraft(p.problemType(), p.language(), p.functionSignature(), p.starterCode(), p.judgeTemplate()); }
        catch (IllegalArgumentException e) { invalid(e.getMessage()); }
        if (p.title()==null || p.title().isBlank() || p.title().length()>300) invalid("题目标题不能为空且不超过 300 字符");
        if (text(p.statement()).length()>200000 || text(p.referenceAnswer()).length()>262144) invalid("题目或参考代码过长");
        if (p.samples().size()>30 || p.testCases().size()>100) invalid("样例或测试点数量超限");
        if (time(p)<100 || time(p)>10000 || memory(p)<16 || memory(p)>512 || codeLimit(p)<1024 || codeLimit(p)>262144) invalid("运行限制超出允许范围");
        if (p.maxScore()!=null && (p.maxScore().signum()<0 || p.maxScore().compareTo(new BigDecimal("10000"))>0 || p.maxScore().scale()>2)) invalid("题目分值不符合要求");
        for (var c:p.testCases()) {
            if (c.score()!=null && (c.score().signum()<0 || c.score().scale()>2 || c.score().compareTo(new BigDecimal("10000"))>0)) invalid("测试点分值不符合要求");
            if (text(c.input()).length()>1048576 || text(c.expectedOutput()).length()>1048576) invalid("单个测试点内容过大");
        }
    }

    private void validatePublish(TeacherProblemView p) {
        validateDraft(p);
        try { ProblemCodePolicy.validate(p.problemType(), p.language(), p.functionSignature(), p.starterCode(), p.judgeTemplate()); }
        catch (IllegalArgumentException e) { invalid(e.getMessage()); }
        if (text(p.statement()).isBlank() || text(p.referenceAnswer()).isBlank() || p.testCases().isEmpty() || p.maxScore()==null || p.maxScore().signum()<=0) invalid("发布前须补齐题干、参考答案、计分测试点和总分");
        BigDecimal sum=BigDecimal.ZERO;
        for (var c:p.testCases()) {
            if (c.input()==null || c.expectedOutput()==null || c.score()==null) invalid("测试点输入、输出或分值缺失");
            sum=sum.add(c.score());
        }
        if (sum.compareTo(p.maxScore())!=0) invalid("测试点分值总和须等于题目总分");
    }

    private static String uuid(){return UUID.randomUUID().toString();}
    private static String text(String value){return value==null?"":value;}
    private static long time(TeacherProblemView p){return p.timeLimitMs()==null?1000:p.timeLimitMs();}
    private static long memory(TeacherProblemView p){return p.memoryLimitMb()==null?128:p.memoryLimitMb();}
    private static long codeLimit(TeacherProblemView p){return p.codeLimitBytes()==null?65536:p.codeLimitBytes();}
    private static LocalDateTime now(){return LocalDateTime.now(ZoneOffset.UTC);}
    private static LocalDateTime utc(Instant t){return LocalDateTime.ofInstant(t,ZoneOffset.UTC);}
    private static void invalid(String message){throw new BusinessException(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED",message);}
    private static void pagination(int page,int size){if(page<1 || size<1 || size>100)invalid("分页参数不符合要求");}
    private static String digest(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}

    public record ClassView(String id,String name,int memberCount){}
    public record MemberView(String id,String name,String studentNumber){}
    public record ProblemSummary(String id,String versionId,String title,BigDecimal maxScore,ProblemProgress progress){}
    public record AssignmentView(String id,String title,String className,Instant opensAt,Instant closesAt,List<ProblemSummary> problems,int completedCount,BigDecimal score,BigDecimal maxScore,String state,String selectionMode,int questionCount,int poolSize,boolean aiEnabled,boolean chatEnabled){}
    public record AssignmentRequest(String title,String classId,List<String> problemIds,Instant opensAt,Instant closesAt,String libraryId,String selectionMode,Integer drawCount,Boolean aiEnabled,Boolean chatEnabled){
        public AssignmentRequest(String title,String classId,List<String> problemIds,Instant opensAt,Instant closesAt){this(title,classId,problemIds,opensAt,closesAt,null,"FULL",null,true,false);}
    }
    public record AssignmentUpdate(String title,Instant opensAt,Instant closesAt,Boolean aiEnabled,Boolean chatEnabled){}
    public record ResultView(String studentId,String name,int completedCount,int totalCount,BigDecimal score,BigDecimal maxScore){}
    private record Score(int completed,BigDecimal score){}
}
