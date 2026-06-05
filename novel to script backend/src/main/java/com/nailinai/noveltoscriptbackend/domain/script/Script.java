package com.nailinai.noveltoscriptbackend.domain.script;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record Script(
        String version,
        Meta meta,
        List<Character> characters,
        List<Scene> scenes,
        List<String> notes
) {
    public static final String CURRENT_VERSION = "1.0";

    public Script {
        if (version == null) version = CURRENT_VERSION;
    }
}
