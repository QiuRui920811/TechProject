package com.rui.techproject.model;

import org.bukkit.Material;

import java.util.List;

public record MachineDefinition(
        String id,
        String displayName,
        TechTier tier,
        GuideCategory guideCategory,
        SystemGroup systemGroup,
        MachineArchetype archetype,
        VisualTier visualTier,
        AcquisitionMode acquisitionMode,
        String family,
        String role,
        Material blockMaterial,
        String itemModel,
        String nexoId,
        String headTexture,
        List<String> inputs,
        List<String> outputs,
        int energyPerTick,
        String effectDescription,
        String unlockRequirement
) {
}
