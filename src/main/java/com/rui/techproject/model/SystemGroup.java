package com.rui.techproject.model;

public enum SystemGroup {
    BOOTSTRAP("開局加工"),
    ENERGY("能源"),
    PROCESSING("標準加工"),
    AGRI_BIO("農業 / 生質"),
    FIELD_AUTOMATION("戶外採集"),
    LOGISTICS("物流 / 儲存"),
    QUANTUM_PRECISION("精密 / 量子"),
    ENDGAME("終局"),
    MEGASTRUCTURE("巨構"),
    SPECIAL("特殊");

    private final String displayName;

    SystemGroup(final String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return this.displayName;
    }
}