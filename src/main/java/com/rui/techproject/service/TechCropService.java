package com.rui.techproject.service;

import com.rui.techproject.TechMCPlugin;
import com.rui.techproject.storage.StorageBackend;
import com.rui.techproject.util.ItemFactoryUtil;
import com.rui.techproject.util.LocationKey;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Skull;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class TechCropService {
    private final TechMCPlugin plugin;
    private final TechRegistry registry;
    private final ItemFactoryUtil itemFactory;
    private final Map<String, TechCropDefinition> crops = new LinkedHashMap<>();
    private final Map<LocationKey, String> plantedCrops = new ConcurrentHashMap<>();
    private StorageBackend storageBackend;

    private static final BlockFace[] RANDOM_FACES = {
            BlockFace.NORTH, BlockFace.NORTH_EAST, BlockFace.EAST, BlockFace.SOUTH_EAST,
            BlockFace.SOUTH, BlockFace.SOUTH_WEST, BlockFace.WEST, BlockFace.NORTH_WEST
    };

    private record TechCropDefinition(String id,
                                      String seedItemId,
                                      String produceItemId,
                                      Material plantMaterial,
                                      int produceAmount,
                                      int seedReturnAmount,
                                      String matureTexture) {
    }

    public TechCropService(final TechMCPlugin plugin,
                           final TechRegistry registry,
                           final ItemFactoryUtil itemFactory) {
        this.plugin = plugin;
        this.registry = registry;
        this.itemFactory = itemFactory;
        this.registerDefaults();
    }

    public void setStorageBackend(final StorageBackend backend) {
        this.storageBackend = backend;
        this.load();
    }

    private void registerDefaults() {
        this.registerCrop(new TechCropDefinition("soybean", "soybean_seeds", "soybean_pods", Material.WHEAT, 2, 1,
                "bb9ec78d598e1360801635bd0ce75aa9e01c4d1d490c9db9def441ff35ef7322"));
        this.registerCrop(new TechCropDefinition("spiceberry", "spiceberry_seeds", "spiceberry", Material.BEETROOTS, 2, 1,
                "d5fe6c718fba719ff622237ed9ea6827d093effab814be2192e9643e3e3d7"));
        this.registerCrop(new TechCropDefinition("tea_leaf", "tea_leaf_seeds", "tea_leaf", Material.CARROTS, 2, 1,
                "1514c8b461247ab17fe3606e6e2f4d363dccae9ed5bedd012b498d7ae8eb3"));
        this.registerCrop(new TechCropDefinition("tomato", "tomato_seeds", "tomato", Material.BEETROOTS, 2, 1,
                "99172226d276070dc21b75ba25cc2aa5649da5cac745ba977695b59aebd"));
        this.registerCrop(new TechCropDefinition("cabbage", "cabbage_seeds", "cabbage", Material.WHEAT, 2, 1,
                "fcd6d67320c9131be85a164cd7c5fcf288f28c2816547db30a3187416bdc45b"));
        this.registerCrop(new TechCropDefinition("corn", "corn_seeds", "corn", Material.CARROTS, 2, 1,
                "9bd3802e5fac03afab742b0f3cca41bcd4723bee911d23be29cffd5b965f1"));
        this.registerCrop(new TechCropDefinition("onion", "onion_bulbs", "onion", Material.POTATOES, 2, 1,
                "6ce036e327cb9d4d8fef36897a89624b5d9b18f705384ce0d7ed1e1fc7f56"));
        this.registerCrop(new TechCropDefinition("void_bloom", "void_bloom_seeds", "void_bloom", Material.BEETROOTS, 2, 1,
                "4e35aade81292e6ff4cd33dc0ea6a1326d04597c0e529def4182b1d1548cfe1"));
        this.registerCrop(new TechCropDefinition("frostbloom", "frostbloom_seeds", "frostbloom", Material.WHEAT, 2, 1,
                "f88cd6dd50359c7d5898c7c7e3e260bfcd3dcb1493a89b9e88e9cbecbfe45949"));
        this.registerCrop(new TechCropDefinition("echo_spore", "echo_spore_seeds", "echo_spore", Material.BEETROOTS, 2, 1,
                "7840b87d52271d2a755dedc82877e0ed3df67dcc42ea479ec146176b02779a5"));
        this.registerCrop(new TechCropDefinition("emberroot", "emberroot_seeds", "emberroot", Material.CARROTS, 2, 1,
                "e8deee5866ab199eda1bdd7707bdb9edd693444f1e3bd336bd2c767151cf2"));
        this.registerCrop(new TechCropDefinition("ion_fern", "ion_fern_seeds", "ion_fern", Material.POTATOES, 2, 1,
                "16149196f3a8d6d6f24e51b27e4cb71c6bab663449daffb7aa211bbe577242"));
    }

    private void registerCrop(final TechCropDefinition definition) {
        this.crops.put(definition.id().toLowerCase(), definition);
    }

    public boolean tryPlant(final Player player,
                            final Block clickedBlock,
                            final BlockFace face,
                            final ItemStack stack) {
        if (player == null || clickedBlock == null || stack == null || face != BlockFace.UP) {
            return false;
        }
        final TechCropDefinition definition = this.definitionBySeed(this.itemFactory.getTechItemId(stack));
        if (definition == null) {
            return false;
        }
        final boolean validSoil = definition.plantMaterial() == Material.NETHER_WART
                ? clickedBlock.getType() == Material.SOUL_SAND || clickedBlock.getType() == Material.SOUL_SOIL
                : clickedBlock.getType() == Material.FARMLAND;
        if (!validSoil) {
            return false;
        }
        final Block cropBlock = clickedBlock.getRelative(BlockFace.UP);
        if (!cropBlock.isEmpty()) {
            return false;
        }
        cropBlock.setType(definition.plantMaterial(), false);
        if (cropBlock.getBlockData() instanceof Ageable ageable) {
            ageable.setAge(0);
            cropBlock.setBlockData(ageable, false);
        }
        this.plantedCrops.put(LocationKey.from(cropBlock.getLocation()), definition.id());
        if (player.getGameMode() != GameMode.CREATIVE) {
            stack.setAmount(stack.getAmount() - 1);
        }
        cropBlock.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, cropBlock.getLocation().add(0.5, 0.6, 0.5), 8, 0.18, 0.18, 0.18, 0.01);
        cropBlock.getWorld().playSound(cropBlock.getLocation(), Sound.ITEM_CROP_PLANT, 0.7f, 1.1f);
        return true;
    }

    public boolean isTrackedCrop(final Block block) {
        return this.getDefinition(block) != null;
    }

    /**
     * 如果方塊是成熟作物上方的頭顱，回傳下方的基礎作物方塊；否則 null。
     */
    public Block resolveHeadToCropBase(final Block block) {
        if (block == null || block.getType() != Material.PLAYER_HEAD) {
            return null;
        }
        final Block below = block.getRelative(BlockFace.DOWN);
        if (this.getDefinition(below) != null) {
            return below;
        }
        return null;
    }

    public boolean isMature(final Block block) {
        final TechCropDefinition definition = this.getDefinition(block);
        if (definition == null) {
            return false;
        }
        // 成熟標誌：上方 Y+1 有 PLAYER_HEAD
        final Block above = block.getRelative(BlockFace.UP);
        if (above.getType() == Material.PLAYER_HEAD) {
            return true;
        }
        if (block.getType() != definition.plantMaterial()) {
            return false;
        }
        if (!(block.getBlockData() instanceof Ageable ageable)) {
            return false;
        }
        return ageable.getAge() >= ageable.getMaximumAge();
    }

    public List<ItemStack> harvest(final Block block, final boolean replant) {
        final TechCropDefinition definition = this.getDefinition(block);
        if (definition == null) {
            return List.of();
        }
        final boolean mature = this.isMature(block);
        final List<ItemStack> outputs = new ArrayList<>();
        if (mature) {
            outputs.add(this.buildStack(definition.produceItemId(), definition.produceAmount()));
            outputs.add(this.buildStack(definition.seedItemId(), definition.seedReturnAmount()));
        } else {
            outputs.add(this.buildStack(definition.seedItemId(), 1));
        }
        outputs.removeIf(stack -> stack == null || stack.getType() == Material.AIR);
        // 移除上方頭顱
        final Block above = block.getRelative(BlockFace.UP);
        if (above.getType() == Material.PLAYER_HEAD) {
            above.setType(Material.AIR, false);
        }
        if (replant && mature) {
            block.setType(definition.plantMaterial(), false);
            if (block.getBlockData() instanceof Ageable ageable) {
                ageable.setAge(0);
                block.setBlockData(ageable, false);
            }
        } else {
            block.setType(Material.AIR, false);
            this.plantedCrops.remove(LocationKey.from(block.getLocation()));
        }
        return outputs;
    }

    public boolean grow(final Block block, final int stages) {
        final TechCropDefinition definition = this.getDefinition(block);
        if (definition == null) {
            return false;
        }
        // 已成熟（底部為 FERN + 上方有 head）
        if (block.getType() == Material.FERN && block.getRelative(BlockFace.UP).getType() == Material.PLAYER_HEAD) {
            return false;
        }
        if (block.getType() != definition.plantMaterial()) {
            return false;
        }
        if (!(block.getBlockData() instanceof Ageable ageable) || ageable.getAge() >= ageable.getMaximumAge()) {
            return false;
        }
        final int newAge = Math.min(ageable.getMaximumAge(), ageable.getAge() + Math.max(1, stages));
        ageable.setAge(newAge);
        block.setBlockData(ageable, true);
        if (newAge >= ageable.getMaximumAge()) {
            this.convertToMatureHead(block);
        }
        return true;
    }

    public void convertToMatureHead(final Block block) {
        final TechCropDefinition definition = this.getDefinition(block);
        if (definition == null || definition.matureTexture() == null || definition.matureTexture().isBlank()) {
            return;
        }
        final Block above = block.getRelative(BlockFace.UP);
        if (above.getType() == Material.PLAYER_HEAD) {
            return;
        }
        if (!above.isEmpty()) {
            return;
        }
        // 底部設為蕨類植物外觀
        block.setType(Material.FERN, false);
        // 上方放置 head
        above.setType(Material.PLAYER_HEAD, true);

        // Folia/Luminol: block entity 在 setType 同 tick 可能尚未建立，延後 2 tick
        final Location loc = above.getLocation();
        final String cropId = definition.id();
        final String hash = definition.matureTexture().trim();
        this.applySkullTexture(loc, cropId, hash, 2L, 0);

        block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                above.getLocation().add(0.5, 0.6, 0.5), 12, 0.25, 0.2, 0.25, 0.02);
        block.getWorld().playSound(above.getLocation(), Sound.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, 0.5f, 1.4f);
    }

    private void applySkullTexture(final Location loc, final String cropId, final String hash,
                                   final long delayTicks, final int attempt) {
        this.plugin.getSafeScheduler().runRegionDelayed(loc, task -> {
            final Block target = loc.getBlock();
            if (target.getType() != Material.PLAYER_HEAD) {
                return;
            }
            if (!(target.getState() instanceof Skull skull)) {
                if (attempt < 2) {
                    this.applySkullTexture(loc, cropId, hash, 5L, attempt + 1);
                } else {
                    this.plugin.getLogger().warning("[CropDebug] 多次嘗試後 getState 仍非 Skull: " + target.getType());
                }
                return;
            }
            try {
                final String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + hash + "\"}}}";
                final String base64 = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
                // 使用基於 cropId 的固定 UUID，避免 Paper 嘗試向 Mojang 查詢隨機 UUID
                final UUID textureUUID = UUID.nameUUIDFromBytes(("techproject:crop:" + cropId).getBytes(StandardCharsets.UTF_8));
                final var profile = Bukkit.getServer().createProfile(textureUUID);
                profile.setProperty(new ProfileProperty("textures", base64));
                skull.setPlayerProfile(profile);
                skull.setRotation(RANDOM_FACES[ThreadLocalRandom.current().nextInt(RANDOM_FACES.length)]);
                final boolean updated = skull.update(true, false);
                if (!updated && attempt < 2) {
                    this.plugin.getLogger().warning("[CropDebug] update=false attempt=" + attempt + " crop=" + cropId + " — 排程重試…");
                    this.applySkullTexture(loc, cropId, hash, 10L, attempt + 1);
                }
            } catch (final Exception e) {
                this.plugin.getLogger().warning("無法套用作物頭顱材質：" + cropId + " / " + e.getMessage());
                e.printStackTrace();
            }
        }, delayTicks);
    }

    public void saveAll() {
        final Map<String, Map<String, Object>> crops = new LinkedHashMap<>();
        int index = 0;
        for (final Map.Entry<LocationKey, String> entry : this.plantedCrops.entrySet()) {
            final Map<String, Object> data = new LinkedHashMap<>();
            final LocationKey key = entry.getKey();
            data.put("world", key.worldName());
            data.put("x", key.x());
            data.put("y", key.y());
            data.put("z", key.z());
            data.put("crop-id", entry.getValue());
            crops.put(String.valueOf(index++), data);
        }
        if (this.storageBackend == null) return;
        this.storageBackend.saveAllCrops(crops);
    }

    private void load() {
        final Map<String, Map<String, Object>> crops = this.storageBackend.loadAllCrops();
        for (final Map.Entry<String, Map<String, Object>> entry : crops.entrySet()) {
            final Map<String, Object> data = entry.getValue();
            final String world = data.get("world") instanceof String s ? s : null;
            final String cropId = data.get("crop-id") instanceof String s ? s : null;
            if (world == null || cropId == null || !this.crops.containsKey(cropId.toLowerCase())) {
                continue;
            }
            final int x = data.get("x") instanceof Number n ? n.intValue() : 0;
            final int y = data.get("y") instanceof Number n ? n.intValue() : 0;
            final int z = data.get("z") instanceof Number n ? n.intValue() : 0;
            this.plantedCrops.put(new LocationKey(world, x, y, z), cropId.toLowerCase());
        }
    }

    private TechCropDefinition definitionBySeed(final String techItemId) {
        if (techItemId == null || techItemId.isBlank()) {
            return null;
        }
        return this.crops.values().stream()
                .filter(definition -> definition.seedItemId().equalsIgnoreCase(techItemId))
                .findFirst()
                .orElse(null);
    }

    private TechCropDefinition getDefinition(final Block block) {
        if (block == null) {
            return null;
        }
        final String cropId = this.plantedCrops.get(LocationKey.from(block.getLocation()));
        if (cropId == null) {
            return null;
        }
        final TechCropDefinition definition = this.crops.get(cropId.toLowerCase());
        if (definition == null) {
            return null;
        }
        if (block.getType() == Material.AIR) {
            this.plantedCrops.remove(LocationKey.from(block.getLocation()));
            return null;
        }
        // 允許 plantMaterial（生長中）和 FERN（成熟底部外觀）
        if (block.getType() != definition.plantMaterial() && block.getType() != Material.FERN) {
            return null;
        }
        return definition;
    }

    private ItemStack buildStack(final String id, final int amount) {
        if (this.registry.getItem(id) != null) {
            final ItemStack stack = this.itemFactory.buildTechItem(this.registry.getItem(id));
            stack.setAmount(Math.min(amount, stack.getMaxStackSize()));
            return stack;
        }
        final Material material = Material.matchMaterial(id.toUpperCase());
        if (material == null || material == Material.AIR) {
            return null;
        }
        final Material safe = this.itemFactory.safeItemMaterial(material);
        return new ItemStack(safe, Math.min(amount, safe.getMaxStackSize()));
    }
}