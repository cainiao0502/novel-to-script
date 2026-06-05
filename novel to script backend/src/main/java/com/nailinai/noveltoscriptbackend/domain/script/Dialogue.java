package com.nailinai.noveltoscriptbackend.domain.script;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record Dialogue(
        String character,
        String parenthetical,
        String line,
        String emotion
) {
}
