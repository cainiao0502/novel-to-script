package com.nailinai.noveltoscriptbackend.domain.script;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record Scene(
        String sceneId,
        Integer chapter,
        Integer order,
        IntExt intExt,
        String location,
        TimeOfDay timeOfDay,
        List<String> characters,
        String summary,
        List<String> actions,
        List<Dialogue> dialogues,
        List<Voiceover> voiceover,
        List<String> props,
        List<String> sfx,
        String musicCue,
        String cameraHint
) {
}
