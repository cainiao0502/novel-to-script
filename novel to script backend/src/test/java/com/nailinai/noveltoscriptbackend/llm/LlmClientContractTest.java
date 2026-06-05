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
