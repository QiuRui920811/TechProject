package com.rui.techproject.model;

import org.bukkit.Material;

public enum GuideCategory {
    MACHINES("製程 / 製造", Material.PISTON),
    ENERGY("發電 / 儲能", Material.LIGHTNING_ROD),
    MATERIALS("材料 / 零件", Material.IRON_INGOT),
    TOOLS("工具 / 機動", Material.CROSSBOW),
    AGRICULTURE("農業 / 果園", Material.WHEAT),
    FOOD("食物 / 飲品", Material.GOLDEN_CARROT),
    LOGISTICS("物流 / 倉儲", Material.CHEST_MINECART),
    NETWORK("網路 / 自動化", Material.COMPARATOR),
    SPECIAL("特殊 / 探索", Material.ENCHANTED_BOOK);

    private final String displayName;
    private final Material icon;

    GuideCategory(final String displayName, final Material icon) {
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
