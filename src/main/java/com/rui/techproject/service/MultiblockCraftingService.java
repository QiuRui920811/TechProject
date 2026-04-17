package com.rui.techproject.service;

import com.rui.techproject.TechMCPlugin;
import com.rui.techproject.util.ItemFactoryUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Dispenser;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Slimefun 風格的多方塊結構合成系統。
 *
 * <p>玩家用原版方塊在世界中搭出結構，把材料放入發射器後右鍵觸發合成。
 * 結構以發射器為核心，上方放指定方塊決定合成台類型。
 *
 * <p>四種多方塊結構：
 * <ul>
 *   <li><b>研磨石</b> — 橡木柵欄 + 發射器：礦石研磨成粉</li>
 *   <li><b>合金冶煉爐</b> — 地獄磚台階 + 發射器 + 鄰近有火：合金冶煉</li>
 *   <li><b>壓力室</b> — 平滑石台階 + 發射器：壓縮板材</li>
 *   <li><b>護甲鍛造台</b> — 鐵砧 + 發射器：特殊護甲</li>
 * </ul>
 */
public final class MultiblockCraftingService {

    /* ---- 多方塊類型 ID ---- */
    public static final String GRIND_STONE = "grind_stone";
    public static final String SMELTERY = "smeltery";
    public static final String PRESSURE_CHAMBER = "pressure_chamber";
    public static final String ARMOR_FORGE = "armor_forge";

    /* ---- 多方塊中文名 ---- */
    private static final Map<String, String> TYPE_NAMES = Map.of(
            GRIND_STONE, "研磨石",
            SMELTERY, "合金冶煉爐",
            PRESSURE_CHAMBER, "壓力室",
            ARMOR_FORGE, "護甲鍛造台"
    );

    /* ---- 配方記錄 ---- */
    private record MultiblockRecipe(String id, String type, Map<String, Integer> ingredients, String outputId, int outputCount) {
    }

    private final TechMCPlugin plugin;
    private final TechRegistry registry;
    private final ItemFactoryUtil itemFactory;
    private final List<MultiblockRecipe> recipes = new ArrayList<>();

    public MultiblockCraftingService(final TechMCPlugin plugin) {
        this.plugin = plugin;
        this.registry = plugin.getTechRegistry();
        this.itemFactory = plugin.getItemFactory();
        this.registerRecipes();
    }

    /* ========== 對外 API ========== */

    /**
     * 嘗試偵測發射器的多方塊結構並執行合成。
     * @return true 表示偵測到結構（不論合成是否成功），外層應取消開啟發射器 GUI
     */
    public boolean tryCraft(final Player player, final Block dispenserBlock) {
        if (dispenserBlock.getType() != Material.DISPENSER) return false;
        final String type = this.detectMultiblockType(dispenserBlock);
        if (type == null) return false;

        if (!(dispenserBlock.getState() instanceof Dispenser dispenser)) return false;
        final Inventory inv = dispenser.getInventory();

        final Map<String, Integer> contents = this.scanContents(inv);
        final MultiblockRecipe recipe = this.findMatch(type, contents);
        if (recipe == null) {
            player.sendMessage("§e⚒ §f" + TYPE_NAMES.getOrDefault(type, type)
                    + " §7— 找不到匹配的配方。請確認材料是否正確。");
            this.playFailEffect(dispenserBlock);
            return true;
        }

        // 消耗材料
        this.consumeIngredients(inv, recipe);

        // 產出物品
        final ItemStack output = this.buildStackForId(recipe.outputId, recipe.outputCount);
        if (output == null) {
            this.plugin.getLogger().warning("多方塊配方 " + recipe.id + " 輸出物品 " + recipe.outputId + " 無法建立");
            return true;
        }
        final HashMap<Integer, ItemStack> overflow = inv.addItem(output);
        if (!overflow.isEmpty()) {
            final Location dropLoc = dispenserBlock.getLocation().add(0.5, 1.2, 0.5);
            for (final ItemStack leftover : overflow.values()) {
                dispenserBlock.getWorld().dropItemNaturally(dropLoc, leftover);
            }
        }

        // 回饋
        player.sendMessage("§a⚒ §f" + TYPE_NAMES.getOrDefault(type, type)
                + " §a— 合成了 §f" + this.itemFactory.displayNameForId(recipe.outputId)
                + " §7×" + recipe.outputCount);
        this.playCraftEffect(dispenserBlock, type);

        // 給經驗
        this.plugin.getSkillService().grantXp(player, "engineering", 2);
        return true;
    }

    /** 偵測發射器所屬的多方塊類型，若不屬於任何結構則回傳 null。 */
    public String detectMultiblockType(final Block dispenserBlock) {
        if (dispenserBlock.getType() != Material.DISPENSER) return null;
        final Block above = dispenserBlock.getRelative(BlockFace.UP);
        return switch (above.getType()) {
            case OAK_FENCE -> GRIND_STONE;
            case NETHER_BRICK_SLAB -> this.hasFireAdjacent(dispenserBlock) ? SMELTERY : null;
            case SMOOTH_STONE_SLAB -> PRESSURE_CHAMBER;
            case ANVIL, CHIPPED_ANVIL, DAMAGED_ANVIL -> ARMOR_FORGE;
            default -> null;
        };
    }

    /* ========== 結構偵測輔助 ========== */

    private boolean hasFireAdjacent(final Block center) {
        for (final BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            if (center.getRelative(face).getType() == Material.FIRE
                    || center.getRelative(face).getType() == Material.SOUL_FIRE) {
                return true;
            }
        }
        // 也檢查上方方塊的四面
        final Block above = center.getRelative(BlockFace.UP);
        for (final BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            if (above.getRelative(face).getType() == Material.FIRE
                    || above.getRelative(face).getType() == Material.SOUL_FIRE) {
                return true;
            }
        }
        return false;
    }

    /* ========== 配方匹配 ========== */

    private Map<String, Integer> scanContents(final Inventory inv) {
        final Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < inv.getSize(); i++) {
            final ItemStack stack = inv.getItem(i);
            if (stack == null || stack.getType() == Material.AIR) continue;
            final String id = this.resolveStackId(stack);
            if (id == null) continue;
            map.merge(id, stack.getAmount(), Integer::sum);
        }
        return map;
    }

    private MultiblockRecipe findMatch(final String type, final Map<String, Integer> contents) {
        for (final MultiblockRecipe recipe : this.recipes) {
            if (!recipe.type.equals(type)) continue;
            boolean match = true;
            for (final Map.Entry<String, Integer> need : recipe.ingredients.entrySet()) {
                final int have = contents.getOrDefault(need.getKey(), 0);
                if (have < need.getValue()) {
                    match = false;
                    break;
                }
            }
            if (match) return recipe;
        }
        return null;
    }

    private void consumeIngredients(final Inventory inv, final MultiblockRecipe recipe) {
        final Map<String, Integer> remaining = new LinkedHashMap<>(recipe.ingredients);
        for (int i = 0; i < inv.getSize() && !remaining.isEmpty(); i++) {
            final ItemStack stack = inv.getItem(i);
            if (stack == null || stack.getType() == Material.AIR) continue;
            final String id = this.resolveStackId(stack);
            if (id == null || !remaining.containsKey(id)) continue;
            final int need = remaining.get(id);
            final int have = stack.getAmount();
            if (have <= need) {
                inv.setItem(i, null);
                if (have < need) {
                    remaining.put(id, need - have);
                } else {
                    remaining.remove(id);
                }
            } else {
                stack.setAmount(have - need);
                remaining.remove(id);
            }
        }
    }

    /* ========== 物品解析 ========== */

    private String resolveStackId(final ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) return null;
        final String techId = this.itemFactory.getTechItemId(stack);
        if (techId != null && !techId.isBlank()) {
            return this.normalizeId(techId.contains(":") ? techId.substring(0, techId.indexOf(':')) : techId);
        }
        final String machineId = this.itemFactory.getMachineId(stack);
        if (machineId != null && !machineId.isBlank()) {
            return this.normalizeId(machineId);
        }
        return this.normalizeId(stack.getType().name());
    }

    private ItemStack buildStackForId(final String id, final int amount) {
        if (this.registry.getItem(id) != null) {
            final ItemStack stack = this.itemFactory.buildTechItem(this.registry.getItem(id));
            stack.setAmount(Math.min(amount, stack.getMaxStackSize()));
            return stack;
        }
        if (this.registry.getMachine(id) != null) {
            final ItemStack stack = this.itemFactory.buildMachineItem(this.registry.getMachine(id));
            stack.setAmount(Math.min(amount, stack.getMaxStackSize()));
            return stack;
        }
        final Material material = Material.matchMaterial(id.toUpperCase(Locale.ROOT));
        if (material == null || material == Material.AIR) return null;
        return new ItemStack(material, Math.min(amount, material.getMaxStackSize()));
    }

    private String normalizeId(final String raw) {
        return raw == null ? null : raw.toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    /* ========== 音效 / 粒子 ========== */

    private void playCraftEffect(final Block block, final String type) {
        final Location loc = block.getLocation().add(0.5, 1.0, 0.5);
        switch (type) {
            case GRIND_STONE -> {
                block.getWorld().playSound(loc, Sound.BLOCK_GRINDSTONE_USE, 0.6f, 1.0f);
                block.getWorld().spawnParticle(Particle.BLOCK, loc, 12, 0.3, 0.3, 0.3, 0.0, Material.STONE.createBlockData());
            }
            case SMELTERY -> {
                block.getWorld().playSound(loc, Sound.BLOCK_LAVA_POP, 0.5f, 1.2f);
                block.getWorld().spawnParticle(Particle.FLAME, loc, 20, 0.3, 0.4, 0.3, 0.02);
                block.getWorld().spawnParticle(Particle.SMOKE, loc, 8, 0.2, 0.3, 0.2, 0.01);
            }
            case PRESSURE_CHAMBER -> {
                block.getWorld().playSound(loc, Sound.BLOCK_PISTON_CONTRACT, 0.6f, 1.3f);
                block.getWorld().spawnParticle(Particle.CRIT, loc, 10, 0.2, 0.2, 0.2, 0.05);
            }
            case ARMOR_FORGE -> {
                block.getWorld().playSound(loc, Sound.BLOCK_ANVIL_USE, 0.5f, 1.0f);
                block.getWorld().spawnParticle(Particle.CRIT, loc, 15, 0.3, 0.3, 0.3, 0.05);
            }
            default -> {
                block.getWorld().playSound(loc, Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 0.6f, 1.2f);
                block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 8, 0.3, 0.3, 0.3, 0.0);
            }
        }
    }

    private void playFailEffect(final Block block) {
        final Location loc = block.getLocation().add(0.5, 1.0, 0.5);
        block.getWorld().playSound(loc, Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
    }

    /* ========== 配方註冊 ========== */

    private void registerRecipes() {
        // ---------- 研磨石 (Grind Stone) ----------
        // 原礦 → 粉（比機器產出稍低，手動代價）
        this.addRecipe("grind_raw_iron", GRIND_STONE,
                Map.of("raw_iron", 1), "iron_dust", 2);
        this.addRecipe("grind_raw_copper", GRIND_STONE,
                Map.of("raw_copper", 1), "copper_dust", 2);
        this.addRecipe("grind_raw_gold", GRIND_STONE,
                Map.of("raw_gold", 1), "gold_dust", 2);
        this.addRecipe("grind_cobblestone", GRIND_STONE,
                Map.of("cobblestone", 4), "gravel", 1);
        this.addRecipe("grind_coal", GRIND_STONE,
                Map.of("coal", 2), "carbon_dust", 1);
        this.addRecipe("grind_netherrack", GRIND_STONE,
                Map.of("netherrack", 4), "sulfur", 1);
        this.addRecipe("grind_blaze_rod", GRIND_STONE,
                Map.of("blaze_rod", 1), "blaze_powder", 4);
        this.addRecipe("grind_flint", GRIND_STONE,
                Map.of("gravel", 4), "flint", 1);

        // ---------- 合金冶煉爐 (Smeltery) ----------
        this.addRecipe("smelt_bronze", SMELTERY,
                Map.of("copper_dust", 3, "tin_dust", 1), "bronze_ingot", 4);
        this.addRecipe("smelt_steel", SMELTERY,
                Map.of("iron_dust", 1, "carbon_dust", 1), "steel_ingot", 1);
        this.addRecipe("smelt_solder", SMELTERY,
                Map.of("tin_dust", 1, "lead_dust", 1), "solder_ingot", 2);
        this.addRecipe("smelt_gilded_iron", SMELTERY,
                Map.of("iron_ingot", 1, "gold_ingot", 1), "gilded_iron_ingot", 2);
        this.addRecipe("smelt_ferrosilicon", SMELTERY,
                Map.of("iron_ingot", 1, "silicon", 1), "ferrosilicon_ingot", 2);

        // ---------- 壓力室 (Pressure Chamber) ----------
        this.addRecipe("press_iron_plate", PRESSURE_CHAMBER,
                Map.of("iron_ingot", 3), "iron_plate", 1);
        this.addRecipe("press_copper_plate", PRESSURE_CHAMBER,
                Map.of("copper_ingot", 3), "copper_plate", 1);
        this.addRecipe("press_steel_plate", PRESSURE_CHAMBER,
                Map.of("steel_ingot", 3), "steel_plate", 1);
        this.addRecipe("press_bronze_plate", PRESSURE_CHAMBER,
                Map.of("bronze_ingot", 3), "bronze_plate", 1);
        this.addRecipe("press_carbon_plate", PRESSURE_CHAMBER,
                Map.of("carbon_dust", 9), "carbon_plate", 1);
        this.addRecipe("press_tin_plate", PRESSURE_CHAMBER,
                Map.of("tin_ingot", 3), "tin_plate", 1);

        // ---------- 護甲鍛造台 (Armor Forge) ----------
        this.addRecipe("forge_titan_helmet", ARMOR_FORGE,
                Map.of("steel_plate", 3, "iron_plate", 2), "titan_helmet", 1);
        this.addRecipe("forge_titan_chestplate", ARMOR_FORGE,
                Map.of("steel_plate", 5, "iron_plate", 3), "titan_chestplate", 1);
        this.addRecipe("forge_titan_leggings", ARMOR_FORGE,
                Map.of("steel_plate", 4, "iron_plate", 3), "titan_leggings", 1);
        this.addRecipe("forge_titan_boots", ARMOR_FORGE,
                Map.of("steel_plate", 2, "iron_plate", 2), "titan_boots", 1);
        this.addRecipe("forge_lumber_axe", ARMOR_FORGE,
                Map.of("steel_ingot", 3, "iron_ingot", 2, "stick", 2), "lumber_axe", 1);
        this.addRecipe("forge_solar_helmet", ARMOR_FORGE,
                Map.of("iron_plate", 3, "daylight_detector", 1, "circuit_board", 1), "solar_helmet", 1);
    }

    private void addRecipe(final String id, final String type,
                           final Map<String, Integer> ingredients, final String outputId, final int count) {
        this.recipes.add(new MultiblockRecipe(id, type, Map.copyOf(ingredients), outputId, count));
    }

    /** 取得某多方塊類型的所有配方（供 Wiki 用）。 */
    public List<String> getRecipeDescriptions(final String type) {
        final List<String> lines = new ArrayList<>();
        for (final MultiblockRecipe recipe : this.recipes) {
            if (!recipe.type.equals(type)) continue;
            final StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (final Map.Entry<String, Integer> e : recipe.ingredients.entrySet()) {
                if (!first) sb.append(" + ");
                first = false;
                sb.append(this.itemFactory.displayNameForId(e.getKey()));
                if (e.getValue() > 1) sb.append(" ×").append(e.getValue());
            }
            sb.append(" → ");
            sb.append(this.itemFactory.displayNameForId(recipe.outputId));
            if (recipe.outputCount > 1) sb.append(" ×").append(recipe.outputCount);
            lines.add(sb.toString());
        }
        return lines;
    }

    /** 取得多方塊名稱。 */
    public static String displayName(final String type) {
        return TYPE_NAMES.getOrDefault(type, type);
    }
}
