package com.rui.techproject.util;

import org.bukkit.Location;
import org.bukkit.World;

public record LocationKey(String worldName, int x, int y, int z) {

    public static LocationKey from(final Location location) {
        final World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Location world cannot be null");
        }
        return new LocationKey(world.getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
}
