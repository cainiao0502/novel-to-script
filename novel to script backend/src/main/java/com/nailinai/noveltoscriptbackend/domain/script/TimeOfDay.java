package com.nailinai.noveltoscriptbackend.domain.script;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TimeOfDay {
    DAY("DAY"),
    NIGHT("NIGHT"),
    DAWN("DAWN"),
    DUSK("DUSK"),
    CONTINUOUS("CONTINUOUS");

    private final String yamlValue;

    TimeOfDay(String yamlValue) {
        this.yamlValue = yamlValue;
    }

    @JsonValue
    public String yamlValue() {
        return yamlValue;
    }

    @JsonCreator
    public static TimeOfDay fromYaml(String s) {
        if (s == null) return null;
        for (TimeOfDay v : values()) {
            if (v.yamlValue.equals(s)) return v;
        }
        throw new IllegalArgumentException("Unknown time_of_day: " + s);
    }
}
