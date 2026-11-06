package com.cassist.importer.web;

import com.cassist.common.*;
import com.cassist.identity.*;
import com.cassist.importer.*;
import com.cassist.problem.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/imports")
public class ImportController {
    private final CurrentActor actors;
    private final JsonJobStore jobs;
    private final DraftImportPort drafts;
    private final EasyParseTextParser parser=new EasyParseTextParser();
    private final EasyParseJsonParser jsonParser=new EasyParseJsonParser();
    private final DocumentTextReader reader=new DocumentTextReader();
    public ImportController(CurrentActor actors,JsonJobStore jobs,DraftImportPort drafts) {this.actors=actors;this.jobs=jobs;this.drafts=drafts;}
    public record TextRequest(String sourceType,String text) {}
    public record CommitRequest(String title,List<ImportPreview.Candidate> candidates) {}
    public record Job(String id,String status,String parserVersion,List<ImportPreview.Candidate> candidates,List<ImportPreview.Issue> issues) {}
    @PostMapping(path="/parse",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String,String>> parse(@RequestBody TextRequest request) {
        Actor actor=actors.requireProblemManager();
        if(!Set.of("TEXT","JSON").contains(value(request.sourceType()))) throw invalid("请选择文本或 JSON 导入");
        return parseText(actor,request.text(),"JSON".equals(request.sourceType()));
    }
    @PostMapping(path="/parse",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String,String>> upload(@RequestParam("file") MultipartFile file,@RequestParam("sourceType") String sourceType) throws IOException {
        Actor actor=actors.requireProblemManager();
        if(file.getSize()>5L*1024*1024) throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE,"UPLOAD_TOO_LARGE","文件不能超过 5 MB");
        try {return parseText(actor,reader.read(file.getOriginalFilename(),file.getBytes(),sourceType),"JSON".equals(sourceType));}
        catch(IllegalArgumentException e) {throw invalid(e.getMessage());}
    }
    private ResponseEntity<Map<String,String>> parseText(Actor actor,String text,boolean structured) {
        if(text==null || text.isBlank()) throw invalid("请输入题目材料");
        ImportPreview preview;
        String start=text.stripLeading();
        if(start.startsWith("\uFEFF")) start=start.substring(1).stripLeading();
        boolean json=structured || start.startsWith("{") || start.startsWith("[");
        try {preview=json?jsonParser.parse(text):parser.parse(text);}
        catch(IllegalArgumentException e) {throw invalid(json?e.getMessage():"材料过大或格式无法解析，请分批导入");}
        String id=UUID.randomUUID().toString();
        jobs.create("import_job",id,actor,"READY",new Job(id,"READY",preview.parserVersion(),preview.candidates(),preview.issues()));
        return ResponseEntity.accepted().body(Map.of("id",id,"status","READY"));
    }
    @GetMapping("/{id}")
    public Job get(@PathVariable String id) {return jobs.read("import_job",id,actors.requireProblemManager(),Job.class);}
    @PostMapping("/{id}/commit")
    public ResponseEntity<DraftImportPort.DraftImportResult> commit(@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody CommitRequest request) {
        Actor actor=actors.requireProblemManager();
        if(!key.matches("[A-Za-z0-9-]{16,128}")) throw invalid("无效的操作标识");
        Job original=jobs.read("import_job",id,actor,Job.class);
        if(!Set.of("READY","COMMITTED").contains(original.status())) throw invalid("导入结果尚未准备好");
        if(request.title()==null || request.title().isBlank() || request.title().length()>200) throw invalid("请填写不超过 200 字的题目集名称");
        if(request.candidates()==null || request.candidates().isEmpty() || request.candidates().size()>100) throw invalid("请选择 1 至 100 道题目");
        Set<String> allowed=new HashSet<>(); for(var c:original.candidates()) allowed.add(c.candidateId());
        Set<String> seen=new HashSet<>(); List<DraftProblem> selected=new ArrayList<>();
        for(var c:request.candidates()) {
            if(c==null || !allowed.contains(c.candidateId()) || !seen.add(c.candidateId())) throw invalid("候选题目无效或重复");
            selected.add(convert(c));
        }
        var result=drafts.saveDrafts(actor,id,selected,request.title().strip());
        // The business port atomically checks owner/content and stores an idempotent receipt.
        jobs.save("import_job",id,"COMMITTED",new Job(id,"COMMITTED",original.parserVersion(),request.candidates(),original.issues()));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
    private DraftProblem convert(ImportPreview.Candidate c) {
        if(c.title()==null || c.title().isBlank() || c.title().length()>200) throw invalid("每道题都需要不超过 200 字的标题");
        if(c.unresolvedContent()!=null && !c.unresolvedContent().isBlank()) throw invalid("请先处理候选题中归属不明确的内容");
        if(c.timeLimitMs()<100 || c.timeLimitMs()>10000 || c.memoryLimitMb()<16 || c.memoryLimitMb()>512 || c.codeLimitBytes()<1024 || c.codeLimitBytes()>262144) throw invalid("运行限制超出允许范围");
        if(c.maxScore()!=null && (c.maxScore().signum()<0 || c.maxScore().compareTo(new BigDecimal("10000"))>0 || c.maxScore().scale()>2)) throw invalid("分数必须在 0 至 10000 之间，最多两位小数");
        for(String s:List.of(value(c.statement()),value(c.referenceAnswer()),value(c.inputDescription()),value(c.outputDescription()),value(c.functionSignature()),value(c.starterCode()),value(c.judgeTemplate())))
            if(s.getBytes(StandardCharsets.UTF_8).length>128*1024) throw invalid("题目字段过长，请拆分材料");
        if(c.samples().size()>30 || c.testCases().size()>100) throw invalid("样例或测试点过多");
        List<Sample> samples=new ArrayList<>();
        for(var s:c.samples()) {if(s.input()==null || s.output()==null) throw invalid("请补齐样例输入与输出"); checkData(s.input());checkData(s.output());checkData(value(s.explanation())); samples.add(new Sample(s.input(),s.output(),s.explanation()));}
        List<PrivateTestCase> tests=new ArrayList<>();
        int testIndex=0;
        for(var t:c.testCases()) {
            if(t.input()==null || t.expectedOutput()==null) throw invalid("请补齐测试输入与输出");checkData(t.input());checkData(t.expectedOutput());
            String stableId=UUID.nameUUIDFromBytes((c.candidateId()+":"+(++testIndex)).getBytes(StandardCharsets.UTF_8)).toString();
            if(t.score()!=null && (t.score().signum()<0 || t.score().scale()>2 || t.score().compareTo(new BigDecimal("10000"))>0)) throw invalid("测试点分数无效");
            try {tests.add(new PrivateTestCase(stableId,t.input(),t.expectedOutput(),PrivateTestCase.Visibility.valueOf(t.visibility()),t.score()==null?BigDecimal.ZERO:t.score(),PrivateTestCase.ComparisonMode.valueOf(t.comparisonMode())));}
            catch(IllegalArgumentException|NullPointerException e) {throw invalid("测试点配置无效");}
        }
        try {ProblemCodePolicy.validateDraft(c.problemType(),c.language(),c.functionSignature(),c.starterCode(),c.judgeTemplate());}
        catch(IllegalArgumentException e) {throw invalid(e.getMessage());}
        return new DraftProblem(c.candidateId(),c.title().strip(),value(c.statement()),value(c.inputDescription()),value(c.outputDescription()),samples,value(c.referenceAnswer()),tests,c.timeLimitMs(),c.memoryLimitMb(),c.codeLimitBytes(),c.maxScore()==null?BigDecimal.ZERO:c.maxScore(),c.problemType(),c.language(),c.functionSignature(),c.starterCode(),c.judgeTemplate());
    }
    private void checkData(String text) {if(text.getBytes(StandardCharsets.UTF_8).length>65536) throw invalid("单个测试输入或输出超过 64 KB");}
    private String value(String s) {return s==null?"":s;}
    private BusinessException invalid(String msg) {return new BusinessException(HttpStatus.BAD_REQUEST,"INVALID_IMPORT",msg);}
}
