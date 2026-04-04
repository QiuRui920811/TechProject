package com.rui.techproject.model;

import java.util.List;

public record MachineRecipe(
        String id,
        String machineId,
        List<String> inputIds,
        List<Integer> inputAmounts,
        String outputId,
        int outputCount,
        int energyCost,
        String guideText,
        boolean allowStackBonus
) {
    public int inputAmount(final int index) {
        return index >= 0 && index < this.inputAmounts.size() ? Math.max(1, this.inputAmounts.get(index)) : 1;
    }
}
