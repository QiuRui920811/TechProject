package com.rui.techproject.service;

import com.rui.techproject.TechProjectPlugin;
import com.rui.techproject.model.MachineDefinition;
import com.rui.techproject.model.MachineRecipe;
import com.rui.techproject.model.TechTier;
import com.rui.techproject.util.ItemFactoryUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BlueprintService {
    private final TechRegistry registry;
    private final ItemFactoryUtil itemFactory;
    private final Map<String, BlueprintEntry> entries = new LinkedHashMap<>();

    public BlueprintService(final TechProjectPlugin plugin,
                            final TechRegistry registry,
                            final ItemFactoryUtil itemFactory) {
        this.registry = registry;
        this.itemFactory = itemFactory;
        this.load(plugin, "tech-blueprints.yml");
        this.ensureCraftableMachineBlueprints();
        this.unregisterRecipes(plugin);
    }

    public BlueprintEntry get(final String id) {
        return this.entries.get(this.normalize(id));
    }

    public void reload(final TechProjectPlugin plugin) {
        this.entries.clear();
        this.load(plugin, "tech-blueprints.yml");
        this.ensureCraftableMachineBlueprints();
        this.unregisterRecipes(plugin);
    }

    private void load(final TechProjectPlugin plugin, final String resourcePath) {
        final File externalFile = new File(plugin.getDataFolder(), resourcePath);
        if (externalFile.isFile()) {
            final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(externalFile);
            this.loadBlueprintEntries(yaml);
            return;
        }
        final var resource = plugin.getResource(resourcePath);
        if (resource == null) {
            return;
        }
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8));
        this.loadBlueprintEntries(yaml);
    }

    private void loadBlueprintEntries(final YamlConfiguration yaml) {
        final ConfigurationSection section = yaml.getConfigurationSection("blueprints");
        if (section == null) {
            return;
        }
        for (final String key : section.getKeys(false)) {
            final String path = key + ".";
            final List<String> ingredients = new ArrayList<>();
            final ConfigurationSection ingredientSection = section.getConfigurationSection(path + "ingredients");
            if (ingredientSection != null) {
                for (final String symbol : ingredientSection.getKeys(false)) {
                    ingredients.add(symbol + " = " + this.displayIngredientName(ingredientSection.getString(symbol, "AIR")));
                }
            }
            final List<String> ingredientLines = section.getStringList(path + "ingredient-lines");
            this.entries.put(this.normalize(key), new BlueprintEntry(
                    key,
                    section.getBoolean(path + "register-recipe", true),
                    section.getStringList(path + "shape"),
                    yaml.getConfigurationSection("blueprints." + key + ".ingredients"),
                    ingredientLines.isEmpty() ? ingredients : ingredientLines,
                    section.getStringList(path + "tutorial"),
                    section.getStringList(path + "placement")
            ));
        }
    }

    private void unregisterRecipes(final TechProjectPlugin plugin) {
        for (final BlueprintEntry entry : this.entries.values()) {
            if (!entry.registerRecipe()) {
                continue;
            }
            final NamespacedKey key = new NamespacedKey(plugin, "machine_" + this.normalize(entry.id()));
            Bukkit.removeRecipe(key);
        }
    }

    private void ensureCraftableMachineBlueprints() {
        for (final MachineDefinition machine : this.registry.allMachines()) {
            final String key = this.normalize(machine.id());
            final BlueprintEntry existing = this.entries.get(key);
            if (existing != null && !existing.registerRecipe()) {
                continue;
            }
            final List<MachineRecipe> outputRecipes = this.registry.getRecipesForOutput(machine.id());
            if (existing != null && existing.registerRecipe() && !existing.shape().isEmpty() && existing.ingredientSection() != null) {
                continue;
            }
            if (!outputRecipes.isEmpty()) {
                this.entries.put(key, this.buildRecipeGuideBlueprint(machine, existing, outputRecipes.get(0)));
                continue;
            }
            this.entries.put(key, this.buildFallbackBlueprint(machine, existing));
        }
    }

    private BlueprintEntry buildRecipeGuideBlueprint(final MachineDefinition machine,
                                                     final BlueprintEntry existing,
                                                     final MachineRecipe recipe) {
        final List<String> ingredientLines = existing != null && !existing.ingredients().isEmpty()
                ? new ArrayList<>(existing.ingredients())
                : new ArrayList<>();
        if (ingredientLines.isEmpty()) {
            ingredientLines.add("製作站：" + this.itemFactory.displayNameForId(recipe.machineId()));
            ingredientLines.add("主材料：" + this.itemFactory.joinDisplayNames(recipe.inputIds(), " + "));
        }
        final List<String> tutorial = existing != null && !existing.tutorial().isEmpty()
                ? existing.tutorial()
                : List.of(
                "此機器不是直接在進階工作台搓出來，而是要先推進對應產線。",
                "先完成前置材料，再到指定機器內組裝本體。"
        );
        final List<String> placement = existing != null && !existing.placement().isEmpty()
                ? existing.placement()
                : List.of("建議先把供電與輸出空間準備好，再放下本體接入產線。\n");
        return new BlueprintEntry(
                machine.id(),
                false,
                List.of(),
                null,
                ingredientLines,
                tutorial,
                placement
        );
    }

    private BlueprintEntry buildFallbackBlueprint(final MachineDefinition machine, final BlueprintEntry existing) {
        final YamlConfiguration generated = new YamlConfiguration();
        final ConfigurationSection ingredientSection = generated.createSection("ingredients");
        final Map<Character, String> ingredients = this.fallbackIngredients(machine);
        for (final var ingredient : ingredients.entrySet()) {
            ingredientSection.set(String.valueOf(ingredient.getKey()), ingredient.getValue());
        }

        final List<String> ingredientLines = new ArrayList<>();
        for (final var ingredient : ingredients.entrySet()) {
            ingredientLines.add(ingredient.getKey() + " = " + this.fallbackMaterialLabel(ingredient.getValue()));
        }

        final List<String> tutorial = existing != null && !existing.tutorial().isEmpty()
                ? existing.tutorial()
                : List.of(
                "此機器原先缺少可實作藍圖，已補上通用科技藍圖。",
                "必須先解鎖對應機器，才會在進階工作台顯示可製作結果。"
        );
        final List<String> placement = existing != null && !existing.placement().isEmpty()
                ? existing.placement()
                : List.of("右鍵後可查看機器狀態、輸入輸出與運作資訊。");

        return new BlueprintEntry(
                machine.id(),
                true,
                this.fallbackShape(machine.tier()),
                ingredientSection,
                ingredientLines,
                tutorial,
                placement
        );
    }

    private List<String> fallbackShape(final TechTier tier) {
        return switch (tier) {
            case TIER1 -> List.of("IRI", "RMR", "IPI");
            case TIER2 -> List.of("CGC", "SMS", "OPO");
            case TIER3 -> List.of("ADA", "SMS", "QNQ");
            case TIER4 -> List.of("FCF", "NMN", "QCQ");
        };
    }

    private Map<Character, String> fallbackIngredients(final MachineDefinition machine) {
        final Map<Character, String> ingredients = new LinkedHashMap<>();
        final String block = machine.blockMaterial() == null || machine.blockMaterial() == Material.AIR
                ? Material.IRON_BLOCK.name()
                : machine.blockMaterial().name();
        ingredients.put('M', block);
        switch (machine.tier()) {
            case TIER1 -> {
                ingredients.put('I', "vanilla:" + Material.IRON_INGOT.name());
                ingredients.put('R', "vanilla:" + Material.REDSTONE.name());
                ingredients.put('P', "vanilla:" + Material.PISTON.name());
            }
            case TIER2 -> {
                ingredients.put('C', "tech:copper_ingot");
                ingredients.put('G', "vanilla:" + Material.GLASS.name());
                ingredients.put('S', "tech:steel_plate");
                ingredients.put('O', "tech:machine_component");
                ingredients.put('P', "vanilla:" + Material.PISTON.name());
            }
            case TIER3 -> {
                ingredients.put('A', "tech:advanced_circuit");
                ingredients.put('D', "vanilla:" + Material.DIAMOND.name());
                ingredients.put('S', "tech:machine_component");
                ingredients.put('Q', "tech:quantum_chip");
                ingredients.put('N', "tech:nano_coating");
            }
            case TIER4 -> {
                ingredients.put('F', "tech:fusion_core");
                ingredients.put('C', "tech:field_plate");
                ingredients.put('N', "vanilla:" + Material.NETHER_STAR.name());
                ingredients.put('Q', "tech:quantum_chip");
            }
        }
        return ingredients;
    }

    private String fallbackMaterialLabel(final String rawMaterial) {
        return this.displayIngredientName(rawMaterial);
    }

    public boolean isAdvancedWorkbench(final Location location) {
        if (location == null) {
            return false;
        }
        return location.getBlock().getType() == Material.CRAFTING_TABLE
                && location.getBlock().getRelative(0, -1, 0).getType() == Material.IRON_BLOCK;
    }

    public BlueprintMatch matchCraftingMatrix(final ItemStack[] matrix) {
        if (matrix == null || matrix.length < 9) {
            return null;
        }
        for (final BlueprintEntry entry : this.entries.values()) {
            if (!entry.registerRecipe() || entry.shape().isEmpty() || entry.ingredientSection() == null) {
                continue;
            }
            final MachineDefinition machine = this.registry.getMachine(entry.id());
            if (machine == null) {
                continue;
            }
            if (this.matches(entry, matrix)) {
                return new BlueprintMatch(entry, machine);
            }
        }
        return null;
    }

    private boolean matches(final BlueprintEntry entry, final ItemStack[] matrix) {
        for (int row = 0; row < 3; row++) {
            final String shapeRow = entry.shape().size() > row ? entry.shape().get(row) : "";
            for (int column = 0; column < 3; column++) {
                final char symbol = shapeRow.length() > column ? shapeRow.charAt(column) : ' ';
                final ItemStack stack = matrix[(row * 3) + column];
                if (!this.matchesSymbol(entry, symbol, stack)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean matchesSymbol(final BlueprintEntry entry, final char symbol, final ItemStack stack) {
        if (symbol == ' ') {
            return stack == null || stack.getType() == Material.AIR;
        }
        final String token = this.normalize(entry.ingredientSection().getString(String.valueOf(symbol), "AIR"));
        if (token.isBlank() || token.equals("air")) {
            return stack == null || stack.getType() == Material.AIR;
        }
        if (token.startsWith("tech:")) {
            final String techId = this.itemFactory.getTechItemId(stack);
            return techId != null && this.normalize(techId).equals(token.substring(5));
        }
        if (token.startsWith("machine:")) {
            final String machineId = this.itemFactory.getMachineId(stack);
            return machineId != null && this.normalize(machineId).equals(token.substring(8));
        }
        final String materialToken = token.startsWith("vanilla:") ? token.substring(8) : token;
        final Material material = Material.matchMaterial(materialToken.toUpperCase(Locale.ROOT));
        if (material == null || material == Material.AIR) {
            return false;
        }
        if (stack == null || stack.getType() != material) {
            return false;
        }
        if (this.itemFactory.getMachineId(stack) != null) {
            return false;
        }
        final String techId = this.itemFactory.getTechItemId(stack);
        if (techId == null) {
            return true;
        }
        // 允許 tech 標記物品在 tech ID 與材料名稱一致時匹配
        // 例：tech:copper_ingot 匹配 COPPER_INGOT 配方格
        return this.normalize(techId).equals(this.normalize(materialToken));
    }

    private String displayIngredientName(final String rawToken) {
        final String token = this.normalize(rawToken);
        if (token.isBlank() || token.equals("air")) {
            return "AIR";
        }
        if (token.startsWith("tech:")) {
            return this.itemFactory.displayNameForId(token.substring(5));
        }
        if (token.startsWith("machine:")) {
            return this.itemFactory.displayNameForId(token.substring(8));
        }
        final String materialToken = token.startsWith("vanilla:") ? token.substring(8) : token;
        final Material material = Material.matchMaterial(materialToken.toUpperCase(Locale.ROOT));
        return material == null ? this.itemFactory.displayNameForId(materialToken) : this.itemFactory.displayNameForMaterial(material);
    }

    private String normalize(final String input) {
        return input == null ? "" : input.toLowerCase(Locale.ROOT).trim();
    }

    public record BlueprintEntry(String id,
                                 boolean registerRecipe,
                                 List<String> shape,
                                 ConfigurationSection ingredientSection,
                                 List<String> ingredients,
                                 List<String> tutorial,
                                 List<String> placement) {
        public List<String> ingredients() {
            return Collections.unmodifiableList(this.ingredients);
        }

        public List<String> tutorial() {
            return Collections.unmodifiableList(this.tutorial);
        }

        public List<String> placement() {
            return Collections.unmodifiableList(this.placement);
        }
    }

    public record BlueprintMatch(BlueprintEntry entry, MachineDefinition machine) {
    }
}
