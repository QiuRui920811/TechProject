package com.rui.techproject.model;

import java.util.List;

public record MachineRecipe(
        String id,
        String machineId,
        List<String> inputIds,
        String outputId,
        int outputCount,
        int energyCost,
        String guideText,
        boolean allowStackBonus
) {
}
