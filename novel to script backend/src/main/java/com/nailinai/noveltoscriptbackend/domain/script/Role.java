package com.nailinai.noveltoscriptbackend.domain.script;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Role {
    PROTAGONIST("protagonist"),
    ANTAGONIST("antagonist"),
    SUPPORTING("supporting"),
    NPC("npc");

    private final String yamlValue;

    Role(String yamlValue) {
        this.yamlValue = yamlValue;
    }

    @JsonValue
    public String yamlValue() {
        return yamlValue;
    }

    @JsonCreator
    public static Role fromYaml(String s) {
        if (s == null) return null;
        for (Role r : values()) {
            if (r.yamlValue.equalsIgnoreCase(s)) return r;
        }
        throw new IllegalArgumentException("Unknown role: " + s);
    }
}
