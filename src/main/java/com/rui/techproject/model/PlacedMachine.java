package com.rui.techproject.model;

import com.rui.techproject.util.LocationKey;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.UUID;

public final class PlacedMachine {
    private final LocationKey locationKey;
    private final String machineId;
    private final UUID owner;
    private final ItemStack[] inputInventory = new ItemStack[9];
    private final ItemStack[] outputInventory = new ItemStack[9];
    private final ItemStack[] upgradeInventory = new ItemStack[3];
    private String inputDirection = "ALL";
    private String outputDirection = "ALL";
    private boolean enabled = true;
    private long storedEnergy;
    private long totalGenerated;
    private long ticksActive;
    private int quarryFuel;
    private int quarryCursorX;
    private int quarryCursorZ;
    private int androidFuel;
    private int androidPatrolRadius = 2;
    private int androidPatrolHeight = 1;
    private int androidRouteCursor;
    private String androidRouteMode = "SERPENTINE";
    private String filterMode = "WHITELIST";
    private MachineRuntimeState runtimeState = MachineRuntimeState.IDLE;
    private String runtimeDetail = "待命";
    private int manualOperationTicks;
    private String manualOperationRecipeId;
    private String lockedRecipeId;

    public PlacedMachine(final LocationKey locationKey, final String machineId, final UUID owner) {
        this.locationKey = locationKey;
        this.machineId = machineId;
        this.owner = owner;
    }

    public LocationKey locationKey() {
        return this.locationKey;
    }

    public String machineId() {
        return this.machineId;
    }

    public UUID owner() {
        return this.owner;
    }

    public long storedEnergy() {
        return this.storedEnergy;
    }

    public long totalGenerated() {
        return this.totalGenerated;
    }

    public long ticksActive() {
        return this.ticksActive;
    }

    public int quarryFuel() {
        return this.quarryFuel;
    }

    public int quarryCursorX() {
        return this.quarryCursorX;
    }

    public int quarryCursorZ() {
        return this.quarryCursorZ;
    }

    public int androidFuel() {
        return this.androidFuel;
    }

    public int androidPatrolRadius() {
        return this.androidPatrolRadius;
    }

    public int androidPatrolHeight() {
        return this.androidPatrolHeight;
    }

    public int androidRouteCursor() {
        return this.androidRouteCursor;
    }

    public String androidRouteMode() {
        return this.androidRouteMode;
    }

    public String filterMode() {
        return this.filterMode;
    }

    public void setFilterMode(final String filterMode) {
        this.filterMode = filterMode == null || filterMode.isBlank() ? "WHITELIST" : filterMode;
    }

    public MachineRuntimeState runtimeState() {
        return this.runtimeState;
    }

    public String runtimeDetail() {
        return this.runtimeDetail;
    }

    public int manualOperationTicks() {
        return this.manualOperationTicks;
    }

    public String manualOperationRecipeId() {
        return this.manualOperationRecipeId;
    }

    public String lockedRecipeId() {
        return this.lockedRecipeId;
    }

    public void setLockedRecipeId(final String lockedRecipeId) {
        this.lockedRecipeId = lockedRecipeId == null || lockedRecipeId.isBlank() ? null : lockedRecipeId;
    }

    public String inputDirection() {
        return this.inputDirection;
    }

    public String outputDirection() {
        return this.outputDirection;
    }

    public boolean enabled() {
        return this.enabled;
    }

    public void setInputDirection(final String inputDirection) {
        this.inputDirection = inputDirection == null || inputDirection.isBlank() ? "ALL" : inputDirection;
    }

    public void setOutputDirection(final String outputDirection) {
        this.outputDirection = outputDirection == null || outputDirection.isBlank() ? "ALL" : outputDirection;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public boolean toggleEnabled() {
        this.enabled = !this.enabled;
        return this.enabled;
    }

    public ItemStack[] inputInventory() {
        return Arrays.copyOf(this.inputInventory, this.inputInventory.length);
    }

    public ItemStack[] outputInventory() {
        return Arrays.copyOf(this.outputInventory, this.outputInventory.length);
    }

    public ItemStack[] upgradeInventory() {
        return Arrays.copyOf(this.upgradeInventory, this.upgradeInventory.length);
    }

    public ItemStack inputAt(final int slot) {
        return slot >= 0 && slot < this.inputInventory.length ? this.inputInventory[slot] : null;
    }

    public ItemStack outputAt(final int slot) {
        return slot >= 0 && slot < this.outputInventory.length ? this.outputInventory[slot] : null;
    }

    public ItemStack upgradeAt(final int slot) {
        return slot >= 0 && slot < this.upgradeInventory.length ? this.upgradeInventory[slot] : null;
    }

    public void setInputAt(final int slot, final ItemStack itemStack) {
        if (slot < 0 || slot >= this.inputInventory.length) {
            return;
        }
        this.inputInventory[slot] = itemStack == null ? null : itemStack.clone();
    }

    public void setOutputAt(final int slot, final ItemStack itemStack) {
        if (slot < 0 || slot >= this.outputInventory.length) {
            return;
        }
        this.outputInventory[slot] = itemStack == null ? null : itemStack.clone();
    }

    public void setUpgradeAt(final int slot, final ItemStack itemStack) {
        if (slot < 0 || slot >= this.upgradeInventory.length) {
            return;
        }
        this.upgradeInventory[slot] = itemStack == null ? null : itemStack.clone();
    }

    public void replaceInputInventory(final ItemStack[] contents) {
        for (int slot = 0; slot < this.inputInventory.length; slot++) {
            this.inputInventory[slot] = slot < contents.length && contents[slot] != null ? contents[slot].clone() : null;
        }
    }

    public void replaceOutputInventory(final ItemStack[] contents) {
        for (int slot = 0; slot < this.outputInventory.length; slot++) {
            this.outputInventory[slot] = slot < contents.length && contents[slot] != null ? contents[slot].clone() : null;
        }
    }

    public void replaceUpgradeInventory(final ItemStack[] contents) {
        for (int slot = 0; slot < this.upgradeInventory.length; slot++) {
            this.upgradeInventory[slot] = slot < contents.length && contents[slot] != null ? contents[slot].clone() : null;
        }
    }

    public void addEnergy(final long amount) {
        this.storedEnergy += amount;
        if (amount > 0L) {
            this.totalGenerated += amount;
        }
    }

    public void setStoredEnergy(final long amount) {
        this.storedEnergy = Math.max(0L, amount);
    }

    public boolean consumeEnergy(final long amount) {
        if (this.storedEnergy < amount) {
            return false;
        }
        this.storedEnergy -= amount;
        return true;
    }

    public void tick() {
        this.ticksActive++;
    }

    public void setQuarryFuel(final int quarryFuel) {
        this.quarryFuel = Math.max(0, quarryFuel);
    }

    public void addQuarryFuel(final int amount) {
        if (amount <= 0) {
            return;
        }
        this.quarryFuel += amount;
    }

    public boolean consumeQuarryFuel(final int amount) {
        if (this.quarryFuel < amount) {
            return false;
        }
        this.quarryFuel -= amount;
        return true;
    }

    public void setQuarryCursor(final int quarryCursorX, final int quarryCursorZ) {
        this.quarryCursorX = Math.max(0, Math.min(15, quarryCursorX));
        this.quarryCursorZ = Math.max(0, Math.min(15, quarryCursorZ));
    }

    public void setAndroidFuel(final int androidFuel) {
        this.androidFuel = Math.max(0, androidFuel);
    }

    public void addAndroidFuel(final int amount) {
        if (amount <= 0) {
            return;
        }
        this.androidFuel += amount;
    }

    public boolean consumeAndroidFuel(final int amount) {
        if (amount <= 0) {
            return true;
        }
        if (this.androidFuel < amount) {
            return false;
        }
        this.androidFuel -= amount;
        return true;
    }

    public void setAndroidPatrolRadius(final int androidPatrolRadius) {
        this.androidPatrolRadius = Math.max(1, Math.min(8, androidPatrolRadius));
    }

    public void setAndroidPatrolHeight(final int androidPatrolHeight) {
        this.androidPatrolHeight = Math.max(0, Math.min(6, androidPatrolHeight));
    }

    public void setAndroidRouteCursor(final int androidRouteCursor) {
        this.androidRouteCursor = Math.max(0, androidRouteCursor);
    }

    public void setAndroidRouteMode(final String androidRouteMode) {
        this.androidRouteMode = androidRouteMode == null || androidRouteMode.isBlank() ? "SERPENTINE" : androidRouteMode;
    }

    public void setRuntimeState(final MachineRuntimeState runtimeState, final String runtimeDetail) {
        this.runtimeState = runtimeState == null ? MachineRuntimeState.IDLE : runtimeState;
        this.runtimeDetail = runtimeDetail == null || runtimeDetail.isBlank() ? "待命" : runtimeDetail;
    }

    public void setManualOperationTicks(final int manualOperationTicks) {
        this.manualOperationTicks = Math.max(0, manualOperationTicks);
    }

    public void setManualOperationRecipeId(final String manualOperationRecipeId) {
        this.manualOperationRecipeId = manualOperationRecipeId == null || manualOperationRecipeId.isBlank() ? null : manualOperationRecipeId;
    }

    public boolean hasManualOperation() {
        return this.manualOperationRecipeId != null;
    }

    public void tickManualOperation() {
        if (this.manualOperationTicks > 0) {
            this.manualOperationTicks--;
        }
        if (this.manualOperationTicks <= 0 && this.manualOperationRecipeId == null) {
            this.manualOperationTicks = 0;
        }
    }

    public void clearManualOperation() {
        this.manualOperationTicks = 0;
        this.manualOperationRecipeId = null;
    }

    public void restoreState(final long storedEnergy, final long totalGenerated, final long ticksActive) {
        this.storedEnergy = Math.max(0L, storedEnergy);
        this.totalGenerated = Math.max(0L, totalGenerated);
        this.ticksActive = Math.max(0L, ticksActive);
    }
}
