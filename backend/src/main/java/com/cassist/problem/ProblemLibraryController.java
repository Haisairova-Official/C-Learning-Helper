package com.cassist.problem;
import com.cassist.identity.CurrentActor;
import com.cassist.common.PageResult;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/teacher/libraries")
public class ProblemLibraryController {
    private final CurrentActor actors;private final ProblemLibraryService libraries;
    public ProblemLibraryController(CurrentActor actors,ProblemLibraryService libraries){this.actors=actors;this.libraries=libraries;}
    @GetMapping public PageResult<ProblemLibraryService.Library> list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(defaultValue="") String q){return libraries.list(actors.requireProblemManager(),page,pageSize,q);}
    @GetMapping("/{id}") public ProblemLibraryService.Detail detail(@PathVariable String id){return libraries.detail(actors.requireProblemManager(),id);}
    @PostMapping public ProblemLibraryService.Library create(@RequestBody Map<String,String> input){return libraries.create(actors.requireProblemManager(),input.get("title"));}
    @PutMapping("/{id}") public ResponseEntity<Void> rename(@PathVariable String id,@RequestBody Map<String,String> input){libraries.rename(actors.requireProblemManager(),id,input.get("title"));return ResponseEntity.noContent().build();}
    @PostMapping("/{id}/problems") public TeacherProblemView createProblem(@PathVariable String id,@RequestBody TeacherProblemView input){return libraries.save(actors.requireProblemManager(),id,null,input);}
    @PutMapping("/{id}/problems/{problemId}") public TeacherProblemView saveProblem(@PathVariable String id,@PathVariable String problemId,@RequestBody TeacherProblemView input){return libraries.save(actors.requireProblemManager(),id,problemId,input);}
    @PostMapping("/{id}/members") public ResponseEntity<Void> add(@PathVariable String id,@RequestBody List<String> problemIds){libraries.add(actors.requireProblemManager(),id,problemIds);return ResponseEntity.noContent().build();}
}
