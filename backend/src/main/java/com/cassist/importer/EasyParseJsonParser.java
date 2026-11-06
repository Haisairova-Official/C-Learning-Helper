package com.cassist.importer;

import com.cassist.importer.ImportPreview.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Structured teacher import. Unrecognized material is kept private for review. */
public final class EasyParseJsonParser {
    public static final String VERSION = "easy-parse-json/0.1";
    private static final Set<String> FIELDS = Set.of("title", "statement", "inputDescription", "outputDescription",
            "samples", "referenceAnswer", "privateNotes", "unresolvedContent", "testCases", "maxScore",
            "timeLimitMs", "memoryLimitMb", "codeLimitBytes", "state", "problemType", "language", "functionSignature", "starterCode", "judgeTemplate");
    private final JsonMapper json = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY).build();

    public ImportPreview parse(String source) {
        if (source == null || source.isBlank()) throw new IllegalArgumentException("JSON 材料不能为空");
        if (source.getBytes(StandardCharsets.UTF_8).length > 1024 * 1024) throw new IllegalArgumentException("JSON 内容不能超过 1 MiB，请分批导入");
        String text = source.strip(); if (text.startsWith("\uFEFF")) text = text.substring(1).strip();
        JsonNode root;
        try { root = json.readTree(text); }
        catch (RuntimeException e) { throw new IllegalArgumentException("JSON 格式无效，请检查引号、逗号和重复字段"); }
        if (root == null || (!root.isArray() && !root.isObject())) throw new IllegalArgumentException("JSON 应为题目对象、题目数组或包含 problems 数组的对象");
        JsonNode rows = root;
        String wrapperUnknown = "";
        String wrapperMetadata = "";
        if (root.isObject() && root.has("problems")) {
            rows = root.get("problems");
            if (!rows.isArray()) throw new IllegalArgumentException("problems 必须是题目数组");
            wrapperUnknown = unknown(root, Set.of("schemaVersion", "title", "source", "problems"), "题目集");
            for (String field : List.of("schemaVersion", "title", "source")) if (root.has(field))
                wrapperMetadata = join(wrapperMetadata, "题目集." + field + ": " + root.get(field));
        }
        List<JsonNode> items = new ArrayList<>();
        if (rows.isArray()) rows.forEach(items::add); else items.add(rows);
        if (items.size() > 100) throw new IllegalArgumentException("每次最多导入 100 道题目");
        SourceLocation location = new SourceLocation(1, Math.max(1, source.split("\n", -1).length));
        List<Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) candidates.add(candidate(items.get(i), i + 1, location, i == 0 ? wrapperUnknown : "", i == 0 ? wrapperMetadata : ""));
        List<Issue> issues = items.isEmpty() ? List.of(new Issue("EMPTY_PACKAGE", Severity.WARNING, "problems", location, "JSON 中没有题目。")) : List.of();
        return new ImportPreview(VERSION, candidates, issues);
    }

    private Candidate candidate(JsonNode node, int index, SourceLocation location, String wrapperUnknown, String wrapperMetadata) {
        if (!node.isObject()) throw new IllegalArgumentException("第 " + index + " 项必须是题目对象");
        List<Issue> issues = new ArrayList<>();
        String title = string(node, "title", "");
        String statement = string(node, "statement", "");
        String type = string(node, "problemType", "PROGRAM");
        String language = string(node, "language", "C17");
        String signature = string(node, "functionSignature", "");
        String starter = string(node, "starterCode", "");
        String template = string(node, "judgeTemplate", "");
        String reference = string(node, "referenceAnswer", "");
        String state = string(node, "state", "DRAFT");
        if (!"DRAFT".equals(state)) throw new IllegalArgumentException("JSON 导入只接受 DRAFT 状态，请先改为草稿；导入不会自动发布");
        String unresolved = join(string(node, "unresolvedContent", ""), join(wrapperUnknown, unknown(node, FIELDS, "题目")));
        if (title.isBlank()) issue(issues, "MISSING_TITLE", "title", location, "请填写题目标题。");
        if (statement.isBlank()) issue(issues, "MISSING_STATEMENT", "statement", location, "缺少题干，请补齐后发布。");
        if (!Set.of("PROGRAM", "FUNCTION").contains(type)) issue(issues, "INVALID_PROBLEM_TYPE", "problemType", location, "题型须修正为 PROGRAM 或 FUNCTION。");
        if (!Set.of("C17", "CPP17").contains(language)) issue(issues, "INVALID_LANGUAGE", "language", location, "语言须修正为 C17 或 CPP17。");
        if ("FUNCTION".equals(type)) {
            if (signature.isBlank()) issue(issues, "MISSING_FUNCTION_SIGNATURE", "functionSignature", location, "函数接口缺失，可保存待修正草稿，发布前须补齐。");
            if (template.isBlank()) issue(issues, "MISSING_JUDGE_TEMPLATE", "judgeTemplate", location, "私有评测主程序缺失，可保存待修正草稿，发布前须补齐。");
            else if (template.split("/\\*__STUDENT_CODE__\\*/", -1).length != 2) issue(issues, "INVALID_JUDGE_TEMPLATE", "judgeTemplate", location, "私有评测主程序须包含且仅包含一次 /*__STUDENT_CODE__*/，修正后再保存。");
        } else if (!template.isBlank()) issue(issues, "UNEXPECTED_JUDGE_TEMPLATE", "judgeTemplate", location, "程序题含有私有评测主程序，请确认题型或将其移至教师私有备注。");
        if (reference.isBlank()) issue(issues, "MISSING_REFERENCE", "referenceAnswer", location, "参考答案缺失，发布前需补齐。");
        List<Sample> samples = new ArrayList<>();
        for (JsonNode s : array(node, "samples")) {
            requireObject(s, "samples");
            String input = string(s, "input", null), output = string(s, "output", null);
            if (input == null || output == null) issue(issues, "UNPAIRED_SAMPLE", "samples", location, "样例输入输出未配对，请修正；空字符串代表明确空内容。");
            samples.add(new Sample(string(s, "key", String.valueOf(samples.size() + 1)), input, output, string(s, "explanation", null)));
            unresolved = join(unresolved, unknown(s, Set.of("key", "input", "output", "explanation"), "样例 " + samples.size()));
        }
        List<TestCase> tests = new ArrayList<>();
        for (JsonNode t : array(node, "testCases")) {
            requireObject(t, "testCases");
            String input = string(t, "input", null), output = string(t, "expectedOutput", null);
            String visibility = string(t, "visibility", "HIDDEN"), comparison = string(t, "comparisonMode", "EXACT");
            BigDecimal score = number(t, "score", null);
            if (input == null || output == null) issue(issues, "UNPAIRED_TEST_CASE", "testCases", location, "测试输入输出未配对，请修正后保存。");
            if (score == null) issue(issues, "MISSING_TEST_SCORE", "testCases", location, "测试点缺少分数，发布前须检查分值。");
            if (!Set.of("HIDDEN", "PUBLIC").contains(visibility) || !Set.of("EXACT", "TOKENS").contains(comparison)) issue(issues, "INVALID_TEST_CONFIGURATION", "testCases", location, "请修正测试点公开性与比较规则。");
            tests.add(new TestCase(string(t, "key", String.valueOf(tests.size() + 1)), input, output, visibility, score, comparison));
            unresolved = join(unresolved, unknown(t, Set.of("key", "input", "expectedOutput", "visibility", "score", "comparisonMode"), "测试点 " + tests.size()));
        }
        if (tests.isEmpty()) issue(issues, "MISSING_TEST_CASES", "testCases", location, "没有计分测试用例，样例不会自动转为测试用例。");
        BigDecimal score = number(node, "maxScore", null);
        if (score == null) issue(issues, "MISSING_SCORE", "maxScore", location, "题目分值缺失，发布前需补齐。");
        if (!unresolved.isBlank()) issue(issues, "UNRESOLVED_JSON_FIELDS", "unresolvedContent", location, "未识别的 JSON 字段已保留在待确认内容中，不会自动归入学生题干。");
        return new Candidate("json-" + index, title, statement, string(node, "inputDescription", ""), string(node, "outputDescription", ""),
                samples, reference, join(string(node, "privateNotes", ""), wrapperMetadata), unresolved, tests, score,
                integer(node, "timeLimitMs", 1000), integer(node, "memoryLimitMb", 128), location, issues,
                !issues.isEmpty(), type, language, signature, starter, template, integer(node, "codeLimitBytes", 65536));
    }

    private static String string(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return fallback;
        if (!value.isString()) throw new IllegalArgumentException("JSON 字段 " + field + " 必须是字符串");
        if (value.asString().getBytes(StandardCharsets.UTF_8).length > 128 * 1024) throw new IllegalArgumentException("JSON 字段 " + field + " 过长");
        return value.asString();
    }
    private static BigDecimal number(JsonNode node, String field, BigDecimal fallback) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return fallback;
        if (!value.isNumber()) throw new IllegalArgumentException("JSON 字段 " + field + " 必须是数字");
        return value.decimalValue();
    }
    private static long integer(JsonNode node, String field, long fallback) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return fallback;
        if (!value.isIntegralNumber() || !value.canConvertToLong()) throw new IllegalArgumentException("JSON 字段 " + field + " 必须是整数");
        return value.longValue();
    }
    private static List<JsonNode> array(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return List.of();
        if (!value.isArray() || value.size() > 100) throw new IllegalArgumentException("JSON 字段 " + field + " 必须为不超过 100 项的数组");
        List<JsonNode> result = new ArrayList<>(); value.forEach(result::add); return result;
    }
    private static void requireObject(JsonNode value, String field) { if (!value.isObject()) throw new IllegalArgumentException("JSON " + field + " 的每项必须是对象"); }
    private static String unknown(JsonNode node, Set<String> known, String prefix) {
        StringBuilder result = new StringBuilder();
        for (String key : node.propertyNames()) if (!known.contains(key)) result.append(prefix).append('.').append(key).append(": ").append(node.get(key)).append('\n');
        return result.toString();
    }
    private static String join(String a, String b) { return a.isEmpty() ? b : b.isEmpty() ? a : a + "\n" + b; }
    private static void issue(List<Issue> issues, String code, String field, SourceLocation location, String message) { issues.add(new Issue(code, Severity.WARNING, field, location, message)); }
}
