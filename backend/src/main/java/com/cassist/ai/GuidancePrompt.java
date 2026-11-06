package com.cassist.ai;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import com.cassist.identity.AiReplyStyle;

public final class GuidancePrompt {
    public static final String VERSION = "c-debug-guidance/0.6";
    private GuidancePrompt() { }

    public static String styleInstruction(AiReplyStyle style) {
        return "\n回复风格只调整表达方式，以上保密、排错与 JSON 规则始终有效。\n" + switch (style) {
            case CONCISE -> "风格 CONCISE：简洁直接，优先指出最多两处关键问题，整体尽量控制在 200 个汉字以内。";
            case GUIDED -> "风格 GUIDED：启发引导，解释问题依据后，用一至两个简短问题引导学生自行定位和验证。";
            case TINO -> loadResource("/prompts/tino-persona.txt");
            case DETAILED -> "风格 DETAILED：详细讲解，围绕至多两处已有代码中的问题，展开原因与相关概念，并提出局部验证问题；详尽程度不扩展到算法设计，不复述完整函数声明，整体最多 800 个汉字。";
        };
    }

    public static String load() { return loadResource("/prompts/c-debug-guidance-v2.txt"); }

    private static String loadResource(String path) {
        try (InputStream stream = GuidancePrompt.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing AI system instruction");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read AI system instruction", ex);
        }
    }
}
