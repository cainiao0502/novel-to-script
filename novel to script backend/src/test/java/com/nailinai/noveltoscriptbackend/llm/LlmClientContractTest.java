package com.nailinai.noveltoscriptbackend.llm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 验证 LlmClient 抽象的可替换性（用 mock 实现做契约测试）。 */
class LlmClientContractTest {

    @Test
    void mockClientReturnsConfiguredResponse() {
        LlmClient mock = new MockLlmClient("ok");
        assertEquals("ok", mock.chat("sys", "user"));
        assertEquals("ok", mock.chatWithHistory(List.of(
                Map.of("role", "user", "content", "hi")
        )));
        assertEquals("mock", mock.name());
    }

    @Test
    void mockClientCanSimulateFailure() {
        LlmClient failing = new MockLlmClient(new LlmException("rate limit"));
        assertThrows(LlmException.class, () -> failing.chat("sys", "user"));
    }

    // ─── stripCodeFences 边界 ────────────────────────────────────────────────

    @Test
    void stripFences_keepsRawYamlUntouched() {
        String raw = "version: \"1.0\"\nscenes: []\n";
        assertEquals(raw.strip(), OpenAiCompatibleClient.stripCodeFences(raw));
    }

    @Test
    void stripFences_handlesYamlFence() {
        String raw = "```yaml\nversion: \"1.0\"\nscenes: []\n```\n";
        assertEquals("version: \"1.0\"\nscenes: []", OpenAiCompatibleClient.stripCodeFences(raw));
    }

    @Test
    void stripFences_handlesBareFence() {
        String raw = "```\nversion: \"1.0\"\nscenes: []\n```";
        assertEquals("version: \"1.0\"\nscenes: []", OpenAiCompatibleClient.stripCodeFences(raw));
    }

    @Test
    void stripFences_cutsAtFirstStrayFence() {
        // 模拟 LLM 在 YAML 主体中间塞了 ```，第一个游离围栏起（含同行 + 后续）一律截断
        String raw = "version: \"1.0\"\nscenes:\n  - scene_id: \"s_001\"\n```\n    order: 1\n";
        String result = OpenAiCompatibleClient.stripCodeFences(raw);
        assertFalse(result.contains("```"), "残留 ```: " + result);
        assertFalse(result.contains("order: 1"), "游离围栏之后的尾巴应被截断: " + result);
        assertTrue(result.contains("scene_id"));
    }

    @Test
    void stripFences_cutsTrailingFenceWithProseOnSameLine() {
        // LLM 在 YAML 之后写 ```comment 这种同行
        String raw = "version: \"1.0\"\nscenes: []\n```这是注释\n";
        String result = OpenAiCompatibleClient.stripCodeFences(raw);
        assertFalse(result.contains("```"), "残留 ```: " + result);
        assertTrue(result.startsWith("version:"));
    }

    @Test
    void stripFences_cutsTrailingFenceFollowedByProseLines() {
        // LLM 在 YAML 之后留空行 + ``` + 多行说明
        String raw = "version: \"1.0\"\nscenes: []\n```\n以上是生成的剧本。\n请查收。\n";
        String result = OpenAiCompatibleClient.stripCodeFences(raw);
        assertFalse(result.contains("```"), "残留 ```: " + result);
        assertFalse(result.contains("以上是"), "残留散文: " + result);
        assertTrue(result.startsWith("version:"));
    }

    @Test
    void stripFences_keepsBackticksInsideYamlStrings() {
        // YAML 字符串里的反引号不是围栏，应该原样保留
        String raw = "version: \"1.0\"\ncharacters: []\n  - note: \"use `code` here\"\n";
        String result = OpenAiCompatibleClient.stripCodeFences(raw);
        assertTrue(result.contains("`code`"), "字符串中的反引号被误删: " + result);
    }

    @Test
    void stripFences_handlesNullAndEmpty() {
        assertEquals("", OpenAiCompatibleClient.stripCodeFences(null));
        assertEquals("", OpenAiCompatibleClient.stripCodeFences(""));
        assertEquals("", OpenAiCompatibleClient.stripCodeFences("   \n  "));
    }

    static class MockLlmClient implements LlmClient {
        private final Object reply;
        MockLlmClient(Object reply) { this.reply = reply; }

        @Override
        public String chat(String systemPrompt, String userPrompt) {
            if (reply instanceof Throwable t) {
                if (t instanceof RuntimeException re) throw re;
                throw new RuntimeException(t);
            }
            return reply.toString();
        }

        @Override
        public String chatWithHistory(List<Map<String, String>> messages) {
            return chat(null, null);
        }

        @Override
        public String name() { return "mock"; }
    }
}
