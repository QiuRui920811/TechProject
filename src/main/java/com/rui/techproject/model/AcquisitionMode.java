package com.rui.techproject.model;

public enum AcquisitionMode {
    ADVANCED_WORKBENCH("進階工作台"),
    MACHINE_ASSEMBLY("機器組裝"),
    FIELD_COLLECTION("世界採集"),
    RITUAL("儀式互動"),
    RESEARCH_REWARD("研究解鎖"),
    UNCRAFTABLE_PREVIEW("僅預覽");

    private final String displayName;

    AcquisitionMode(final String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return this.displayName;
    }
}