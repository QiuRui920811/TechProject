package com.rui.techproject.service;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 「異界傳送門」GUI 的 InventoryHolder，用來在事件中識別這個專屬介面。
 */
public final class OtherworldPortalHolder implements InventoryHolder {

    private final UUID playerId;
    private Inventory inventory;

    public OtherworldPortalHolder(final UUID playerId) {
        this.playerId = playerId;
    }

    public UUID playerId() {
        return this.playerId;
    }

    public void setInventory(final Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return this.inventory;
    }

    @Nullable
    public Inventory getInventoryOrNull() {
        return this.inventory;
    }
}
