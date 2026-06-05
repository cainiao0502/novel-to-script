package com.nailinai.noveltoscriptbackend.domain.script;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record Voiceover(
        String character,
        String line
) {
}
