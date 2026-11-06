package com.cassist.judge;

import com.cassist.judge.core.*;
import com.cassist.judge.http.HttpGoJudgeTransport;
import java.net.URI;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in: every compiler and generated binary runs in the already configured go-judge service. */
@EnabledIfEnvironmentVariable(named="CASSIST_JUDGE_INTEGRATION", matches="true")
class FunctionJudgeIntegrationTest {
    static Stream<Arguments> functions() {
        return Stream.of(CompileLanguage.values()).flatMap(language -> Stream.of(
                Arguments.of(language, "int add(int a,int b){return a+b;}", Verdict.ACCEPTED),
                Arguments.of(language, "int add(int a,int b){return a-b;}", Verdict.WRONG_ANSWER),
                Arguments.of(language, "int add(int a,int b){return broken_identifier;}", Verdict.COMPILATION_ERROR)));
    }
    private HttpGoJudgeTransport transport() {
        var transport = new HttpGoJudgeTransport(URI.create(System.getenv("JUDGE_BASE_URL")),
                System.getenv("JUDGE_TOKEN"), Duration.ofSeconds(35));
        assertTrue(transport.isReady(), "Existing go-judge must enforce seccomp and cpu/memory/pids cgroups");
        return transport;
    }
    @ParameterizedTest @MethodSource("functions")
    void functionCorrectWrongAndCompilationFailure(CompileLanguage language, String student, Verdict expected) {
        var p=FunctionProblemTest.snapshot("FUNCTION",language.name(),FunctionProblemTest.TEMPLATE);
        try(var transport=transport()) {
            var outcome=new JudgeEngine(transport).judge(FunctionSourceAssembler.fromAuthorized(p,student,language.name()),
                    JudgeProblemMapper.fromAuthorized(p,student));
            assertEquals(expected,outcome.verdict(),outcome.errorCode());
            assertFalse(outcome.compileMessage().contains("PRIVATE_"));
            assertFalse(outcome.compileMessage().contains("judge_template"));
            if(expected==Verdict.COMPILATION_ERROR)assertTrue(outcome.compileMessage().contains(language.studentFile()+":1:"));
        }
    }
    @ParameterizedTest @EnumSource(CompileLanguage.class)
    void programRegressionIncludingCpp17StandardLibrary(CompileLanguage language) {
        String source=language==CompileLanguage.C17
                ? "#include <stdio.h>\nint main(void){int a,b;if(scanf(\"%d%d\",&a,&b)!=2)return 1;printf(\"%d\\n\",a+b);}"
                : "#include <cstdio>\n#include <utility>\nint main(){int a,b;if(std::scanf(\"%d%d\",&a,&b)!=2)return 1;auto [x,y]=std::pair{a,b};std::printf(\"%d\\n\",x+y);}";
        var p=FunctionProblemTest.snapshot("PROGRAM",language.name(),"");
        try(var transport=transport()) {
            var outcome=new JudgeEngine(transport).judge(FunctionSourceAssembler.fromAuthorized(p,source,language.name()),
                    JudgeProblemMapper.fromAuthorized(p,source));
            assertEquals(Verdict.ACCEPTED,outcome.verdict(),outcome.errorCode());
        }
    }
}
