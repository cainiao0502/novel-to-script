package com.nailinai.noveltoscriptbackend.domain.script;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum IntExt {
    INT("INT"),
    EXT("EXT"),
    INT_EXT("INT-EXT");

    private final String yamlValue;

    IntExt(String yamlValue) {
        this.yamlValue = yamlValue;
    }

    @JsonValue
    public String yamlValue() {
        return yamlValue;
    }

    @JsonCreator
    public static IntExt fromYaml(String s) {
        if (s == null) return null;
        for (IntExt v : values()) {
            if (v.yamlValue.equals(s)) return v;
        }
        throw new IllegalArgumentException("Unknown int_ext: " + s);
    }
}
