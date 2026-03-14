package com.rui.techproject.model;

public enum VisualTier {
    VANILLA("原版"),
    TECH("科技"),
    ADVANCED("高階"),
    ENDGAME("終局");

    private final String displayName;

    VisualTier(final String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return this.displayName;
    }
}