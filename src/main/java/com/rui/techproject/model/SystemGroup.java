package com.rui.techproject.model;

import org.bukkit.Material;

public enum SystemGroup {
    BOOTSTRAP("開局加工", Material.CRAFTING_TABLE),
    ENERGY("能源", Material.LIGHTNING_ROD),
    PROCESSING("標準加工", Material.PISTON),
    AGRI_BIO("農業 / 生質", Material.WHEAT_SEEDS),
    FIELD_AUTOMATION("戶外採集", Material.IRON_PICKAXE),
    LOGISTICS("物流 / 儲存", Material.HOPPER),
    QUANTUM_PRECISION("精密 / 量子", Material.END_CRYSTAL),
    ENDGAME("終局", Material.NETHER_STAR),
    MEGASTRUCTURE("巨構", Material.BEACON),
    SPECIAL("特殊", Material.ENCHANTED_BOOK);

    private final String displayName;
    private final Material icon;

    SystemGroup(final String displayName, final Material icon) {
        this.displayName = displayName;
        this.icon = icon;
    }

    public String displayName() {
        return this.displayName;
    }

    public Material icon() {
        return this.icon;
    }
}