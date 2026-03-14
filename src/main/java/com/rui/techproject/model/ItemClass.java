package com.rui.techproject.model;

public enum ItemClass {
    VANILLA_RESOURCE("原版資源"),
    TECH_MATERIAL("科技材料"),
    CORE_COMPONENT("核心零件"),
    ENDGAME_COMPONENT("終局部件"),
    SPECIAL("特殊物品");

    private final String displayName;

    ItemClass(final String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return this.displayName;
    }
}