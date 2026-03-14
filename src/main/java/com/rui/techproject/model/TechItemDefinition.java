package com.rui.techproject.model;

import org.bukkit.Material;

import java.util.List;

public record TechItemDefinition(
        String id,
        String displayName,
        TechTier tier,
        GuideCategory guideCategory,
        SystemGroup systemGroup,
        ItemClass itemClass,
        VisualTier visualTier,
        AcquisitionMode acquisitionMode,
        String family,
        String role,
        Material icon,
        String itemModel,
        String nexoId,
        String headTexture,
        String description,
        String unlockRequirement,
        List<String> useCases
) {
}
