package com.cassist.roster;

import com.cassist.common.BusinessException;
import com.cassist.identity.*;
import java.io.IOException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/classes/{classId}/students/import")
public class StudentImportController {
    private final CurrentActor actors;private final StudentImportService imports;private final RosterParser parser=new RosterParser();
    public StudentImportController(CurrentActor actors,StudentImportService imports) { this.actors=actors;this.imports=imports; }
    public record Text(String text) {}
    public record Rows(java.util.List<RosterParser.Row> rows) {}
    private Actor manager(String classId) { Actor actor=actors.requireProblemManager();imports.authorize(actor,classId);return actor; }
    private <T> ResponseEntity<T> response(T value) {return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).header("Vary","Cookie").body(value);}
    @PostMapping(value="/preview",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StudentImportService.Preview> preview(@PathVariable String classId,@RequestBody Text body) {
        Actor actor=manager(classId);
        try { return response(imports.preview(actor,classId,parser.parse(body.text()))); }
        catch(IllegalArgumentException e) { throw new BusinessException(HttpStatus.BAD_REQUEST,"INVALID_ROSTER",e.getMessage()); }
    }
    @PostMapping(value="/preview",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StudentImportService.Preview> upload(@PathVariable String classId,@RequestPart("file") MultipartFile file) throws IOException {
        Actor actor=manager(classId);
        if(file.getSize()>5L*1024*1024) throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE,"UPLOAD_TOO_LARGE","文件不能超过 5 MB");
        try { return response(imports.preview(actor,classId,parser.read(file.getOriginalFilename(),file.getBytes()))); }
        catch(IllegalArgumentException e) { throw new BusinessException(HttpStatus.BAD_REQUEST,"INVALID_ROSTER",e.getMessage()); }
    }
    @PostMapping("/validate") public ResponseEntity<StudentImportService.Preview> validate(@PathVariable String classId,@RequestBody Rows body) {
        return response(imports.preview(manager(classId),classId,new RosterParser.Parsed(body.rows(),java.util.List.of())));
    }
    @PostMapping("/commit") public ResponseEntity<StudentImportService.Result> commit(@PathVariable String classId,@RequestHeader("Idempotency-Key") String key,@RequestBody StudentImportService.Commit body) {
        return response(imports.commit(manager(classId),classId,key,body));
    }
}
