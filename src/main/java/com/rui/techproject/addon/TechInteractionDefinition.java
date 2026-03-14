package com.rui.techproject.addon;

import com.rui.techproject.model.GuideCategory;
import com.rui.techproject.model.TechTier;

import java.util.List;

public record TechInteractionDefinition(
        String id,
        String displayName,
        TechInteractionType type,
        GuideCategory guideCategory,
        TechTier tier,
        String unlockRequirement,
        List<String> steps,
        List<String> effects
) {
}
