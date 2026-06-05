package com.nailinai.noveltoscriptbackend.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nailinai.noveltoscriptbackend.config.LlmProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiCompatibleClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleClient.class);

    private final WebClient webClient;
    private final LlmProperties props;

    public OpenAiCompatibleClient(WebClient llmWebClient, LlmProperties props) {
        this.webClient = llmWebClient;
        this.props = props;
        if (props.getApiKey() == null || props.getApiKey().isBlank()) {
            log.warn("LLM API key is empty. Set APP_LLM_API_KEY in .env before generation.");
        }
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        return chat(systemPrompt, userPrompt, 4096);
    }

    @Override
    public String chat(String systemPrompt, String userPrompt, int maxTokens) {
        return chatWithHistory(List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ), maxTokens);
    }

    @Override
    public String chatWithHistory(List<Map<String, String>> messages) {
        return chatWithHistory(messages, 4096);
    }

    @SuppressWarnings("unchecked")
    private String chatWithHistory(List<Map<String, String>> messages, int maxTokens) {
        if (props.getApiKey() == null || props.getApiKey().isBlank()) {
            throw new LlmException("LLM API key is not configured (APP_LLM_API_KEY)");
        }
        ChatRequest body = new ChatRequest(props.getModel(), messages, 0.4, maxTokens);
        try {
            Map<String, Object> response = webClient.post()
                    .uri("/chat/completions")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                    .onErrorResume(e -> Mono.error(new LlmException("LLM call failed: " + e.getMessage(), e)))
                    .block();
            if (response == null) {
                throw new LlmException("LLM returned empty response");
            }
            return extractContent(response);
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("LLM call failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> response) {
        Object choicesObj = response.get("choices");
        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
            throw new LlmException("LLM response missing choices");
        }
        Object first = choices.get(0);
        if (!(first instanceof Map<?, ?> firstMap)) {
            throw new LlmException("LLM choice is malformed");
        }
        Object message = firstMap.get("message");
        if (!(message instanceof Map<?, ?> msgMap)) {
            throw new LlmException("LLM message is malformed");
        }
        Object content = msgMap.get("content");
        if (content == null) {
            throw new LlmException("LLM content is empty");
        }
        return content.toString();
    }

    @Override
    public String name() {
        return "openAiCompatible";
    }

    public record ChatRequest(
            String model,
            List<Map<String, String>> messages,
            @JsonProperty("temperature") Double temperature,
            @JsonProperty("max_tokens") Integer maxTokens
    ) {
        public ChatRequest(String model, List<Map<String, String>> messages) {
            this(model, messages, 0.4, 4096);
        }
    }
}
