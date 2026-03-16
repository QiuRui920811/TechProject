package com.rui.techproject.util;

import com.rui.techproject.TechProjectPlugin;
import com.rui.techproject.model.AcquisitionMode;
import com.rui.techproject.model.ItemClass;
import com.rui.techproject.model.MachineArchetype;
import com.rui.techproject.model.SystemGroup;
import com.rui.techproject.model.TechTier;
import com.rui.techproject.model.VisualTier;
import com.rui.techproject.service.TechRegistry;
import com.rui.techproject.model.MachineDefinition;
import com.rui.techproject.model.TechItemDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.profile.PlayerProfile;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ItemFactoryUtil {
    private static final TextColor PRIMARY = TextColor.color(0xF38CFF);
    private static final TextColor SECONDARY = TextColor.color(0x7FDBFF);
    private static final TextColor SUCCESS = TextColor.color(0x7CFC9A);
    private static final TextColor WARNING = TextColor.color(0xFFD166);
    private static final TextColor MUTED = TextColor.color(0xA8B2C1);
    private static final TextColor DANGER = TextColor.color(0xFF7B7B);
    private static final TextColor BASIC_TIER = TextColor.color(0x7CFC9A);
    private static final TextColor MID_TIER = TextColor.color(0x7FDBFF);
    private static final TextColor HIGH_TIER = TextColor.color(0xC58BFF);
    private static final TextColor SPECIAL_TIER = TextColor.color(0xFFD166);

    private final TechProjectPlugin plugin;
    private final NamespacedKey techItemKey;
    private final NamespacedKey machineKey;
    private final NamespacedKey machineEnergyKey;
    private final NamespacedKey techItemEnergyKey;
    private final NamespacedKey guiActionKey;
    private final NamespacedKey guiPlaceholderKey;
    private final NamespacedKey previewClaimKey;
    private final NamespacedKey dataVersionKey;
    private final TechRegistry registry;
    private final Map<String, GuiButtonDefinition> guiButtons = new LinkedHashMap<>();
    private final Map<String, GuiIconDefinition> guiIcons = new LinkedHashMap<>();
    private final Map<String, GuiPaneDefinition> guiPanes = new LinkedHashMap<>();

    public ItemFactoryUtil(final TechProjectPlugin plugin, final TechRegistry registry) {
        this.plugin = plugin;
        this.techItemKey = new NamespacedKey(plugin, "tech_item_id");
        this.machineKey = new NamespacedKey(plugin, "machine_id");
        this.machineEnergyKey = new NamespacedKey(plugin, "machine_energy");
        this.techItemEnergyKey = new NamespacedKey(plugin, "tech_item_energy");
        this.guiActionKey = new NamespacedKey(plugin, "gui_action");
        this.guiPlaceholderKey = new NamespacedKey(plugin, "gui_placeholder");
        this.previewClaimKey = new NamespacedKey(plugin, "preview_claim");
        this.dataVersionKey = new NamespacedKey(plugin, "item_data_version");
        this.registry = registry;
        this.loadGuiConfig();
    }

    private void loadGuiConfig() {
        this.guiButtons.clear();
        this.guiIcons.clear();
        this.guiPanes.clear();
        final File file = new File(this.plugin.getDataFolder(), "tech-content-core.yml");
        if (file.isFile()) {
            final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            this.loadGuiButtons(yaml.getConfigurationSection("gui-buttons"));
            this.loadGuiIcons(yaml.getConfigurationSection("gui-icons"));
            this.loadGuiPanes(yaml.getConfigurationSection("gui-panes"));
            return;
        }
        final var resource = this.plugin.getResource("tech-content-core.yml");
        if (resource == null) {
            return;
        }
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8));
        this.loadGuiButtons(yaml.getConfigurationSection("gui-buttons"));
        this.loadGuiIcons(yaml.getConfigurationSection("gui-icons"));
        this.loadGuiPanes(yaml.getConfigurationSection("gui-panes"));
    }

    public void reloadGuiConfig() {
        this.loadGuiConfig();
    }

    private void loadGuiButtons(final ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (final String key : section.getKeys(false)) {
            final String path = key + ".";
            this.guiButtons.put(
                    this.normalizeGuiKey(key),
                    new GuiButtonDefinition(
                            this.parseGuiMaterial(section.getString(path + "icon", "PAPER")),
                            section.getString(path + "item-model", ""),
                            section.getString(path + "nexo-id", ""),
                            section.getString(path + "display-name", key),
                            section.getStringList(path + "lore")
                    )
            );
        }
    }

    private void loadGuiPanes(final ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (final String key : section.getKeys(false)) {
            final String path = key + ".";
            final String materialName = section.contains(path + "icon")
                    ? section.getString(path + "icon", "PAPER")
                    : section.getString(path + "material", key);
            final Material material = this.parseGuiMaterial(materialName);
            if (material == Material.PAPER && !"PAPER".equalsIgnoreCase(materialName)) {
                continue;
            }
            this.guiPanes.put(this.normalizeGuiKey(key), new GuiPaneDefinition(
                    material,
                    section.getString(path + "item-model", "-1"),
                    section.getString(path + "nexo-id", "")
            ));
        }
    }

    private void loadGuiIcons(final ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (final String key : section.getKeys(false)) {
            final String path = key + ".";
            this.guiIcons.put(
                    this.normalizeGuiKey(key),
                    new GuiIconDefinition(
                            this.parseGuiMaterial(section.getString(path + "icon", "PAPER")),
                            section.getString(path + "item-model", "-1"),
                            section.getString(path + "nexo-id", "")
                    )
            );
        }
    }

    public ItemStack buildGuiButton(final String key,
                                    final Material fallbackMaterial,
                                    final String fallbackTitle,
                                    final List<String> fallbackLore) {
        return this.buildGuiButton(key, fallbackMaterial, fallbackTitle, fallbackLore, Collections.emptyMap());
    }

    public ItemStack buildGuiButton(final String key,
                                    final Material fallbackMaterial,
                                    final String fallbackTitle,
                                    final List<String> fallbackLore,
                                    final Map<String, String> placeholders) {
        final GuiButtonDefinition definition = this.guiButtons.get(this.normalizeGuiKey(key));
        final Material material = definition == null ? fallbackMaterial : definition.icon();
        final String nexoId = definition == null ? "" : definition.nexoId();
        final ItemStack stack = this.resolveBaseItem(material, nexoId);
        final ItemMeta meta = stack.getItemMeta();
        final String title = this.replacePlaceholders(definition == null ? fallbackTitle : definition.displayName(), placeholders);
        final List<String> sourceLore = definition == null ? fallbackLore : definition.lore();
        final List<Component> lore = new ArrayList<>();
        for (final String line : sourceLore) {
            final String resolved = this.replacePlaceholders(line, placeholders).trim();
            if (!resolved.isBlank()) {
                lore.add(this.muted(this.localizeInlineTerms(resolved)));
            }
        }
        meta.displayName(this.warning(this.localizeInlineTerms(title)));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        this.applyConfiguredItemModel(meta, definition == null ? "" : definition.itemModel());
        this.applyGuiHudModel(meta, stack.getType(), true);
        stack.setItemMeta(meta);
        return stack;
    }

    public ItemStack buildGuiPane(final Material fallbackMaterial,
                                  final Component displayName,
                                  final List<Component> lore,
                                  final boolean infoCard) {
        return this.buildGuiPane(null, fallbackMaterial, displayName, lore, infoCard);
    }

    public ItemStack buildGuiPane(final String key,
                                  final Material fallbackMaterial,
                                  final Component displayName,
                                  final List<Component> lore,
                                  final boolean infoCard) {
        GuiPaneDefinition definition = null;
        if (key != null && !key.isBlank()) {
            definition = this.guiPanes.get(this.normalizeGuiKey(key));
        }
        if (definition == null) {
            definition = this.guiPanes.get(this.normalizeGuiKey(this.safeItemMaterial(fallbackMaterial).name()));
        }
        final Material material = definition == null ? this.safeItemMaterial(fallbackMaterial) : definition.icon();
        final ItemStack stack = definition == null
                ? new ItemStack(material)
                : this.resolveBaseItem(material, definition.nexoId());
        final ItemMeta meta = stack.getItemMeta();
        meta.displayName(displayName);
        meta.lore(lore);
        this.applyConfiguredItemModel(meta, definition == null ? "-1" : definition.itemModel());
        this.applyGuiHudModel(meta, stack.getType(), infoCard);
        stack.setItemMeta(meta);
        return stack;
    }

    public ItemStack buildGuiIcon(final String key,
                                  final Material fallbackMaterial,
                                  final Component displayName,
                                  final List<Component> lore,
                                  final boolean infoCard) {
        final GuiIconDefinition definition = this.guiIcons.get(this.normalizeGuiKey(key));
        final Material material = definition == null ? this.safeItemMaterial(fallbackMaterial) : definition.icon();
        final ItemStack stack = definition == null
                ? new ItemStack(material)
                : this.resolveBaseItem(material, definition.nexoId());
        final ItemMeta meta = stack.getItemMeta();
        meta.displayName(displayName);
        meta.lore(lore);
        this.applyConfiguredItemModel(meta, definition == null ? "-1" : definition.itemModel());
        this.applyGuiHudModel(meta, stack.getType(), infoCard);
        stack.setItemMeta(meta);
        return stack;
    }

    private String replacePlaceholders(final String input, final Map<String, String> placeholders) {
        if (input == null || input.isBlank() || placeholders == null || placeholders.isEmpty()) {
            return input == null ? "" : input;
        }
        String resolved = input;
        for (final Map.Entry<String, String> entry : placeholders.entrySet()) {
            resolved = resolved.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return resolved;
    }

    private String normalizeGuiKey(final String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    private Material parseGuiMaterial(final String name) {
        if (name == null || name.isBlank()) {
            return Material.PAPER;
        }
        try {
            return this.safeItemMaterial(Material.valueOf(name.trim().toUpperCase(Locale.ROOT)));
        } catch (final IllegalArgumentException ignored) {
            return Material.PAPER;
        }
    }

    private record GuiButtonDefinition(Material icon,
                                       String itemModel,
                                       String nexoId,
                                       String displayName,
                                       List<String> lore) {
    }

    private record GuiIconDefinition(Material icon,
                                     String itemModel,
                                     String nexoId) {
    }

    private record GuiPaneDefinition(Material icon,
                                     String itemModel,
                                     String nexoId) {
    }

    public ItemStack buildTechItem(final TechItemDefinition definition) {
        final boolean hasItemModel = definition.itemModel() != null && !definition.itemModel().isBlank() && !definition.itemModel().trim().equals("-1");
        final ItemStack stack = hasItemModel
                ? this.resolveBaseItem(this.techDisplayMaterial(definition), null, definition.headTexture())
                : this.resolveBaseItem(this.techDisplayMaterial(definition), definition.nexoId(), definition.headTexture());
        final ItemMeta meta = stack.getItemMeta();
        meta.displayName(this.schemaTitle(definition.tier(), definition.visualTier(), "✦ " + this.displayNameForId(definition.id())));
        final List<Component> lore = new ArrayList<>();
        lore.add(this.colored("┌─────────────────────────┐", MUTED));
        lore.add(this.tierLine(definition.tier()));
        lore.add(this.visualTierLine(definition.visualTier()));
        lore.add(this.secondary("◈ 分類：" + definition.guideCategory().displayName()));
        lore.add(this.systemLine(definition.systemGroup()));
        lore.add(this.itemClassLine(definition.itemClass()));
        lore.add(this.acquisitionLine(definition.acquisitionMode()));
        if (definition.family() != null && !definition.family().isBlank() && !definition.family().equalsIgnoreCase(definition.id())) {
            lore.add(this.muted("  ▸ 系列：" + this.localizeChainedId(definition.family())));
        }
        if (definition.role() != null && !definition.role().isBlank()) {
            lore.add(this.muted("  ▸ 定位：" + this.localizeChainedId(definition.role())));
        }
        lore.add(this.colored("├─────────────────────────┤", MUTED));
        lore.add(this.colored("  ◇ " + this.localizeInlineTerms(definition.description()), TextColor.color(0xD4E4F7)));
        lore.add(this.colored("  ◆ 解鎖：" + this.formatUnlockRequirement(definition.unlockRequirement()), TextColor.color(0xB8CFEA)));
        if (!definition.useCases().isEmpty()) {
            lore.add(this.colored("├─────────────────────────┤", MUTED));
            for (final String useCase : definition.useCases()) {
                lore.add(this.detailBullet(definition.visualTier(), useCase));
            }
        }
        final long maxEnergy = this.maxItemEnergy(definition.id());
        if (maxEnergy > 0L) {
            lore.add(this.colored("├─────────────────────────┤", MUTED));
            lore.add(this.itemEnergyBarLine(maxEnergy, maxEnergy));
            lore.add(this.colored("  ▸ 蹲下+右鍵 對準儲能機器充電", TextColor.color(0x8AACCC)));
        }
        lore.add(this.colored("└─────────────────────────┘", MUTED));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        this.applyConfiguredItemModel(meta, definition.itemModel());
        meta.getPersistentDataContainer().set(this.techItemKey, PersistentDataType.STRING, definition.id());
        meta.getPersistentDataContainer().set(this.dataVersionKey, PersistentDataType.INTEGER, this.currentItemDataVersion());
        if (maxEnergy > 0L) {
            meta.getPersistentDataContainer().set(this.techItemEnergyKey, PersistentDataType.LONG, maxEnergy);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    public ItemStack buildMachineItem(final MachineDefinition definition) {
        return this.buildMachineItem(definition, 0L);
    }

    public ItemStack buildMachineItem(final MachineDefinition definition, final long storedEnergy) {
        final boolean hasItemModel = definition.itemModel() != null && !definition.itemModel().isBlank() && !definition.itemModel().trim().equals("-1");
        final ItemStack stack = hasItemModel
                ? this.resolveBaseItem(definition.blockMaterial(), null, definition.headTexture())
                : this.resolveBaseItem(definition.blockMaterial(), definition.nexoId(), definition.headTexture());
        final ItemMeta meta = stack.getItemMeta();
        this.applyMachineMeta(meta, definition, storedEnergy);
        if (storedEnergy > 0L) {
            meta.getPersistentDataContainer().set(this.machineEnergyKey, PersistentDataType.LONG, storedEnergy);
        }
        this.applyConfiguredItemModel(meta, definition.itemModel());
        stack.setItemMeta(meta);
        return stack;
    }

    public ItemStack buildMachineGuiIcon(final MachineDefinition definition) {
        return this.buildMachineItem(definition);
    }

    private ItemStack resolveBaseItem(final Material fallbackMaterial, final String nexoId) {
        return this.resolveBaseItem(fallbackMaterial, nexoId, null);
    }

    private ItemStack resolveBaseItem(final Material fallbackMaterial, final String nexoId, final String headTexture) {
        final ItemStack nexoStack = this.tryBuildNexoItem(nexoId);
        if (nexoStack != null && nexoStack.getType() != Material.AIR) {
            nexoStack.setAmount(1);
            return nexoStack;
        }
        final Material baseMaterial = headTexture != null && !headTexture.isBlank()
                ? Material.PLAYER_HEAD
                : this.safeItemMaterial(fallbackMaterial);
        final ItemStack stack = new ItemStack(baseMaterial);
        this.applyHeadTexture(stack, headTexture);
        return stack;
    }

    private void applyHeadTexture(final ItemStack stack, final String headTexture) {
        if (stack == null || headTexture == null || headTexture.isBlank() || stack.getType() != Material.PLAYER_HEAD) {
            return;
        }
        final ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof SkullMeta skullMeta)) {
            return;
        }
        final URL skinUrl = this.normalizeHeadTextureUrl(headTexture);
        if (skinUrl == null) {
            return;
        }
        try {
            final PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
            profile.getTextures().setSkin(skinUrl);
            skullMeta.setOwnerProfile(profile);
            stack.setItemMeta(skullMeta);
        } catch (final IllegalArgumentException ignored) {
        }
    }

    private URL normalizeHeadTextureUrl(final String texture) {
        if (texture == null || texture.isBlank()) {
            return null;
        }
        final String trimmed = texture.trim();
        final String resolved = trimmed.startsWith("http://") || trimmed.startsWith("https://")
                ? trimmed
                : "https://textures.minecraft.net/texture/" + trimmed;
        try {
            return URI.create(resolved).toURL();
        } catch (final IllegalArgumentException | MalformedURLException ignored) {
            return null;
        }
    }

    private void applyConfiguredItemModel(final ItemMeta meta, final String itemModel) {
        if (itemModel == null || itemModel.isBlank() || itemModel.trim().equals("-1")) {
            return;
        }
        final NamespacedKey key = NamespacedKey.fromString(itemModel.trim(), this.plugin);
        if (key != null) {
            meta.setItemModel(key);
        }
    }

    private ItemStack tryBuildNexoItem(final String nexoId) {
        if (nexoId == null || nexoId.isBlank() || Bukkit.getPluginManager().getPlugin("Nexo") == null) {
            return null;
        }
        try {
            final Class<?> apiClass = Class.forName("com.nexomc.nexo.api.NexoItems");
            final Method itemFromId = apiClass.getMethod("itemFromId", String.class);
            final Object resolved = itemFromId.invoke(null, nexoId);
            final Object unwrapped = resolved instanceof Optional<?> optional ? optional.orElse(null) : resolved;
            if (unwrapped == null) {
                return null;
            }
            if (unwrapped instanceof ItemStack stack) {
                return stack.clone();
            }
            for (final String methodName : List.of("build", "getItem", "toItemStack", "getItemStack")) {
                try {
                    final Method method = unwrapped.getClass().getMethod(methodName);
                    final Object built = method.invoke(unwrapped);
                    if (built instanceof ItemStack stack) {
                        return stack.clone();
                    }
                } catch (final NoSuchMethodException ignored) {
                }
            }
        } catch (final ReflectiveOperationException ignored) {
        }
        return null;
    }

    private void applyMachineMeta(final ItemMeta meta, final MachineDefinition definition, final long storedEnergy) {
        meta.displayName(this.schemaTitle(definition.tier(), definition.visualTier(), "✦ " + this.displayNameForId(definition.id())));
        final List<Component> lore = new ArrayList<>();
        lore.add(this.colored("┌─────────────────────────┐", MUTED));
        lore.add(this.tierLine(definition.tier()));
        lore.add(this.visualTierLine(definition.visualTier()));
        lore.add(this.secondary("◈ 分類：" + definition.guideCategory().displayName()));
        lore.add(this.systemLine(definition.systemGroup()));
        lore.add(this.archetypeLine(definition.archetype()));
        lore.add(this.acquisitionLine(definition.acquisitionMode()));
        if (definition.family() != null && !definition.family().isBlank() && !definition.family().equalsIgnoreCase(definition.id())) {
            lore.add(this.muted("  ▸ 系列：" + this.localizeChainedId(definition.family())));
        }
        if (definition.role() != null && !definition.role().isBlank()) {
            lore.add(this.muted("  ▸ 定位：" + this.localizeChainedId(definition.role())));
        }
        lore.add(this.colored("├─────────────────────────┤", MUTED));
        for (final String line : this.machineIoSummaryLines(definition)) {
            lore.add(this.colored("  ◇ " + line, TextColor.color(0xD4E4F7)));
        }
        for (final Component energyLine : this.machineEnergyLore(definition)) {
            lore.add(energyLine);
        }
        if (storedEnergy > 0L) {
            lore.add(this.success("  ⚡ 回收儲能：" + storedEnergy + " / " + this.machineEnergyCapacity(definition) + " EU"));
        }
        lore.add(this.colored("├─────────────────────────┤", MUTED));
        lore.add(this.colored("  ◆ 功能：" + this.localizeInlineTerms(definition.effectDescription()), TextColor.color(0xB8CFEA)));
        for (final String line : this.machineRuntimeSummaryLines(definition)) {
            lore.add(this.muted("  ▸ " + line));
        }
        lore.add(this.colored("  ◆ 解鎖：" + this.formatUnlockRequirement(definition.unlockRequirement()), TextColor.color(0xB8CFEA)));
        lore.add(this.colored("└─────────────────────────┘", MUTED));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(this.machineKey, PersistentDataType.STRING, definition.id());
        meta.getPersistentDataContainer().set(this.dataVersionKey, PersistentDataType.INTEGER, this.currentItemDataVersion());
    }

    public Material safeItemMaterial(final Material material) {
        if (material == null || material == Material.AIR) {
            return Material.PAPER;
        }
        if (material.isItem()) {
            return material;
        }
        return switch (material) {
            case WATER_CAULDRON, LAVA_CAULDRON, POWDER_SNOW_CAULDRON -> Material.CAULDRON;
            default -> Material.PAPER;
        };
    }

    private Material techDisplayMaterial(final TechItemDefinition definition) {
        if (definition == null) {
            return Material.PAPER;
        }
        if (definition.headTexture() != null && !definition.headTexture().isBlank()) {
            return Material.PLAYER_HEAD;
        }
        if ((definition.nexoId() != null && !definition.nexoId().isBlank())
                || (definition.itemModel() != null && !definition.itemModel().isBlank() && !definition.itemModel().trim().equals("-1"))) {
            return this.safeItemMaterial(definition.icon());
        }
        final Material material = this.safeItemMaterial(definition.icon());
        if (!material.isBlock()) {
            return material;
        }
        return this.remapTechBlockDisplayMaterial(material);
    }

    private Material remapTechBlockDisplayMaterial(final Material material) {
        if (material == null || material == Material.AIR) {
            return Material.PAPER;
        }
        return switch (material) {
            case BEACON -> Material.HEART_OF_THE_SEA;
            case BLACK_STAINED_GLASS_PANE, TINTED_GLASS -> Material.GLASS_BOTTLE;
            case BLUE_ICE, PACKED_ICE -> Material.SNOWBALL;
            case CHISELED_BOOKSHELF -> Material.WRITABLE_BOOK;
            case CHEST -> Material.CHEST_MINECART;
            case COBWEB, WHITE_WOOL, LOOM -> Material.STRING;
            case DRAGON_EGG -> Material.DRAGON_BREATH;
            case END_CRYSTAL -> Material.SPYGLASS;
            case END_STONE_BRICKS, TERRACOTTA -> Material.BRICK;
            case IRON_BARS -> Material.IRON_NUGGET;
            case NETHERITE_BLOCK -> Material.NETHERITE_INGOT;
            case OAK_FENCE, OAK_SLAB -> Material.STICK;
            case OBSERVER -> Material.COMPARATOR;
            case PISTON -> Material.IRON_NUGGET;
            case POLISHED_BLACKSTONE_BRICKS -> Material.NETHERITE_SCRAP;
            case PRISMARINE_BRICKS, SEA_LANTERN -> Material.PRISMARINE_CRYSTALS;
            case PURPUR_BLOCK -> Material.CHORUS_FRUIT;
            case RESPAWN_ANCHOR -> Material.RECOVERY_COMPASS;
            default -> {
                final String name = material.name();
                if (name.contains("GLASS")) {
                    yield Material.GLASS_BOTTLE;
                }
                if (name.contains("WOOL") || name.contains("WEB")) {
                    yield Material.STRING;
                }
                if (name.contains("BARS") || name.contains("FENCE")) {
                    yield Material.IRON_NUGGET;
                }
                if (name.contains("SLAB") || name.contains("BRICK")) {
                    yield Material.BRICK;
                }
                if (name.contains("ICE")) {
                    yield Material.SNOWBALL;
                }
                if (name.contains("ANCHOR")) {
                    yield Material.RECOVERY_COMPASS;
                }
                if (name.contains("BEACON")) {
                    yield Material.HEART_OF_THE_SEA;
                }
                if (name.contains("CHEST")) {
                    yield Material.CHEST_MINECART;
                }
                yield material;
            }
        };
    }

    public ItemStack buildTechBook() {
        final TechItemDefinition definition = this.registry.getItem("tech_book");
        final boolean hasItemModel = definition != null && definition.itemModel() != null && !definition.itemModel().isBlank() && !definition.itemModel().trim().equals("-1");
        final ItemStack stack = definition == null
                ? new ItemStack(Material.ENCHANTED_BOOK)
                : hasItemModel ? new ItemStack(this.safeItemMaterial(definition.icon())) : this.resolveBaseItem(definition.icon(), definition.nexoId());
        final ItemMeta meta = stack.getItemMeta();
        final String displayName = definition == null ? "科技書" : this.displayNameForId(definition.id());
        final String description = definition == null || definition.description() == null || definition.description().isBlank()
                ? "科技樹、配方與教學入口"
                : this.localizeInlineTerms(definition.description());
        meta.displayName(this.primary(displayName));
        meta.lore(List.of(
                this.muted(description),
                this.success("右鍵：開啟科技書（等同 /tech book）"),
                this.warning("左鍵：無特殊功能"),
                this.secondary("補發：/tech book get")
        ));
        if (definition != null) {
            this.applyConfiguredItemModel(meta, definition.itemModel());
        }
        meta.getPersistentDataContainer().set(this.techItemKey, PersistentDataType.STRING, "tech_book");
        stack.setItemMeta(meta);
        return stack;
    }

    public ItemStack buildFullUnlockBook() {
        final ItemStack stack = new ItemStack(Material.KNOWLEDGE_BOOK);
        final ItemMeta meta = stack.getItemMeta();
        meta.displayName(this.warning("全解鎖書"));
        meta.lore(List.of(
                this.muted("管理員用的一次性解鎖道具。"),
                this.success("右鍵：解鎖全部科技研究項目"),
                this.secondary("包含：物品、機器、互動科技"),
                this.warning("使用後會自動消耗")
        ));
        meta.getPersistentDataContainer().set(this.techItemKey, PersistentDataType.STRING, "full_unlock_book");
        stack.setItemMeta(meta);
        return stack;
    }

    public ItemStack buildEnergyToken(final int amount) {
        final ItemStack stack = new ItemStack(Material.SUNFLOWER, Math.max(1, amount));
        final ItemMeta meta = stack.getItemMeta();
        meta.displayName(this.warning("能源代幣"));
        meta.lore(List.of(this.muted("科技能源貨幣")));
        meta.getPersistentDataContainer().set(this.techItemKey, PersistentDataType.STRING, "energy_token");
        stack.setItemMeta(meta);
        return stack;
    }

    public ItemStack buildAchievementBadge(final String id) {
        final ItemStack stack = new ItemStack(Material.NETHER_STAR);
        final ItemMeta meta = stack.getItemMeta();
        meta.displayName(this.secondary("成就徽章"));
        meta.lore(List.of(this.muted("成就徽章：" + this.displayNameForId(id))));
        meta.getPersistentDataContainer().set(this.techItemKey, PersistentDataType.STRING, "achievement_badge:" + id);
        stack.setItemMeta(meta);
        return stack;
    }

    public Component primary(final String text) {
        return this.colored(text, PRIMARY);
    }

    public Component secondary(final String text) {
        return this.colored(text, SECONDARY);
    }

    public Component success(final String text) {
        return this.colored(text, SUCCESS);
    }

    public Component warning(final String text) {
        return this.colored(text, WARNING);
    }

    public Component muted(final String text) {
        return this.colored(text, MUTED);
    }

    public Component danger(final String text) {
        return this.colored(text, DANGER);
    }

    public Component schemaTitle(final TechTier tier, final VisualTier visualTier, final String text) {
        final TextColor color = switch (visualTier) {
            case VANILLA -> MUTED;
            case TECH -> this.tierColor(tier);
            case ADVANCED -> HIGH_TIER;
            case ENDGAME -> DANGER;
        };
        return this.colored(text, color);
    }

    public Component visualTierLine(final VisualTier visualTier) {
        return this.colored("  ☆ 視覺層級：" + visualTier.displayName(), this.visualTierColor(visualTier));
    }

    public Component systemLine(final SystemGroup group) {
        return this.colored("  ◈ 系統：" + group.displayName(), this.systemColor(group));
    }

    public Component itemClassLine(final ItemClass itemClass) {
        return this.colored("  ◈ 物品類型：" + itemClass.displayName(), this.itemClassColor(itemClass));
    }

    public Component archetypeLine(final MachineArchetype archetype) {
        return this.colored("  ◈ 機器類型：" + archetype.displayName(), this.archetypeColor(archetype));
    }

    public Component acquisitionLine(final AcquisitionMode acquisitionMode) {
        return this.colored("  ◈ 取得方式：" + acquisitionMode.displayName(), this.acquisitionColor(acquisitionMode));
    }

    public Component detailBullet(final VisualTier visualTier, final String text) {
        return this.colored("  ✧ " + this.localizeInlineTerms(text), this.visualTierColor(visualTier));
    }

    public Component energyLine(final int energyPerTick, final MachineArchetype archetype) {
        if (energyPerTick <= 0) {
            final String label = archetype == MachineArchetype.GENERATOR ? "被動供能 / 內建產能" : "無持續耗能";
            return this.success("  ⚡ 每刻耗能：" + label);
        }
        return this.warning("  ⚡ 每刻耗能：" + energyPerTick + " EU");
    }

    public List<Component> machineEnergyLore(final MachineDefinition definition) {
        final List<Component> lines = new ArrayList<>();
        for (final String line : this.machineEnergySummaryLines(definition)) {
            lines.add(this.warning(line));
        }
        return lines;
    }

    public long machineEnergyCapacity(final MachineDefinition definition) {
        if (definition == null) {
            return 0L;
        }
        return switch (definition.id().toLowerCase(Locale.ROOT)) {
            case "battery_bank" -> 24000L;
            case "solar_array", "storm_turbine", "fusion_reactor" -> 12000L;
            case "energy_node", "energy_cable" -> 6000L;
            case "quarry_drill", "quarry_drill_mk2", "quarry_drill_mk3" -> 6400L;
            case "solar_generator", "coal_generator" -> 3200L;
            default -> switch (definition.tier()) {
                case TIER1 -> 2400L;
                case TIER2 -> 4800L;
                case TIER3 -> 9600L;
                case TIER4 -> 16000L;
            };
        };
    }

    public List<String> machineEnergySummaryLines(final MachineDefinition definition) {
        if (definition == null) {
            return List.of("耗能資訊：未知");
        }
        final String normalizedId = definition.id().toLowerCase(Locale.ROOT);
        return switch (normalizedId) {
            case "planetary_gate" -> List.of(
                    "待機吸能：" + definition.energyPerTick() + " EU / 刻",
                    "啟動耗能：1200 EU / 次"
            );
            case "battery_bank" -> List.of(
                    "固定耗能：無",
                    "儲放電傳輸：每側最多 4 EU / 刻"
            );
            case "greenhouse" -> List.of(
                    "建議吸能：" + definition.energyPerTick() + " EU / 刻",
                    "催熟脈衝：2 EU / 格"
            );
            case "crop_harvester" -> List.of(
                    "建議吸能：" + definition.energyPerTick() + " EU / 刻",
                    "收割耗能：8 EU / 次"
            );
            case "planetary_harvester" -> List.of(
                    "建議吸能：" + definition.energyPerTick() + " EU / 刻",
                    "採集耗能：約 12 + 產物數 EU / 次"
            );
            case "tree_feller" -> List.of(
                    "建議吸能：" + definition.energyPerTick() + " EU / 刻",
                    "伐木耗能：約 10 + 樹幹數 EU / 次"
            );
            case "mob_collector" -> List.of(
                    "建議吸能：" + definition.energyPerTick() + " EU / 刻",
                    "收集耗能：14 EU / 隻"
            );
            case "fishing_dock" -> List.of(
                    "建議吸能：" + definition.energyPerTick() + " EU / 刻",
                    "釣取耗能：7 EU / 次"
            );
            case "vacuum_inlet" -> List.of(
                    "建議吸能：" + definition.energyPerTick() + " EU / 刻",
                    "吸取耗能：3 EU / 件"
            );
            default -> {
                final boolean hasRecipes = !this.registry.getRecipesForMachine(definition.id()).isEmpty();
                if (definition.archetype() == MachineArchetype.GENERATOR) {
                    yield List.of("被動供能 / 內建產能");
                }
                if (hasRecipes) {
                    yield List.of(
                            "建議吸能：" + definition.energyPerTick() + " EU / 刻",
                            "實際耗能：依配方顯示"
                    );
                }
                if (definition.energyPerTick() <= 0) {
                    yield List.of("無固定耗能");
                }
                yield List.of("持續耗能：" + definition.energyPerTick() + " EU / 刻");
            }
        };
    }

    public List<String> machineIoSummaryLines(final MachineDefinition definition) {
        if (definition == null) {
            return List.of("傳輸：未知");
        }
        final String normalizedId = definition.id().toLowerCase(Locale.ROOT);
        return switch (normalizedId) {
            case "research_desk" -> List.of("傳輸：無物品加工，右鍵直接開研究台");
            case "solar_generator", "solar_array", "storm_turbine" -> List.of("傳輸：不處理物品，只負責發電");
            case "coal_generator" -> List.of("燃料輸入：煤 / 煤粉", "傳輸：只負責供電，不產物品");
            case "battery_bank" -> List.of("傳輸：不改變物品，只做儲能與轉運");
            case "energy_node", "energy_cable" -> List.of("傳輸：只轉送電力，不處理物品");
            case "logistics_node", "item_tube" -> List.of("傳輸：只搬運物品，不改變物品種類");
            case "storage_hub" -> List.of("傳輸：大型物流緩衝，不改變物品種類");
            case "filter_router" -> List.of("模板輸入：第 1 格放樣本", "傳輸：只分流符合樣本的物品");
            case "splitter_node" -> List.of("傳輸：把單一路徑平均拆往多條輸出");
            case "industrial_bus" -> List.of("傳輸：高吞吐物流主幹，不改變物品種類");
            case "crop_harvester" -> List.of("傳輸：自動收成熟作物並重植，無需手動放入材料");
            case "vacuum_inlet" -> List.of("傳輸：自動吸入附近掉落物，無需手動放入材料");
            case "tree_feller" -> List.of("傳輸：自動砍自然樹並回收原木 / 樹苗");
            case "mob_collector" -> List.of("傳輸：自動收白名單生物掉落，無需手動放入材料");
            case "fishing_dock" -> List.of("傳輸：依水域條件隨機取得漁獲");
            case "quarry_drill", "quarry_drill_mk2", "quarry_drill_mk3" -> List.of("燃料輸入：上方箱子的木炭 / 岩漿桶", "傳輸：採出的礦物與副產物都回到上方箱子");
            case "planetary_gate" -> List.of("傳輸：不處理物品，消耗電力啟動星球傳送");
            case "planetary_harvester" -> List.of("傳輸：自動回收星球節點樣本，無需手動放入材料");
            default -> {
                final boolean hasRecipes = !this.registry.getRecipesForMachine(definition.id()).isEmpty();
                if (hasRecipes) {
                    final String outputs = definition.outputs().isEmpty()
                            ? "依配方而定"
                            : this.joinDisplayNames(definition.outputs(), ", ");
                    yield List.of("配方輸入：依所選配方", "代表產物：" + outputs);
                }
                yield List.of(
                        "輸入：" + (definition.inputs().isEmpty() ? "無" : this.joinDisplayNames(definition.inputs(), ", ")),
                        "輸出：" + (definition.outputs().isEmpty() ? "無" : this.joinDisplayNames(definition.outputs(), ", "))
                );
            }
        };
    }

    public List<String> machineRuntimeSummaryLines(final MachineDefinition definition) {
        if (definition == null) {
            return List.of();
        }
        final String normalizedId = definition.id().toLowerCase(Locale.ROOT);
        return switch (normalizedId) {
            case "solar_generator", "solar_array" -> List.of("條件：白天、露天、天光充足才會發電");
            case "coal_generator" -> List.of("節奏：每 10 刻燒 1 份燃料並產 18 EU");
            case "storm_turbine" -> List.of("天候：平時 2 EU / 刻，雨天或雷暴 8 EU / 刻");
            case "battery_bank" -> List.of("傳輸：每側最多充 / 放 4 EU / 刻");
            case "energy_node", "energy_cable" -> List.of("限制：需檢查輸入 / 輸出方向與網路深度");
            case "logistics_node", "item_tube" -> List.of("限制：來源看輸出方向，目標看輸入方向");
            case "storage_hub" -> List.of("吞吐：每刻最多內搬 9 件，外送 2 件", "升級：範圍模組會增加物流網深度");
            case "filter_router" -> List.of("規則：第 1 格模板會保留 1 件不被搬走", "吞吐：每刻內搬 4 件，外送 2 件");
            case "splitter_node" -> List.of("規則：輸出目標會輪轉，平均分流", "吞吐：每刻內搬 / 外送各 3 件");
            case "industrial_bus" -> List.of("吞吐：每刻內搬 / 外送各 6 件", "升級：範圍模組會額外擴大物流深度");
            case "greenhouse" -> List.of("副功能：每 6 刻會對周圍 2 格內作物做一次催熟脈衝", "升級：速度 / 效率 / 產量 影響配方，範圍不影響催熟半徑");
            case "crop_harvester" -> List.of("範圍：半徑 2 + 範圍模組", "耗能：每次收割 8 EU，需成熟作物");
            case "vacuum_inlet" -> List.of("範圍：半徑 2.5 + 範圍模組", "限制：掉落物需達最小存在時間且可被撿取");
            case "tree_feller" -> List.of("範圍：半徑 2 + 範圍模組（上限額外 +2）", "限制：只會處理自然樹；單次耗能隨樹幹數增加");
            case "mob_collector" -> List.of("範圍：半徑 4 + 範圍模組", "限制：不處理命名、馴服、拴繩、幼體與黑名單目標");
            case "fishing_dock" -> List.of("條件：周圍至少 3 格相鄰水面", "耗能：每次釣取 7 EU");
            case "quarry_drill" -> List.of("結構：中心高爐 + 上方箱子 + 兩側鐵塊與雙鑽機", "限制：需燃料，會掃描所在區塊往下所有礦物");
            case "quarry_drill_mk2" -> List.of("結構：與 Mk1 相同", "效率：每輪 2 次作業，單次耗能更高");
            case "quarry_drill_mk3" -> List.of("結構：與 Mk1 相同", "效率：同樣掃整個所在區塊，但每輪 4 次作業、單次產量更高");
            case "planetary_gate" -> List.of("操作：右鍵打開 54 格航線面板，點選星球或地球直接傳送", "限制：啟動時需站在門上，且目標世界必須就緒");
            case "planetary_harvester" -> List.of("世界限制：只能在星球世界運作", "範圍：半徑 3 + 範圍模組，垂直 -1 ~ +3");
            case "electric_crusher", "electric_compressor", "electric_ore_washer", "electric_wire_mill",
                 "electric_purifier", "electric_centrifuge", "electric_bio_lab", "electric_chemical_reactor" ->
                    List.of("純電力版：全自動加工，不需手動右鍵觸發", "升級：速度提高處理次數、效率降低配方耗能、產量提高單次產量");
            default -> {
                final boolean hasRecipes = !this.registry.getRecipesForMachine(definition.id()).isEmpty();
                if (hasRecipes) {
                    yield List.of("升級：速度提高處理次數、效率降低配方耗能、產量提高單次產量");
                }
                yield List.of();
            }
        };
    }

    public List<String> machinePlacementHintLines(final String machineId) {
        if (machineId == null || machineId.isBlank()) {
            return List.of();
        }
        return switch (machineId.toLowerCase(Locale.ROOT)) {
            case "research_desk" -> List.of("這台不加工，右鍵直接開研究台。", "建議放在主基地中央或科技書旁。");
            case "solar_generator", "solar_array" -> List.of("盡量露天，正上方不要被遮住。", "初期最常直接貼著粉碎機或電池庫。");
            case "coal_generator" -> List.of("貼著耗能機器放，並持續補煤。", "最適合補夜間缺口或當備援電源。");
            case "crusher" -> List.of("核心上方先加鐵柵欄，接好電後右鍵鐵柵欄才會開始粉碎。", "蹲下 + 右鍵核心可開介面，這台現在不是全自動連續加工。");
            case "electric_saw", "recycler" -> List.of("旁邊先接任一發電機。", "輸出欄滿了就會停機，物流最好往外拉。");
            case "auto_farm" -> List.of("先接發電，再放入對應原料做農務配方。", "後續可往溫室、生物實驗室、作物收割機延伸。");
            case "battery_bank" -> List.of("放在發電區與耗能區之間當緩衝。", "這台不做配方，只做儲放電。");
            case "greenhouse" -> List.of("最好貼著農田區或自動農場。", "周圍 2 格內有作物時，催熟效果才會發揮。");
            case "energy_node", "energy_cable" -> List.of("用來把發電區跨區接到耗能區。", "若傳不到電，先檢查方向與中繼距離。");
            case "logistics_node", "item_tube" -> List.of("接在產線中段當幹線延伸。", "來源看輸出方向，目標看輸入方向。");
            case "crop_harvester" -> List.of("請與作物同高度，周圍最好是農地。", "會自動重植，半徑受範圍升級影響。");
            case "vacuum_inlet" -> List.of("放在掉落物集中區中央最有效。", "只會吸可撿取、存在時間足夠的掉落物。");
            case "storm_turbine" -> List.of("建議放高處方便接電與觀察。", "暴雨天供電最強，後方最好接電池庫。");
            case "tree_feller" -> List.of("貼著樹列底部放置，系統只會處理自然樹。", "建議和真空吸入口同區，避免原木滿地。");
            case "mob_collector" -> List.of("放在刷怪塔或畜牧區外圍。", "不會收命名、生物被馴服、拴繩或幼年個體。");
            case "fishing_dock" -> List.of("至少要有 3 格相鄰水面。", "最適合放在岸邊或人工魚池邊。");
            case "quarry_drill" -> List.of("中心改放原版高爐，上方放箱子，同一軸兩側放鐵方塊，鐵方塊上方各放一台採礦鑽機。", "燃料直接放上方箱子，右鍵中央高爐啟動；蹲下 + 右鍵中央高爐才會開介面。");
            case "quarry_drill_mk2" -> List.of("結構與 Mk1 相同，但每輪會抽更多礦。", "建議先有穩定供電與燃料箱。");
            case "quarry_drill_mk3" -> List.of("結構與 Mk1 相同，但抽礦效率最高、耗電也最大。", "同樣掃描所在區塊，只是每輪次數與產量更高。");
            case "storage_hub" -> List.of("適合放在多條產線交會點。", "先當大緩衝，再往過濾器 / 總線分流。");
            case "filter_router" -> List.of("第 1 格放樣本物品當過濾模板。", "最適合接在回收或中間件分線口。");
            case "splitter_node" -> List.of("單一輸出要拆往多條線時放這台。", "後方支線越平均，越不容易卡料。");
            case "industrial_bus" -> List.of("放在主幹道中央，前後各接數條支線。", "吞吐量高，適合跨區物流骨幹。");
            case "planetary_gate" -> List.of("右鍵打開星門航線選單，中央可直接選五顆星球與地球。", "平時維持 12 EU/刻吸能，每次傳送額外消耗 1200 EU；真正啟動前仍要站在門上。");
            case "planetary_harvester" -> List.of("只能放在星球上。", "會掃附近固定生成且會逐點恢復的異星資源節點。");
            case "electric_crusher" -> List.of("純電力版粉碎機，放下即自動加工，不需手動右鍵。", "耗能約手動版 2×，建議接穩定供電。");
            case "electric_compressor" -> List.of("純電力版壓縮機，全自動持續壓製。", "接好物流與電源即可無人運作。");
            case "electric_ore_washer" -> List.of("純電力版洗礦機，自動洗礦無需拉桿。", "耗能約手動版 2×。");
            case "electric_wire_mill" -> List.of("純電力版拉線機，自動拉線無需手動觸發。", "耗能約手動版 2×。");
            case "electric_purifier" -> List.of("純電力版淨化器，自動淨化無需按鈕。", "耗能約手動版 2.4×。");
            case "electric_centrifuge" -> List.of("純電力版離心機，自動離心無需手動觸發。", "耗能約手動版 2×，建議配效率升級。");
            case "electric_bio_lab" -> List.of("純電力版生質實驗室，自動培養無需手動觸發。", "耗能約手動版 2×。");
            case "electric_chemical_reactor" -> List.of("純電力版化學反應器，全自動反應。", "耗能約手動版 2×，高端產線必備。");
            default -> List.of();
        };
    }

    public List<String> machineDisplayInputIds(final MachineDefinition definition) {
        if (definition == null) {
            return List.of();
        }
        return switch (definition.id().toLowerCase(Locale.ROOT)) {
            case "research_desk", "solar_generator", "solar_array", "storm_turbine", "battery_bank", "energy_node", "energy_cable",
                    "logistics_node", "item_tube", "storage_hub", "splitter_node", "industrial_bus", "crop_harvester", "vacuum_inlet",
                    "tree_feller", "mob_collector", "fishing_dock", "planetary_gate", "planetary_harvester" -> List.of();
            default -> definition.inputs();
        };
    }

    public List<String> machineDisplayOutputIds(final MachineDefinition definition) {
        if (definition == null) {
            return List.of();
        }
        return switch (definition.id().toLowerCase(Locale.ROOT)) {
            case "research_desk", "solar_generator", "solar_array", "storm_turbine", "battery_bank", "energy_node", "energy_cable",
                    "logistics_node", "item_tube", "storage_hub", "splitter_node", "industrial_bus", "planetary_gate" -> List.of();
            default -> definition.outputs();
        };
    }

    public Component hex(final String text, final String hex) {
        final TextColor color = TextColor.fromHexString(hex);
        return this.colored(text, color == null ? PRIMARY : color);
    }

    public List<Component> mutedLore(final List<String> lines) {
        final List<Component> lore = new ArrayList<>();
        for (final String line : lines) {
            lore.add(this.muted(line));
        }
        return lore;
    }

    public String joinDisplayNames(final List<String> ids, final String delimiter) {
        return ids.stream().map(this::displayNameForId).toList().stream().reduce((a, b) -> a + delimiter + b).orElse("");
    }

    public void applyGuiHudModel(final ItemMeta meta, final Material material, final boolean infoCard) {
        // 已依需求停用插件內建 GUI custom model data。
    }

    private Integer guiHudCustomModelData(final Material material, final boolean infoCard) {
        if (material == null) {
            return null;
        }
        return switch (material) {
            case BOOK -> 830201;
            case ENCHANTED_BOOK -> 830202;
            case WRITABLE_BOOK -> 830203;
            case KNOWLEDGE_BOOK -> 830204;
            case EXPERIENCE_BOTTLE -> 830205;
            case ARROW -> 830206;
            case SPECTRAL_ARROW -> 830207;
            case LIGHTNING_ROD -> 830208;
            case CRAFTING_TABLE -> 830209;
            default -> null;
        };
    }

    private void applyCustomModelData(final ItemMeta meta, final int value) {
        final var component = meta.getCustomModelDataComponent();
        component.setFloats(List.of((float) value));
        meta.setCustomModelDataComponent(component);
    }

    public ItemStack tagPreviewClaim(final ItemStack stack, final String previewId) {
        if (stack == null || previewId == null || previewId.isBlank()) {
            return stack;
        }
        final ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(this.previewClaimKey, PersistentDataType.STRING, previewId);
        stack.setItemMeta(meta);
        return stack;
    }

    public String getPreviewClaimId(final ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(this.previewClaimKey, PersistentDataType.STRING);
    }

    public ItemStack buildPreviewReward(final String previewId) {
        if (previewId == null || previewId.isBlank()) {
            return null;
        }
        final String[] split = previewId.split(":", 2);
        final String type = split[0].toLowerCase(Locale.ROOT);
        final String id = split.length > 1 ? split[1] : "";
        return switch (type) {
            case "item" -> {
                final TechItemDefinition definition = this.registry.getItem(id);
                yield definition == null ? null : this.buildTechItem(definition);
            }
            case "machine" -> {
                final MachineDefinition definition = this.registry.getMachine(id);
                yield definition == null ? null : this.buildMachineItem(definition);
            }
            case "material" -> {
                final Material material = Material.matchMaterial(id.toUpperCase(Locale.ROOT));
                if (material == null || material == Material.AIR) {
                    yield null;
                }
                final Material displayMaterial = this.safeItemMaterial(material);
                final ItemStack stack = new ItemStack(displayMaterial);
                final ItemMeta meta = stack.getItemMeta();
                meta.displayName(this.warning(this.displayNameForId(id)));
                stack.setItemMeta(meta);
                yield stack;
            }
            default -> null;
        };
    }

    public String displayNameForId(final String id) {
        if (id == null || id.isBlank()) {
            return "未知";
        }
        if ("initial".equalsIgnoreCase(id)) {
            return "初始可用";
        }
        final String alias = this.knownChineseName(id);
        if (alias != null) {
            return alias;
        }
        final var item = this.registry.getItem(id);
        if (item != null) {
            return this.localizedDisplayName(id, item.displayName());
        }
        final var machine = this.registry.getMachine(id);
        if (machine != null) {
            return this.localizedDisplayName(id, machine.displayName());
        }
        final var achievement = this.registry.getAchievement(id);
        if (achievement != null) {
            return this.localizedDisplayName(id, achievement.displayName());
        }
        final Material material = Material.matchMaterial(id.toUpperCase(Locale.ROOT));
        if (material != null) {
            return this.displayNameForMaterial(material);
        }
        return this.humanize(id);
    }

    public String displayNameForMaterial(final Material material) {
        return switch (material) {
            case IRON_ORE -> "鐵礦石";
            case COPPER_ORE -> "銅礦石";
            case IRON_INGOT -> "鐵錠";
            case COPPER_INGOT -> "銅錠";
            case REDSTONE -> "紅石粉";
            case PISTON -> "活塞";
            case HOPPER -> "漏斗";
            case BARREL -> "木桶";
            case TARGET -> "標靶";
            case STONECUTTER -> "切石機";
            case FURNACE -> "熔爐";
            case HAY_BLOCK -> "乾草捆";
            case IRON_BARS -> "鐵欄杆";
            case GLASS_PANE -> "玻璃片";
            case SPAWNER -> "生怪磚";
            case STRIPPED_OAK_LOG -> "剝皮橡木原木";
            case CHEST -> "箱子";
            case DROPPER -> "投擲器";
            case COMPARATOR -> "比較器";
            case REPEATER -> "中繼器";
            case OBSERVER -> "觀察者";
            case COBWEB -> "蜘蛛網";
            case OAK_SLAB -> "橡木半磚";
            case OAK_FENCE -> "橡木柵欄";
            case CROSSBOW -> "弩";
            case SHEARS -> "剪刀";
            case NETHERITE_PICKAXE -> "獄髓鎬";
            case IRON_AXE -> "鐵斧";
            case QUARTZ -> "石英";
            case COAL -> "煤炭";
            case GLASS -> "玻璃";
            case BAMBOO -> "竹子";
            case BONE_MEAL -> "骨粉";
            case PAPER -> "紙";
            case BLAZE_POWDER -> "烈焰粉";
            case SNOWBALL -> "雪球";
            case AMETHYST_SHARD -> "紫水晶碎片";
            case OAK_PLANKS -> "橡木木板";
            case CLAY_BALL -> "黏土球";
            case WHEAT -> "小麥";
            case WHEAT_SEEDS -> "小麥種子";
            case GLASS_BOTTLE -> "玻璃瓶";
            case ENDER_PEARL -> "終界珍珠";
            case EXPERIENCE_BOTTLE -> "經驗瓶";
            case MUD -> "泥巴";
            case HONEYCOMB -> "蜂巢片";
            case HONEY_BOTTLE -> "蜂蜜瓶";
            case PACKED_ICE -> "浮冰";
            case BLUE_ICE -> "藍冰";
            case LIGHTNING_ROD -> "避雷針";
            case RESPAWN_ANCHOR -> "重生錨";
            case ENCHANTED_BOOK -> "附魔書";
            default -> this.humanize(material.name());
        };
    }

    public String formatUnlockRequirement(final String requirement) {
        if (requirement == null || requirement.isBlank() || requirement.equalsIgnoreCase("initial")) {
            return "初始可用";
        }
        final List<String> orGroups = new ArrayList<>();
        for (final String orGroup : requirement.split("\\|")) {
            final List<String> andTokens = new ArrayList<>();
            for (final String token : orGroup.split("&")) {
                final String trimmed = token.trim();
                if (!trimmed.isBlank()) {
                    andTokens.add(this.describeRequirementToken(trimmed));
                }
            }
            if (!andTokens.isEmpty()) {
                orGroups.add(String.join(" 並且 ", andTokens));
            }
        }
        return orGroups.isEmpty() ? this.displayNameForId(requirement) : String.join(" 或 ", orGroups);
    }

    private String describeRequirementToken(final String token) {
        if (token.regionMatches(true, 0, "item:", 0, 5)) {
            return "物品「" + this.displayNameForId(token.substring(5)) + "」";
        }
        if (token.regionMatches(true, 0, "machine:", 0, 8)) {
            return "機器「" + this.displayNameForId(token.substring(8)) + "」";
        }
        if (token.regionMatches(true, 0, "achievement:", 0, 12)) {
            return "成就「" + this.displayNameForId(token.substring(12)) + "」";
        }
        if (token.regionMatches(true, 0, "interaction:", 0, 12)) {
            return "互動「" + this.displayNameForId(token.substring(12)) + "」";
        }
        if (token.regionMatches(true, 0, "stat:", 0, 5)) {
            final String raw = token.substring(5);
            final int comparatorIndex = raw.indexOf(">=");
            if (comparatorIndex > 0) {
                return this.displayStatName(raw.substring(0, comparatorIndex).trim()) + " ≥ " + raw.substring(comparatorIndex + 2).trim();
            }
            final int legacyIndex = raw.lastIndexOf(':');
            if (legacyIndex > 0) {
                return this.displayStatName(raw.substring(0, legacyIndex).trim()) + " ≥ " + raw.substring(legacyIndex + 1).trim();
            }
            return this.displayStatName(raw.trim());
        }
        return this.displayNameForId(token);
    }

    private String displayStatName(final String statKey) {
        return switch (statKey.toLowerCase(Locale.ROOT)) {
            case "energy_generated" -> "累計發電";
            case "farm_harvested" -> "收成作物";
            case "recycled_items" -> "回收物品";
            case "items_transferred" -> "物流搬運";
            case "machines_placed" -> "放置機器";
            case "quarry_mined" -> "採礦數";
            case "vacuum_collected" -> "吸入物品";
            case "storm_energy" -> "風暴發電";
            case "planet_elites_defeated" -> "擊敗星球精英";
            case "planets_visited" -> "探訪星球";
            case "planet_harvested" -> "星球採集";
            case "trees_felled" -> "伐木數";
            case "mobs_collected" -> "收集生物";
            case "fish_caught" -> "釣魚數";
            case "crops_auto_harvested" -> "自動收割";
            default -> this.humanize(statKey);
        };
    }

    private String humanize(final String id) {
        final String[] split = id.replace('-', '_').split("_");
        final StringBuilder builder = new StringBuilder();
        for (final String token : split) {
            if (token.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(token.charAt(0))).append(token.substring(1).toLowerCase(Locale.ROOT));
        }
        return builder.toString();
    }

    private String localizeChainedId(final String id) {
        if (id == null || id.isBlank()) {
            return "";
        }
        final String label = this.familyRoleLabel(id.toLowerCase(Locale.ROOT).replace('-', '_'));
        if (label != null) {
            return label;
        }
        final String resolved = this.displayNameForId(id);
        if (resolved.chars().anyMatch(ch -> Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN)) {
            return resolved;
        }
        return this.localizeInlineTerms(this.humanize(id));
    }

    private String familyRoleLabel(final String key) {
        return switch (key) {
            // ── family ──
            case "advanced_components" -> "進階零件";
            case "agri_automation" -> "農業自動化";
            case "agri_tools" -> "農具";
            case "alloy_processing" -> "合金加工";
            case "anchor_network" -> "錨定網路";
            case "antimatter" -> "反物質";
            case "archive_chain" -> "典藏鏈";
            case "assembly" -> "裝配";
            case "automation_parts" -> "自動化零件";
            case "bio_chain" -> "生質鏈";
            case "biosynth" -> "生質合成";
            case "capture_tools" -> "捕捉工具";
            case "chemistry" -> "化工";
            case "chrono" -> "時序";
            case "combustion_power" -> "燃燒發電";
            case "continuum_chain" -> "連續體鏈";
            case "crop_chain" -> "作物鏈";
            case "cryo_chain" -> "低溫鏈";
            case "cryon_suit" -> "克里昂套裝";
            case "crystal_chain" -> "結晶鏈";
            case "dark_matter" -> "暗物質";
            case "drone_chain" -> "無人機鏈";
            case "electronics" -> "電子";
            case "energy_network" -> "能源網路";
            case "energy_storage" -> "儲能";
            case "entropy" -> "熵";
            case "expedition_tools" -> "遠征工具";
            case "field_extraction" -> "野外採集";
            case "field_rations" -> "野戰口糧";
            case "field_tech" -> "野外科技";
            case "fluid_processing" -> "流體加工";
            case "frontier_suit" -> "前線套裝";
            case "fusion_power" -> "聚變發電";
            case "gene_chain" -> "基因鏈";
            case "graviton" -> "重力子";
            case "industrial_tools" -> "工業工具";
            case "logistics" -> "物流";
            case "machine_parts" -> "機器零件";
            case "matter_fabrication" -> "物質合成";
            case "megastructure_control" -> "巨構控制";
            case "megastructure_fabrication" -> "巨構製造";
            case "megastructure_materials" -> "巨構材料";
            case "metal_components" -> "金屬元件";
            case "metal_processing" -> "金屬加工";
            case "modular_suits" -> "模組套裝";
            case "nanite" -> "奈米機械";
            case "neutronium" -> "中子物質";
            case "nyx_suit" -> "倪克斯套裝";
            case "observatory" -> "觀測站";
            case "omega" -> "歐米伽";
            case "orbital" -> "軌道";
            case "orchard_chain" -> "果園鏈";
            case "orchard_drinks" -> "果園飲品";
            case "orchard_meals" -> "果園餐點";
            case "orchard_pastry" -> "果園糕點";
            case "ore_processing" -> "礦物加工";
            case "planet_cuisine" -> "星球料理";
            case "planet_research" -> "星球研究";
            case "planet_surface" -> "星球地表";
            case "plasma_chain" -> "電漿鏈";
            case "polymer_chain" -> "聚合物鏈";
            case "precision_fabrication" -> "精密製造";
            case "quantum_chain" -> "量子鏈";
            case "reactor_structures" -> "反應爐結構";
            case "recycling" -> "回收";
            case "research" -> "研究";
            case "rewards" -> "獎勵";
            case "sensors" -> "感測器";
            case "singularity" -> "奇點";
            case "solar_power" -> "太陽能";
            case "stellar" -> "恆星";
            case "thermal_processing" -> "熱處理";
            case "void" -> "虛空";
            case "warp_chain" -> "曲率鏈";
            case "weather_power" -> "氣象發電";
            case "wiring" -> "電線";
            case "wood_processing" -> "木材加工";
            // ── role ──
            case "achievement_reward" -> "成就獎勵";
            case "actuator_bundle" -> "致動器組";
            case "advanced_assembly" -> "進階裝配";
            case "advanced_circuit" -> "進階電路";
            case "advanced_heat_shield" -> "進階隔熱板";
            case "alloy_smelting" -> "合金冶煉";
            case "anchor_fabrication" -> "錨定鍛造";
            case "antimatter_battery" -> "反物質電池";
            case "antimatter_fuel" -> "反物質燃料";
            case "antimatter_synthesis" -> "反物質合成";
            case "apex_core" -> "巔峰核心";
            case "archive_core" -> "典藏核心";
            case "archive_medium" -> "典藏介質";
            case "archive_storage" -> "典藏儲存";
            case "assembly_component" -> "裝配元件";
            case "aurora_plate" -> "極光板";
            case "backbone_routing" -> "骨幹路由";
            case "balanced_split" -> "均分";
            case "bio_catalyst" -> "生質催化劑";
            case "biomass_processing" -> "生質加工";
            case "biomass_synthesis" -> "生質合成";
            case "bio_template" -> "生質模板";
            case "bread_food" -> "麵包類";
            case "capture_tool" -> "捕捉工具";
            case "central_buffer" -> "中央緩衝";
            case "ceramic_firing" -> "陶瓷燒製";
            case "chemical_synthesis" -> "化學合成";
            case "circuit_component" -> "電路元件";
            case "civilization_forge" -> "文明鍛造";
            case "civilization_matrix" -> "文明矩陣";
            case "compressed_plate" -> "壓縮板";
            case "compression" -> "壓縮";
            case "controlled_growth" -> "可控生長";
            case "coolant_mixing" -> "冷卻劑混合";
            case "cooling_container" -> "冷卻容器";
            case "cooling_core" -> "冷卻核心";
            case "cooling_medium" -> "冷卻介質";
            case "cosmic_assembly" -> "宇宙裝配";
            case "cosmic_matrix" -> "宇宙矩陣";
            case "cosmic_panel" -> "宇宙面板";
            case "crisp_food" -> "酥脆類";
            case "crop_output" -> "作物產出";
            case "crop_production" -> "作物生產";
            case "crop_seed" -> "作物種子";
            case "cryo_bio_sample" -> "低溫生物樣本";
            case "cryo_distillation" -> "低溫蒸餾";
            case "cryo_relic" -> "低溫遺物";
            case "cryo_sample" -> "低溫樣本";
            case "cryo_ward_food" -> "抗寒食品";
            case "crystal_growth" -> "結晶生長";
            case "dash_tool" -> "衝刺工具";
            case "data_component" -> "資料元件";
            case "dense_fabric" -> "高密度織物";
            case "dimension_anchor" -> "維度錨";
            case "drone_assembly" -> "無人機裝配";
            case "dust_material" -> "粉末材料";
            case "endgame_circuit" -> "終局電路";
            case "endgame_core" -> "終局核心";
            case "endgame_fiber" -> "終局纖維";
            case "endgame_fragment" -> "終局碎片";
            case "endgame_generation" -> "終局發電";
            case "endgame_metal" -> "終局金屬";
            case "endgame_optics" -> "終局光學";
            case "endgame_smelting" -> "終局冶煉";
            case "energy_buffer" -> "能源緩衝";
            case "energy_link" -> "能源連結";
            case "energy_relay" -> "能源中繼";
            case "energy_unit" -> "能源單元";
            case "entropy_stabilization" -> "熵穩定";
            case "event_horizon_plate" -> "事件視界板";
            case "exotic_material" -> "異域材料";
            case "exotic_powder" -> "異域粉末";
            case "exotic_sheet" -> "異域薄片";
            case "exotic_weaving" -> "異域編織";
            case "explorer_drink" -> "探索飲品";
            case "field_component" -> "野外元件";
            case "field_forging" -> "野外鍛造";
            case "field_harvest" -> "野外收穫";
            case "filtered_routing" -> "過濾路由";
            case "final_core" -> "最終核心";
            case "final_fabrication" -> "最終製造";
            case "final_showcase" -> "最終展示";
            case "fishing_harvest" -> "漁獲";
            case "flare_module" -> "閃焰模組";
            case "food_output" -> "食品產出";
            case "forestry_tool" -> "林業工具";
            case "frontier_core" -> "前線核心";
            case "frontier_fragment" -> "前線碎片";
            case "fuel_generation" -> "燃料發電";
            case "gene_processing" -> "基因處理";
            case "grapple_tool" -> "牽引工具";
            case "gravity_component" -> "重力元件";
            case "gravity_core" -> "重力核心";
            case "gravity_optics" -> "重力光學";
            case "gravity_stabilization" -> "重力穩定";
            case "growth_tool" -> "催生工具";
            case "guide_book" -> "手冊";
            case "harmonic_crystal" -> "共振結晶";
            case "harvest_tool" -> "收穫工具";
            case "heat_bio_sample" -> "高溫生物樣本";
            case "heat_shield" -> "隔熱板";
            case "high_energy_metal" -> "高能金屬";
            case "high_energy_powder" -> "高能粉末";
            case "hotmeal_food" -> "熱食類";
            case "insulated_wire" -> "絕緣線材";
            case "insulation_forming" -> "絕緣成型";
            case "item_collection" -> "物品收集";
            case "jetpack_tool" -> "噴射工具";
            case "juice_drink" -> "果汁類";
            case "laser_engraving" -> "雷射雕刻";
            case "material_recovery" -> "材料回收";
            case "matter_compilation" -> "物質編譯";
            case "mid_assembly" -> "中階裝配";
            case "mob_harvest" -> "生物收穫";
            case "nanite_cluster" -> "奈米叢集";
            case "nanite_fabrication" -> "奈米製造";
            case "nanite_medium" -> "奈米介質";
            case "network_manifold" -> "網路歧管";
            case "neural_material" -> "神經材料";
            case "optical_crystal" -> "光學結晶";
            case "optical_plate" -> "光學板";
            case "optics_component" -> "光學元件";
            case "orbital_frame" -> "軌道框架";
            case "orbital_printing" -> "軌道列印";
            case "orbital_processor" -> "軌道處理器";
            case "orbital_scanning" -> "軌道掃描";
            case "orchard_fruit" -> "果園水果";
            case "orchard_sapling" -> "果園樹苗";
            case "ore_washing" -> "洗礦";
            case "passive_generation" -> "被動發電";
            case "photon_weaving" -> "光子編織";
            case "pie_food" -> "派類";
            case "planetary_bio_sample" -> "星球生物樣本";
            case "planetary_forging" -> "星球鍛造";
            case "planetary_gateway" -> "星際閘門";
            case "planetary_harvesting" -> "星球收穫";
            case "planetary_relic" -> "星球遺物";
            case "planetary_sample" -> "星球樣本";
            case "planet_survey" -> "星球調查";
            case "plasma_refining" -> "電漿精煉";
            case "precision_material" -> "精密材料";
            case "precision_tool" -> "精密工具";
            case "pressure_forming" -> "加壓成型";
            case "pressure_module" -> "抗壓模組";
            case "pressure_resistant_glass" -> "耐壓玻璃";
            case "protective_casing" -> "防護外殼";
            case "protective_coating" -> "防護鍍層";
            case "protective_gear" -> "防護裝備";
            case "puree_food" -> "泥醬類";
            case "purification" -> "淨化";
            case "quantum_component" -> "量子元件";
            case "quantum_processing" -> "量子處理";
            case "radiation_module" -> "輻射模組";
            case "radiation_ward_food" -> "抗輻射食品";
            case "ration_food" -> "口糧";
            case "reactor_structure" -> "反應爐結構";
            case "refining" -> "精煉";
            case "relic_analysis" -> "遺物分析";
            case "relic_data" -> "遺物資料";
            case "relic_signal" -> "遺物信號";
            case "research_gateway" -> "研究入口";
            case "reward_currency" -> "獎勵貨幣";
            case "safety_filter" -> "安全濾芯";
            case "salad_food" -> "沙拉類";
            case "scaled_generation" -> "規模化發電";
            case "scan_data" -> "掃描資料";
            case "sensing_component" -> "感測元件";
            case "separation" -> "分離";
            case "singularity_compression" -> "奇點壓縮";
            case "singularity_core" -> "奇點核心";
            case "singularity_forging" -> "奇點鍛造";
            case "singularity_medium" -> "奇點介質";
            case "size_reduction" -> "粒度縮減";
            case "smelting" -> "冶煉";
            case "smoothie_drink" -> "冰沙類";
            case "soda_drink" -> "汽水類";
            case "solar_component" -> "太陽能元件";
            case "solar_relic" -> "太陽遺物";
            case "solar_sample" -> "太陽樣本";
            case "solar_ward_food" -> "抗輻射太陽食品";
            case "stability_core" -> "穩定核心";
            case "stability_plate" -> "穩定板";
            case "stellar_forging" -> "恆星鍛造";
            case "stellar_glass" -> "恆星玻璃";
            case "stellar_metal" -> "恆星金屬";
            case "storage_core" -> "儲存核心";
            case "storm_bio_sample" -> "風暴生物樣本";
            case "storm_generation" -> "風暴發電";
            case "storm_module" -> "風暴模組";
            case "storm_relic" -> "風暴遺物";
            case "storm_sample" -> "風暴樣本";
            case "storm_ward_food" -> "抗風暴食品";
            case "structural_frame" -> "結構框架";
            case "structural_plate" -> "結構板";
            case "suit_frame" -> "套裝骨架";
            case "suit_seal" -> "套裝密封";
            case "survey_component" -> "調查元件";
            case "survey_data" -> "調查資料";
            case "tart_food" -> "酥塔類";
            case "temporal_conduit" -> "時序導管";
            case "temporal_core" -> "時序核心";
            case "temporal_engine" -> "時序引擎";
            case "temporal_lattice" -> "時序晶格";
            case "temporal_machining" -> "時序加工";
            case "temporal_shell" -> "時序外殼";
            case "thermal_module" -> "熱控模組";
            case "tonic_drink" -> "調劑類";
            case "transfer_node" -> "傳輸節點";
            case "tree_harvest" -> "伐木收穫";
            case "tube_transport" -> "導管輸送";
            case "vacuum_bio_sample" -> "真空生物樣本";
            case "vacuum_module" -> "真空模組";
            case "vacuum_processing" -> "真空處理";
            case "vacuum_relic" -> "真空遺物";
            case "vacuum_sample" -> "真空樣本";
            case "vacuum_ward_food" -> "抗真空食品";
            case "void_crystal" -> "虛空結晶";
            case "void_extraction" -> "虛空萃取";
            case "void_fragment" -> "虛空碎片";
            case "void_network_core" -> "虛空網路核心";
            case "void_network_relay" -> "虛空網路中繼";
            case "warp_assembly" -> "曲率裝配";
            case "warp_driver" -> "曲率驅動";
            case "warp_power" -> "曲率能源";
            case "wire_drawing" -> "拉線";
            case "wood_cutting" -> "切木";
            case "xeno_alloy" -> "異星合金";
            case "xeno_refining" -> "異星精煉";
            // ── inferred roles (from TechRegistry.inferItemRole) ──
            case "general_item" -> "通用物品";
            case "processing_material" -> "加工材料";
            case "upgrade_part" -> "升級零件";
            case "utility_tool" -> "實用工具";
            case "recycled_material" -> "回收材料";
            case "refined_product" -> "精煉產物";
            case "bio_product" -> "生質產物";
            case "food_product" -> "食品產物";
            default -> null;
        };
    }

    private String localizedDisplayName(final String id, final String rawDisplayName) {
        final String alias = this.knownChineseName(id);
        if (alias != null) {
            return alias;
        }
        if (rawDisplayName != null && rawDisplayName.chars().anyMatch(ch -> Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN)) {
            final int start = this.firstHanIndex(rawDisplayName);
            if (start >= 0) {
                return rawDisplayName.substring(start).trim();
            }
        }
        if (rawDisplayName == null || rawDisplayName.isBlank()) {
            return this.humanize(id);
        }
        return this.localizeInlineTerms(rawDisplayName);
    }

    public String localizeInlineTerms(final String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String localized = text;
        for (final TechItemDefinition item : this.registry.allItems()) {
            localized = this.replaceKnownTerm(localized, item.id(), item.displayName());
        }
        for (final MachineDefinition machine : this.registry.allMachines()) {
            localized = this.replaceKnownTerm(localized, machine.id(), machine.displayName());
        }
        for (final var achievement : this.registry.allAchievements()) {
            localized = this.replaceKnownTerm(localized, achievement.id(), achievement.displayName());
        }
        localized = localized.replace("Starter", "開局");
        localized = localized.replace("First", "初始");
        localized = localized.replace("Energy", "能源");
        localized = localized.replace("Logistics", "物流");
        localized = localized.replace("Upgrade", "升級");
        localized = localized.replace("Advanced", "進階");
        localized = localized.replace("Research", "研究");
        localized = localized.replace("XP", "研究點");
        localized = localized.replace("Dust", "粉");
        localized = localized.replace("Ingot", "錠");
        localized = localized.replace("Plate", "板");
        localized = localized.replace("Cell", "單元");
        localized = localized.replace("Gear", "齒輪");
        localized = localized.replace("Processor", "處理器");
        localized = localized.replace("Matrix", "矩陣");
        localized = localized.replace("Mesh", "網");
        localized = localized.replace("Frame", "框架");
        localized = localized.replace("Filter", "濾芯");
        localized = localized.replace("Lens", "透鏡");
        localized = localized.replace("Module", "模組");
        localized = localized.replace("Fluid", "流體");
        localized = localized.replace("Shard", "碎片");
        localized = localized.replace("Core", "核心");
        localized = localized.replace("Nozzle", "噴嘴");
        localized = localized.replace("Desk", "台");
        localized = localized.replace("Press", "壓床");
        localized = localized.replace("Distiller", "蒸餾器");
        localized = localized.replace("Refiner", "精煉機");
        localized = localized.replace("Collector", "收集器");
        localized = localized.replace("Dock", "釣台");
        localized = localized.replace("Harvester", "收割機");
        localized = localized.replace("Drill", "鑽機");
        localized = localized.replace("Machines", "機器");
        localized = localized.replace("Machine", "機器");
        localized = localized.replace("Farming", "農業");
        localized = localized.replace("Recycling", "回收");
        localized = localized.replace("Chemistry", "化工");
        localized = localized.replace("Circuit", "電路");
        localized = localized.replace("Board", "基板");
        localized = localized.replace("Wiring", "電網");
        localized = localized.replace("Assembly", "裝配");
        localized = localized.replace("Precision", "精密");
        localized = localized.replace("Quantum", "量子");
        localized = localized.replace("Endgame", "終局");
        localized = localized.replace("Network", "網路");
        localized = localized.replace("Troubleshooting", "除錯");
        localized = localized.replace("Factory", "工廠");
        localized = localized.replace("Layout", "布局");
        localized = localized.replace("Server", "伺服器");
        localized = localized.replace("Expansion", "擴充");
        localized = localized.replace("Megastructure", "巨構");
        localized = localized.replace("Aurelia", "奧蕾莉亞");
        localized = localized.replace("Cryon", "克里昂");
        localized = localized.replace("Nyx", "倪克斯");
        localized = localized.replace("Helion", "赫利昂");
        localized = localized.replace("Tempest", "坦佩斯特");
        localized = localized.replace("Frontier", "前線");
        localized = localized.replace("TechBook", "科技書");
        localized = localized.replace("addon", "擴充模組");
        localized = localized.replace("Node", "節點");
        localized = localized.replace("Cable", "線纜");
        localized = localized.replace("Tube", "導管");
        localized = localized.replace("Array", "陣列");
        localized = localized.replace("Tier", "階級");
        localized = localized.replace("Carbon", "碳");
        localized = localized.replace("Copper Ingot", "銅錠");
        localized = localized.replace("Iron Ingot", "鐵錠");
        localized = localized.replace("Copper", "銅");
        localized = localized.replace("Iron", "鐵");
        localized = localized.replace("Lithium", "鋰");
        localized = localized.replace("Bronze", "青銅");
        localized = localized.replace("Redstone", "紅石");
        localized = localized.replace("Lectern", "講台");
        localized = localized.replace("Book", "書");
        localized = localized.replace("Research Desk", "研究台");
        localized = localized.replace("Solar Generator", "太陽能發電機");
        localized = localized.replace("Coal Generator", "燃煤發電機");
        localized = localized.replace("Crusher", "粉碎機");
        localized = localized.replace("Furnace", "熔爐");
        localized = localized.replace("Piston", "活塞");
        localized = localized.replace("TechBook", "科技書");
        localized = localized.replace("Logistics Node", "物流節點");
        localized = localized.replace("Item Tube", "物流導管");
        localized = localized.replace("Energy Node", "能源節點");
        localized = localized.replace("Energy Cable", "導能線纜");
        localized = localized.replace("Battery Bank", "電池庫");
        localized = localized.replace("Auto Farm", "自動農場");
        localized = localized.replace("AutoFarm", "自動農場");
        localized = localized.replace("Greenhouse", "溫室培育艙");
        localized = localized.replace("Bio Lab", "生質實驗室");
        localized = localized.replace("Recycler", "回收機");
        localized = localized.replace("Electric Saw", "電鋸");
        localized = localized.replace("Wire Mill", "拉線機");
        localized = localized.replace("Laser Engraver", "雷射雕刻機");
        localized = localized.replace("Quantum Processor", "量子處理器");
        localized = localized.replace("Void Extractor", "虛空萃取器");
        localized = localized.replace("Graviton Stabilizer", "重力穩定器");
        localized = localized.replace("Singularity Press", "奇點壓製機");
        localized = localized.replace("Photon Weaver", "光子編織機");
        localized = localized.replace("Observatory", "觀測站");
        localized = localized.replace("Data Core", "資料核心");
        localized = localized.replace("Archive Plate", "典藏板");
        localized = localized.replace("Archive Core", "典藏核心");
        localized = localized.replace("Starsteel Foundry", "星鋼鑄造廠");
        localized = localized.replace("Vacuum Chamber", "真空艙");
        localized = localized.replace("Warp Assembler", "曲率裝配機");
        localized = localized.replace("Event Horizon Smith", "事件視界鍛造台");
        localized = localized.replace("Anchor Forge", "錨定鍛爐");
        localized = localized.replace("Continuum Lathe", "連續體車床");
        localized = localized.replace("Apex Forge", "巔峰鍛造爐");
        localized = localized.replace("Omega Archive", "歐米伽典藏庫");
        localized = localized.replace("Speed Upgrade", "速度模組");
        localized = localized.replace("Efficiency Upgrade", "效率模組");
        localized = localized.replace("Stack Upgrade", "堆疊模組");
        localized = localized.replace("Range Upgrade", "範圍模組");
        localized = localized.replace("Vacuum Inlet", "真空入口");
        localized = localized.replace("Storage Hub", "倉儲匯流站");
        localized = localized.replace("Router", "路由器");
        localized = localized.replace("Industrial Bus", "工業總線");
        localized = localized.replace("Tree Feller", "伐木機");
        localized = localized.replace("Mob Collector", "生物收集器");
        localized = localized.replace("Fishing Dock", "自動釣台");
        localized = localized.replace("Harvest Matrix", "收割矩陣");
        localized = localized.replace("Vacuum Core", "真空核心");
        localized = localized.replace("Storm Rotor", "風暴轉子");
        localized = localized.replace("Weather Sensor", "天候感測器");
        localized = localized.replace("Logging Blade", "伐木刀組");
        localized = localized.replace("Arbor Frame", "林業機架");
        localized = localized.replace("Bait Module", "誘餌模組");
        localized = localized.replace("Storage Crate", "儲存箱模組");
        localized = localized.replace("Filter Mesh Core", "過濾核心");
        localized = localized.replace("Routing Chip", "路由晶片");
        localized = localized.replace("Bus Frame", "總線框架");
        localized = localized.replace("Steel Plate", "鋼板");
        localized = localized.replace("Titanium", "鈦");
        localized = localized.replace("Titanium Alloy", "鈦合金");
        localized = localized.replace("Machine Component", "機械零件");
        localized = localized.replace("Reactor", "反應爐");
        localized = localized.replace("Reinforced", "強化");
        localized = localized.replace("Micro", "微型");
        localized = localized.replace("Data", "資料");
        localized = localized.replace("Fiber", "纖維");
        localized = localized.replace("Pressure", "壓力");
        localized = localized.replace("Rust", "鏽蝕");
        localized = localized.replace("Optic", "光學");
        localized = localized.replace("Logic", "邏輯");
        localized = localized.replace("Servo", "伺服");
        localized = localized.replace("Heat", "熱能");
        localized = localized.replace("Drone", "無人機");
        localized = localized.replace("Starlight", "星光");
        localized = localized.replace("Emitter", "發射器");
        localized = localized.replace("Capacitor", "電容");
        localized = localized.replace("Signal", "訊號");
        localized = localized.replace("Composite", "複合");
        localized = localized.replace("Purified", "淨化");
        localized = localized.replace("Grid", "電網");
        localized = localized.replace("Magnetic", "磁力");
        localized = localized.replace("Stability", "穩定");
        localized = localized.replace("Thermal", "熱能");
        localized = localized.replace("Harvest", "收割");
        localized = localized.replace("Rubber", "橡膠");
        localized = localized.replace("Treated", "處理");
        localized = localized.replace("Synthetic", "合成");
        localized = localized.replace("Laser", "雷射");
        localized = localized.replace("Crystal", "晶體");
        localized = localized.replace("Seed", "種子");
        localized = localized.replace("Grown", "培育");
        localized = localized.replace("Matter", "物質");
        localized = localized.replace("etched circuit", "蝕刻電路");
        localized = localized.replace("control unit", "控制單元");
        localized = localized.replace("quantum chip", "量子晶片");
        localized = localized.replace("field emitter", "場域發射器");
        localized = localized.replace("flux link", "通量連結器");
        localized = localized.replace("phase plate", "相位板");
        localized = localized.replace("crops", "作物");
        localized = localized.replace("wood dust", "木屑");
        localized = localized.replace("bio resin", "生質樹脂");
        localized = localized.replace("hydro gel", "水凝膠");
        localized = localized.replace("growth lamp", "生長燈");
        localized = localized.replace("recycled material", "回收材料");
        localized = localized.replace("polymer", "聚合樹脂");
        localized = localized.replace("coolant", "冷卻混合液");
        localized = localized.replace("copper wire", "銅線");
        localized = localized.replace("steel wire", "鋼線");
        localized = localized.replace("void", "虛空");
        localized = localized.replace("graviton", "重力子");
        localized = localized.replace("singularity", "奇點");
        localized = localized.replace("antimatter", "反物質");
        localized = localized.replace("stellar", "星核");
        localized = localized.replace("orbital", "軌道");
        localized = localized.replace("chrono", "時序");
        localized = localized.replace("entropy", "熵變");
        localized = localized.replace("celestial", "天穹");
        localized = localized.replace("Omega Core", "歐米伽核心");
        localized = localized.replace("Cryo Distiller", "低溫蒸餾器");
        localized = localized.replace("Plasma Refiner", "電漿精煉機");
        localized = localized.replace("Reactor Lattice", "反應爐格架");
        localized = localized.replace("Cryo", "低溫");
        localized = localized.replace("Plasma", "電漿");
        localized = localized.replace("data matrix", "資料矩陣");
        localized = localized.replace("Starsteel", "星鋼");
        localized = localized.replace("Warp", "曲率");
        localized = localized.replace("Continuum", "連續體");
        localized = localized.replace("Apex", "巔峰");
        localized = localized.replace("Mk1", "一型");
        localized = localized.replace("Mk2", "二型");
        localized = localized.replace("Mk3", "三型");
        localized = localized.replace("tick", "刻");
        return localized;
    }

    private String replaceKnownTerm(final String text, final String id, final String rawDisplayName) {
        final String alias = this.inlineReplacementAlias(id, rawDisplayName);
        if (alias == null || alias.isBlank()) {
            return text;
        }
        String replaced = text;
        final String humanized = this.humanize(id);
        if (!humanized.equalsIgnoreCase(alias)) {
            replaced = replaced.replace(humanized, alias);
        }
        if (rawDisplayName != null && !rawDisplayName.isBlank()) {
            replaced = replaced.replace(rawDisplayName, alias);
            final int hanIndex = this.firstHanIndex(rawDisplayName);
            if (hanIndex > 0) {
                final String englishPrefix = rawDisplayName.substring(0, hanIndex).trim();
                if (!englishPrefix.isBlank() && !englishPrefix.equalsIgnoreCase(alias)) {
                    replaced = replaced.replace(englishPrefix, alias);
                }
            }
        }
        return replaced;
    }

    private String inlineReplacementAlias(final String id, final String rawDisplayName) {
        final String alias = this.knownChineseName(id);
        if (alias != null && !alias.isBlank()) {
            return alias;
        }
        if (rawDisplayName == null || rawDisplayName.isBlank()) {
            return null;
        }
        final int hanIndex = this.firstHanIndex(rawDisplayName);
        if (hanIndex >= 0) {
            return rawDisplayName.substring(hanIndex).trim();
        }
        return null;
    }

    private int firstHanIndex(final String text) {
        for (int index = 0; index < text.length(); index++) {
            if (Character.UnicodeScript.of(text.charAt(index)) == Character.UnicodeScript.HAN) {
                return index;
            }
        }
        return -1;
    }

    private String knownChineseName(final String id) {
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "tech_book" -> "科技書";
            case "energy_token" -> "能源代幣";
            case "achievement_badge" -> "成就徽章";
            case "first_machine" -> "第一台機器";
            case "energy_beginner" -> "能源新手";
            case "automation_master" -> "自動化大師";
            case "tech_collector" -> "科技收藏家";
            case "recycler_expert" -> "回收專家";
            case "quantum_engineer" -> "量子工程師";
            case "full_factory" -> "滿載工廠";
            case "crop_king" -> "農田之王";
            case "item_conqueror" -> "物品征服者";
            case "completionist" -> "全成就達成者";
            case "iron_dust" -> "鐵粉";
            case "coal_dust" -> "煤粉";
            case "silicon" -> "矽晶";
            case "plastic" -> "塑膠";
            case "steel_plate" -> "鋼板";
            case "quantum_chip" -> "量子晶片";
            case "nano_coating" -> "奈米塗層";
            case "wood_dust" -> "木屑";
            case "iron_plate" -> "鐵板";
            case "recycled_material" -> "回收材料";
            case "crop_seeds" -> "作物種子";
            case "crops" -> "作物";
            case "copper_ingot" -> "銅錠";
            case "copper_dust" -> "銅粉";
            case "carbon_mesh" -> "碳網";
            case "reactor_core" -> "反應核心";
            case "reinforced_glass" -> "強化玻璃";
            case "titanium_alloy" -> "鈦合金";
            case "coolant_cell" -> "冷卻單元";
            case "bronze_gear" -> "青銅齒輪";
            case "data_matrix" -> "資料矩陣";
            case "fiber_mesh" -> "纖維網";
            case "alloy_frame" -> "合金機架";
            case "rust_filter" -> "鏽濾材";
            case "optic_lens" -> "光學透鏡";
            case "plasma_cell" -> "電漿單元";
            case "crystal_matrix" -> "晶體矩陣";
            case "heat_coil" -> "熱線圈";
            case "drone_core" -> "無人機核心";
            case "hydro_gel" -> "水凝膠";
            case "fertilizer_mix" -> "肥料混合物";
            case "bio_fiber" -> "生質纖維";
            case "quantum_fluid" -> "量子流體";
            case "starlight_plate" -> "星光板";
            case "fusion_mesh" -> "聚變網";
            case "vacuum_tube" -> "真空管";
            case "composite_panel" -> "複合板";
            case "flux_link" -> "通量連結器";
            case "ore_slurry" -> "礦漿";
            case "purified_shard" -> "淨化碎晶";
            case "phase_plate" -> "相位板";
            case "grid_module" -> "電網模組";
            case "field_emitter" -> "場域發射器";
            case "ion_dust" -> "離子粉";
            case "magnetic_ring" -> "磁環";
            case "stability_core" -> "穩定核心";
            case "thermal_shell" -> "耐熱外殼";
            case "crushed_biomass" -> "生質碎料";
            case "seed_cluster" -> "種子簇";
            case "harvest_unit" -> "收割單元";
            case "growth_lamp" -> "生長燈";
            case "quantum_frame" -> "量子框架";
            case "polymer_resin" -> "聚合樹脂";
            case "treated_plastic" -> "處理塑膠";
            case "synthetic_fiber" -> "合成纖維";
            case "coolant_mix" -> "冷卻混合液";
            case "refined_oil" -> "精煉油";
            case "laser_lens" -> "雷射透鏡";
            case "drone_shell" -> "無人機外殼";
            case "drone_frame" -> "無人機骨架";
            case "fusion_core" -> "聚變核心";
            case "crystal_seed" -> "晶種";
            case "grown_crystal" -> "成長晶體";
            case "field_plate" -> "場域板";
            case "matter_blob" -> "物質團";
            case "singularity_fragment" -> "奇點碎片";
            case "precision_nozzle" -> "精密噴嘴";
            case "speed_upgrade" -> "速度模組";
            case "efficiency_upgrade" -> "效率模組";
            case "stack_upgrade" -> "堆疊模組";
            case "range_upgrade" -> "範圍模組";
            case "lumenfruit_sapling" -> "流明果苗";
            case "lumenfruit" -> "流明果";
            case "frost_apple_sapling" -> "霜蘋幼株";
            case "frost_apple" -> "霜蘋果";
            case "shadow_berry_sapling" -> "幽影莓苗";
            case "shadow_berry_cluster" -> "幽影莓串";
            case "sunflare_fig_sapling" -> "曜焰無花果苗";
            case "sunflare_fig" -> "曜焰無花果";
            case "stormplum_sapling" -> "雷莓李苗";
            case "stormplum" -> "雷莓李";
            case "fruit_puree" -> "異星果泥";
            case "nebula_juice" -> "星霧果飲";
            case "orchard_ration" -> "果園後勤口糧";
            case "drill_head" -> "鑽頭組";
            case "quarry_frame" -> "採礦機架";
            case "harvest_matrix" -> "收割矩陣";
            case "vacuum_core" -> "真空核心";
            case "storm_rotor" -> "風暴轉子";
            case "weather_sensor" -> "天候感測器";
            case "field_processor" -> "場域處理器";
            case "logging_blade" -> "伐木刀組";
            case "arbor_frame" -> "林業機架";
            case "net_launcher" -> "捕網發射器";
            case "bait_module" -> "誘餌模組";
            case "dock_frame" -> "釣台骨架";
            case "storage_crate" -> "儲存箱模組";
            case "routing_chip" -> "路由晶片";
            case "filter_mesh_core" -> "過濾核心";
            case "bus_frame" -> "總線框架";
            case "splitter_core" -> "分流核心";
            case "machine_component" -> "機械零件";
            case "circuit_board" -> "電路板";
            case "advanced_circuit" -> "進階電路";
            case "logic_gate" -> "邏輯閘";
            case "servo_motor" -> "伺服馬達";
            case "machine_casing" -> "機器外殼";
            case "signal_relay" -> "訊號中繼器";
            case "agri_module" -> "農業模組";
            case "bio_resin" -> "生質樹脂";
            case "pressure_tube" -> "壓力導管";
            case "control_unit" -> "控制單元";
            case "etched_circuit" -> "蝕刻電路";
            case "micro_processor" -> "微型處理器";
            case "copper_wire" -> "銅線";
            case "steel_wire" -> "鋼線";
            case "glass_panel" -> "玻璃板";
            case "energy_cell" -> "能量電池";
            case "lithium_cell" -> "鋰電池";
            case "emitter_node" -> "發射節點";
            case "capacitor_bank" -> "電容組";
            case "crusher" -> "粉碎機";
            case "furnace" -> "熔爐";
            case "auto_farm" -> "自動農場";
            case "electric_saw" -> "電鋸";
            case "recycler" -> "回收機";
            case "solar_generator" -> "太陽能發電機";
            case "coal_generator" -> "燃煤發電機";
            case "compressor" -> "壓縮機";
            case "assembler" -> "製造機";
            case "quantum_processor" -> "量子處理器";
            case "smeltery" -> "合金熔煉爐";
            case "advanced_assembler" -> "進階製造機";
            case "solar_array" -> "太陽能陣列";
            case "battery_bank" -> "電池庫";
            case "purifier" -> "淨化器";
            case "centrifuge" -> "離心機";
            case "ore_washer" -> "洗礦機";
            case "bio_lab" -> "生質實驗室";
            case "greenhouse" -> "溫室培育艙";
            case "polymer_press" -> "聚合壓床";
            case "wire_mill" -> "拉線機";
            case "chemical_reactor" -> "化學反應器";
            case "coolant_mixer" -> "冷卻混合器";
            case "refinery" -> "精煉塔";
            case "laser_engraver" -> "雷射雕刻機";
            case "drone_bay" -> "無人機艙";
            case "fusion_reactor" -> "聚變反應爐";
            case "crystal_growth_chamber" -> "晶體培育艙";
            case "field_forge" -> "力場鍛造台";
            case "matter_compiler" -> "物質編譯器";
            case "energy_node" -> "能源節點";
            case "energy_cable" -> "導能線纜";
            case "logistics_node" -> "物流節點";
            case "item_tube" -> "物流導管";
            case "research_desk" -> "研究台";
            case "quarry_drill" -> "採礦鑽機";
            case "quarry_drill_mk2" -> "採礦鑽機二型";
            case "quarry_drill_mk3" -> "採礦鑽機三型";
            case "crop_harvester" -> "作物收割機";
            case "vacuum_inlet" -> "真空吸入口";
            case "storm_turbine" -> "風暴渦輪機";
            case "tree_feller" -> "伐木機";
            case "mob_collector" -> "生物收集器";
            case "fishing_dock" -> "自動釣台";
            case "storage_hub" -> "倉儲匯流站";
            case "filter_router" -> "過濾路由器";
            case "splitter_node" -> "分流節點";
            case "industrial_bus" -> "工業總線";
            case "arcane_circuit_circle" -> "奧術電路法陣";
            case "storm_matrix_obelisk" -> "風暴矩陣方尖碑";
            case "quantum_gate_lattice" -> "量子門晶格";
            default -> null;
        };
    }

    private Component colored(final String text, final TextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    public ItemStack tagGuiAction(final ItemStack stack, final String action) {
        final ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(this.guiActionKey, PersistentDataType.STRING, action);
        stack.setItemMeta(meta);
        return stack;
    }

    public ItemStack tagGuiPlaceholder(final ItemStack stack) {
        final ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(this.guiPlaceholderKey, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return stack;
    }

    public String getTechItemId(final ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(this.techItemKey, PersistentDataType.STRING);
    }

    public String getMachineId(final ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(this.machineKey, PersistentDataType.STRING);
    }

    public long getStoredMachineEnergy(final ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return 0L;
        }
        final Long stored = stack.getItemMeta().getPersistentDataContainer().get(this.machineEnergyKey, PersistentDataType.LONG);
        return stored == null ? 0L : Math.max(0L, stored);
    }

    public String getGuiAction(final ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(this.guiActionKey, PersistentDataType.STRING);
    }

    public boolean isGuiPlaceholder(final ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        final Byte marker = stack.getItemMeta().getPersistentDataContainer().get(this.guiPlaceholderKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    public boolean hasTechBookTag(final ItemStack stack) {
        if ("tech_book".equalsIgnoreCase(this.getTechItemId(stack))) {
            return true;
        }
        if (stack == null || !stack.hasItemMeta() || stack.getItemMeta().displayName() == null) {
            return false;
        }
        final String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(stack.getItemMeta().displayName());
        return plain.equalsIgnoreCase("科技書") || plain.equalsIgnoreCase("TechBook");
    }

    public boolean hasFullUnlockBookTag(final ItemStack stack) {
        return "full_unlock_book".equalsIgnoreCase(this.getTechItemId(stack));
    }

    // ═══════════════════ 物品版本 + 自動刷新 ═══════════════════

    /**
     * 目前的物品資料版本。
     * <p>
     * 每次修改任何物品/機器的 lore、名稱、描述、屬性時，遞增此值。
     * 持有舊版本物品的玩家在下次取出/拾取時會自動更新外觀。
     * </p>
     */
    public int currentItemDataVersion() {
        return this.plugin.getConfig().getInt("item-data-version", 1);
    }

    /**
     * 檢查物品的 PDC 版本號，若低於目前版本則自動重新套用 displayName + lore。
     * <p>
     * 保留物品的 ID、儲能、堆疊數量等狀態不變，只更新外觀文字。
     * </p>
     *
     * @return true 如果物品被更新了
     */
    public boolean refreshTechItemIfNeeded(final ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        final ItemMeta meta = stack.getItemMeta();
        final Integer storedVersion = meta.getPersistentDataContainer().get(this.dataVersionKey, PersistentDataType.INTEGER);
        final int current = this.currentItemDataVersion();

        // 沒有版本標記的舊物品 → 視為版本 0 → 需要更新
        if (storedVersion != null && storedVersion >= current) {
            return false;
        }

        // 嘗試以科技物品刷新
        final String techId = this.getTechItemId(stack);
        if (techId != null) {
            final var definition = this.registry.getItem(techId);
            if (definition == null) {
                return false; // 已刪除的物品不處理
            }
            return this.refreshTechItemLore(stack, definition, current);
        }

        // 嘗試以機器刷新
        final String machineId = this.getMachineId(stack);
        if (machineId != null) {
            final var definition = this.registry.getMachine(machineId);
            if (definition == null) {
                return false;
            }
            return this.refreshMachineItemLore(stack, definition, current);
        }

        return false;
    }

    private boolean refreshTechItemLore(final ItemStack stack, final TechItemDefinition definition, final int newVersion) {
        final ItemMeta meta = stack.getItemMeta();
        // 保留儲能
        final Long energy = meta.getPersistentDataContainer().get(this.techItemEnergyKey, PersistentDataType.LONG);

        // 重新套用標題
        meta.displayName(this.schemaTitle(definition.tier(), definition.visualTier(), "✦ " + this.displayNameForId(definition.id())));

        // 重新產生 lore
        final List<Component> lore = new ArrayList<>();
        lore.add(this.colored("┌─────────────────────────┐", MUTED));
        lore.add(this.tierLine(definition.tier()));
        lore.add(this.visualTierLine(definition.visualTier()));
        lore.add(this.secondary("◈ 分類：" + definition.guideCategory().displayName()));
        lore.add(this.systemLine(definition.systemGroup()));
        lore.add(this.itemClassLine(definition.itemClass()));
        lore.add(this.acquisitionLine(definition.acquisitionMode()));
        if (definition.family() != null && !definition.family().isBlank() && !definition.family().equalsIgnoreCase(definition.id())) {
            lore.add(this.muted("  ▸ 系列：" + this.localizeChainedId(definition.family())));
        }
        if (definition.role() != null && !definition.role().isBlank()) {
            lore.add(this.muted("  ▸ 定位：" + this.localizeChainedId(definition.role())));
        }
        lore.add(this.colored("├─────────────────────────┤", MUTED));
        lore.add(this.colored("  ◇ " + this.localizeInlineTerms(definition.description()), TextColor.color(0xD4E4F7)));
        lore.add(this.colored("  ◆ 解鎖：" + this.formatUnlockRequirement(definition.unlockRequirement()), TextColor.color(0xB8CFEA)));
        if (!definition.useCases().isEmpty()) {
            lore.add(this.colored("├─────────────────────────┤", MUTED));
            for (final String useCase : definition.useCases()) {
                lore.add(this.detailBullet(definition.visualTier(), useCase));
            }
        }
        final long maxEnergy = this.maxItemEnergy(definition.id());
        if (maxEnergy > 0L) {
            lore.add(this.colored("├─────────────────────────┤", MUTED));
            final long currentEnergy = energy != null ? Math.min(energy, maxEnergy) : maxEnergy;
            lore.add(this.itemEnergyBarLine(currentEnergy, maxEnergy));
            lore.add(this.colored("  ▸ 蹲下+右鍵 對準儲能機器充電", TextColor.color(0x8AACCC)));
        }
        lore.add(this.colored("└─────────────────────────┘", MUTED));
        meta.lore(lore);

        // 更新版本戳
        meta.getPersistentDataContainer().set(this.dataVersionKey, PersistentDataType.INTEGER, newVersion);
        stack.setItemMeta(meta);
        return true;
    }

    private boolean refreshMachineItemLore(final ItemStack stack, final MachineDefinition definition, final int newVersion) {
        final ItemMeta meta = stack.getItemMeta();
        // 保留儲能
        final Long energy = meta.getPersistentDataContainer().get(this.machineEnergyKey, PersistentDataType.LONG);
        final long storedEnergy = energy != null ? energy : 0L;

        // 重新套用
        this.applyMachineMeta(meta, definition, storedEnergy);
        meta.getPersistentDataContainer().set(this.dataVersionKey, PersistentDataType.INTEGER, newVersion);
        stack.setItemMeta(meta);
        return true;
    }

    public Component tierName(final TechTier tier, final String text) {
        return this.colored(text, this.tierColor(tier));
    }

    public Component tierLine(final TechTier tier) {
        return this.colored("  ★ 科技階級：" + this.tierLabel(tier), this.tierColor(tier));
    }

    private TextColor visualTierColor(final VisualTier visualTier) {
        return switch (visualTier) {
            case VANILLA -> MUTED;
            case TECH -> MID_TIER;
            case ADVANCED -> HIGH_TIER;
            case ENDGAME -> DANGER;
        };
    }

    private TextColor systemColor(final SystemGroup group) {
        return switch (group) {
            case BOOTSTRAP -> SUCCESS;
            case ENERGY -> WARNING;
            case PROCESSING -> SECONDARY;
            case AGRI_BIO -> TextColor.color(0x7CD992);
            case FIELD_AUTOMATION -> TextColor.color(0x63D2FF);
            case LOGISTICS -> TextColor.color(0x4ED0C8);
            case QUANTUM_PRECISION -> HIGH_TIER;
            case ENDGAME, MEGASTRUCTURE -> DANGER;
            case SPECIAL -> PRIMARY;
        };
    }

    private TextColor itemClassColor(final ItemClass itemClass) {
        return switch (itemClass) {
            case VANILLA_RESOURCE -> MUTED;
            case TECH_MATERIAL -> SECONDARY;
            case CORE_COMPONENT -> HIGH_TIER;
            case ENDGAME_COMPONENT -> DANGER;
            case SPECIAL -> PRIMARY;
        };
    }

    private TextColor archetypeColor(final MachineArchetype archetype) {
        return switch (archetype) {
            case PROCESSOR -> SECONDARY;
            case GENERATOR -> WARNING;
            case RELAY -> TextColor.color(0x4ED0C8);
            case STORAGE -> TextColor.color(0x5DA9FF);
            case FIELD -> SUCCESS;
            case RESEARCH -> PRIMARY;
            case RITUAL -> DANGER;
        };
    }

    private TextColor acquisitionColor(final AcquisitionMode acquisitionMode) {
        return switch (acquisitionMode) {
            case ADVANCED_WORKBENCH -> SECONDARY;
            case MACHINE_ASSEMBLY -> HIGH_TIER;
            case FIELD_COLLECTION -> SUCCESS;
            case RITUAL -> DANGER;
            case RESEARCH_REWARD -> PRIMARY;
            case UNCRAFTABLE_PREVIEW -> MUTED;
        };
    }

    private TextColor tierColor(final TechTier tier) {
        return switch (tier) {
            case TIER1 -> BASIC_TIER;
            case TIER2 -> MID_TIER;
            case TIER3 -> HIGH_TIER;
            case TIER4 -> SPECIAL_TIER;
        };
    }

    private String tierLabel(final TechTier tier) {
        return switch (tier) {
            case TIER1 -> "第一階：基礎";
            case TIER2 -> "第二階：低階";
            case TIER3 -> "第三階：中階";
            case TIER4 -> "第四階：高階";
        };
    }

    public NamespacedKey techItemKey() {
        return this.techItemKey;
    }

    public NamespacedKey machineKey() {
        return this.machineKey;
    }

    public NamespacedKey machineEnergyKey() {
        return this.machineEnergyKey;
    }

    public NamespacedKey guiActionKey() {
        return this.guiActionKey;
    }

    public long maxItemEnergy(final String itemId) {
        return switch (itemId == null ? "" : itemId.toLowerCase(java.util.Locale.ROOT)) {
            case "vector_grapple" -> 80L;
            case "pulse_thruster" -> 120L;
            case "storm_jetpack" -> 200L;
            case "pulse_staff", "cryo_wand" -> 60L;
            case "storm_staff", "gravity_staff" -> 80L;
            case "warp_orb" -> 100L;
            case "plasma_lance", "time_dilator", "entropy_scepter" -> 100L;
            case "void_mirror" -> 120L;
            case "heal_beacon" -> 150L;
            default -> 0L;
        };
    }

    public long getItemStoredEnergy(final ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return 0L;
        }
        final Long stored = stack.getItemMeta().getPersistentDataContainer().get(this.techItemEnergyKey, PersistentDataType.LONG);
        return stored == null ? 0L : Math.max(0L, stored);
    }

    public void setItemStoredEnergy(final ItemStack stack, final long energy) {
        if (stack == null) {
            return;
        }
        final ItemMeta meta = stack.getItemMeta();
        final long clamped = Math.max(0L, energy);
        meta.getPersistentDataContainer().set(this.techItemEnergyKey, PersistentDataType.LONG, clamped);
        final String itemId = this.getTechItemId(stack);
        final long maxEnergy = this.maxItemEnergy(itemId);
        if (maxEnergy > 0L) {
            this.refreshItemEnergyLore(meta, clamped, maxEnergy);
        }
        stack.setItemMeta(meta);
    }

    public boolean drainItemEnergy(final ItemStack stack, final long amount) {
        final long current = this.getItemStoredEnergy(stack);
        if (current < amount) {
            return false;
        }
        this.setItemStoredEnergy(stack, current - amount);
        return true;
    }

    /**
     * 產生物品電量進度條 lore 行。
     * 格式：⚡ 電量 ██████████░░░░░░░░░░ 40/80 EU
     */
    public Component itemEnergyBarLine(final long current, final long max) {
        final int totalBars = 20;
        final int filled = max <= 0L ? 0 : (int) Math.round((double) current / max * totalBars);
        final int empty = totalBars - filled;
        final StringBuilder bar = new StringBuilder();
        bar.append("  ⚡ 電量 ");
        bar.append("█".repeat(Math.max(0, filled)));
        bar.append("░".repeat(Math.max(0, empty)));
        bar.append(" ").append(current).append("/").append(max).append(" EU");
        final TextColor barColor;
        final double ratio = max <= 0L ? 0.0 : (double) current / max;
        if (ratio > 0.6) {
            barColor = SUCCESS;
        } else if (ratio > 0.25) {
            barColor = WARNING;
        } else {
            barColor = DANGER;
        }
        return this.colored(bar.toString(), barColor);
    }

    /**
     * 就地刷新 ItemMeta 中的電量進度條 lore 行。
     * 尋找含有 "⚡ 電量" 的行並替換，若找不到則在 └ 行之前插入。
     */
    private void refreshItemEnergyLore(final ItemMeta meta, final long current, final long max) {
        final List<Component> lore = meta.lore();
        if (lore == null || lore.isEmpty()) {
            return;
        }
        final Component newBar = this.itemEnergyBarLine(current, max);
        final List<Component> updated = new ArrayList<>(lore);
        int energyBarIndex = -1;
        int closingBorderIndex = -1;
        for (int i = 0; i < updated.size(); i++) {
            final String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(updated.get(i));
            if (plain.contains("⚡ 電量")) {
                energyBarIndex = i;
            }
            if (plain.contains("└")) {
                closingBorderIndex = i;
            }
        }
        if (energyBarIndex >= 0) {
            updated.set(energyBarIndex, newBar);
        } else if (closingBorderIndex >= 0) {
            updated.add(closingBorderIndex, newBar);
        } else {
            updated.add(newBar);
        }
        meta.lore(updated);
    }
}
