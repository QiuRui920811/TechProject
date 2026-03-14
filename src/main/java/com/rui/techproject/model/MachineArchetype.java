package com.rui.techproject.model;

public enum MachineArchetype {
    PROCESSOR("加工機"),
    GENERATOR("發電機"),
    RELAY("中繼 / 物流"),
    STORAGE("儲存 / 匯流"),
    FIELD("現場部署"),
    RESEARCH("研究 / 導引"),
    RITUAL("儀式 / 特殊");

    private final String displayName;

    MachineArchetype(final String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return this.displayName;
    }
}