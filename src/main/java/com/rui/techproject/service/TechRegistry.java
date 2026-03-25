package com.rui.techproject.service;

import com.rui.techproject.model.AchievementDefinition;
import com.rui.techproject.model.AcquisitionMode;
import com.rui.techproject.model.GuideCategory;
import com.rui.techproject.model.MachineDefinition;
import com.rui.techproject.model.MachineArchetype;
import com.rui.techproject.model.MachineRecipe;
import com.rui.techproject.model.ItemClass;
import com.rui.techproject.model.SystemGroup;
import com.rui.techproject.model.TechCategory;
import com.rui.techproject.model.TechItemDefinition;
import com.rui.techproject.model.TechTier;
import com.rui.techproject.model.VisualTier;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class TechRegistry {
    private volatile Map<String, TechItemDefinition> items = new LinkedHashMap<>();
    private volatile Map<String, MachineDefinition> machines = new LinkedHashMap<>();
    private volatile Map<String, MachineRecipe> recipes = new LinkedHashMap<>();
    private volatile Map<String, AchievementDefinition> achievements = new LinkedHashMap<>();
    private volatile Map<String, List<MachineRecipe>> recipesByMachine = Map.of();
    private YamlConfiguration metadata;

    public void seedDefaults(final JavaPlugin plugin) {
        if (!this.items.isEmpty()) {
            return;
        }

        this.loadMetadata(plugin, "tech-metadata.yml");
        this.loadDataDrivenContent(plugin, "tech-content-core.yml");
        this.loadDataDrivenContent(plugin, "tech-content-systems.yml");
        this.loadDataDrivenContent(plugin, "tech-content.yml");
        this.loadDataDrivenContent(plugin, "tech-content-expansion.yml");
        this.loadDataDrivenContent(plugin, "tech-content-megastructures.yml");
        this.loadDataDrivenContent(plugin, "tech-content-chickens.yml");
        this.rebuildRecipesByMachineIndex();
    }

    public void reload(final JavaPlugin plugin) {
        final Map<String, TechItemDefinition> newItems = new LinkedHashMap<>();
        final Map<String, MachineDefinition> newMachines = new LinkedHashMap<>();
        final Map<String, MachineRecipe> newRecipes = new LinkedHashMap<>();
        final Map<String, AchievementDefinition> newAchievements = new LinkedHashMap<>();

        final Map<String, TechItemDefinition> oldItems = this.items;
        final Map<String, MachineDefinition> oldMachines = this.machines;
        final Map<String, MachineRecipe> oldRecipes = this.recipes;
        final Map<String, AchievementDefinition> oldAchievements = this.achievements;

        this.items = newItems;
        this.machines = newMachines;
        this.recipes = newRecipes;
        this.achievements = newAchievements;
        this.metadata = null;
        this.seedDefaults(plugin);
    }

    public Collection<TechItemDefinition> allItems() {
        return List.copyOf(this.items.values());
    }

    public Collection<MachineDefinition> allMachines() {
        return List.copyOf(this.machines.values());
    }

    public Collection<MachineRecipe> allRecipes() {
        return List.copyOf(this.recipes.values());
    }

    public Collection<AchievementDefinition> allAchievements() {
        return List.copyOf(this.achievements.values());
    }

    public TechItemDefinition getItem(final String id) {
        return this.items.get(this.normalize(id));
    }

    public MachineDefinition getMachine(final String id) {
        return this.machines.get(this.normalize(id));
    }

    public AchievementDefinition getAchievement(final String id) {
        return this.achievements.get(this.normalize(id));
    }

    public List<TechItemDefinition> getItemsByGuideCategory(final GuideCategory category) {
        return this.items.values().stream().filter(def -> def.guideCategory() == category).toList();
    }

    public List<MachineDefinition> getMachinesByGuideCategory(final GuideCategory category) {
        return this.machines.values().stream().filter(def -> def.guideCategory() == category).toList();
    }

    public List<TechItemDefinition> getItemsByTier(final TechTier tier) {
        return this.items.values().stream().filter(def -> def.tier() == tier).toList();
    }

    public List<MachineDefinition> getMachinesByTier(final TechTier tier) {
        return this.machines.values().stream().filter(def -> def.tier() == tier).toList();
    }

    public List<TechItemDefinition> getItemsByGuideCategoryAndTier(final GuideCategory category, final TechTier tier) {
        return this.items.values().stream().filter(def -> def.guideCategory() == category && def.tier() == tier).toList();
    }

    public List<MachineDefinition> getMachinesByGuideCategoryAndTier(final GuideCategory category, final TechTier tier) {
        return this.machines.values().stream().filter(def -> def.guideCategory() == category && def.tier() == tier).toList();
    }

    public List<TechItemDefinition> getItemsByGuideCategoryAndSystemGroup(final GuideCategory category, final SystemGroup group) {
        return this.items.values().stream().filter(def -> def.guideCategory() == category && def.systemGroup() == group).toList();
    }

    public List<MachineDefinition> getMachinesByGuideCategoryAndSystemGroup(final GuideCategory category, final SystemGroup group) {
        return this.machines.values().stream().filter(def -> def.guideCategory() == category && def.systemGroup() == group).toList();
    }

    public List<SystemGroup> getSystemGroupsForGuideCategory(final GuideCategory category) {
        final java.util.LinkedHashSet<SystemGroup> groups = new java.util.LinkedHashSet<>();
        for (final TechItemDefinition item : this.items.values()) {
            if (item.guideCategory() == category) {
                groups.add(item.systemGroup());
            }
        }
        for (final MachineDefinition machine : this.machines.values()) {
            if (machine.guideCategory() == category) {
                groups.add(machine.systemGroup());
            }
        }
        return new ArrayList<>(groups);
    }

    public List<MachineRecipe> getRecipesForOutput(final String itemId) {
        return this.recipes.values().stream()
                .filter(recipe -> recipe.outputId().equalsIgnoreCase(itemId))
                .toList();
    }

    public List<MachineRecipe> getRecipesForMachine(final String machineId) {
        return this.recipesByMachine.getOrDefault(this.normalize(machineId), List.of());
    }

    public List<TechItemDefinition> itemsUnlockedBy(final String unlockKey) {
        return this.items.values().stream()
                .filter(item -> Objects.equals(this.normalize(item.unlockRequirement()), this.normalize(unlockKey)))
                .toList();
    }

    public List<MachineDefinition> machinesUnlockedBy(final String unlockKey) {
        return this.machines.values().stream()
                .filter(machine -> Objects.equals(this.normalize(machine.unlockRequirement()), this.normalize(unlockKey)))
                .toList();
    }

    public List<String> allIds() {
        final List<String> ids = new ArrayList<>();
        ids.addAll(this.items.keySet());
        ids.addAll(this.machines.keySet());
        return ids;
    }

    public String summaryLine() {
        return "items=" + this.items.size() + ", machines=" + this.machines.size() + ", recipes=" + this.recipes.size() + ", achievements=" + this.achievements.size();
    }

    private void registerItem(final String id,
                              final String displayName,
                              final TechCategory category,
                              final Material icon,
                              final String description,
                              final String unlockRequirement,
                              final List<String> useCases) {
        this.registerItem(
                id,
                displayName,
                category.defaultTier(),
                this.inferGuideCategoryForItem(id, category),
                this.inferSystemGroupForItem(id, category),
                this.inferItemClass(id, category),
                this.inferVisualTierForItem(id, category),
                this.inferAcquisitionModeForItem(id),
                this.inferFamily(id),
                this.inferItemRole(id),
                icon,
                null,
                null,
                null,
                description,
                unlockRequirement,
                useCases
        );
    }

    private void registerItem(final String id,
                              final String displayName,
                              final TechTier tier,
                              final GuideCategory guideCategory,
                              final SystemGroup systemGroup,
                              final ItemClass itemClass,
                              final VisualTier visualTier,
                              final AcquisitionMode acquisitionMode,
                              final String family,
                              final String role,
                              final Material icon,
                              final String itemModel,
                              final String nexoId,
                              final String headTexture,
                              final String description,
                              final String unlockRequirement,
                              final List<String> useCases) {
        this.items.put(this.normalize(id), new TechItemDefinition(id, displayName, tier, guideCategory, systemGroup, itemClass, visualTier, acquisitionMode, family, role, icon, itemModel, nexoId, headTexture, description, unlockRequirement, List.copyOf(useCases)));
    }

    private void registerMachine(final String id,
                                 final String displayName,
                                 final TechCategory category,
                                 final Material blockMaterial,
                                 final List<String> inputs,
                                 final List<String> outputs,
                                 final int energyPerTick,
                                 final String effectDescription,
                                 final String unlockRequirement) {
        this.registerMachine(
                id,
                displayName,
                category.defaultTier(),
                this.inferGuideCategoryForMachine(id, category),
                this.inferSystemGroupForMachine(id, category),
                this.inferMachineArchetype(id),
                this.inferVisualTierForMachine(id, category),
                this.inferAcquisitionModeForMachine(id),
                this.inferFamily(id),
                this.inferMachineRole(id),
                blockMaterial,
                null,
                null,
                null,
                inputs,
                outputs,
                energyPerTick,
                0,
                effectDescription,
                unlockRequirement
        );
    }

    private void registerMachine(final String id,
                                 final String displayName,
                                 final TechTier tier,
                                 final GuideCategory guideCategory,
                                 final SystemGroup systemGroup,
                                 final MachineArchetype archetype,
                                 final VisualTier visualTier,
                                 final AcquisitionMode acquisitionMode,
                                 final String family,
                                 final String role,
                                 final Material blockMaterial,
                                 final String itemModel,
                                 final String nexoId,
                                 final String headTexture,
                                 final List<String> inputs,
                                 final List<String> outputs,
                                 final int energyPerTick,
                                 final int energyGeneration,
                                 final String effectDescription,
                                 final String unlockRequirement) {
        this.machines.put(this.normalize(id), new MachineDefinition(id, displayName, tier, guideCategory, systemGroup, archetype, visualTier, acquisitionMode, family, role, blockMaterial, itemModel, nexoId, headTexture, List.copyOf(inputs), List.copyOf(outputs), energyPerTick, energyGeneration, effectDescription, unlockRequirement));
    }

    private void registerRecipe(final String id,
                                final String machineId,
                                final List<String> inputIds,
                                final String outputId,
                                final int outputCount,
                                final int energyCost,
                                final String guideText) {
        this.recipes.put(this.normalize(id), new MachineRecipe(id, machineId, List.copyOf(inputIds), outputId, Math.max(1, outputCount), energyCost, guideText));
    }

    private void registerAchievement(final String id,
                                     final String displayName,
                                     final String description,
                                     final int rewardXp,
                                     final int rewardTokens,
                                     final String hint) {
        this.achievements.put(this.normalize(id), new AchievementDefinition(id, displayName, description, rewardXp, rewardTokens, hint));
    }

    private void loadDataDrivenContent(final JavaPlugin plugin, final String resourcePath) {
        final File externalFile = new File(plugin.getDataFolder(), resourcePath);
        if (externalFile.isFile()) {
            final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(externalFile);
            this.loadItems(yaml.getConfigurationSection("items"));
            this.loadMachines(yaml.getConfigurationSection("machines"));
            this.loadRecipes(yaml.getConfigurationSection("recipes"));
            this.loadAchievements(yaml.getConfigurationSection("achievements"));
            return;
        }
        final var resource = plugin.getResource(resourcePath);
        if (resource == null) {
            return;
        }

        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8));
        this.loadItems(yaml.getConfigurationSection("items"));
        this.loadMachines(yaml.getConfigurationSection("machines"));
        this.loadRecipes(yaml.getConfigurationSection("recipes"));
        this.loadAchievements(yaml.getConfigurationSection("achievements"));
    }

    private void loadMetadata(final JavaPlugin plugin, final String resourcePath) {
        final File externalFile = new File(plugin.getDataFolder(), resourcePath);
        if (externalFile.isFile()) {
            this.metadata = YamlConfiguration.loadConfiguration(externalFile);
            return;
        }
        final var resource = plugin.getResource(resourcePath);
        if (resource == null) {
            this.metadata = new YamlConfiguration();
            return;
        }
        this.metadata = YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8));
    }

    private void loadItems(final ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (final String key : section.getKeys(false)) {
            final String path = key + ".";
            final TechCategory legacyCategory = this.parseLegacyCategory(section.getString(path + "category", "ADVANCED"));
            this.registerItem(
                    key,
                    section.getString(path + "display-name", this.metadataString("items", key, "display-name", key)),
                    this.parseTier(this.metadataString("items", key, "tier", section.getString(path + "tier", "")), legacyCategory.defaultTier()),
                    this.parseGuideCategory(this.metadataString("items", key, "guide-category", section.getString(path + "guide-category", "")), this.inferGuideCategoryForItem(key, legacyCategory)),
                    this.parseSystemGroup(this.metadataString("items", key, "system-group", section.getString(path + "system-group", "")), this.inferSystemGroupForItem(key, legacyCategory)),
                    this.parseItemClass(this.metadataString("items", key, "item-class", section.getString(path + "item-class", "")), this.inferItemClass(key, legacyCategory)),
                    this.parseVisualTier(this.metadataString("items", key, "visual-tier", section.getString(path + "visual-tier", "")), this.inferVisualTierForItem(key, legacyCategory)),
                    this.parseAcquisitionMode(this.metadataString("items", key, "acquisition-mode", section.getString(path + "acquisition-mode", "")), this.inferAcquisitionModeForItem(key)),
                    this.metadataString("items", key, "family", section.getString(path + "family", this.inferFamily(key))),
                    this.metadataString("items", key, "role", section.getString(path + "role", this.inferItemRole(key))),
                    this.parseMaterial(this.metadataString("items", key, "icon", section.getString(path + "icon", "PAPER")), Material.PAPER, "item:" + key),
                    this.metadataString("items", key, "item-model", section.getString(path + "item-model", "")),
                    this.metadataString("items", key, "nexo-id", section.getString(path + "nexo-id", "")),
                    this.metadataString("items", key, "head-texture", section.getString(path + "head-texture", "")),
                    this.metadataString("items", key, "description", section.getString(path + "description", "")),
                    this.metadataString("items", key, "unlock", section.getString(path + "unlock", "initial")),
                    this.metadataStringList("items", key, "use-cases", section.getStringList(path + "use-cases"))
            );
        }
    }

    private void loadMachines(final ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (final String key : section.getKeys(false)) {
            final String path = key + ".";
            final TechCategory legacyCategory = this.parseLegacyCategory(section.getString(path + "category", "ADVANCED"));
            this.registerMachine(
                    key,
                    section.getString(path + "display-name", this.metadataString("machines", key, "display-name", key)),
                    this.parseTier(this.metadataString("machines", key, "tier", section.getString(path + "tier", "")), legacyCategory.defaultTier()),
                    this.parseGuideCategory(this.metadataString("machines", key, "guide-category", section.getString(path + "guide-category", "")), this.inferGuideCategoryForMachine(key, legacyCategory)),
                    this.parseSystemGroup(this.metadataString("machines", key, "system-group", section.getString(path + "system-group", "")), this.inferSystemGroupForMachine(key, legacyCategory)),
                    this.parseMachineArchetype(this.metadataString("machines", key, "machine-archetype", section.getString(path + "machine-archetype", "")), this.inferMachineArchetype(key)),
                    this.parseVisualTier(this.metadataString("machines", key, "visual-tier", section.getString(path + "visual-tier", "")), this.inferVisualTierForMachine(key, legacyCategory)),
                    this.parseAcquisitionMode(this.metadataString("machines", key, "acquisition-mode", section.getString(path + "acquisition-mode", "")), this.inferAcquisitionModeForMachine(key)),
                    this.metadataString("machines", key, "family", section.getString(path + "family", this.inferFamily(key))),
                    this.metadataString("machines", key, "role", section.getString(path + "role", this.inferMachineRole(key))),
                    this.parseMaterial(this.metadataString("machines", key, "block", section.getString(path + "block", "IRON_BLOCK")), Material.IRON_BLOCK, "machine:" + key),
                    this.metadataString("machines", key, "item-model", section.getString(path + "item-model", "")),
                    this.metadataString("machines", key, "nexo-id", section.getString(path + "nexo-id", "")),
                    this.metadataString("machines", key, "head-texture", section.getString(path + "head-texture", "")),
                    this.metadataStringList("machines", key, "inputs", section.getStringList(path + "inputs")),
                    this.metadataStringList("machines", key, "outputs", section.getStringList(path + "outputs")),
                    this.metadataInt("machines", key, "energy-per-tick", section.getInt(path + "energy-per-tick", 0)),
                    this.metadataInt("machines", key, "energy-generation", section.getInt(path + "energy-generation", 0)),
                    this.metadataString("machines", key, "effect", section.getString(path + "effect", "")),
                    this.metadataString("machines", key, "unlock", section.getString(path + "unlock", "initial"))
            );
        }
    }

    private void loadRecipes(final ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (final String key : section.getKeys(false)) {
            final String path = key + ".";
            this.registerRecipe(
                    key,
                    section.getString(path + "machine", ""),
                    section.getStringList(path + "inputs"),
                    section.getString(path + "output", ""),
                    section.getInt(path + "count", 1),
                    section.getInt(path + "energy", 1),
                    section.getString(path + "guide", "")
            );
        }
    }

    private void loadAchievements(final ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (final String key : section.getKeys(false)) {
            final String path = key + ".";
            this.registerAchievement(
                    key,
                    section.getString(path + "display-name", key),
                    section.getString(path + "description", ""),
                    section.getInt(path + "reward-xp", 0),
                    section.getInt(path + "reward-tokens", 0),
                    section.getString(path + "hint", "")
            );
        }
    }

    private String normalize(final String input) {
        return input == null ? "" : input.toLowerCase(Locale.ROOT).trim();
    }

    private void rebuildRecipesByMachineIndex() {
        final Map<String, List<MachineRecipe>> index = new LinkedHashMap<>();
        for (final MachineRecipe recipe : this.recipes.values()) {
            index.computeIfAbsent(this.normalize(recipe.machineId()), k -> new ArrayList<>()).add(recipe);
        }
        index.replaceAll((k, v) -> Collections.unmodifiableList(v));
        this.recipesByMachine = Collections.unmodifiableMap(index);
    }

    private String metadataString(final String section, final String id, final String key, final String fallback) {
        if (this.metadata == null) {
            return fallback;
        }
        return this.metadata.getString(section + "." + id + "." + key, fallback);
    }

    private List<String> metadataStringList(final String section, final String id, final String key, final List<String> fallback) {
        if (this.metadata == null) {
            return fallback;
        }
        final String path = section + "." + id + "." + key;
        if (!this.metadata.contains(path)) {
            return fallback;
        }
        final List<String> values = this.metadata.getStringList(path);
        return values == null || values.isEmpty() ? fallback : values;
    }

    private int metadataInt(final String section, final String id, final String key, final int fallback) {
        if (this.metadata == null) {
            return fallback;
        }
        final String path = section + "." + id + "." + key;
        return this.metadata.contains(path) ? this.metadata.getInt(path, fallback) : fallback;
    }

    private TechCategory parseLegacyCategory(final String value) {
        try {
            return TechCategory.valueOf((value == null || value.isBlank() ? "ADVANCED" : value).trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException ignored) {
            return TechCategory.ADVANCED;
        }
    }

    private TechTier parseTier(final String value, final TechTier fallback) {
        try {
            return TechTier.valueOf((value == null || value.isBlank() ? fallback.name() : value).trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private GuideCategory parseGuideCategory(final String value, final GuideCategory fallback) {
        try {
            return GuideCategory.valueOf((value == null || value.isBlank() ? fallback.name() : value).trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private SystemGroup parseSystemGroup(final String value, final SystemGroup fallback) {
        try {
            return SystemGroup.valueOf((value == null || value.isBlank() ? fallback.name() : value).trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private ItemClass parseItemClass(final String value, final ItemClass fallback) {
        try {
            return ItemClass.valueOf((value == null || value.isBlank() ? fallback.name() : value).trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private MachineArchetype parseMachineArchetype(final String value, final MachineArchetype fallback) {
        try {
            return MachineArchetype.valueOf((value == null || value.isBlank() ? fallback.name() : value).trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private VisualTier parseVisualTier(final String value, final VisualTier fallback) {
        try {
            return VisualTier.valueOf((value == null || value.isBlank() ? fallback.name() : value).trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private AcquisitionMode parseAcquisitionMode(final String value, final AcquisitionMode fallback) {
        try {
            return AcquisitionMode.valueOf((value == null || value.isBlank() ? fallback.name() : value).trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private GuideCategory inferGuideCategoryForItem(final String id, final TechCategory legacyCategory) {
        final String normalized = this.normalize(id);
        if (legacyCategory == TechCategory.SPECIAL || normalized.contains("book") || normalized.contains("badge") || normalized.contains("token")) {
            return GuideCategory.SPECIAL;
        }
        if (normalized.contains("sickle") || normalized.contains("spade") || normalized.contains("grapple")
                || normalized.contains("thruster") || normalized.contains("jetpack") || normalized.contains("blade")
                || normalized.contains("launcher")
                || normalized.contains("staff") || normalized.contains("wand") || normalized.contains("orb")
                || normalized.contains("lance") || normalized.contains("mirror") || normalized.contains("dilator")
                || normalized.contains("beacon") || normalized.contains("scepter")
                || normalized.contains("talisman")
                || normalized.contains("helmet") || normalized.contains("chestplate") || normalized.contains("leggings")
                || normalized.contains("boots") || normalized.contains("crown") || normalized.contains("cuirass")
                || normalized.contains("greaves") || normalized.contains("sabatons")) {
            return GuideCategory.TOOLS;
        }
        if (normalized.contains("berry") || normalized.contains("tea") || normalized.contains("ration") || normalized.contains("nutrition")
                || normalized.contains("meat") || normalized.contains("drink") || normalized.contains("fruit") || normalized.contains("nectar")
                || normalized.contains("juice") || normalized.contains("puree") || normalized.contains("fig") || normalized.contains("apple")
                || normalized.contains("plum") || normalized.contains("meal")
                || normalized.contains("tomato") || normalized.contains("cabbage") || normalized.contains("corn")
                || normalized.contains("onion") || normalized.contains("cherry") || normalized.contains("lemon")
                || normalized.contains("peach") || normalized.contains("pear") || normalized.contains("orange")
                || normalized.contains("grape") || normalized.contains("banana") || normalized.contains("mango")
                || normalized.contains("coconut") || normalized.contains("melon") || normalized.contains("kiwi")
                || normalized.contains("garlic") || normalized.contains("lettuce") || normalized.contains("pepper")
                || normalized.contains("spinach") || normalized.contains("radish") || normalized.contains("potato")
                || normalized.contains("mushroom") || normalized.contains("citrus")
                || normalized.contains("smoothie") || normalized.contains("cocoa") || normalized.contains("ale")
                || normalized.contains("latte") || normalized.contains("espresso") || normalized.contains("milkshake")
                || normalized.contains("pie") || normalized.contains("bread") || normalized.contains("salad")
                || normalized.contains("stew") || normalized.contains("lemonade") || normalized.contains("cobbler")
                || normalized.contains("crisp") || normalized.contains("hotpot") || normalized.contains("sorbet")
                || normalized.contains("fizz") || normalized.contains("rice") || normalized.contains("soup")
                || normalized.contains("noodle") || normalized.contains("risotto") || normalized.contains("curry")
                || normalized.contains("sushi") || normalized.contains("ramen") || normalized.contains("pizza")
                || normalized.contains("dumpling") || normalized.contains("wrap") || normalized.contains("burrito")
                || normalized.contains("cake") || normalized.contains("ice_cream") || normalized.contains("donut")
                || normalized.contains("muffin") || normalized.contains("brownie") || normalized.contains("cheesecake")
                || normalized.contains("pancake") || normalized.contains("roll") || normalized.contains("toast")
                || normalized.contains("grilled") || normalized.contains("roasted") || normalized.contains("chocolate")
                || normalized.contains("cinnamon") || normalized.contains("honey") || normalized.contains("cheese")
                || normalized.contains("stuffed") || normalized.contains("sparkling") || normalized.contains("glaze")
                || normalized.contains("gel") || normalized.contains("veggie") || normalized.contains("vegetable")
                || normalized.contains("fish") || normalized.contains("breakfast")) {
            return GuideCategory.FOOD;
        }
        if (normalized.contains("seed") || normalized.contains("crop") || normalized.contains("sapling") || normalized.contains("bloom")
                || normalized.contains("spore") || normalized.contains("root") || normalized.contains("fern") || normalized.contains("soybean")
                || normalized.contains("fertilizer") || normalized.contains("hydro") || normalized.contains("orchard") || normalized.contains("agri")) {
            return GuideCategory.AGRICULTURE;
        }
        if (normalized.contains("energy") || normalized.contains("battery") || normalized.contains("capacitor") || normalized.contains("fusion") || normalized.contains("flux") || normalized.contains("coolant")) {
            return GuideCategory.ENERGY;
        }
        if (normalized.contains("tube") || normalized.contains("router") || normalized.contains("splitter") || normalized.contains("bus")
                || normalized.contains("storage") || normalized.contains("crate") || normalized.contains("filter") || normalized.contains("vacuum")) {
            return GuideCategory.LOGISTICS;
        }
        if (normalized.contains("control") || normalized.contains("processor") || normalized.contains("sensor") || normalized.contains("matrix")
                || normalized.contains("node") || normalized.contains("cable") || normalized.contains("logic") || normalized.contains("relay")
                || normalized.contains("network") || normalized.contains("routing") || normalized.contains("server") || normalized.contains("data")) {
            return GuideCategory.NETWORK;
        }
        if (normalized.contains("upgrade") || normalized.contains("module") || normalized.contains("drone") || normalized.contains("android") || normalized.contains("harvest")
                || normalized.contains("quarry") || normalized.contains("tree") || normalized.contains("mob") || normalized.contains("fishing")
                || normalized.contains("logging") || normalized.contains("bait") || normalized.contains("collector")) {
            return GuideCategory.NETWORK;
        }
        if (normalized.contains("shield") || normalized.contains("armor") || normalized.contains("turret") || normalized.contains("ward")) {
            return GuideCategory.SPECIAL;
        }
        return GuideCategory.MATERIALS;
    }

    private SystemGroup inferSystemGroupForItem(final String id, final TechCategory legacyCategory) {
        final String normalized = this.normalize(id);
        if (legacyCategory == TechCategory.SPECIAL) {
            return SystemGroup.SPECIAL;
        }
        if (normalized.contains("fusion") || normalized.contains("omega") || normalized.contains("singularity") || normalized.contains("antimatter") || normalized.contains("void") || normalized.contains("entropy") || normalized.contains("chrono")) {
            return SystemGroup.ENDGAME;
        }
        if (normalized.contains("warp") || normalized.contains("starsteel") || normalized.contains("continuum") || normalized.contains("anchor") || normalized.contains("relic") || normalized.contains("event_horizon")) {
            return SystemGroup.MEGASTRUCTURE;
        }
        if (normalized.contains("energy") || normalized.contains("battery") || normalized.contains("capacitor") || normalized.contains("flux") || normalized.contains("coolant") || normalized.contains("reactor")) {
            return SystemGroup.ENERGY;
        }
        if (normalized.contains("seed") || normalized.contains("crop") || normalized.contains("bio") || normalized.contains("fertilizer") || normalized.contains("hydro")) {
            return SystemGroup.AGRI_BIO;
        }
        if (normalized.contains("tube") || normalized.contains("router") || normalized.contains("splitter") || normalized.contains("bus") || normalized.contains("storage") || normalized.contains("crate") || normalized.contains("relay")) {
            return SystemGroup.LOGISTICS;
        }
        if (normalized.contains("quantum") || normalized.contains("field") || normalized.contains("matrix") || normalized.contains("processor") || normalized.contains("photon") || normalized.contains("archive") || normalized.contains("observatory") || normalized.contains("android")) {
            return SystemGroup.QUANTUM_PRECISION;
        }
        return legacyCategory == TechCategory.BASIC ? SystemGroup.BOOTSTRAP : SystemGroup.PROCESSING;
    }

    private ItemClass inferItemClass(final String id, final TechCategory legacyCategory) {
        final String normalized = this.normalize(id);
        if (legacyCategory == TechCategory.SPECIAL || normalized.contains("book") || normalized.contains("token") || normalized.contains("badge")) {
            return ItemClass.SPECIAL;
        }
        if (normalized.contains("core") || normalized.contains("chip") || normalized.contains("processor") || normalized.contains("unit") || normalized.contains("matrix") || normalized.contains("component") || normalized.contains("frame")) {
            return ItemClass.CORE_COMPONENT;
        }
        if (normalized.contains("omega") || normalized.contains("singularity") || normalized.contains("antimatter") || normalized.contains("graviton") || normalized.contains("void") || normalized.contains("stellar") || normalized.contains("entropy") || normalized.contains("chrono")) {
            return ItemClass.ENDGAME_COMPONENT;
        }
        return ItemClass.TECH_MATERIAL;
    }

    private VisualTier inferVisualTierForItem(final String id, final TechCategory legacyCategory) {
        return switch (this.inferItemClass(id, legacyCategory)) {
            case SPECIAL, TECH_MATERIAL -> legacyCategory == TechCategory.ADVANCED ? VisualTier.ADVANCED : VisualTier.TECH;
            case CORE_COMPONENT -> legacyCategory == TechCategory.ADVANCED ? VisualTier.ADVANCED : VisualTier.TECH;
            case ENDGAME_COMPONENT -> VisualTier.ENDGAME;
            case VANILLA_RESOURCE -> VisualTier.VANILLA;
        };
    }

    private AcquisitionMode inferAcquisitionModeForItem(final String id) {
        final String normalized = this.normalize(id);
        if (normalized.contains("book") || normalized.contains("token") || normalized.contains("badge")) {
            return AcquisitionMode.RESEARCH_REWARD;
        }
        return AcquisitionMode.MACHINE_ASSEMBLY;
    }

    private String inferItemRole(final String id) {
        final String normalized = this.normalize(id);
        if (normalized.contains("sickle") || normalized.contains("spade") || normalized.contains("grapple")
                || normalized.contains("thruster") || normalized.contains("jetpack") || normalized.contains("blade")
                || normalized.contains("launcher")) {
            return "utility-tool";
        }
        if (normalized.contains("dust") || normalized.contains("slurry") || normalized.contains("ingot") || normalized.contains("plate") || normalized.contains("wire")) {
            return "processing-material";
        }
        if (normalized.contains("chip") || normalized.contains("core") || normalized.contains("unit") || normalized.contains("component")) {
            return "assembly-component";
        }
        if (normalized.contains("upgrade") || normalized.contains("module")) {
            return "upgrade-part";
        }
        if (normalized.contains("recycled") || normalized.contains("scrap") || normalized.contains("salvage")) {
            return "recycled-material";
        }
        if (normalized.contains("oil") || normalized.contains("refined") || normalized.contains("distill") || normalized.contains("extract")) {
            return "refined-product";
        }
        if (normalized.contains("bio") || normalized.contains("resin") || normalized.contains("fiber") || normalized.contains("compost") || normalized.contains("fertilizer")) {
            return "bio-product";
        }
        if (normalized.contains("juice") || normalized.contains("food") || normalized.contains("meal") || normalized.contains("peach") || normalized.contains("berry")) {
            return "food-product";
        }
        return "general-item";
    }

    private GuideCategory inferGuideCategoryForMachine(final String id, final TechCategory legacyCategory) {
        final String normalized = this.normalize(id);
        if (legacyCategory == TechCategory.SPECIAL) {
            return GuideCategory.SPECIAL;
        }
        if (normalized.equals("bio_lab") || normalized.equals("coolant_mixer") || normalized.equals("biosynth_vat")) {
            return GuideCategory.FOOD;
        }
        if (normalized.contains("farm") || normalized.contains("greenhouse") || normalized.contains("bio") || normalized.contains("gene")
                || normalized.contains("vat") || normalized.contains("orchard") || normalized.contains("arboretum")) {
            return GuideCategory.AGRICULTURE;
        }
        if (normalized.contains("kitchen") || normalized.contains("brew") || normalized.contains("distiller") || normalized.contains("cooker")) {
            return GuideCategory.FOOD;
        }
        if (normalized.contains("generator") || normalized.contains("reactor") || normalized.contains("battery") || normalized.contains("energy") || normalized.contains("solar") || normalized.contains("turbine") || normalized.contains("cable")) {
            return GuideCategory.ENERGY;
        }
        if (normalized.contains("tube") || normalized.contains("router") || normalized.contains("splitter") || normalized.contains("bus")
                || normalized.contains("storage") || normalized.contains("filter") || normalized.contains("vacuum")) {
            return GuideCategory.LOGISTICS;
        }
        if (normalized.contains("node") || normalized.contains("relay") || normalized.contains("research") || normalized.contains("processor")
                || normalized.contains("server") || normalized.contains("control") || normalized.contains("network")) {
            return GuideCategory.NETWORK;
        }
        if (normalized.contains("drone") || normalized.contains("android") || normalized.contains("quarry") || normalized.contains("tree") || normalized.contains("mob")
                || normalized.contains("fishing") || normalized.contains("gate") || normalized.contains("harvester") || normalized.contains("collector")) {
            return GuideCategory.NETWORK;
        }
        if (normalized.contains("turret") || normalized.contains("shield") || normalized.contains("barrier") || normalized.contains("ward")) {
            return GuideCategory.SPECIAL;
        }
        return GuideCategory.MACHINES;
    }

    private SystemGroup inferSystemGroupForMachine(final String id, final TechCategory legacyCategory) {
        final String normalized = this.normalize(id);
        if (legacyCategory == TechCategory.SPECIAL) {
            return SystemGroup.SPECIAL;
        }
        if (normalized.contains("generator") || normalized.contains("reactor") || normalized.contains("battery") || normalized.contains("energy") || normalized.contains("solar") || normalized.contains("turbine") || normalized.contains("cable")) {
            return SystemGroup.ENERGY;
        }
        if (normalized.contains("node") || normalized.contains("tube") || normalized.contains("router") || normalized.contains("splitter") || normalized.contains("bus") || normalized.contains("storage")) {
            return SystemGroup.LOGISTICS;
        }
        if (normalized.contains("farm") || normalized.contains("greenhouse") || normalized.contains("bio") || normalized.contains("gene") || normalized.contains("vat")) {
            return SystemGroup.AGRI_BIO;
        }
        if (normalized.contains("harvester") || normalized.contains("vacuum") || normalized.contains("tree") || normalized.contains("mob") || normalized.contains("fishing") || normalized.contains("quarry")) {
            return SystemGroup.FIELD_AUTOMATION;
        }
        if (normalized.contains("quantum") || normalized.contains("field") || normalized.contains("crystal") || normalized.contains("matter") || normalized.contains("drone") || normalized.contains("android") || normalized.contains("observatory") || normalized.contains("archive")) {
            return SystemGroup.QUANTUM_PRECISION;
        }
        if (normalized.contains("void") || normalized.contains("singularity") || normalized.contains("antimatter") || normalized.contains("stellar") || normalized.contains("omega") || normalized.contains("entropy") || normalized.contains("chrono")) {
            return SystemGroup.ENDGAME;
        }
        if (normalized.contains("warp") || normalized.contains("starsteel") || normalized.contains("continuum") || normalized.contains("anchor") || normalized.contains("relic") || normalized.contains("apex")) {
            return SystemGroup.MEGASTRUCTURE;
        }
        if (normalized.contains("research")) {
            return SystemGroup.BOOTSTRAP;
        }
        return legacyCategory == TechCategory.BASIC ? SystemGroup.BOOTSTRAP : SystemGroup.PROCESSING;
    }

    private MachineArchetype inferMachineArchetype(final String id) {
        final String normalized = this.normalize(id);
        if (normalized.contains("generator") || normalized.contains("reactor") || normalized.contains("turbine")) {
            return MachineArchetype.GENERATOR;
        }
        if (normalized.contains("node") || normalized.contains("tube") || normalized.contains("router") || normalized.contains("splitter") || normalized.contains("bus")) {
            return MachineArchetype.RELAY;
        }
        if (normalized.contains("storage") || normalized.contains("crate") || normalized.contains("hub")) {
            return MachineArchetype.STORAGE;
        }
        if (normalized.contains("quarry") || normalized.contains("harvester") || normalized.contains("fishing") || normalized.contains("tree") || normalized.contains("mob") || normalized.contains("vacuum") || normalized.contains("farm") || normalized.contains("greenhouse")) {
            return MachineArchetype.FIELD;
        }
        if (normalized.contains("research")) {
            return MachineArchetype.RESEARCH;
        }
        return MachineArchetype.PROCESSOR;
    }

    private VisualTier inferVisualTierForMachine(final String id, final TechCategory legacyCategory) {
        final SystemGroup group = this.inferSystemGroupForMachine(id, legacyCategory);
        return switch (group) {
            case ENDGAME, MEGASTRUCTURE -> VisualTier.ENDGAME;
            case QUANTUM_PRECISION -> VisualTier.ADVANCED;
            case SPECIAL -> VisualTier.ADVANCED;
            default -> legacyCategory == TechCategory.ADVANCED ? VisualTier.ADVANCED : VisualTier.TECH;
        };
    }

    private AcquisitionMode inferAcquisitionModeForMachine(final String id) {
        final String normalized = this.normalize(id);
        if (normalized.contains("quarry") || normalized.contains("harvester") || normalized.contains("fishing") || normalized.contains("mob") || normalized.contains("tree") || normalized.contains("vacuum")) {
            return AcquisitionMode.MACHINE_ASSEMBLY;
        }
        if (normalized.contains("research")) {
            return AcquisitionMode.RESEARCH_REWARD;
        }
        return AcquisitionMode.ADVANCED_WORKBENCH;
    }

    private String inferFamily(final String id) {
        final String normalized = this.normalize(id);
        if (normalized.startsWith("quarry_drill")) {
            return "quarry_drill";
        }
        if (normalized.startsWith("solar_")) {
            return "solar_power";
        }
        if (normalized.contains("battery")) {
            return "energy_storage";
        }
        if (normalized.contains("router") || normalized.contains("splitter") || normalized.contains("bus") || normalized.contains("storage_hub")) {
            return "logistics";
        }
        return normalized;
    }

    private String inferMachineRole(final String id) {
        final String normalized = this.normalize(id);
        if (normalized.contains("quarry")) {
            return "ore-extraction";
        }
        if (normalized.contains("generator") || normalized.contains("reactor") || normalized.contains("turbine")) {
            return "power-generation";
        }
        if (normalized.contains("node") || normalized.contains("tube") || normalized.contains("router") || normalized.contains("splitter") || normalized.contains("bus")) {
            return "item-routing";
        }
        if (normalized.contains("storage") || normalized.contains("hub") || normalized.contains("crate")) {
            return "item-buffering";
        }
        if (normalized.contains("research")) {
            return "research-gateway";
        }
        return "machine-processing";
    }

    private Material parseMaterial(final String name, final Material fallback, final String context) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        try {
            return Material.valueOf(name);
        } catch (final IllegalArgumentException ignored) {
            System.out.println("[TechProject] Invalid material '" + name + "' for " + context + ", fallback=" + fallback.name());
            return fallback;
        }
    }
}
