package com.rui.techproject.service;

import com.rui.techproject.TechProjectPlugin;
import com.rui.techproject.model.TechTier;
import com.rui.techproject.util.ItemFactoryUtil;
import com.rui.techproject.util.SafeScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

    public AchievementService(final TechProjectPlugin plugin,
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
        this.tryUnlock(uuid, unlocked, "first_machine", this.progressService.getStat(uuid, "machines_placed") >= 1
            || unlockedMachineCount >= 1);
        this.tryUnlock(uuid, unlocked, "newcomer_craft", this.progressService.getStat(uuid, "items_crafted") >= 1
            || unlockedItemCount >= 1);
        this.tryUnlock(uuid, unlocked, "starter_power", this.progressService.getStat(uuid, "generators_placed") >= 1
            || this.countGenerators(uuid) >= 1);
        this.tryUnlock(uuid, unlocked, "first_research", this.progressService.getStat(uuid, "research_spent") >= 1
            || unlockedItemCount >= 2 || unlockedMachineCount >= 2);

        // ═══ 加工 ═══
        this.tryUnlock(uuid, unlocked, "first_crush", this.progressService.getStat(uuid, "crusher_cycles") >= 1);
        this.tryUnlock(uuid, unlocked, "first_compress", this.progressService.getStat(uuid, "compressor_cycles") >= 1);
        this.tryUnlock(uuid, unlocked, "first_smelt", this.progressService.getStat(uuid, "smeltery_cycles") >= 1
            || this.progressService.hasMachineUnlocked(uuid, "smeltery"));
        this.tryUnlock(uuid, unlocked, "assembly_starter", this.progressService.getStat(uuid, "assembler_cycles") >= 1);
        this.tryUnlock(uuid, unlocked, "process_milestone_100", this.progressService.getStat(uuid, "total_processed") >= 100
            || unlockedItemCount >= 15);
        this.tryUnlock(uuid, unlocked, "process_milestone_500", this.progressService.getStat(uuid, "total_processed") >= 500
            || unlockedItemCount >= 30);
        this.tryUnlock(uuid, unlocked, "process_milestone_2000", this.progressService.getStat(uuid, "total_processed") >= 2000
            || unlockedItemCount >= 60);
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
        this.tryUnlock(uuid, unlocked, "orchard_keeper", this.progressService.getStat(uuid, "crop_varieties") >= 5
            || this.progressService.getStat(uuid, "farm_harvested") >= 500);

        // ═══ 物流 ═══
        this.tryUnlock(uuid, unlocked, "first_transfer", this.progressService.getStat(uuid, "items_transferred") >= 1);
        this.tryUnlock(uuid, unlocked, "transfer_500", this.progressService.getStat(uuid, "items_transferred") >= 500);
        this.tryUnlock(uuid, unlocked, "network_architect", this.progressService.getStat(uuid, "items_transferred") >= 2000);
        this.tryUnlock(uuid, unlocked, "upgrade_engineer", this.progressService.hasItemUnlocked(uuid, "range_upgrade"));
        this.tryUnlock(uuid, unlocked, "storage_master", this.progressService.getStat(uuid, "storage_units_placed") >= 5
            || this.progressService.hasMachineUnlocked(uuid, "storage_hub"));

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
        this.tryUnlock(uuid, unlocked, "planet_explorer_3", this.progressService.getStat(uuid, "planets_visited") >= 3);
        this.tryUnlock(uuid, unlocked, "planet_explorer_all", this.progressService.getStat(uuid, "planets_visited") >= 5);
        this.tryUnlock(uuid, unlocked, "gate_builder", this.progressService.hasMachineUnlocked(uuid, "stargate")
            || this.progressService.hasItemUnlocked(uuid, "stargate_frame"));
        this.tryUnlock(uuid, unlocked, "discover_ruins", this.progressService.getStat(uuid, "planet_ruins_activated") >= 1);
        this.tryUnlock(uuid, unlocked, "planet_sample_collector", this.progressService.getStat(uuid, "planetary_samples_collected") >= 20);

        // ═══ 物流（系統） ═══
        this.tryUnlock(uuid, unlocked, "logistics_director", this.progressService.hasMachineUnlocked(uuid, "storage_hub")
            && this.progressService.hasMachineUnlocked(uuid, "filter_router")
            && this.progressService.hasMachineUnlocked(uuid, "splitter_node")
            && this.progressService.hasMachineUnlocked(uuid, "industrial_bus"));

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
        this.tryUnlock(uuid, unlocked, "all_machines_collector", unlockedMachineCount >= this.registry.allMachines().size());

        // ═══ 終局 ═══
        this.tryUnlock(uuid, unlocked, "quantum_engineer", this.progressService.hasItemUnlocked(uuid, "quantum_chip"));
        this.tryUnlock(uuid, unlocked, "fusion_overlord", this.progressService.hasItemUnlocked(uuid, "fusion_core"));
        this.tryUnlock(uuid, unlocked, "void_pioneer", this.progressService.hasItemUnlocked(uuid, "void_shard"));
        this.tryUnlock(uuid, unlocked, "singularity_breaker", this.progressService.hasItemUnlocked(uuid, "singularity_core"));
        this.tryUnlock(uuid, unlocked, "stellar_founder", this.progressService.hasItemUnlocked(uuid, "stellar_alloy"));
        this.tryUnlock(uuid, unlocked, "omega_theory", this.progressService.hasItemUnlocked(uuid, "cosmic_matrix"));
        this.tryUnlock(uuid, unlocked, "apex_of_tech", this.progressService.hasItemUnlocked(uuid, "omega_core"));
        this.tryUnlock(uuid, unlocked, "plasma_forge_master", this.progressService.hasMachineUnlocked(uuid, "plasma_refinery"));
        this.tryUnlock(uuid, unlocked, "cosmic_assembler", this.progressService.hasMachineUnlocked(uuid, "celestial_assembler"));
        this.tryUnlock(uuid, unlocked, "mega_builder", this.progressService.getStat(uuid, "megastructures_built") >= 1
            || this.progressService.hasMachineUnlocked(uuid, "singularity_compressor")
            || this.progressService.hasMachineUnlocked(uuid, "temporal_engine")
            || this.progressService.hasMachineUnlocked(uuid, "starsteel_foundry"));

        // ═══ 通用 ═══
        this.tryUnlock(uuid, unlocked, "automation_master", this.progressService.getStat(uuid, "max_active_machines") >= 10);
        this.tryUnlock(uuid, unlocked, "full_factory", this.progressService.getStat(uuid, "max_active_machines") >= 20);
        this.tryUnlock(uuid, unlocked, "mega_factory", this.progressService.getStat(uuid, "max_active_machines") >= 50);

        // ═══ 擴展 ═══
        this.tryUnlock(uuid, unlocked, "cryo_specialist", this.progressService.hasMachineUnlocked(uuid, "cryo_distiller"));
        this.tryUnlock(uuid, unlocked, "refinery_master", this.progressService.hasMachineUnlocked(uuid, "refinery")
            && (this.progressService.getStat(uuid, "total_processed") >= 500 || unlockedItemCount >= 30));

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
            player.sendMessage(Component.text("成就解鎖：" + this.itemFactory.displayNameForId(achievementId), NamedTextColor.AQUA));
            player.sendMessage(Component.text(definition.description(), NamedTextColor.GRAY));
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
