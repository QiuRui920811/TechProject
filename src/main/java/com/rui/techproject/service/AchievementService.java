package com.rui.techproject.service;

import com.rui.techproject.TechMCPlugin;
import com.rui.techproject.model.TechTier;
import com.rui.techproject.util.ItemFactoryUtil;
import com.rui.techproject.util.SafeScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class AchievementService {
    private final TechRegistry registry;
    private final PlayerProgressService progressService;
    private final ItemFactoryUtil itemFactory;
    private final SafeScheduler scheduler;

    public AchievementService(final TechMCPlugin plugin,
                              final TechRegistry registry,
                              final PlayerProgressService progressService,
                              final ItemFactoryUtil itemFactory,
                              final SafeScheduler scheduler) {
        this.registry = registry;
        this.progressService = progressService;
        this.itemFactory = itemFactory;
        this.scheduler = scheduler;
    }

    public void evaluate(final UUID uuid) {
        final Set<String> unlocked = new HashSet<>(this.progressService.unlockedAchievements(uuid));
        final int unlockedItemCount = this.progressService.unlockedItemCount(uuid);
        final int unlockedMachineCount = this.progressService.unlockedMachineCount(uuid);

        // ═══ 入門 ═══
        this.tryUnlock(uuid, unlocked, "first_machine", this.progressService.getStat(uuid, "machines_placed") >= 1);
        this.tryUnlock(uuid, unlocked, "newcomer_craft", this.progressService.getStat(uuid, "items_crafted") >= 1);
        this.tryUnlock(uuid, unlocked, "starter_power", this.progressService.getStat(uuid, "generators_placed") >= 1);
        this.tryUnlock(uuid, unlocked, "first_research", this.progressService.getStat(uuid, "research_spent") >= 1);

        // ═══ 加工 ═══
        this.tryUnlock(uuid, unlocked, "first_crush", this.progressService.getStat(uuid, "crusher_cycles") >= 1);
        this.tryUnlock(uuid, unlocked, "first_compress", this.progressService.getStat(uuid, "compressor_cycles") >= 1);
        this.tryUnlock(uuid, unlocked, "first_smelt", this.progressService.getStat(uuid, "smeltery_cycles") >= 1);
        this.tryUnlock(uuid, unlocked, "assembly_starter", this.progressService.getStat(uuid, "assembler_cycles") >= 1);
        this.tryUnlock(uuid, unlocked, "process_milestone_100", this.progressService.getStat(uuid, "total_processed") >= 100);
        this.tryUnlock(uuid, unlocked, "process_milestone_500", this.progressService.getStat(uuid, "total_processed") >= 500);
        this.tryUnlock(uuid, unlocked, "process_milestone_2000", this.progressService.getStat(uuid, "total_processed") >= 2000);
        this.tryUnlock(uuid, unlocked, "recycler_expert", this.progressService.getStat(uuid, "recycled_items") >= 1000);

        // ═══ 能源 ═══
        this.tryUnlock(uuid, unlocked, "energy_beginner", this.progressService.getStat(uuid, "energy_generated") >= 100);
        this.tryUnlock(uuid, unlocked, "energy_500", this.progressService.getStat(uuid, "energy_generated") >= 500);
        this.tryUnlock(uuid, unlocked, "energy_2000", this.progressService.getStat(uuid, "energy_generated") >= 2000);
        this.tryUnlock(uuid, unlocked, "energy_10000", this.progressService.getStat(uuid, "energy_generated") >= 10000);
        this.tryUnlock(uuid, unlocked, "power_grid_master", this.progressService.getStat(uuid, "energy_generated") >= 50000);
        this.tryUnlock(uuid, unlocked, "solar_pioneer", this.progressService.hasMachineUnlocked(uuid, "solar_generator"));
        this.tryUnlock(uuid, unlocked, "generator_collector", this.countGenerators(uuid) >= 3);

        // ═══ 農業 / 烹飪 ═══
        this.tryUnlock(uuid, unlocked, "first_harvest", this.progressService.getStat(uuid, "farm_harvested") >= 1);
        this.tryUnlock(uuid, unlocked, "crop_100", this.progressService.getStat(uuid, "farm_harvested") >= 100);
        this.tryUnlock(uuid, unlocked, "crop_king", this.progressService.getStat(uuid, "farm_harvested") >= 1000);
        this.tryUnlock(uuid, unlocked, "cook_first_meal", this.progressService.getStat(uuid, "meals_cooked") >= 1);
        this.tryUnlock(uuid, unlocked, "cook_10_meals", this.progressService.getStat(uuid, "meals_cooked") >= 10);
        this.tryUnlock(uuid, unlocked, "cook_50_meals", this.progressService.getStat(uuid, "meals_cooked") >= 50);
        this.tryUnlock(uuid, unlocked, "bio_researcher", this.progressService.hasMachineUnlocked(uuid, "gene_splicer"));
        final long cropVarieties = java.util.stream.Stream.of("wheat", "carrots", "potatoes", "beetroots",
                "nether_wart", "sweet_berry_bush", "cocoa", "melon_stem", "pumpkin_stem", "sugar_cane", "bamboo", "cactus")
                .filter(c -> this.progressService.getStat(uuid, "crop_var_" + c) > 0).count();
        this.tryUnlock(uuid, unlocked, "orchard_keeper", cropVarieties >= 5
            || this.progressService.getStat(uuid, "farm_harvested") >= 500);

        // ═══ 物流 ═══
        this.tryUnlock(uuid, unlocked, "first_transfer", this.progressService.getStat(uuid, "items_transferred") >= 1);
        this.tryUnlock(uuid, unlocked, "transfer_500", this.progressService.getStat(uuid, "items_transferred") >= 500);
        this.tryUnlock(uuid, unlocked, "network_architect", this.progressService.getStat(uuid, "items_transferred") >= 2000);
        this.tryUnlock(uuid, unlocked, "upgrade_engineer", this.progressService.hasItemUnlocked(uuid, "range_upgrade"));
        this.tryUnlock(uuid, unlocked, "storage_master", this.progressService.getStat(uuid, "storage_units_placed") >= 5);

        // ═══ 探索 ═══
        this.tryUnlock(uuid, unlocked, "field_operator", this.progressService.hasMachineUnlocked(uuid, "crop_harvester")
            || this.progressService.hasMachineUnlocked(uuid, "vacuum_inlet")
            || this.progressService.hasMachineUnlocked(uuid, "quarry_drill"));
        this.tryUnlock(uuid, unlocked, "ecosystem_engineer", this.progressService.hasMachineUnlocked(uuid, "tree_feller")
            && this.progressService.hasMachineUnlocked(uuid, "fishing_dock")
            && this.progressService.hasMachineUnlocked(uuid, "mob_collector"));
        this.tryUnlock(uuid, unlocked, "first_planet_visit", this.progressService.getStat(uuid, "planets_visited") >= 1
            || this.progressService.getStat(uuid, "planet_ruins_activated") >= 1
            || this.progressService.getStat(uuid, "planetary_samples_collected") >= 1);
        final long uniquePlanets = java.util.stream.Stream.of("aurelia", "cryon", "nyx", "helion", "tempest")
                .filter(p -> this.progressService.getStat(uuid, "planet_visited_" + p) > 0).count();
        this.tryUnlock(uuid, unlocked, "planet_explorer_3", uniquePlanets >= 3);
        this.tryUnlock(uuid, unlocked, "planet_explorer_all", uniquePlanets >= 5);
        this.tryUnlock(uuid, unlocked, "gate_builder", this.progressService.hasMachineUnlocked(uuid, "planetary_gate")
            || this.progressService.hasItemUnlocked(uuid, "gravitic_bearing"));
        this.tryUnlock(uuid, unlocked, "discover_ruins", this.progressService.getStat(uuid, "planet_ruins_activated") >= 1);
        this.tryUnlock(uuid, unlocked, "planet_sample_collector", this.progressService.getStat(uuid, "planetary_samples_collected") >= 20);

        // ═══ 物流（系統） ═══
        this.tryUnlock(uuid, unlocked, "logistics_director", this.progressService.hasMachineUnlocked(uuid, "storage_hub")
            && this.progressService.hasMachineUnlocked(uuid, "filter_router")
            && this.progressService.hasMachineUnlocked(uuid, "splitter_node")
            && this.progressService.hasMachineUnlocked(uuid, "industrial_bus"));
        this.tryUnlock(uuid, unlocked, "cargo_architect", this.progressService.hasMachineUnlocked(uuid, "cargo_input_node")
            && this.progressService.hasMachineUnlocked(uuid, "cargo_output_node")
            && this.progressService.hasMachineUnlocked(uuid, "cargo_manager")
            && this.progressService.hasMachineUnlocked(uuid, "cargo_motor")
            && this.progressService.hasMachineUnlocked(uuid, "trash_node"));

        // ═══ 收集 ═══
        this.tryUnlock(uuid, unlocked, "tech_collector", this.progressService.unlockedItems(uuid).stream()
            .filter(id -> this.registry.getItem(id) != null && this.registry.getItem(id).tier() == TechTier.TIER1)
            .count() >= 8);
        this.tryUnlock(uuid, unlocked, "collect_10", unlockedItemCount >= 10);
        this.tryUnlock(uuid, unlocked, "collect_25", unlockedItemCount >= 25);
        this.tryUnlock(uuid, unlocked, "item_conqueror", unlockedItemCount >= 50);
        this.tryUnlock(uuid, unlocked, "collect_100", unlockedItemCount >= 100);
        this.tryUnlock(uuid, unlocked, "collect_200", unlockedItemCount >= 200);
        this.tryUnlock(uuid, unlocked, "machine_collector_10", unlockedMachineCount >= 10);
        this.tryUnlock(uuid, unlocked, "machine_collector_20", unlockedMachineCount >= 20);
        this.tryUnlock(uuid, unlocked, "all_machines_collector", this.progressService.builtMachineCount(uuid) >= this.registry.allMachines().size());

        // ═══ 終局 ═══
        this.tryUnlock(uuid, unlocked, "quantum_engineer", this.progressService.hasItemUnlocked(uuid, "quantum_chip"));
        this.tryUnlock(uuid, unlocked, "fusion_overlord", this.progressService.hasItemUnlocked(uuid, "fusion_core"));
        this.tryUnlock(uuid, unlocked, "void_pioneer", this.progressService.hasItemUnlocked(uuid, "voidglass_fragment")
            || this.progressService.hasItemUnlocked(uuid, "void_mirror"));
        this.tryUnlock(uuid, unlocked, "singularity_breaker", this.progressService.hasItemUnlocked(uuid, "singularity_casing"));
        this.tryUnlock(uuid, unlocked, "stellar_founder", this.progressService.hasItemUnlocked(uuid, "starsteel_ingot"));
        this.tryUnlock(uuid, unlocked, "omega_theory", this.progressService.hasItemUnlocked(uuid, "omega_matrix"));
        this.tryUnlock(uuid, unlocked, "apex_of_tech", this.progressService.hasItemUnlocked(uuid, "apex_core"));
        this.tryUnlock(uuid, unlocked, "plasma_forge_master", this.progressService.hasMachineUnlocked(uuid, "plasma_refiner"));
        this.tryUnlock(uuid, unlocked, "cosmic_assembler", this.progressService.hasMachineUnlocked(uuid, "warp_assembler"));
        this.tryUnlock(uuid, unlocked, "mega_builder", this.progressService.getStat(uuid, "megastructures_built") >= 1);

        // ═══ 通用 ═══
        this.tryUnlock(uuid, unlocked, "automation_master", this.progressService.getStat(uuid, "max_active_machines") >= 10);
        this.tryUnlock(uuid, unlocked, "full_factory", this.progressService.getStat(uuid, "max_active_machines") >= 20);
        this.tryUnlock(uuid, unlocked, "mega_factory", this.progressService.getStat(uuid, "max_active_machines") >= 50);

        // ═══ 擴展 ═══
        this.tryUnlock(uuid, unlocked, "cryo_specialist", this.progressService.hasMachineUnlocked(uuid, "cryo_distiller"));
        this.tryUnlock(uuid, unlocked, "refinery_master", this.progressService.hasMachineUnlocked(uuid, "refinery")
            && this.progressService.getStat(uuid, "total_processed") >= 500);

        // ═══ 地質 / 祭壇 / 安卓自訂 / 輻射防護 ═══
        this.tryUnlock(uuid, unlocked, "geo_prospector",
            this.progressService.getStat(uuid, "geo_scanned") >= 5);
        this.tryUnlock(uuid, unlocked, "geo_extractor_baron",
            this.progressService.getStat(uuid, "geo_extracted") >= 500);
        this.tryUnlock(uuid, unlocked, "altar_initiate",
            this.progressService.getStat(uuid, "altar_rituals") >= 1);
        this.tryUnlock(uuid, unlocked, "altar_high_priest",
            this.progressService.getStat(uuid, "altar_rituals") >= 10);
        this.tryUnlock(uuid, unlocked, "radiation_safe",
            this.progressService.hasItemUnlocked(uuid, "hazmat_helmet")
                && this.progressService.hasItemUnlocked(uuid, "hazmat_chestplate")
                && this.progressService.hasItemUnlocked(uuid, "hazmat_leggings")
                && this.progressService.hasItemUnlocked(uuid, "hazmat_boots"));
        this.tryUnlock(uuid, unlocked, "cargo_drawer_keeper",
            this.progressService.hasMachineUnlocked(uuid, "cargo_drawer"));
        this.tryUnlock(uuid, unlocked, "android_programmer",
            this.progressService.getStat(uuid, "android_custom_harvested")
                + this.progressService.getStat(uuid, "android_custom_logs")
                + this.progressService.getStat(uuid, "android_custom_kills")
                + this.progressService.getStat(uuid, "android_custom_salvaged") >= 50);

        // ═══ 全成就 ═══
        final boolean allCore = this.registry.allAchievements().stream()
            .map(achievement -> achievement.id())
            .filter(id -> !id.equals("completionist"))
            .allMatch(unlocked::contains);
        this.tryUnlock(uuid, unlocked, "completionist", allCore);
    }

    private int countGenerators(final UUID uuid) {
        int count = 0;
        final String[] generators = {"solar_generator", "coal_generator", "lava_generator",
            "wind_turbine", "nuclear_reactor", "fusion_reactor", "bio_reactor"};
        for (final String gen : generators) {
            if (this.progressService.hasMachineUnlocked(uuid, gen)) {
                count++;
            }
        }
        return count;
    }

    private void tryUnlock(final UUID uuid, final Set<String> unlocked, final String achievementId, final boolean condition) {
        if (!condition || unlocked.contains(achievementId)) {
            return;
        }
        if (!this.progressService.unlockAchievement(uuid, achievementId)) {
            return;
        }
        this.progressService.unlockByRequirement(uuid, achievementId);

        final var definition = this.registry.getAchievement(achievementId);
        if (definition == null) {
            return;
        }

        final Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            return;
        }

        this.scheduler.runEntity(player, () -> {
            final String displayName = this.itemFactory.displayNameForId(achievementId);

            // 廣播成就訊息給所有線上玩家（類似原版進度）
            final Component hoverText = Component.text(definition.description(), NamedTextColor.GRAY);
            final Component broadcast = Component.text(player.getName(), NamedTextColor.AQUA)
                .append(Component.text(" 達成了工業成就 ", NamedTextColor.GREEN))
                .append(Component.text("[" + displayName + "]", NamedTextColor.AQUA, TextDecoration.BOLD)
                    .hoverEvent(HoverEvent.showText(
                        Component.text(displayName, NamedTextColor.AQUA, TextDecoration.BOLD)
                            .append(Component.newline())
                            .append(Component.text(definition.description(), NamedTextColor.GRAY))
                    )));
            for (final Player online : Bukkit.getOnlinePlayers()) {
                online.sendMessage(broadcast);
            }

            if (definition.rewardXp() > 0) {
                this.progressService.addTechXp(uuid, definition.rewardXp());
                player.sendMessage(Component.text("研究 XP +" + definition.rewardXp(), NamedTextColor.LIGHT_PURPLE));
            }
            if (definition.rewardTokens() > 0) {
                player.getInventory().addItem(this.itemFactory.buildEnergyToken(definition.rewardTokens()));
            }
            if (achievementId.equals("completionist")) {
                player.getInventory().addItem(this.itemFactory.buildAchievementBadge(achievementId));
            }
        });
    }
}
