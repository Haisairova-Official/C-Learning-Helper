package com.cassist.judge;

import com.cassist.judge.core.*;
import com.cassist.judge.protocol.*;
import com.cassist.problem.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class FunctionProblemTest {
    static final String TEMPLATE = "#include <stdio.h>\n/*__STUDENT_CODE__*/\n"
            + "int main(void){int a,b; if(scanf(\"%d%d\",&a,&b)!=2)return 1; printf(\"%d\\n\",add(a,b));}\n/*PRIVATE_HARNESS_7829*/";
    static ProblemSnapshot snapshot(String type, String language, String template) {
        return new ProblemSnapshot(new StudentProblemView("p", "v", "sum", "implement add", "", "", List.of(),
                1000, 128, 65536, BigDecimal.TEN, type, language, "int add(int a,int b);", "int add(int a,int b){\n}\n"),
                "PRIVATE_REFERENCE_7829", List.of(new PrivateTestCase("t", "2 3\n", "5\n", PrivateTestCase.Visibility.HIDDEN,
                        BigDecimal.TEN, PrivateTestCase.ComparisonMode.EXACT)), template);
    }

    @Test void oldConstructorsAndOldJsonDefaultToProgramC17() {
        var old = new StudentProblemView("p", "v", "t", "s", "", "", List.of(), 1000, 128, 65536, BigDecimal.TEN);
        assertEquals("PROGRAM", old.problemType()); assertEquals("C17", old.language());
        var teacher = JsonMapper.builder().build().readValue("{\"title\":\"old\"}", TeacherProblemView.class);
        assertEquals("PROGRAM", teacher.problemType()); assertEquals("C17", teacher.language());
        assertEquals("", new ProblemSnapshot(old, "private", List.of()).judgeTemplate());
    }

    @Test void functionAssemblerInsertsOriginalCodeExactlyOnceWithoutReference() {
        String student = "int add(int a,int b){return a+b;}";
        var p = snapshot("FUNCTION", "C17", TEMPLATE);
        var compiled = FunctionSourceAssembler.fromAuthorized(p, student, "C17");
        assertTrue(compiled.hasPrivateTemplate());
        assertTrue(compiled.content().contains("#line 1 \"student.c\"\n" + student));
        assertEquals(compiled.content().indexOf(student), compiled.content().lastIndexOf(student));
        assertFalse(compiled.content().contains("PRIVATE_REFERENCE"));
        assertFalse(compiled.content().contains(ProblemCodePolicy.STUDENT_MARKER));
        assertFalse(compiled.toString().contains("PRIVATE_HARNESS"));
        assertFalse(p.toString().contains("PRIVATE_"));
    }

    @Test void publicViewSerializesOnlyPublicFunctionFields() {
        String serialized = JsonMapper.builder().build().writeValueAsString(snapshot("FUNCTION", "CPP17", TEMPLATE).problem());
        assertTrue(serialized.contains("\"problemType\":\"FUNCTION\""));
        assertTrue(serialized.contains("\"language\":\"CPP17\""));
        assertTrue(serialized.contains("functionSignature")); assertTrue(serialized.contains("starterCode"));
        assertFalse(serialized.contains("judgeTemplate")); assertFalse(serialized.contains("referenceAnswer"));
        assertFalse(serialized.contains("PRIVATE_"));
    }

    @Test void rejectsMissingDuplicateMarkerSignatureWrongTypeAndUnknownLanguage() {
        for (String template : List.of("int main(){}", TEMPLATE + ProblemCodePolicy.STUDENT_MARKER)) {
            assertThrows(IllegalArgumentException.class, () -> ProblemCodePolicy.validate("FUNCTION", "C17", "int f();", "", template));
        }
        assertThrows(IllegalArgumentException.class, () -> ProblemCodePolicy.validate("FUNCTION", "C17", "", "", TEMPLATE));
        assertThrows(IllegalArgumentException.class, () -> ProblemCodePolicy.validate("PROGRAM", "C17", "", "", TEMPLATE));
        assertThrows(IllegalArgumentException.class, () -> ProblemCodePolicy.validate("OTHER", "C17", "", "", ""));
        assertThrows(IllegalArgumentException.class, () -> ProblemCodePolicy.validate("PROGRAM", "C99", "", "", ""));
        assertThrows(IllegalArgumentException.class, () -> FunctionSourceAssembler.fromAuthorized(snapshot("FUNCTION", "C17", TEMPLATE), "int add(){return 1;}", "CPP17"));
    }

    @Test void incompleteImportedFunctionCanBeDraftButCannotPublishOrCompile() {
        assertDoesNotThrow(() -> ProblemCodePolicy.validateDraft("FUNCTION", "CPP17", "", "", ""));
        assertThrows(IllegalArgumentException.class, () -> ProblemCodePolicy.validate("FUNCTION", "CPP17", "", "", ""));
        assertThrows(IllegalArgumentException.class, () -> ProblemCodePolicy.validateDraft("FUNCTION", "CPP17", "", "", "not a template"));
        var old = new DraftProblem("c", "t", "", "", "", List.of(), "", List.of(), null, null, null, null);
        assertEquals("PROGRAM", old.problemType()); assertEquals("C17", old.language());
    }

    @Test void cpp17UsesFixedCompilerAndFilenameAndProgramRemainsUnmodified() {
        String source = "int main(){return 0;}";
        var compiled = FunctionSourceAssembler.fromAuthorized(snapshot("PROGRAM", "CPP17", ""), source, "CPP17");
        assertEquals(source, compiled.content()); assertFalse(compiled.hasPrivateTemplate());
        var cmd = (Map<?,?>)((List<?>)GoJudgeRequests.compile(compiled).get("cmd")).getFirst();
        assertEquals("/usr/bin/g++", ((List<?>)cmd.get("args")).getFirst());
        assertTrue(((List<?>)cmd.get("args")).contains("-std=c++17"));
        assertTrue(((Map<?,?>)cmd.get("copyIn")).containsKey("main.cpp"));
    }

    @Test void functionDiagnosticsNeverEchoPrivateCompilerProseEvenAtStudentLocation() {
        String stderr = "student.cpp:2:5: error: conflicts with PRIVATE_HARNESS_7829\n"
                + "judge_template:4:2: error: PRIVATE_REFERENCE_7829\n"
                + "student.cpp:3: error: arbitrary quoted secret\n/path/private: failure\n";
        var transport = new JudgeCoreTest.Fake(new GoJudgeResult("Nonzero Exit Status",1,1L,1L,1L,Map.of("stderr",stderr),Map.of()));
        var p = snapshot("FUNCTION", "CPP17", TEMPLATE);
        var result = new JudgeEngine(transport).judge(FunctionSourceAssembler.fromAuthorized(p, "broken", "CPP17"), JudgeProblemMapper.fromAuthorized(p, "broken"));
        assertEquals(Verdict.COMPILATION_ERROR, result.verdict());
        assertTrue(result.compileMessage().contains("student.cpp:2:5"));
        assertTrue(result.compileMessage().contains("student.cpp:3"));
        assertFalse(result.compileMessage().contains("PRIVATE_")); assertFalse(result.compileMessage().contains("secret"));
        assertFalse(result.compileMessage().contains("judge_template"));
    }
}
