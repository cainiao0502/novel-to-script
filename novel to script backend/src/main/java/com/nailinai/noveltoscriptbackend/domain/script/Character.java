package com.nailinai.noveltoscriptbackend.domain.script;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record Character(
        String id,
        String name,
        List<String> aliases,
        String gender,
        String ageRange,
        Role role,
        String appearance,
        String voice
) {
    public static final String NARRATOR_ID = "narrator";

    public boolean isNarrator() {
        return NARRATOR_ID.equals(id);
    }
}
