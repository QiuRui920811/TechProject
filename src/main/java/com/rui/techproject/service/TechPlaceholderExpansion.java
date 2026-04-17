package com.rui.techproject.service;

import com.rui.techproject.TechMCPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TechPlaceholderExpansion extends PlaceholderExpansion {
    private final TechMCPlugin plugin;

    public TechPlaceholderExpansion(final TechMCPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "techproject";
    }

    @Override
    public @NotNull String getAuthor() {
        return "_RuCat";
    }

    @Override
    public @NotNull String getVersion() {
        return this.plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(final Player player, @NotNull final String params) {
        if (player == null) {
            return "";
        }
        final var uuid = player.getUniqueId();
        final var progressService = this.plugin.getPlayerProgressService();
        final var titleService = this.plugin.getTitleService();
        final var registry = this.plugin.getTechRegistry();

        return switch (params.toLowerCase()) {
            case "title" -> titleService.getPlayerTitle(uuid);
            case "title_raw" -> titleService.getPlayerTitleRaw(uuid);
            case "title_id" -> titleService.getPlayerTitleId(uuid);
            case "title_count" -> String.valueOf(titleService.getPlayerUnlockedTitleCount(uuid));
            case "title_total" -> String.valueOf(titleService.totalTitleCount());
            case "level" -> String.valueOf(progressService.getTechLevel(uuid));
            case "xp" -> String.valueOf(progressService.getTechXpTotal(uuid));
            case "xp_available" -> String.valueOf(progressService.getAvailableTechXp(uuid));
            case "achievements" -> String.valueOf(progressService.unlockedAchievementCount(uuid));
            case "achievements_total" -> String.valueOf(registry.allAchievements().size());
            case "items" -> String.valueOf(progressService.unlockedItemCount(uuid));
            case "machines" -> String.valueOf(progressService.unlockedMachineCount(uuid));
            case "mana" -> {
                final var mana = this.plugin.getManaService();
                yield mana != null ? String.valueOf((int) mana.getCurrentMana(uuid)) : "0";
            }
            case "mana_max" -> {
                final var mana = this.plugin.getManaService();
                yield mana != null ? String.valueOf((int) mana.getMaxMana(uuid)) : "0";
            }
            default -> null;
        };
    }
}
