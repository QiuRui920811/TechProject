package com.rui.techproject.service;

import com.rui.techproject.TechProjectPlugin;
import com.rui.techproject.storage.StorageBackend;
import com.rui.techproject.util.ItemFactoryUtil;
import com.rui.techproject.util.LocationKey;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class TechCropService {
    private final TechProjectPlugin plugin;
    private final TechRegistry registry;
    private final ItemFactoryUtil itemFactory;
    private final Map<String, TechCropDefinition> crops = new LinkedHashMap<>();
    private final Map<LocationKey, String> plantedCrops = new ConcurrentHashMap<>();
    private StorageBackend storageBackend;

    private record TechCropDefinition(String id,
                                      String seedItemId,
                                      String produceItemId,
                                      Material plantMaterial,
                                      int produceAmount,
                                      int seedReturnAmount) {
    }

    public TechCropService(final TechProjectPlugin plugin,
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
        this.registerCrop(new TechCropDefinition("soybean", "soybean_seeds", "soybean_pods", Material.WHEAT, 2, 1));
        this.registerCrop(new TechCropDefinition("spiceberry", "spiceberry_seeds", "spiceberry", Material.BEETROOTS, 2, 1));
        this.registerCrop(new TechCropDefinition("tea_leaf", "tea_leaf_seeds", "tea_leaf", Material.CARROTS, 2, 1));
        this.registerCrop(new TechCropDefinition("tomato", "tomato_seeds", "tomato", Material.BEETROOTS, 2, 1));
        this.registerCrop(new TechCropDefinition("cabbage", "cabbage_seeds", "cabbage", Material.WHEAT, 2, 1));
        this.registerCrop(new TechCropDefinition("corn", "corn_seeds", "corn", Material.CARROTS, 2, 1));
        this.registerCrop(new TechCropDefinition("onion", "onion_bulbs", "onion", Material.POTATOES, 2, 1));
        this.registerCrop(new TechCropDefinition("void_bloom", "void_bloom_seeds", "void_bloom", Material.NETHER_WART, 2, 1));
        this.registerCrop(new TechCropDefinition("frostbloom", "frostbloom_seeds", "frostbloom", Material.WHEAT, 2, 1));
        this.registerCrop(new TechCropDefinition("echo_spore", "echo_spore_seeds", "echo_spore", Material.BEETROOTS, 2, 1));
        this.registerCrop(new TechCropDefinition("emberroot", "emberroot_seeds", "emberroot", Material.CARROTS, 2, 1));
        this.registerCrop(new TechCropDefinition("ion_fern", "ion_fern_seeds", "ion_fern", Material.POTATOES, 2, 1));
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
        if (definition == null || clickedBlock.getType() != Material.FARMLAND) {
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

    public boolean isMature(final Block block) {
        final TechCropDefinition definition = this.getDefinition(block);
        if (definition == null || block.getType() != definition.plantMaterial()) {
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
        if (replant && mature && block.getBlockData() instanceof Ageable ageable) {
            ageable.setAge(0);
            block.setBlockData(ageable, false);
        } else {
            block.setType(Material.AIR, false);
            this.plantedCrops.remove(LocationKey.from(block.getLocation()));
        }
        return outputs;
    }

    public boolean grow(final Block block, final int stages) {
        final TechCropDefinition definition = this.getDefinition(block);
        if (definition == null || block.getType() != definition.plantMaterial()) {
            return false;
        }
        if (!(block.getBlockData() instanceof Ageable ageable) || ageable.getAge() >= ageable.getMaximumAge()) {
            return false;
        }
        ageable.setAge(Math.min(ageable.getMaximumAge(), ageable.getAge() + Math.max(1, stages)));
        block.setBlockData(ageable, true);
        return true;
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