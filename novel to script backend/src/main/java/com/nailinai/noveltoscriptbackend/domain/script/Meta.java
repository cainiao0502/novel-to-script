package com.nailinai.noveltoscriptbackend.domain.script;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record Meta(
        String title,
        String sourceNovel,
        Integer episode,
        String genre,
        String logline,
        Integer totalScenes,
        @JsonFormat(shape = JsonFormat.Shape.STRING) java.time.Instant generatedAt
) {
}
