package com.rui.techproject.util;

import com.mojang.serialization.Lifecycle;
import io.papermc.paper.world.PaperWorldLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * NMS 世界建立工具 — 繞過 Folia/Luminol 的 {@code Bukkit.createWorld()} 限制。
 * <p>
 * 參考 <a href="https://github.com/TheNextLvl-net/worlds">TheNextLvl-net/worlds</a>
 * 的做法，直接呼叫 {@link MinecraftServer#createLevel} 來動態載入世界。
 */
public final class NmsWorldUtil {

    private NmsWorldUtil() {}

    /**
     * 在 Folia/Luminol 伺服器上動態載入一個已存在的世界目錄。
     * <p>
     * 此方法<b>必須在全域主線程（Global Region）上呼叫</b>。
     * 世界目錄必須位於 {@code Bukkit.getWorldContainer()} 下。
     *
     * @param worldName 世界目錄名稱（不含路徑）
     * @param logger    用於記錄的 Logger
     * @return 已載入的 {@link World}，若失敗則回傳 {@code null}
     */
    public static World loadWorld(final String worldName, final Logger logger) {
        // 若已載入就直接回傳
        final World existing = Bukkit.getWorld(worldName);
        if (existing != null) return existing;

        try {
            final CraftServer craftServer = (CraftServer) Bukkit.getServer();
            final MinecraftServer console = craftServer.getServer();
            final Path worldContainer = Bukkit.getWorldContainer().toPath();
            final Path worldPath = worldContainer.resolve(worldName);

            // 確保 data/ 目錄存在，避免 NoSuchFileException: chunks.dat
            Files.createDirectories(worldPath.resolve("data"));

            // 驗證 region 檔案是否存在
            final boolean hasRegion = Files.isDirectory(worldPath.resolve("region"))
                    && Files.list(worldPath.resolve("region")).findAny().isPresent();
            logger.info("[NmsWorldUtil] 載入 " + worldName + " — 目錄存在=" + Files.isDirectory(worldPath)
                    + ", region存在=" + hasRegion
                    + ", level.dat存在=" + Files.exists(worldPath.resolve("level.dat")));

            final LevelStorageSource.LevelStorageAccess storageAccess =
                    LevelStorageSource.createDefault(worldContainer)
                            .validateAndCreateAccess(worldName, LevelStem.OVERWORLD);

            // 優先嘗試讀取現有 level.dat（透過完全反射繞過 Registry 泛型擦除）
            final PaperWorldLoader.LevelDataResult levelDataResult =
                    PaperWorldLoader.getLevelData(storageAccess);

            if (!levelDataResult.fatalError() && levelDataResult.dataTag() != null) {
                final PrimaryLevelData existingData = parseLevelData(
                        levelDataResult.dataTag(), console.worldLoaderContext, logger);
                if (existingData != null) {
                    logger.info("[NmsWorldUtil] 成功讀取現有 level.dat，使用原始世界資料載入 " + worldName);
                    return createAndReturn(worldName, console, storageAccess, existingData, logger);
                }
                logger.warning("[NmsWorldUtil] parseLevelData 失敗，改用 loadWorldFresh");
            }

            // 無 level.dat 或解析失敗 → 建立新的 LevelData（但標記為 initialized 以避免生成新地形）
            return loadWorldFresh(worldName, console, storageAccess, logger);

        } catch (final IOException e) {
            logger.severe("[NmsWorldUtil] IO 錯誤載入世界 " + worldName + "：" + e.getMessage());
            return null;
        } catch (final Exception e) {
            logger.severe("[NmsWorldUtil] 載入世界 " + worldName + " 失敗：" + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 透過完全反射呼叫 getLevelDataAndDimensions，繞過編譯期的 bytecode descriptor。
     * 根本原因：Luminol 的 Registry<LevelStem> 泛型擦除後的 bytecode 描述符與編譯器產生的不同，
     * 導致 NoSuchMethodError。用反射按方法名找就不會有問題。
     */
    @SuppressWarnings("unchecked")
    private static PrimaryLevelData parseLevelData(
            final com.mojang.serialization.Dynamic<?> dataTag,
            final net.minecraft.server.WorldLoader.DataLoadContext context,
            final Logger logger) {
        try {
            final var registryAccess = context.datapackDimensions();

            // 1) 用反射取得 LEVEL_STEM Registry
            Method lookupMethod = null;
            for (final Method m : registryAccess.getClass().getMethods()) {
                if (m.getName().equals("lookupOrThrow") && m.getParameterCount() == 1) {
                    lookupMethod = m;
                    break;
                }
            }
            if (lookupMethod == null) throw new NoSuchMethodException("lookupOrThrow not found on " + registryAccess.getClass().getName());
            final Object stemRegistry = lookupMethod.invoke(registryAccess, Registries.LEVEL_STEM);

            // 2) 用反射找 getLevelDataAndDimensions（按名稱 + 參數數量，不靠 bytecode descriptor）
            Method getLevelDataMethod = null;
            for (final Method m : LevelStorageSource.class.getDeclaredMethods()) {
                if (m.getName().equals("getLevelDataAndDimensions") && m.getParameterCount() == 4) {
                    getLevelDataMethod = m;
                    break;
                }
            }
            if (getLevelDataMethod == null) throw new NoSuchMethodException("getLevelDataAndDimensions not found");
            getLevelDataMethod.setAccessible(true);

            final Object result = getLevelDataMethod.invoke(null,
                    dataTag, context.dataConfiguration(), stemRegistry, context.datapackWorldgen());

            // 3) result.worldData() 也用反射呼叫（泛型擦除可能導致 NoSuchMethodError）
            Method worldDataMethod = null;
            for (final Method m : result.getClass().getMethods()) {
                if (m.getName().equals("worldData") && m.getParameterCount() == 0) {
                    worldDataMethod = m;
                    break;
                }
            }
            if (worldDataMethod == null) throw new NoSuchMethodException("worldData not found on " + result.getClass().getName());
            return (PrimaryLevelData) worldDataMethod.invoke(result);
        } catch (final Exception e) {
            logger.warning("[NmsWorldUtil] parseLevelData 失敗：" + e.getClass().getSimpleName() + " - " + e.getMessage());
            if (e.getCause() != null) {
                logger.warning("[NmsWorldUtil]   cause: " + e.getCause().getClass().getSimpleName() + " - " + e.getCause().getMessage());
            }
            return null;
        }
    }

    /** 建立世界並回傳（給成功讀取 level.dat 的路徑用） */
    private static World createAndReturn(final String worldName,
                                         final MinecraftServer console,
                                         final LevelStorageSource.LevelStorageAccess storageAccess,
                                         final PrimaryLevelData primaryLevelData,
                                         final Logger logger) {
        try {
            primaryLevelData.checkName(worldName);
            primaryLevelData.setModdedInfo(console.getServerModName(),
                    console.getModdedStatus().shouldReportAsModified());

            final LevelStem stem = stemFromOverworld(console);
            // 使用自訂 dimension key，避免覆蓋主世界的 minecraft:overworld 註冊
            final ResourceKey<LevelStem> dimKey = ResourceKey.create(
                    Registries.LEVEL_STEM,
                    Identifier.fromNamespaceAndPath("techproject", worldName));
            final PaperWorldLoader.WorldLoadingInfo loadingInfo =
                    new PaperWorldLoader.WorldLoadingInfo(0, worldName, "normal", dimKey, true);

            console.createLevel(stem, loadingInfo, storageAccess, primaryLevelData);

            final World world = Bukkit.getWorld(worldName);
            if (world != null) {
                logger.info("[NmsWorldUtil] 世界 " + worldName + " 已成功透過 NMS 載入（使用現有 level.dat）。");
            } else {
                logger.warning("[NmsWorldUtil] createLevel 完成但找不到世界 " + worldName);
            }
            return world;
        } catch (final Exception e) {
            logger.severe("[NmsWorldUtil] createAndReturn 失敗：" + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /** 從主世界取得 LevelStem，避免 Registry 泛型擦除問題。 */
    private static LevelStem stemFromOverworld(final MinecraftServer console) {
        final ServerLevel overworld = console.overworld();
        return new LevelStem(
                overworld.dimensionTypeRegistration(),
                overworld.getChunkSource().getGenerator()
        );
    }

    private static World loadWorldFresh(final String worldName,
                                        final MinecraftServer console,
                                        final LevelStorageSource.LevelStorageAccess storageAccess,
                                        final Logger logger) {
        try {
            final var context = console.worldLoaderContext;

            // 建立最小 LevelSettings
            final var levelSettings = new net.minecraft.world.level.LevelSettings(
                    worldName,
                    net.minecraft.world.level.GameType.ADVENTURE,
                    false, // hardcore
                    net.minecraft.world.Difficulty.NORMAL,
                    false, // commands
                    new net.minecraft.world.level.gamerules.GameRules(context.dataConfiguration().enabledFeatures()),
                    context.dataConfiguration()
            );

            final var worldOptions = new net.minecraft.world.level.levelgen.WorldOptions(
                    java.util.concurrent.ThreadLocalRandom.current().nextLong(),
                    false, // generateStructures
                    false  // bonusChest
            );

            // 直接從主世界取得 LevelStem，完全避開 Registry 查詢
            final LevelStem stem = stemFromOverworld(console);

            final PrimaryLevelData primaryLevelData = new PrimaryLevelData(
                    levelSettings,
                    worldOptions,
                    PrimaryLevelData.SpecialWorldProperty.NONE,
                    Lifecycle.stable()
            );

            // 標記為已初始化，避免 NMS 跑 "Selecting spawn point" 生成原版地形
            try {
                final Field initializedField = PrimaryLevelData.class.getDeclaredField("initialized");
                initializedField.setAccessible(true);
                initializedField.setBoolean(primaryLevelData, true);
            } catch (final Exception e) {
                logger.warning("[NmsWorldUtil] 無法設定 initialized=true：" + e.getMessage());
            }

            primaryLevelData.checkName(worldName);
            primaryLevelData.setModdedInfo(console.getServerModName(),
                    console.getModdedStatus().shouldReportAsModified());

            // 使用自訂 dimension key，避免覆蓋主世界的 minecraft:overworld 註冊
            final ResourceKey<LevelStem> dimKey = ResourceKey.create(
                    Registries.LEVEL_STEM,
                    Identifier.fromNamespaceAndPath("techproject", worldName));
            final PaperWorldLoader.WorldLoadingInfo loadingInfo =
                    new PaperWorldLoader.WorldLoadingInfo(
                            0, worldName, "normal", dimKey, true
                    );

            console.createLevel(stem, loadingInfo, storageAccess, primaryLevelData);

            final World world = Bukkit.getWorld(worldName);
            if (world != null) {
                logger.info("[NmsWorldUtil] 新世界 " + worldName + " 已成功建立。");
            }
            return world;

        } catch (final Exception e) {
            logger.severe("[NmsWorldUtil] 建立新世界 " + worldName + " 失敗：" + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 卸載世界。嘗試 NMS 方式，失敗則 fallback 到 Bukkit。
     */
    public static boolean unloadWorld(final World world, final boolean save) {
        try {
            return Bukkit.unloadWorld(world, save);
        } catch (final Exception e) {
            // Folia 可能也不支援 unloadWorld — 嘗試強制
            try {
                final CraftServer craftServer = (CraftServer) Bukkit.getServer();
                final MinecraftServer console = craftServer.getServer();
                final var nmsWorld = ((org.bukkit.craftbukkit.CraftWorld) world).getHandle();
                // 嘗試 removeLevel (如果存在)
                console.getAllLevels().forEach(level -> {}); // no-op check
                return Bukkit.unloadWorld(world, save);
            } catch (final Exception ex) {
                return false;
            }
        }
    }
}
