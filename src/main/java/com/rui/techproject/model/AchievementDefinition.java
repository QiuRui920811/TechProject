package com.rui.techproject.model;

public record AchievementDefinition(
        String id,
        String displayName,
        String description,
        int rewardXp,
        int rewardTokens,
        String hint
) {
}
