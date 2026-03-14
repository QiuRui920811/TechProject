package com.rui.techproject.model;

import org.bukkit.Material;

public enum TechCategory {
    BASIC("基礎", Material.IRON_NUGGET),
    INTERMEDIATE("中級", Material.COMPARATOR),
    ADVANCED("高級", Material.AMETHYST_SHARD),
    SPECIAL("特殊", Material.ENCHANTED_BOOK);

    private final String displayName;
    private final Material icon;

    TechCategory(final String displayName, final Material icon) {
        this.displayName = displayName;
        this.icon = icon;
    }

    public String displayName() {
        return this.displayName;
    }

    public Material icon() {
        return this.icon;
    }

    public TechTier defaultTier() {
        return switch (this) {
            case BASIC -> TechTier.TIER1;
            case INTERMEDIATE -> TechTier.TIER2;
            case ADVANCED -> TechTier.TIER3;
            case SPECIAL -> TechTier.TIER1;
        };
    }

    public GuideCategory defaultGuideCategory() {
        return switch (this) {
            case BASIC, INTERMEDIATE, ADVANCED -> GuideCategory.MACHINES;
            case SPECIAL -> GuideCategory.SPECIAL;
        };
    }
}
