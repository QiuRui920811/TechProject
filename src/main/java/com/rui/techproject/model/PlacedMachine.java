package com.rui.techproject.model;

import com.rui.techproject.util.LocationKey;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public final class PlacedMachine {
    private final LocationKey locationKey;
    private final String machineId;
    private final UUID owner;
    private final ItemStack[] inputInventory = new ItemStack[9];
    private final ItemStack[] outputInventory = new ItemStack[9];
    private final ItemStack[] upgradeInventory = new ItemStack[3];
    private String inputDirection = "ALL";
    private String outputDirection = "ALL";
    private String energyInputDirection = "ALL";
    private String energyOutputDirection = "ALL";
    private boolean enabled = true;
    private volatile long enabledSince = System.currentTimeMillis();
    private final AtomicLong storedEnergy = new AtomicLong();
    private final AtomicLong totalGenerated = new AtomicLong();
    private volatile long ticksActive;
    /** 跨 region 存取防護鎖。用 tryLock() 避免死鎖。 */
    private final ReentrantLock inventoryLock = new ReentrantLock();
    private volatile double chickenProgress;
    private int quarryFuel;
    private int quarryCursorX;
    private int quarryCursorZ;
    private int androidFuel;
    private int androidPatrolRadius = 2;
    private int androidPatrolHeight = 1;
    private int androidRouteCursor;
    private String androidRouteMode = "SERPENTINE";
    private String filterMode = "WHITELIST";
    private final Set<UUID> trustedPlayers = ConcurrentHashMap.newKeySet();
    private volatile MachineRuntimeState runtimeState = MachineRuntimeState.IDLE;
    private volatile String runtimeDetail = "待命";
    private volatile int manualOperationTicks;
    private volatile String manualOperationRecipeId;
    private String lockedRecipeId;
    private String redstoneMode = "NONE";
    private String teleportLabel;
    private boolean teleportPublic;

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

    public boolean isTrusted(final UUID playerUuid) {
        return this.trustedPlayers.contains(playerUuid);
    }

    public boolean addTrusted(final UUID playerUuid) {
        return this.trustedPlayers.add(playerUuid);
    }

    public boolean removeTrusted(final UUID playerUuid) {
        return this.trustedPlayers.remove(playerUuid);
    }

    public Set<UUID> trustedPlayers() {
        return Collections.unmodifiableSet(this.trustedPlayers);
    }

    public void setTrustedPlayers(final Set<UUID> uuids) {
        this.trustedPlayers.clear();
        if (uuids != null) {
            this.trustedPlayers.addAll(uuids);
        }
    }

    public long storedEnergy() {
        return this.storedEnergy.get();
    }

    public long totalGenerated() {
        return this.totalGenerated.get();
    }

    public long ticksActive() {
        return this.ticksActive;
    }

    public double chickenProgress() {
        return this.chickenProgress;
    }

    public void setChickenProgress(final double value) {
        this.chickenProgress = value;
    }

    public void addChickenProgress(final double delta) {
        this.chickenProgress += delta;
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

    public String redstoneMode() {
        return this.redstoneMode;
    }

    public void setRedstoneMode(final String redstoneMode) {
        this.redstoneMode = redstoneMode == null || redstoneMode.isBlank() ? "NONE" : redstoneMode;
    }

    public String teleportLabel() {
        return this.teleportLabel;
    }

    public void setTeleportLabel(final String teleportLabel) {
        this.teleportLabel = teleportLabel == null || teleportLabel.isBlank() ? null : teleportLabel.trim();
    }

    public boolean teleportPublic() {
        return this.teleportPublic;
    }

    public void setTeleportPublic(final boolean teleportPublic) {
        this.teleportPublic = teleportPublic;
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

    public String energyInputDirection() {
        return this.energyInputDirection;
    }

    public String energyOutputDirection() {
        return this.energyOutputDirection;
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

    public void setEnergyInputDirection(final String energyInputDirection) {
        this.energyInputDirection = energyInputDirection == null || energyInputDirection.isBlank() ? "ALL" : energyInputDirection;
    }

    public void setEnergyOutputDirection(final String energyOutputDirection) {
        this.energyOutputDirection = energyOutputDirection == null || energyOutputDirection.isBlank() ? "ALL" : energyOutputDirection;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            this.enabledSince = System.currentTimeMillis();
        }
    }

    public boolean toggleEnabled() {
        this.enabled = !this.enabled;
        if (this.enabled) {
            this.enabledSince = System.currentTimeMillis();
        }
        return this.enabled;
    }

    /** 回傳自上次開機以來經過的毫秒數；關機時回傳 0。 */
    public long enabledDurationMs() {
        return this.enabled ? System.currentTimeMillis() - this.enabledSince : 0L;
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
        this.storedEnergy.addAndGet(amount);
        if (amount > 0L) {
            this.totalGenerated.addAndGet(amount);
        }
    }

    public void setStoredEnergy(final long amount) {
        this.storedEnergy.set(Math.max(0L, amount));
    }

    /**
     * 原子扣除能量。跨 region 安全（CAS 迴圈）。
     */
    public boolean consumeEnergy(final long amount) {
        while (true) {
            final long current = this.storedEnergy.get();
            if (current < amount) {
                return false;
            }
            if (this.storedEnergy.compareAndSet(current, current - amount)) {
                return true;
            }
        }
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
        this.storedEnergy.set(Math.max(0L, storedEnergy));
        this.totalGenerated.set(Math.max(0L, totalGenerated));
        this.ticksActive = Math.max(0L, ticksActive);
    }

    /** 嘗試取得 inventory 鎖。回傳 false 表示其他 region 正在操作，應跳過本次傳輸。 */
    public boolean tryLockInventory() {
        return this.inventoryLock.tryLock();
    }

    public void unlockInventory() {
        this.inventoryLock.unlock();
    }

    public void restoreChickenProgress(final double progress) {
        this.chickenProgress = Math.max(0.0, progress);
    }
}
