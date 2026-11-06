package com.cassist.importer;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class EasyParseTextParserTest {
    private final EasyParseTextParser parser = new EasyParseTextParser();

    @Test void parsesLooseLabelsUnitsAndMultiplePairedSamples() {
        var preview = parser.parse("""
                题目 1：两数求和
                时间限制：1.5 s
                内存限制：64 MB
                分值：10分
                题目描述：读入两个整数，输出它们的和。
                输入格式：两个整数。
                输出格式：一个整数。
                Sample Input 1:
                1 2
                Sample Output 1:
                3
                样例输出2：
                0
                样例输入2：
                -1 1
                参考答案：
                ```c
                int main(void) {
                    return 0;
                }
                ```
                """);
        assertEquals(1, preview.candidates().size());
        var candidate = preview.candidates().getFirst();
        assertEquals("两数求和", candidate.title());
        assertEquals(new BigDecimal("10"), candidate.maxScore());
        assertEquals(1500, candidate.timeLimitMs());
        assertEquals(64, candidate.memoryLimitMb());
        assertEquals("1 2", candidate.samples().getFirst().input());
        assertEquals("-1 1", candidate.samples().get(1).input());
        assertEquals("0", candidate.samples().get(1).output());
        assertEquals("int main(void) {\n    return 0;\n}", candidate.referenceAnswer());
        assertFalse(candidate.statement().contains("return"));
        assertTrue(candidate.testCases().isEmpty());
        assertTrue(candidate.issues().stream().anyMatch(i -> i.code().equals("MISSING_TEST_CASES")));
    }

    @Test void referenceCommentsStringsAndNumbersCannotSplitTheDocument() {
        var result = parser.parse("""
                第1题 打印字符串
                题干：按要求输出。
                参考答案：
                #include <stdio.h>
                int main(void) {
                    /*
                第2题 这仍是代码注释
                输入样例：不是文档字段
                    */
                    puts("7-1 不是标题 { }");
                    return 0;
                }

                第2题 真正的下一题
                题干：输出数字。
                参考答案：int main(void) { return 0; }
                """);
        assertEquals(2, result.candidates().size());
        assertTrue(result.candidates().getFirst().referenceAnswer().contains("第2题 这仍是代码注释"));
        assertTrue(result.candidates().getFirst().samples().isEmpty());
        assertEquals("真正的下一题", result.candidates().get(1).title());
    }

    @Test void quarantinesUnclassifiedCodeInsteadOfPublishingIt() {
        var result = parser.parse("""
                题目1：未分类答案
                题干：计算结果。
                #include <stdio.h>
                int main(void) {
                    /*
                第2题 注释
                    */
                    return 123;
                }
                """);
        assertEquals(1, result.candidates().size());
        var c = result.candidates().getFirst();
        assertEquals("计算结果。", c.statement());
        assertTrue(c.referenceAnswer().isEmpty());
        assertTrue(c.unresolvedContent().contains("return 123;"));
        assertTrue(c.needsReview());
        assertTrue(c.issues().stream().anyMatch(i -> i.code().equals("UNCLASSIFIED_CODE")));
    }

    @Test void keepsAnswerExplanationsPrivateAndMarksIncompleteSamples() {
        var c = parser.parse("""
                题目1：测试
                题干：完成任务。
                样例输入：
                5
                答案解析：先执行某步骤。
                这是教师的私有提示。
                """).candidates().getFirst();
        assertFalse(c.statement().contains("私有"));
        assertTrue(c.privateNotes().contains("私有"));
        assertNull(c.samples().getFirst().output());
        assertTrue(c.issues().stream().anyMatch(i -> i.code().equals("UNPAIRED_INPUT_OUTPUT")));
    }

    @Test void unknownAnswerLabelsAndTheirCodeCommentsStayPrivate() {
        var result = parser.parse("题目1：测试\n题干：完成任务。\n官方解法：\nint main(void) {\n/*\n第2题 注释\n*/\nreturn 1;\n}\n");
        assertEquals(1, result.candidates().size());
        assertEquals("完成任务。", result.candidates().getFirst().statement());
        assertTrue(result.candidates().getFirst().privateNotes().contains("第2题 注释"));
    }

    @Test void distinguishesAnExplicitEmptySampleFromMissingInput() {
        var c = parser.parse("题目1：常量\n题干：输出 1。\n样例输入：\n样例输出：\n1\n").candidates().getFirst();
        assertEquals("", c.samples().getFirst().input());
        assertTrue(c.issues().stream().noneMatch(i -> i.code().equals("UNPAIRED_INPUT_OUTPUT")));
    }

    @Test void acceptsCrLfBomAndNumberedTitlesButRequestsBoundaryReview() {
        var c = parser.parse("\uFEFF7-1 求和\r\n题目描述：计算。\r\n输入样例：1 2\r\n输出样例：3\r\n").candidates().getFirst();
        assertEquals("求和", c.title());
        assertEquals("1 2", c.samples().getFirst().input());
        assertTrue(c.issues().stream().anyMatch(i -> i.code().equals("HEURISTIC_BOUNDARY")));
    }

    @Test void explicitTestsStayPrivateAndUnscoredUntilReviewed() {
        var c = parser.parse("题目1：求和\n题干：求和。\n测试输入1：1 2\n测试输出1：3\n").candidates().getFirst();
        assertEquals("HIDDEN", c.testCases().getFirst().visibility());
        assertNull(c.testCases().getFirst().score());
        assertTrue(c.issues().stream().anyMatch(i -> i.code().equals("TEST_CONFIGURATION_REQUIRED")));
    }

    @Test void rejectsOversizedInputsAndTooManyCandidates() {
        assertThrows(IllegalArgumentException.class, () -> new EasyParseTextParser(4, 2, 1000, 128).parse("中文"));
        assertThrows(IllegalArgumentException.class, () -> new EasyParseTextParser(1000, 1, 1000, 128)
                .parse("题目1：一\n题干：一\n题目2：二\n题干：二"));
        assertEquals("EMPTY_DOCUMENT", parser.parse("  \n").issues().getFirst().code());
    }

    @Test void unclosedFenceAndInvalidLimitsHaveSourceLocations() {
        var c = parser.parse("题目1：测试\n时间限制：许多\n参考答案：\n```c\nint main(void) {}")
                .candidates().getFirst();
        assertTrue(c.issues().stream().anyMatch(i -> i.code().equals("INVALID_METADATA") && i.sourceLocation().startLine() == 2));
        assertTrue(c.issues().stream().anyMatch(i -> i.code().equals("UNCLOSED_CODE_FENCE") && i.sourceLocation().startLine() == 4));
    }

    @Test void parsesTheRepresentativeFixture() throws Exception {
        try (var stream = getClass().getResourceAsStream("/importer/loose-problems.txt")) {
            assertNotNull(stream);
            var result = parser.parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            assertEquals(2, result.candidates().size());
            assertEquals("两数之和", result.candidates().getFirst().title());
            assertTrue(result.candidates().get(1).needsReview());
        }
    }
}
