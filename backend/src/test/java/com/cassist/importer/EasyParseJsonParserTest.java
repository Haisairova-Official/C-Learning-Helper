package com.cassist.importer;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class EasyParseJsonParserTest {
    private final EasyParseJsonParser parser = new EasyParseJsonParser();
    private final JsonMapper json = JsonMapper.builder().build();
    private Map<String, Object> function() {
        return Map.of("title", "函数练习", "statement", "实现公开函数。", "problemType", "FUNCTION", "language", "CPP17",
                "functionSignature", "int calculate(int value);", "starterCode", "int calculate(int value) {\n  // TODO\n}\n",
                "judgeTemplate", "#include <iostream>\n/*__STUDENT_CODE__*/\nint main() { /* private harness */ }\n",
                "referenceAnswer", "private function reference", "samples", List.of(Map.of("input", "", "output", "0\n")),
                "testCases", List.of(Map.of("input", "private input\n", "expectedOutput", "private output\n", "score", 20)));
    }
    @Test void preservesFunctionLanguageAndCodeOnlyInTheirDeclaredFields() {
        var candidate = parser.parse(json.writeValueAsString(function())).candidates().getFirst();
        assertEquals("FUNCTION", candidate.problemType()); assertEquals("CPP17", candidate.language());
        assertEquals(function().get("functionSignature"), candidate.functionSignature());
        assertEquals(function().get("starterCode"), candidate.starterCode());
        assertEquals(function().get("judgeTemplate"), candidate.judgeTemplate());
        assertEquals("private function reference", candidate.referenceAnswer());
        assertEquals("实现公开函数。", candidate.statement());
        assertFalse(candidate.statement().contains("private"));
        assertEquals("", candidate.samples().getFirst().input());
        assertEquals("0\n", candidate.samples().getFirst().output());
        assertEquals("HIDDEN", candidate.testCases().getFirst().visibility());
        assertEquals(new BigDecimal("20"), candidate.testCases().getFirst().score());
    }
    @Test void supportsArrayAndBundleMetadataWithoutPublishingOrQuarantiningKnownMetadata() {
        assertEquals(2, parser.parse(json.writeValueAsString(List.of(function(), function()))).candidates().size());
        var bundle = Map.of("schemaVersion", "1", "title", "课程练习", "source", Map.of("name", "private origin"), "problems", List.of(function()));
        var candidate = parser.parse(json.writeValueAsString(bundle)).candidates().getFirst();
        assertEquals("", candidate.unresolvedContent()); assertTrue(candidate.privateNotes().contains("private origin"));
        assertFalse(candidate.statement().contains("private origin"));
    }
    @Test void missingFunctionFieldsRemainFunctionDraftsWithVisibleReviewIssues() {
        var candidate = parser.parse("{\"title\":\"待补齐函数\",\"problemType\":\"FUNCTION\",\"language\":\"C17\"}").candidates().getFirst();
        assertEquals("FUNCTION", candidate.problemType()); assertEquals("", candidate.judgeTemplate()); assertTrue(candidate.needsReview());
        assertTrue(candidate.issues().stream().anyMatch(i -> i.code().equals("MISSING_FUNCTION_SIGNATURE")));
        assertTrue(candidate.issues().stream().anyMatch(i -> i.code().equals("MISSING_JUDGE_TEMPLATE")));
    }
    @Test void unknownPrivateMaterialStaysUnresolvedAndEmptyInputDiffersFromMissingInput() {
        var candidate = parser.parse("{\"title\":\"练习\",\"secretHarness\":\"private value\",\"samples\":[{\"input\":\"\",\"output\":\"\"},{\"output\":\"x\"}]}").candidates().getFirst();
        assertEquals("PROGRAM", candidate.problemType()); assertEquals("C17", candidate.language());
        assertEquals("", candidate.samples().getFirst().input()); assertNull(candidate.samples().get(1).input());
        assertTrue(candidate.unresolvedContent().contains("private value")); assertFalse(candidate.statement().contains("private value"));
    }
    @Test void rejectsDuplicateKeysTrailingDataInvalidTypesAndOversizedBatches() {
        for (String source : List.of("{\"title\":\"a\",\"title\":\"b\"}", "{} {}", "{\"starterCode\":123}", "{\"problems\":{}}", "{\"samples\":[7]}", "{\"title\":\"x\",\"state\":\"PUBLISHED\"}"))
            assertThrows(IllegalArgumentException.class, () -> parser.parse(source));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(json.writeValueAsString(java.util.Collections.nCopies(101, function()))));
    }
}
