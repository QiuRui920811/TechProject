package com.rui.techproject.service;

import com.rui.techproject.TechMCPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 輕量區域系統 — 支援選點建立區域、進入觸發動作（RTP 火箭序列）、傳送點。
 */
public final class RegionService {

    // ── 選取狀態 ──
    private final Map<UUID, Location> pos1 = new ConcurrentHashMap<>();
    private final Map<UUID, Location> pos2 = new ConcurrentHashMap<>();

    // ── 區域資料 ──
    private final Map<String, TechRegion> regions = new ConcurrentHashMap<>();

    // ── 冷卻（防止重複觸發）──
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    // ── 正在傳送中的玩家 ──
    private final Set<UUID> travelingPlayers = ConcurrentHashMap.newKeySet();
    // ── 進入區域前的移動速度（用於傳送完畢後恢復）──
    private final Map<UUID, float[]> savedSpeeds = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 15_000L;

    private final TechMCPlugin plugin;
    private final File dataFile;

    public RegionService(final TechMCPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "tech-regions.yml");
        this.load();
    }

    // ══════════════════════════ 區域模型 ══════════════════════════

    public record TechRegion(String id, String worldName,
                             int minX, int minY, int minZ,
                             int maxX, int maxY, int maxZ,
                             String action,           // "rtp" 或 null
                             Location spawnPoint) {    // /tech set points 設定的傳送目標

        boolean contains(final Location loc) {
            return loc.getWorld() != null
                    && loc.getWorld().getName().equals(worldName)
                    && loc.getBlockX() >= minX && loc.getBlockX() <= maxX
                    && loc.getBlockY() >= minY && loc.getBlockY() <= maxY
                    && loc.getBlockZ() >= minZ && loc.getBlockZ() <= maxZ;
        }
    }

    // ══════════════════════════ 選取工具 ══════════════════════════

    public void setPos1(final Player player, final Location loc) {
        this.pos1.put(player.getUniqueId(), loc);
        player.sendMessage(Component.text("✔ 位置 1: " + fmt(loc), NamedTextColor.GREEN));
    }

    public void setPos2(final Player player, final Location loc) {
        this.pos2.put(player.getUniqueId(), loc);
        player.sendMessage(Component.text("✔ 位置 2: " + fmt(loc), NamedTextColor.GREEN));
    }

    public boolean hasSelection(final Player player) {
        return this.pos1.containsKey(player.getUniqueId()) && this.pos2.containsKey(player.getUniqueId());
    }

    // ══════════════════════════ 建立 / 刪除 ══════════════════════════

    public boolean createRegion(final Player player, final String id) {
        if (this.regions.containsKey(id)) return false;
        final Location a = this.pos1.get(player.getUniqueId());
        final Location b = this.pos2.get(player.getUniqueId());
        if (a == null || b == null || a.getWorld() == null) return false;

        final TechRegion region = new TechRegion(id, a.getWorld().getName(),
                Math.min(a.getBlockX(), b.getBlockX()), Math.min(a.getBlockY(), b.getBlockY()), Math.min(a.getBlockZ(), b.getBlockZ()),
                Math.max(a.getBlockX(), b.getBlockX()), Math.max(a.getBlockY(), b.getBlockY()), Math.max(a.getBlockZ(), b.getBlockZ()),
                null, null);
        this.regions.put(id, region);
        this.save();
        return true;
    }

    public boolean deleteRegion(final String id) {
        if (this.regions.remove(id) != null) {
            this.save();
            return true;
        }
        return false;
    }

    public boolean setAction(final String id, final String action) {
        final TechRegion r = this.regions.get(id);
        if (r == null) return false;
        this.regions.put(id, new TechRegion(r.id(), r.worldName(), r.minX(), r.minY(), r.minZ(), r.maxX(), r.maxY(), r.maxZ(), action, r.spawnPoint()));
        this.save();
        return true;
    }

    public boolean setSpawnPoint(final String id, final Location loc) {
        final TechRegion r = this.regions.get(id);
        if (r == null) return false;
        this.regions.put(id, new TechRegion(r.id(), r.worldName(), r.minX(), r.minY(), r.minZ(), r.maxX(), r.maxY(), r.maxZ(), r.action(), loc));
        this.save();
        return true;
    }

    public Collection<String> regionIds() {
        return this.regions.keySet();
    }

    // ══════════════════════════ 進入偵測 ══════════════════════════

    /**
     * 由 TechListener 的 PlayerMoveEvent 呼叫，僅在方塊座標變化時觸發。
     */
    public void onPlayerMove(final Player player, final Location to) {
        final UUID uuid = player.getUniqueId();
        if (this.travelingPlayers.contains(uuid)) return;
        final long now = System.currentTimeMillis();
        if (now - this.cooldowns.getOrDefault(uuid, 0L) < COOLDOWN_MS) return;

        for (final TechRegion region : this.regions.values()) {
            if (region.action() == null) continue;
            if (!region.contains(to)) continue;

            this.cooldowns.put(uuid, now);

            if ("rtp".equals(region.action())) {
                this.travelingPlayers.add(uuid);
                // 立即凍結：儲存原速度 → 鎖定移動
                this.savedSpeeds.put(uuid, new float[]{player.getWalkSpeed(), player.getFlySpeed()});
                player.setWalkSpeed(0.0f);
                player.setFlySpeed(0.0f);

                if (region.spawnPoint() != null) {
                    final Location sp = region.spawnPoint();
                    player.teleportAsync(sp).thenAccept(ok -> {
                        if (ok) {
                            this.plugin.getSafeScheduler().runEntity(player, () ->
                                    this.launchRocketTravel(player, sp));
                        } else {
                            this.restoreAndCleanup(player);
                        }
                    });
                } else {
                    this.launchRocketTravel(player, to);
                }
            }
            break;
        }
    }

    // ══════════════════════════ 火箭 RTP 序列（掛接 PlanetService 飛船動畫）══════════════════════════

    private void launchRocketTravel(final Player player, final Location origin) {
        this.plugin.getPlanetService().startCustomTravel(player, origin, p -> {
            // 回調時遊戲模式已恢復，但速度仍為 0 → 保持凍結直到 RTP 到位
            this.performRtp(p, 0);
        });
    }

    private void performRtp(final Player player, final int attempt) {
        if (attempt >= 20 || !player.isOnline()) {
            this.restoreAndCleanup(player);
            if (player.isOnline()) {
                player.sendMessage(this.plugin.getItemFactory().warning("找不到安全的傳送地點，請稍後再試。"));
            }
            return;
        }

        final World world = Bukkit.getWorlds().getFirst();
        final ThreadLocalRandom rng = ThreadLocalRandom.current();
        final int x = rng.nextInt(-500, 501);
        final int z = rng.nextInt(-500, 501);

        world.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk -> {
            final Block highest = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            final Location dest = highest.getLocation().add(0.5, 1, 0.5);
            dest.setYaw(player.getLocation().getYaw());
            dest.setPitch(player.getLocation().getPitch());

            if (!isSafe(highest)) {
                this.plugin.getSafeScheduler().runEntity(player, () -> this.performRtp(player, attempt + 1));
                return;
            }

            player.teleportAsync(dest).thenAccept(ok -> {
                if (!ok) {
                    this.plugin.getSafeScheduler().runEntity(player, () -> this.performRtp(player, attempt + 1));
                    return;
                }
                this.plugin.getSafeScheduler().runEntity(player, () -> {
                    this.restoreAndCleanup(player);
                    this.plugin.getTitleMsgService().send(player,
                            Component.text("✔ 抵達目的地", NamedTextColor.GREEN, TextDecoration.BOLD),
                            Component.text("X:" + dest.getBlockX() + " Z:" + dest.getBlockZ(), NamedTextColor.GRAY),
                            40L, Sound.BLOCK_NOTE_BLOCK_BELL);
                    player.playSound(dest, Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.3f);
                    player.playSound(dest, "techproject:electric_keys", 0.35f, 1f);
                    this.launchFirework(dest);
                });
            });
        });
    }

    /** 恢復玩家速度並從傳送追蹤中移除。 */
    private void restoreAndCleanup(final Player player) {
        final UUID uuid = player.getUniqueId();
        this.travelingPlayers.remove(uuid);
        final float[] speeds = this.savedSpeeds.remove(uuid);
        if (player.isOnline()) {
            player.setWalkSpeed(speeds != null ? speeds[0] : 0.2f);
            player.setFlySpeed(speeds != null ? speeds[1] : 0.1f);
        }
    }

    /** 玩家是否正在區域傳送流程中。 */
    public boolean isTraveling(final UUID playerId) {
        return this.travelingPlayers.contains(playerId);
    }

    /** 斷線 / 死亡時清除所有暫存狀態，避免記憶體洩漏與速度鎖死。 */
    public void cleanupPlayer(final UUID playerId) {
        this.travelingPlayers.remove(playerId);
        this.savedSpeeds.remove(playerId);
        this.cooldowns.remove(playerId);
        this.pos1.remove(playerId);
        this.pos2.remove(playerId);
    }

    private static boolean isSafe(final Block ground) {
        final Material type = ground.getType();
        if (type == Material.LAVA || type == Material.FIRE || type == Material.MAGMA_BLOCK
                || type == Material.CACTUS || type == Material.SWEET_BERRY_BUSH
                || type == Material.POWDER_SNOW || !type.isSolid()) {
            return false;
        }
        final Block above1 = ground.getRelative(BlockFace.UP);
        final Block above2 = above1.getRelative(BlockFace.UP);
        return above1.isPassable() && above2.isPassable();
    }

    private void launchFirework(final Location loc) {
        final World world = loc.getWorld();
        if (world == null) return;
        final Firework fw = world.spawn(loc, Firework.class);
        final FireworkMeta meta = fw.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder()
                .with(FireworkEffect.Type.BALL_LARGE)
                .withColor(Color.AQUA, Color.WHITE)
                .withFade(Color.PURPLE)
                .trail(true).flicker(true).build());
        meta.setPower(0);
        fw.setFireworkMeta(meta);
        fw.detonate();
    }

    // ══════════════════════════ 持久化 ══════════════════════════

    private void save() {
        final YamlConfiguration cfg = new YamlConfiguration();
        for (final TechRegion r : this.regions.values()) {
            final String path = "regions." + r.id();
            cfg.set(path + ".world", r.worldName());
            cfg.set(path + ".min", r.minX() + "," + r.minY() + "," + r.minZ());
            cfg.set(path + ".max", r.maxX() + "," + r.maxY() + "," + r.maxZ());
            if (r.action() != null) cfg.set(path + ".action", r.action());
            if (r.spawnPoint() != null) {
                cfg.set(path + ".spawn", r.spawnPoint().getWorld().getName()
                        + "," + r.spawnPoint().getX()
                        + "," + r.spawnPoint().getY()
                        + "," + r.spawnPoint().getZ()
                        + "," + r.spawnPoint().getYaw()
                        + "," + r.spawnPoint().getPitch());
            }
        }
        this.plugin.getSafeScheduler().runAsync(() -> {
            try { cfg.save(this.dataFile); } catch (final IOException e) { this.plugin.getLogger().warning("區域存檔失敗: " + e.getMessage()); }
        });
    }

    private void load() {
        if (!this.dataFile.exists()) return;
        final YamlConfiguration cfg = YamlConfiguration.loadConfiguration(this.dataFile);
        final ConfigurationSection sec = cfg.getConfigurationSection("regions");
        if (sec == null) return;
        for (final String id : sec.getKeys(false)) {
            try {
                final String worldName = sec.getString(id + ".world", "world");
                final String[] min = sec.getString(id + ".min", "0,0,0").split(",");
                final String[] max = sec.getString(id + ".max", "0,0,0").split(",");
                final String action = sec.getString(id + ".action");
                Location spawn = null;
                if (sec.contains(id + ".spawn")) {
                    final String[] sp = sec.getString(id + ".spawn", "").split(",");
                    if (sp.length >= 4) {
                        final World w = Bukkit.getWorld(sp[0]);
                        if (w != null) {
                            spawn = new Location(w,
                                    Double.parseDouble(sp[1]), Double.parseDouble(sp[2]), Double.parseDouble(sp[3]),
                                    sp.length >= 5 ? Float.parseFloat(sp[4]) : 0f,
                                    sp.length >= 6 ? Float.parseFloat(sp[5]) : 0f);
                        }
                    }
                }
                this.regions.put(id, new TechRegion(id, worldName,
                        Integer.parseInt(min[0].trim()), Integer.parseInt(min[1].trim()), Integer.parseInt(min[2].trim()),
                        Integer.parseInt(max[0].trim()), Integer.parseInt(max[1].trim()), Integer.parseInt(max[2].trim()),
                        action, spawn));
            } catch (final Exception e) {
                this.plugin.getLogger().warning("載入區域 " + id + " 失敗: " + e.getMessage());
            }
        }
        this.plugin.getLogger().info("已載入 " + this.regions.size() + " 個科技區域。");
    }

    private static String fmt(final Location loc) {
        return loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
    }
}
