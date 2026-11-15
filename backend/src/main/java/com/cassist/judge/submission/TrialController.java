package com.cassist.judge.submission;
import com.cassist.identity.CurrentActor;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/trials")
public class TrialController {
    private final CurrentActor actors; private final TrialService trials;
    public TrialController(CurrentActor actors,TrialService trials){this.actors=actors;this.trials=trials;}
    @PostMapping public ResponseEntity<TrialService.View> start(@Valid @RequestBody TrialService.Input input){
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(trials.start(actors.require(),input));
    }
    @GetMapping("/{id}") public ResponseEntity<TrialService.View> get(@PathVariable String id){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(trials.get(actors.require(),id));
    }
}
