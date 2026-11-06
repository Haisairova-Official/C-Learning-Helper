package com.cassist.importer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cassist.importer.ImportPreview.*;

/** Conservative, dependency-free parser. Ambiguous private material is quarantined for review. */
public final class EasyParseTextParser {
    public static final String VERSION = "easy-parse-text/0.1";
    private static final Pattern EXPLICIT_TITLE = Pattern.compile(
            "^\\s*(?:#{1,6}\\s*)?(?:(?:第\\s*[一二三四五六七八九十百0-9]+\\s*题)|(?:(?:题目|Problem)\\s*[:：#]?\\s*\\d+))\\s*[.、:：\\-]?\\s*(.*?)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBERED_TITLE = Pattern.compile(
            "^\\s*(?:#{1,6}\\s*)?(\\d{1,3}(?:-\\d{1,3})?)[.、)）\\s]+\\s*([\\p{L}].*?)\\s*$");
    private static final Pattern LABEL = Pattern.compile("^\\s*(?:#{1,6}\\s*)?(.{1,32}?)(?:[:：]\\s*(.*))?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern VALUE = Pattern.compile("^(\\d+(?:\\.\\d+)?)\\s*([a-zA-Z秒毫秒兆字节分]*)\\s*$");
    private static final Pattern SUSPICIOUS_CODE = Pattern.compile("^\\s*(?:#\\s*include\\b|(?:int|void|signed)\\s+main\\s*\\().*");
    private final int maxInputBytes;
    private final int maxCandidates;
    private final long defaultTimeMs;
    private final long defaultMemoryMb;

    public EasyParseTextParser() { this(1024 * 1024, 200, 1000, 128); }

    public EasyParseTextParser(int maxInputBytes, int maxCandidates, long defaultTimeMs, long defaultMemoryMb) {
        if (maxInputBytes < 1 || maxCandidates < 1 || defaultTimeMs < 1 || defaultMemoryMb < 1)
            throw new IllegalArgumentException("Parser limits must be positive");
        this.maxInputBytes = maxInputBytes;
        this.maxCandidates = maxCandidates;
        this.defaultTimeMs = defaultTimeMs;
        this.defaultMemoryMb = defaultMemoryMb;
    }

    public ImportPreview parse(String source) {
        if (source == null || source.isBlank()) {
            return new ImportPreview(VERSION, List.of(), List.of(issue("EMPTY_DOCUMENT", Severity.ERROR,
                    "source", 1, "没有可解析的内容。")));
        }
        if (source.getBytes(StandardCharsets.UTF_8).length > maxInputBytes)
            throw new IllegalArgumentException("IMPORT_TOO_LARGE");
        String text = source.replace("\r\n", "\n").replace('\r', '\n');
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        String[] lines = text.split("\n", -1);
        List<Candidate> result = new ArrayList<>();
        Builder b = null;
        String fence = null;
        int fenceLine = 0;
        boolean previousBlank = true;
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            int number = index + 1;
            String trimmed = line.strip();
            if (fence != null) {
                if (trimmed.startsWith(fence)) {
                    fence = null;
                } else {
                    b.append(line);
                    if (codeSection(b.section)) b.code.scan(line);
                }
                previousBlank = trimmed.isEmpty();
                continue;
            }
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                if (b == null) b = new Builder(1, number, "待确认题目");
                fence = trimmed.substring(0, 3);
                fenceLine = number;
                if (b.section == Section.STATEMENT) {
                    b.section = Section.QUARANTINE;
                    b.issues.add(issue("UNCLASSIFIED_CODE", Severity.WARNING, "unresolvedContent", number,
                            "未标注用途的代码块已隔离，请确认它属于题干还是参考答案。"));
                }
                previousBlank = false;
                continue;
            }
            boolean canRecognize = b == null || !codeSection(b.section) || b.code.atTopLevel();
            Matcher explicit = EXPLICIT_TITLE.matcher(line);
            Matcher numbered = NUMBERED_TITLE.matcher(line);
            boolean explicitMatch = canRecognize && explicit.matches();
            boolean numberedMatch = canRecognize && previousBlank && numbered.matches();
            if (explicitMatch || numberedMatch) {
                if (b != null) result.add(b.finish(number - 1));
                if (result.size() >= maxCandidates) throw new IllegalArgumentException("TOO_MANY_CANDIDATES");
                String title = explicitMatch ? explicit.group(1) : numbered.group(2);
                b = new Builder(result.size() + 1, number, title.isBlank() ? "待确认题目" : title);
                if (numberedMatch && !explicitMatch)
                    b.issues.add(issue("HEURISTIC_BOUNDARY", Severity.WARNING, "title", number,
                            "根据编号推测题目边界，请确认拆题结果。"));
                previousBlank = false;
                continue;
            }
            if (b == null) {
                if (trimmed.isEmpty()) continue;
                b = new Builder(1, number, "待确认题目");
                b.issues.add(issue("MISSING_TITLE", Severity.WARNING, "title", number, "未识别题目标题，请补充。"));
            }
            Header header = canRecognize ? header(line) : null;
            if (header != null) {
                if (header.section == Section.METADATA) {
                    b.metadata(header.key, header.value, number);
                } else {
                    b.section = header.section;
                    if (header.section == Section.SAMPLE_INPUT || header.section == Section.SAMPLE_OUTPUT
                            || header.section == Section.TEST_INPUT || header.section == Section.TEST_OUTPUT)
                        b.selectPair(header.key, number);
                    if (!header.value.isEmpty()) {
                        b.append(header.value);
                        if (codeSection(header.section)) b.code.scan(header.value);
                    }
                }
            } else {
                if (b.section == Section.STATEMENT && SUSPICIOUS_CODE.matcher(line).matches()) {
                    b.section = Section.QUARANTINE;
                    b.issues.add(issue("UNCLASSIFIED_CODE", Severity.WARNING, "unresolvedContent", number,
                            "发现未标注用途的 C 代码，已从公开题干隔离，请人工确认。"));
                }
                b.append(line);
                if (codeSection(b.section)) b.code.scan(line);
            }
            previousBlank = trimmed.isEmpty();
        }
        if (b != null) {
            if (fence != null) b.issues.add(issue("UNCLOSED_CODE_FENCE", Severity.WARNING, "source", fenceLine,
                    "代码块未闭合，后续内容可能被合并，请检查。"));
            result.add(b.finish(lines.length));
        }
        return new ImportPreview(VERSION, result, List.of());
    }

    private Header header(String line) {
        Matcher m = LABEL.matcher(line);
        if (!m.matches()) return null;
        String label = m.group(1).strip().replaceAll("[\\s_\\-]+", "").toLowerCase(Locale.ROOT);
        String value = m.group(2) == null ? "" : m.group(2);
        String key = "";
        Matcher suffix = Pattern.compile("^(.*?)[（(]?([0-9]+)[）)]?$").matcher(label);
        if (suffix.matches()) { label = suffix.group(1); key = suffix.group(2); }
        Section section = switch (label) {
            case "题干", "题目描述", "题目内容", "description", "problemdescription" -> Section.STATEMENT;
            case "输入格式", "输入说明", "输入描述", "input", "inputformat" -> Section.INPUT_DESCRIPTION;
            case "输出格式", "输出说明", "输出描述", "output", "outputformat" -> Section.OUTPUT_DESCRIPTION;
            case "输入样例", "样例输入", "sampleinput", "inputsample" -> Section.SAMPLE_INPUT;
            case "输出样例", "样例输出", "sampleoutput", "outputsample" -> Section.SAMPLE_OUTPUT;
            case "测试输入", "测试用例输入", "testinput" -> Section.TEST_INPUT;
            case "测试输出", "测试用例输出", "testoutput", "expectedoutput" -> Section.TEST_OUTPUT;
            case "参考答案", "参考代码", "标准答案", "标准代码", "答案", "参考解答", "标准解答", "referenceanswer", "referencecode", "solution", "answer" -> Section.ANSWER;
            case "解析", "题解", "解答", "解法", "解题思路", "教师备注", "答案解析", "explanation", "teachernotes" -> Section.PRIVATE;
            case "时间限制", "timelimit", "内存限制", "memorylimit", "分数", "分值", "score" -> Section.METADATA;
            default -> label.matches(".*(?:答案|解答|题解|解析|解法).*") ? Section.PRIVATE : null;
        };
        return section == null ? null : new Header(section, section == Section.METADATA ? label : key, value);
    }

    private enum Section { STATEMENT, INPUT_DESCRIPTION, OUTPUT_DESCRIPTION, SAMPLE_INPUT, SAMPLE_OUTPUT,
        TEST_INPUT, TEST_OUTPUT, ANSWER, PRIVATE, QUARANTINE, METADATA }
    private static boolean codeSection(Section section) {
        return section == Section.ANSWER || section == Section.QUARANTINE || section == Section.PRIVATE;
    }
    private record Header(Section section, String key, String value) { }

    private final class Builder {
        final String id;
        final int start;
        final String title;
        final Map<Section, StringBuilder> fields = new LinkedHashMap<>();
        final Map<String, Pair> samples = new LinkedHashMap<>();
        final Map<String, Pair> tests = new LinkedHashMap<>();
        final List<Issue> issues = new ArrayList<>();
        final CodeContext code = new CodeContext();
        Section section = Section.STATEMENT;
        Pair activePair;
        BigDecimal score;
        Long timeMs;
        Long memoryMb;

        Builder(int index, int start, String title) { this.id = "candidate-" + index; this.start = start; this.title = title; }

        void selectPair(String key, int line) {
            Map<String, Pair> map = section == Section.TEST_INPUT || section == Section.TEST_OUTPUT ? tests : samples;
            boolean input = section == Section.SAMPLE_INPUT || section == Section.TEST_INPUT;
            if (key.isEmpty()) {
                key = map.isEmpty() ? "1" : map.keySet().stream().reduce((first, second) -> second).orElse("1");
                Pair last = map.get(key);
                if (last != null && (input ? last.hasInput : last.hasOutput)) key = Integer.toString(map.size() + 1);
            }
            activePair = map.computeIfAbsent(key, ignored -> new Pair(line));
            if (input ? activePair.hasInput : activePair.hasOutput)
                issues.add(issue("DUPLICATE_SAMPLE_FIELD", Severity.WARNING, "samples", line, "同一编号的输入或输出出现多次，请确认。"));
            if (input) activePair.hasInput = true; else activePair.hasOutput = true;
        }

        void append(String line) {
            switch (section) {
                case SAMPLE_INPUT, TEST_INPUT -> activePair.input.append(line).append('\n');
                case SAMPLE_OUTPUT, TEST_OUTPUT -> activePair.output.append(line).append('\n');
                default -> fields.computeIfAbsent(section, ignored -> new StringBuilder()).append(line).append('\n');
            }
        }

        void metadata(String key, String raw, int line) {
            Matcher m = VALUE.matcher(raw.strip());
            try {
                if (!m.matches()) throw new IllegalArgumentException();
                BigDecimal number = new BigDecimal(m.group(1));
                String unit = m.group(2).toLowerCase(Locale.ROOT);
                if (number.signum() <= 0) throw new IllegalArgumentException();
                if (key.equals("时间限制") || key.equals("timelimit")) {
                    if (unit.equals("s") || unit.equals("秒")) number = number.multiply(BigDecimal.valueOf(1000));
                    else if (!unit.isEmpty() && !unit.equals("ms") && !unit.equals("毫秒")) throw new IllegalArgumentException();
                    timeMs = number.longValueExact();
                } else if (key.equals("内存限制") || key.equals("memorylimit")) {
                    if (unit.equals("gb")) number = number.multiply(BigDecimal.valueOf(1024));
                    else if (!unit.isEmpty() && !unit.equals("mb") && !unit.equals("mib") && !unit.equals("兆字节")) throw new IllegalArgumentException();
                    memoryMb = number.longValueExact();
                } else {
                    if (!unit.isEmpty() && !unit.equals("分")) throw new IllegalArgumentException();
                    score = number;
                }
            } catch (IllegalArgumentException | ArithmeticException ex) {
                issues.add(issue("INVALID_METADATA", Severity.WARNING, key, line, "数值或单位无法识别，请修正。"));
                fields.computeIfAbsent(Section.QUARANTINE, ignored -> new StringBuilder()).append(key).append('：').append(raw).append('\n');
            }
        }

        Candidate finish(int end) {
            if (field(Section.STATEMENT).isBlank()) issues.add(issue("MISSING_STATEMENT", Severity.WARNING, "statement", start, "缺少题干。"));
            if (field(Section.ANSWER).isBlank()) issues.add(issue("MISSING_REFERENCE", Severity.WARNING, "referenceAnswer", start, "缺少明确标注的参考答案。"));
            if (score == null) issues.add(issue("MISSING_SCORE", Severity.WARNING, "maxScore", start, "缺少题目分值，发布前需补齐。"));
            if (timeMs == null) issues.add(issue("DEFAULT_TIME_LIMIT", Severity.INFO, "timeLimitMs", start, "暂用系统默认时间限制，请在发布前确认。"));
            if (memoryMb == null) issues.add(issue("DEFAULT_MEMORY_LIMIT", Severity.INFO, "memoryLimitMb", start, "暂用系统默认内存限制，请在发布前确认。"));
            List<Sample> sampleList = new ArrayList<>();
            samples.forEach((key, pair) -> { checkPair(pair, "samples"); sampleList.add(new Sample(key,
                    pair.hasInput ? contents(pair.input) : null, pair.hasOutput ? contents(pair.output) : null)); });
            List<TestCase> testList = new ArrayList<>();
            tests.forEach((key, pair) -> { checkPair(pair, "testCases"); testList.add(new TestCase(key,
                    pair.hasInput ? contents(pair.input) : null, pair.hasOutput ? contents(pair.output) : null,
                    "HIDDEN", null, "EXACT")); });
            if (!tests.isEmpty()) issues.add(issue("TEST_CONFIGURATION_REQUIRED", Severity.WARNING, "testCases", start,
                    "显式测试数据暂按隐藏用例保存，需确认分值、公开性与比较规则。"));
            else issues.add(issue("MISSING_TEST_CASES", Severity.WARNING, "testCases", start, "尚无显式计分测试用例，样例不会自动转为计分用例。"));
            return new Candidate(id, title, field(Section.STATEMENT), field(Section.INPUT_DESCRIPTION),
                    field(Section.OUTPUT_DESCRIPTION), sampleList, field(Section.ANSWER), field(Section.PRIVATE),
                    field(Section.QUARANTINE), testList, score, timeMs == null ? defaultTimeMs : timeMs,
                    memoryMb == null ? defaultMemoryMb : memoryMb, new SourceLocation(start, Math.max(start, end)),
                    issues, issues.stream().anyMatch(i -> i.severity() != Severity.INFO));
        }

        String field(Section key) { return contents(fields.getOrDefault(key, new StringBuilder())); }
        void checkPair(Pair pair, String field) {
            if (!pair.hasInput || !pair.hasOutput) issues.add(issue("UNPAIRED_INPUT_OUTPUT", Severity.WARNING,
                    field, pair.line, "输入输出未配对，请检查原文与拆题结果。"));
        }
    }

    private static final class Pair {
        final int line;
        final StringBuilder input = new StringBuilder();
        final StringBuilder output = new StringBuilder();
        boolean hasInput;
        boolean hasOutput;
        Pair(int line) { this.line = line; }
    }

    private static Issue issue(String code, Severity severity, String field, int line, String message) {
        return new Issue(code, severity, field, new SourceLocation(line, line), message);
    }

    /** Preserve interior spaces/newlines. Only remove section separator blank lines. */
    private static String contents(StringBuilder value) {
        return value.toString().replaceAll("^(?:[ \\t]*\\n)+", "").replaceAll("(?:\\n[ \\t]*)+$", "");
    }

    /** Tracks C braces, strings and comments so reference code cannot accidentally become document headings. */
    private static final class CodeContext {
        int braces;
        boolean blockComment;
        char quote;
        boolean atTopLevel() { return braces == 0 && !blockComment && quote == 0; }
        void scan(String line) {
            boolean escaped = false;
            for (int i = 0; i < line.length(); i++) {
                char ch = line.charAt(i);
                char next = i + 1 < line.length() ? line.charAt(i + 1) : 0;
                if (blockComment) { if (ch == '*' && next == '/') { blockComment = false; i++; } continue; }
                if (quote != 0) {
                    if (escaped) escaped = false;
                    else if (ch == '\\') escaped = true;
                    else if (ch == quote) quote = 0;
                    continue;
                }
                if (ch == '/' && next == '/') break;
                if (ch == '/' && next == '*') { blockComment = true; i++; continue; }
                if (ch == '"' || ch == '\'') quote = ch;
                else if (ch == '{') braces++;
                else if (ch == '}') braces = Math.max(0, braces - 1);
            }
        }
    }
}
