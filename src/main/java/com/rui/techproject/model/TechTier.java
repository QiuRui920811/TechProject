package com.rui.techproject.model;

import org.bukkit.Material;

public enum TechTier {
    TIER1("第一階：基礎", "第一階", Material.WOODEN_PICKAXE),
    TIER2("第二階：低階", "第二階", Material.COPPER_INGOT),
    TIER3("第三階：中階", "第三階", Material.REPEATER),
    TIER4("第四階：高階", "第四階", Material.NETHER_STAR);

    private final String displayName;
    private final String shortName;
    private final Material icon;

    TechTier(final String displayName, final String shortName, final Material icon) {
        this.displayName = displayName;
        this.shortName = shortName;
        this.icon = icon;
    }

    public String displayName() {
        return this.displayName;
    }

    public String shortName() {
        return this.shortName;
    }

    public Material icon() {
        return this.icon;
    }
}
