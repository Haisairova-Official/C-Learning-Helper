package com.cassist.problem;

import com.cassist.common.PageResult;
import com.cassist.identity.CurrentActor;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class TeachingController {
    private final TeachingService service;
    private final CurrentActor actors;
    private final ProblemLibraryService libraries;
    public TeachingController(TeachingService service,CurrentActor actors,ProblemLibraryService libraries){this.service=service;this.actors=actors;this.libraries=libraries;}

    @GetMapping("/teacher/problems")
    public PageResult<TeacherProblemView> problems(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize){
        return service.teacherProblems(actors.requireProblemManager(),page,pageSize);
    }
    @GetMapping("/teacher/problems/{id}")
    public TeacherProblemView problem(@PathVariable String id){return service.teacherProblem(actors.requireProblemManager(),id);}
    @PostMapping("/teacher/problems") @ResponseStatus(HttpStatus.CREATED)
    public TeacherProblemView create(@RequestBody TeacherProblemView input){return libraries.saveUncategorized(actors.requireProblemManager(),input);}
    @PutMapping("/teacher/problems/{id}")
    public TeacherProblemView update(@PathVariable String id,@RequestBody TeacherProblemView input){return service.saveProblem(actors.requireProblemManager(),id,input);}

    @GetMapping("/classes")
    public PageResult<TeachingService.ClassView> classes(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize){return service.classes(actors.require(),page,pageSize);}
    @PostMapping("/classes") @ResponseStatus(HttpStatus.CREATED)
    public TeachingService.ClassView createClass(@RequestBody Map<String,String> body){return service.createClass(actors.requireProblemManager(),body.get("name"));}
    @GetMapping("/classes/{id}/members")
    public PageResult<TeachingService.MemberView> members(@PathVariable String id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize){return service.members(actors.requireProblemManager(),id,page,pageSize);}
    @PostMapping("/classes/{id}/members") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addMember(@PathVariable String id,@RequestBody Map<String,String> body){service.addMember(actors.requireProblemManager(),id,body.get("studentId"));}

    @GetMapping("/teacher/assignments")
    public PageResult<TeachingService.AssignmentView> teacherAssignments(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize){return service.assignments(actors.requireProblemManager(),true,page,pageSize);}
    @GetMapping("/teacher/assignments/{id}")
    public TeachingService.AssignmentView teacherAssignment(@PathVariable String id){return service.assignmentView(actors.requireProblemManager(),id);}
    @PostMapping("/teacher/assignments") @ResponseStatus(HttpStatus.CREATED)
    public TeachingService.AssignmentView publish(@RequestBody TeachingService.AssignmentRequest input){return service.publish(actors.requireProblemManager(),input);}
    @PutMapping("/teacher/assignments/{id}")
    public TeachingService.AssignmentView updateAssignment(@PathVariable String id,@RequestBody TeachingService.AssignmentUpdate input){return service.updateAssignment(actors.requireProblemManager(),id,input);}
    @GetMapping("/teacher/assignments/{id}/results")
    public PageResult<TeachingService.ResultView> results(@PathVariable String id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize){return service.results(actors.requireProblemManager(),id,page,pageSize);}

    @GetMapping("/student/assignments")
    public PageResult<TeachingService.AssignmentView> studentAssignments(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize){return service.assignments(actors.require(),false,page,pageSize);}
    @GetMapping("/student/assignments/{id}")
    public TeachingService.AssignmentView studentAssignment(@PathVariable String id){return service.assignmentView(actors.require(),id);}
    @GetMapping("/student/assignments/{id}/problems/{problemId}")
    public StudentProblemView studentProblem(@PathVariable String id,@PathVariable String problemId){return service.studentProblem(actors.require(),id,problemId);}
}
