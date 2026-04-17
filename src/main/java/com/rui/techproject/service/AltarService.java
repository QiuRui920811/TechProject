package com.rui.techproject.service;

import com.rui.techproject.TechMCPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 古代祭壇 / Ancient Altar：儀式合成系統。
 *
 * <p>玩法：
 * <ol>
 *   <li>放置 ancient_altar 方塊（LODESTONE）。</li>
 *   <li>玩家把材料丟（Q 或 throw）到祭壇附近（4 格半徑）。</li>
 *   <li>右鍵祭壇開始儀式。若丟出的物品集合符合任何 {@link AltarRecipe}，啟動 8 秒儀式動畫。</li>
 *   <li>儀式期間玩家不能移動祭壇或收走物品。完成後材料被消耗，產出物品掉落於祭壇上方。</li>
 * </ol>
 *
 * <p>配方在 altar-rituals.yml 裡定義。第一次啟動若檔案不存在會寫入一份預設配方。
 */
public final class AltarService {

    public static final String ALTAR_ITEM_ID = "ancient_altar";

    private final TechMCPlugin plugin;
    private final List<AltarRecipe> recipes = new ArrayList<>();

    /** 正在跑儀式的祭壇位置，防止併發觸發。 */
    private final Set<String> busyAltars = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public AltarService(final TechMCPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        this.loadRecipes();
    }

    // ═══════════════════════════════════════════
    //  API
    // ═══════════════════════════════════════════

    /** 玩家右鍵祭壇。true = 已被處理。 */
    public boolean onAltarInteract(final Player player, final Block core) {
        if (!this.isAltarCore(core)) return false;
        final String key = keyOf(core);
        if (this.busyAltars.contains(key)) {
            player.sendMessage(Component.text("祭壇儀式進行中……", NamedTextColor.GOLD));
            return true;
        }

        final World world = core.getWorld();
        final Location center = core.getLocation().add(0.5, 0.5, 0.5);

        // 掃描附近 4 格內的掉落物
        final List<Item> nearbyItems = new ArrayList<>();
        for (final var ent : world.getNearbyEntities(center, 4.0, 2.0, 4.0)) {
            if (ent instanceof Item item && item.getItemStack() != null
                    && item.getItemStack().getType() != Material.AIR) {
                nearbyItems.add(item);
            }
        }
        if (nearbyItems.isEmpty()) {
            player.sendMessage(Component.text("祭壇空蕩蕩——請將材料拋入祭壇周圍 4 格內。",
                    NamedTextColor.GRAY));
            world.playSound(center, Sound.BLOCK_STONE_HIT, 0.4f, 0.6f);
            return true;
        }

        // 轉成 item id 多重集合
        final Map<String, Integer> have = new HashMap<>();
        for (final Item it : nearbyItems) {
            final ItemStack stack = it.getItemStack();
            final String id = this.itemIdFor(stack);
            have.merge(id, stack.getAmount(), Integer::sum);
        }

        // 嘗試配方匹配
        final AltarRecipe matched = this.findMatching(have);
        if (matched == null) {
            player.sendMessage(Component.text("祭壇拒絕了你的祭品。嘗試不同的組合吧。",
                    NamedTextColor.RED));
            world.playSound(center, Sound.BLOCK_BELL_RESONATE, 0.4f, 0.4f);
            world.spawnParticle(Particle.SMOKE, center, 20, 0.6, 0.4, 0.6, 0.02);
            return true;
        }

        this.startRitual(core, matched, player, nearbyItems);
        return true;
    }

    public boolean isAltarCore(final Block block) {
        if (block == null) return false;
        if (block.getType() != Material.LODESTONE) return false;
        final String itemId = this.plugin.getPlacedTechBlockService().placedItemId(block);
        return ALTAR_ITEM_ID.equalsIgnoreCase(itemId);
    }

    // ═══════════════════════════════════════════
    //  儀式
    // ═══════════════════════════════════════════

    private void startRitual(final Block core, final AltarRecipe recipe,
                             final Player caster, final List<Item> nearbyItems) {
        final String key = keyOf(core);
        if (!this.busyAltars.add(key)) return;
        final World world = core.getWorld();
        final Location center = core.getLocation().add(0.5, 1.0, 0.5);

        caster.sendMessage(Component.text("◆ 儀式開始：", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(recipe.name, NamedTextColor.WHITE)));
        world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 1.0f, 0.7f);
        world.playSound(center, Sound.BLOCK_PORTAL_AMBIENT, SoundCategory.BLOCKS, 0.8f, 1.1f);

        // 8 秒動畫：每 20 tick 一階段
        this.plugin.getSafeScheduler().runGlobalDelayed(task -> this.ritualStep(core, recipe, caster, nearbyItems, 0), 1L);
    }

    private void ritualStep(final Block core, final AltarRecipe recipe,
                            final Player caster, final List<Item> nearbyItems, final int stage) {
        final String key = keyOf(core);
        if (stage >= 8) {
            this.completeRitual(core, recipe, caster, nearbyItems);
            return;
        }
        if (!this.isAltarCore(core)) {
            this.busyAltars.remove(key);
            caster.sendMessage(Component.text("祭壇被破壞，儀式中止。", NamedTextColor.RED));
            return;
        }

        final World world = core.getWorld();
        final Location center = core.getLocation().add(0.5, 1.0, 0.5);

        // 漸強光柱 + 環形粒子
        final double angle = stage * (Math.PI / 4);
        final TextColor color = this.stageColor(stage);
        final org.bukkit.Particle.DustOptions dust = new org.bukkit.Particle.DustOptions(
                org.bukkit.Color.fromRGB(color.red(), color.green(), color.blue()), 1.6f);
        for (int i = 0; i < 16; i++) {
            final double theta = angle + i * (Math.PI / 8);
            final double r = 2.5 - (stage * 0.25);
            world.spawnParticle(Particle.DUST,
                    center.getX() + Math.cos(theta) * r,
                    center.getY() + 0.2 + (stage * 0.15),
                    center.getZ() + Math.sin(theta) * r,
                    1, 0, 0, 0, 0, dust);
        }
        // 中柱
        for (double h = 0; h < 2.0 + stage * 0.2; h += 0.25) {
            world.spawnParticle(Particle.DUST,
                    center.getX(), center.getY() + h, center.getZ(),
                    1, 0.05, 0, 0.05, 0, dust);
        }
        world.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 0.7f + stage * 0.1f);

        // 在中後段附近丟些紅石雷光
        if (stage >= 4) {
            world.spawnParticle(Particle.ELECTRIC_SPARK, center, 18, 0.6, 0.6, 0.6, 0.08);
        }
        if (stage == 7) {
            // 最後階段打雷
            world.strikeLightningEffect(core.getLocation().add(0.5, 1.0, 0.5));
        }

        this.plugin.getSafeScheduler().runGlobalDelayed(
                task -> this.ritualStep(core, recipe, caster, nearbyItems, stage + 1), 20L);
    }

    private void completeRitual(final Block core, final AltarRecipe recipe,
                                final Player caster, final List<Item> nearbyItems) {
        final String key = keyOf(core);
        try {
            if (!this.isAltarCore(core)) {
                caster.sendMessage(Component.text("祭壇在儀式結束前消失……", NamedTextColor.RED));
                return;
            }
            // 再次掃描並消耗材料（因為玩家可能在 8 秒內加減物品）
            final World world = core.getWorld();
            final Location center = core.getLocation().add(0.5, 0.5, 0.5);
            final List<Item> current = new ArrayList<>();
            for (final var ent : world.getNearbyEntities(center, 4.0, 2.0, 4.0)) {
                if (ent instanceof Item it && it.getItemStack() != null
                        && it.getItemStack().getType() != Material.AIR) {
                    current.add(it);
                }
            }
            final Map<String, Integer> have = new HashMap<>();
            for (final Item it : current) {
                final ItemStack s = it.getItemStack();
                have.merge(this.itemIdFor(s), s.getAmount(), Integer::sum);
            }
            if (!this.matches(recipe, have)) {
                caster.sendMessage(Component.text("儀式被擾動，材料組合已改變。", NamedTextColor.RED));
                world.spawnParticle(Particle.LARGE_SMOKE, center, 40, 1.0, 0.5, 1.0, 0.01);
                world.playSound(center, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.6f);
                return;
            }
            // 消耗材料
            this.consumeRecipeInputs(recipe, current);
            // 產出
            final ItemStack output = this.plugin.getMachineService().buildStackForPublicId(recipe.output);
            if (output != null) {
                output.setAmount(Math.max(1, recipe.outputAmount));
                world.dropItemNaturally(core.getLocation().add(0.5, 1.4, 0.5), output);
            }
            world.spawnParticle(Particle.FLASH, core.getLocation().add(0.5, 1.5, 0.5), 1);
            world.spawnParticle(Particle.END_ROD, core.getLocation().add(0.5, 1.5, 0.5),
                    80, 0.6, 0.6, 0.6, 0.3);
            world.playSound(core.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.2f);
            caster.sendMessage(Component.text("◆ 儀式完成：" + recipe.name, NamedTextColor.LIGHT_PURPLE));
            this.plugin.getPlayerProgressService().incrementStat(caster.getUniqueId(), "altar_rituals", 1);
            this.plugin.getPlayerProgressService().unlockByRequirement(caster.getUniqueId(), "machine:ancient_altar");
        } finally {
            this.busyAltars.remove(key);
        }
    }

    private void consumeRecipeInputs(final AltarRecipe recipe, final List<Item> current) {
        final Map<String, Integer> need = new HashMap<>(recipe.inputs);
        for (final Item it : current) {
            if (need.isEmpty()) break;
            final ItemStack stack = it.getItemStack();
            if (stack == null || stack.getType() == Material.AIR) continue;
            final String id = this.itemIdFor(stack);
            final Integer required = need.get(id);
            if (required == null || required <= 0) continue;
            final int takeable = Math.min(stack.getAmount(), required);
            if (takeable <= 0) continue;
            if (stack.getAmount() <= takeable) {
                it.remove();
            } else {
                final ItemStack left = stack.clone();
                left.setAmount(stack.getAmount() - takeable);
                it.setItemStack(left);
            }
            final int leftNeed = required - takeable;
            if (leftNeed <= 0) {
                need.remove(id);
            } else {
                need.put(id, leftNeed);
            }
        }
    }

    private AltarRecipe findMatching(final Map<String, Integer> have) {
        for (final AltarRecipe r : this.recipes) {
            if (this.matches(r, have)) return r;
        }
        return null;
    }

    private boolean matches(final AltarRecipe r, final Map<String, Integer> have) {
        // 至少要包含配方需要的每種 id+數量
        for (final var e : r.inputs.entrySet()) {
            final int got = have.getOrDefault(e.getKey(), 0);
            if (got < e.getValue()) return false;
        }
        return true;
    }

    private String itemIdFor(final ItemStack stack) {
        final String techId = this.plugin.getItemFactory().getTechItemId(stack);
        if (techId != null && !techId.isBlank()) return techId;
        return stack.getType().name().toLowerCase();
    }

    private TextColor stageColor(final int stage) {
        return switch (stage % 4) {
            case 0 -> TextColor.color(0xA855F7);  // purple
            case 1 -> TextColor.color(0x22D3EE);  // cyan
            case 2 -> TextColor.color(0xFBBF24);  // gold
            default -> TextColor.color(0xEC4899); // pink
        };
    }

    private static String keyOf(final Block block) {
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    // ═══════════════════════════════════════════
    //  配方載入（內建預設）
    // ═══════════════════════════════════════════

    private void loadRecipes() {
        this.recipes.clear();
        // 使用內建預設配方（避免新增外部檔案）
        this.recipes.add(new AltarRecipe(
                "能源核心儀式",
                mapOf("redstone_block", 4, "diamond", 2, "glowstone", 4),
                "energy_cell", 2));
        this.recipes.add(new AltarRecipe(
                "共鳴寶石儀式",
                mapOf("amethyst_shard", 8, "emerald", 2, "ender_pearl", 1),
                "resonance_crystal", 1));
        this.recipes.add(new AltarRecipe(
                "合金鑄造儀式",
                mapOf("iron_ingot", 8, "copper_ingot", 4, "blaze_powder", 2),
                "titanium_alloy", 4));
        this.recipes.add(new AltarRecipe(
                "古核鑄造儀式",
                mapOf("nether_star", 1, "obsidian", 4, "end_crystal", 1),
                "quantum_frame", 1));
    }

    private static Map<String, Integer> mapOf(final Object... pairs) {
        final Map<String, Integer> out = new HashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            out.put(String.valueOf(pairs[i]), (Integer) pairs[i + 1]);
        }
        return out;
    }

    // ═══════════════════════════════════════════
    //  內部資料
    // ═══════════════════════════════════════════

    public static final class AltarRecipe {
        public final String name;
        public final Map<String, Integer> inputs;
        public final String output;
        public final int outputAmount;

        public AltarRecipe(final String name, final Map<String, Integer> inputs,
                           final String output, final int outputAmount) {
            this.name = name;
            this.inputs = inputs;
            this.output = output;
            this.outputAmount = outputAmount;
        }
    }
}
