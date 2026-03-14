package com.rui.techproject.service;

import com.rui.techproject.TechProjectPlugin;
import com.rui.techproject.model.TechItemDefinition;
import com.rui.techproject.storage.StorageBackend;
import com.rui.techproject.util.ItemFactoryUtil;
import com.rui.techproject.util.LocationKey;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PlacedTechBlockService {
    private final TechRegistry registry;
    private final ItemFactoryUtil itemFactory;
    private final Map<LocationKey, String> placedBlocks = new ConcurrentHashMap<>();
    private StorageBackend storageBackend;
    private final TechProjectPlugin plugin;

    public PlacedTechBlockService(final TechProjectPlugin plugin,
                                  final TechRegistry registry,
                                  final ItemFactoryUtil itemFactory) {
        this.plugin = plugin;
        this.registry = registry;
        this.itemFactory = itemFactory;
    }

    public void setStorageBackend(final StorageBackend backend) {
        this.storageBackend = backend;
        this.load();
    }

    public boolean shouldTrackPlacement(final String techItemId) {
        if (techItemId == null || techItemId.isBlank() || "tech_book".equalsIgnoreCase(techItemId)) {
            return false;
        }
        final TechItemDefinition definition = this.registry.getItem(techItemId);
        return definition != null && definition.icon().isBlock();
    }

    public void registerPlacedBlock(final Block block, final String techItemId) {
        if (!this.shouldTrackPlacement(techItemId)) {
            return;
        }
        this.placedBlocks.put(LocationKey.from(block.getLocation()), techItemId);
    }

    public void registerRuntimeBlock(final Block block, final String techItemId) {
        if (block == null || techItemId == null || techItemId.isBlank() || this.registry.getItem(techItemId) == null) {
            return;
        }
        this.placedBlocks.put(LocationKey.from(block.getLocation()), techItemId);
    }

    public boolean isTrackedBlock(final Block block) {
        return this.placedBlocks.containsKey(LocationKey.from(block.getLocation()));
    }

    public String placedItemId(final Block block) {
        if (block == null) {
            return null;
        }
        return this.placedBlocks.get(LocationKey.from(block.getLocation()));
    }

    public ItemStack buildDrop(final Block block) {
        final String techItemId = this.placedBlocks.get(LocationKey.from(block.getLocation()));
        if (techItemId == null) {
            return null;
        }
        final TechItemDefinition definition = this.registry.getItem(techItemId);
        return definition == null ? null : this.itemFactory.buildTechItem(definition);
    }

    public void unregister(final Block block) {
        this.placedBlocks.remove(LocationKey.from(block.getLocation()));
    }

    public void saveAll() {
        final Map<String, Map<String, Object>> blocks = new LinkedHashMap<>();
        int index = 0;
        for (final Map.Entry<LocationKey, String> entry : this.placedBlocks.entrySet()) {
            final Map<String, Object> data = new LinkedHashMap<>();
            final LocationKey key = entry.getKey();
            data.put("world", key.worldName());
            data.put("x", key.x());
            data.put("y", key.y());
            data.put("z", key.z());
            data.put("item-id", entry.getValue());
            blocks.put(String.valueOf(index++), data);
        }
        this.storageBackend.saveAllTechBlocks(blocks);
    }

    private void load() {
        final Map<String, Map<String, Object>> blocks = this.storageBackend.loadAllTechBlocks();
        for (final Map.Entry<String, Map<String, Object>> entry : blocks.entrySet()) {
            final Map<String, Object> data = entry.getValue();
            final String world = data.get("world") instanceof String s ? s : null;
            final String itemId = data.get("item-id") instanceof String s ? s : null;
            if (world == null || itemId == null || this.registry.getItem(itemId) == null) {
                continue;
            }
            final int x = data.get("x") instanceof Number n ? n.intValue() : 0;
            final int y = data.get("y") instanceof Number n ? n.intValue() : 0;
            final int z = data.get("z") instanceof Number n ? n.intValue() : 0;
            this.placedBlocks.put(new LocationKey(world, x, y, z), itemId);
        }
    }
}
