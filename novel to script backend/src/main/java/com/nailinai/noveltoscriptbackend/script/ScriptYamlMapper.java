package com.nailinai.noveltoscriptbackend.script;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.nailinai.noveltoscriptbackend.domain.script.Script;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * YAML ⇄ Script 互转。配置了严格模式：未知字段直接抛错，
 * 与 JSON Schema 的 additionalProperties:false 形成双保险。
 */
@Component
public class ScriptYamlMapper {

    private final ObjectMapper mapper;

    public ScriptYamlMapper() {
        YAMLFactory factory = new YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .disable(YAMLGenerator.Feature.SPLIT_LINES)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);
        this.mapper = new ObjectMapper(factory)
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    public Script fromYaml(String yaml) {
        if (yaml == null || yaml.isBlank()) {
            throw new ScriptException("YAML content is empty");
        }
        try {
            return mapper.readValue(yaml, Script.class);
        } catch (JsonProcessingException e) {
            throw new ScriptException("YAML parse error: " + e.getOriginalMessage(), e);
        } catch (IOException e) {
            throw new ScriptException("YAML read error: " + e.getMessage(), e);
        }
    }

    public String toYaml(Script script) {
        try {
            return mapper.writeValueAsString(script);
        } catch (JsonProcessingException e) {
            throw new ScriptException("YAML write error: " + e.getOriginalMessage(), e);
        }
    }

    /** 暴露底层 ObjectMapper（用于 YAML→JsonNode 转换等场景）。 */
    public ObjectMapper getYamlMapper() {
        return mapper;
    }
}
