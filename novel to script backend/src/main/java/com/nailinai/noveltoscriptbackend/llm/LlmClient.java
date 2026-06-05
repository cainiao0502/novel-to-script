package com.nailinai.noveltoscriptbackend.llm;

import java.util.List;
import java.util.Map;

/**
 * 大模型客户端抽象。方便 Mock / 切换实现。
 */
public interface LlmClient {

    /**
     * 单次对话。返回模型输出文本（不含 markdown 围栏）。
     */
    String chat(String systemPrompt, String userPrompt);

    /**
     * 多轮对话历史。
     */
    String chatWithHistory(List<Map<String, String>> messages);

    /** 客户端标识，注入用：{@code @Qualifier("openAiClient")}。 */
    String name();
}
