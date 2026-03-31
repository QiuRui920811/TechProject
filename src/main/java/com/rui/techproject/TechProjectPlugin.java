package com.rui.techproject;

import com.rui.techproject.addon.TechAddonService;
import com.rui.techproject.addon.TechInteractionDefinition;
import com.rui.techproject.addon.TechInteractionType;
import com.rui.techproject.command.TechCommand;
import com.rui.techproject.listener.TechListener;
import com.rui.techproject.model.GuideCategory;
import com.rui.techproject.model.TechTier;
import com.rui.techproject.service.AchievementGuiService;
import com.rui.techproject.service.AchievementService;
import com.rui.techproject.service.BlueprintService;
import com.rui.techproject.service.CookingService;
import com.rui.techproject.service.MeteorService;
import com.rui.techproject.service.ItemSearchService;
import com.rui.techproject.service.MachineService;
import com.rui.techproject.service.PlacedTechBlockService;
import com.rui.techproject.service.PlanetService;
import com.rui.techproject.service.PlayerProgressService;
import com.rui.techproject.service.TechBookService;
import com.rui.techproject.service.TechCropService;
import com.rui.techproject.service.TechRegistry;
import com.rui.techproject.service.TitleService;
import com.rui.techproject.storage.MigrationManager;
import com.rui.techproject.storage.StorageManager;
import com.rui.techproject.util.ItemFactoryUtil;
import com.rui.techproject.util.SafeScheduler;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TechProjectPlugin extends JavaPlugin {
    private static final String[] BUNDLED_DATAPACK_RESOURCES = {
            "datapacks/techproject_dimensions/pack.mcmeta",
            "datapacks/techproject_dimensions/data/techproject/dimension_type/aurelia_twilight.json",
            "datapacks/techproject_dimensions/data/techproject/dimension_type/cryon_glacier.json",
            "datapacks/techproject_dimensions/data/techproject/dimension_type/nyx_end_sky.json",
            "datapacks/techproject_dimensions/data/techproject/dimension_type/helion_scorch.json",
            "datapacks/techproject_dimensions/data/techproject/dimension_type/tempest_overcast.json",
            "datapacks/techproject_dimensions/data/techproject/dimension/aurelia_surface.json",
            "datapacks/techproject_dimensions/data/techproject/dimension/cryon_surface.json",
            "datapacks/techproject_dimensions/data/techproject/dimension/nyx_orbit.json",
            "datapacks/techproject_dimensions/data/techproject/dimension/helion_surface.json",
                "datapacks/techproject_dimensions/data/techproject/dimension/tempest_surface.json",
                "datapacks/techproject_dimensions/data/techproject/worldgen/biome/aurelia_luminous_wastes.json",
                "datapacks/techproject_dimensions/data/techproject/worldgen/biome/cryon_shiverfields.json",
                "datapacks/techproject_dimensions/data/techproject/worldgen/biome/nyx_abyss.json",
                "datapacks/techproject_dimensions/data/techproject/worldgen/biome/helion_cinderfields.json",
                "datapacks/techproject_dimensions/data/techproject/worldgen/biome/tempest_supercell.json",
                "datapacks/techproject_dimensions/data/techproject/worldgen/noise_settings/aurelia_surface_noise.json",
                "datapacks/techproject_dimensions/data/techproject/worldgen/noise_settings/cryon_surface_noise.json",
                "datapacks/techproject_dimensions/data/techproject/worldgen/noise_settings/nyx_orbit_noise.json",
                "datapacks/techproject_dimensions/data/techproject/worldgen/noise_settings/helion_surface_noise.json",
                "datapacks/techproject_dimensions/data/techproject/worldgen/noise_settings/tempest_surface_noise.json"
    };

    private static final String[] MANAGED_DATA_FILES = {
            "tech-metadata.yml",
            "tech-content-core.yml",
            "tech-content-systems.yml",
            "tech-content.yml",
            "tech-content-expansion.yml",
            "tech-content-megastructures.yml",
            "tech-content-chickens.yml",
            "tech-blueprints.yml",
            "tech-guides.yml",
            "tech-guides_zh_tw.yml",
            "tech-guides_en_us.yml",
            "tech-titles.yml"
    };

    private TechRegistry techRegistry;
    private SafeScheduler safeScheduler;
    private ItemFactoryUtil itemFactory;
    private PlayerProgressService playerProgressService;
    private BlueprintService blueprintService;
    private TechBookService techBookService;
    private TechAddonService techAddonService;
    private AchievementService achievementService;
    private MachineService machineService;
    private PlacedTechBlockService placedTechBlockService;
    private TechCropService techCropService;
    private PlanetService planetService;
    private CookingService cookingService;
    private AchievementGuiService achievementGuiService;
    private ItemSearchService itemSearchService;
    private MeteorService meteorService;
    private StorageManager storageManager;
    private TitleService titleService;
    private com.rui.techproject.listener.DiscordSrvHook discordSrvHook;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        this.mergeDefaultConfig();
        this.syncManagedDataFiles();
        this.installBundledPlanetDatapack();

        // 初始化儲存後端
        this.storageManager = new StorageManager(this.getDataFolder(), this.getLogger());
        this.storageManager.init(this.getConfig());

        // 執行資料遷移
        final MigrationManager migrationManager = new MigrationManager(this.getLogger());
        migrationManager.runPendingMigrations(this.storageManager.getBackend());

        this.techRegistry = new TechRegistry();
        this.techRegistry.seedDefaults(this);
        this.safeScheduler = new SafeScheduler(this);
        this.itemFactory = new ItemFactoryUtil(this, this.techRegistry);
        this.playerProgressService = new PlayerProgressService(this, this.techRegistry);
        this.blueprintService = new BlueprintService(this, this.techRegistry, this.itemFactory);
        this.techAddonService = new TechAddonService();
        this.techBookService = new TechBookService(this, this.techRegistry, this.playerProgressService, this.itemFactory, this.blueprintService, this.techAddonService);
        this.bootstrapAddonApi();
        this.achievementService = new AchievementService(this, this.techRegistry, this.playerProgressService, this.itemFactory, this.safeScheduler);
        this.placedTechBlockService = new PlacedTechBlockService(this, this.techRegistry, this.itemFactory);
        this.techCropService = new TechCropService(this, this.techRegistry, this.itemFactory);
        this.planetService = new PlanetService(this, this.safeScheduler, this.itemFactory, this.techRegistry);
        this.machineService = new MachineService(this, this.techRegistry, this.playerProgressService, this.achievementService, this.safeScheduler, this.itemFactory, this.techCropService);
        this.cookingService = new CookingService(this, this.techRegistry, this.itemFactory, this.safeScheduler);
        this.achievementGuiService = new AchievementGuiService(this, this.techRegistry, this.playerProgressService, this.itemFactory);
        this.titleService = new TitleService(this, this.playerProgressService);
        this.achievementGuiService.setTitleService(this.titleService);
        this.itemSearchService = new ItemSearchService(this);
        this.meteorService = new MeteorService(this, this.safeScheduler, this.itemFactory);

        final com.rui.techproject.storage.StorageBackend backend = this.storageManager.getBackend();
        this.playerProgressService.setStorageBackend(backend);
        this.placedTechBlockService.setStorageBackend(backend);
        this.techCropService.setStorageBackend(backend);
        this.planetService.setStorageBackend(backend);
        this.machineService.setStorageBackend(backend);

        this.getServer().getPluginManager().registerEvents(new TechListener(this), this);
        final TechCommand techCommand = new TechCommand(this, this.techRegistry, this.itemFactory);
        if (this.getCommand("tech") != null) {
            this.getCommand("tech").setExecutor(techCommand);
            this.getCommand("tech").setTabCompleter(techCommand);
        }

        this.machineService.start();
        this.machineService.purgeOrphanDisplays();
        this.planetService.start();
        this.meteorService.start();

        if (this.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new com.rui.techproject.service.TechPlaceholderExpansion(this).register();
            this.getLogger().info("PlaceholderAPI 已挂接，請使用 %techproject_title% 等佔位符。");
        }

        // DiscordSRV 挂接：攤截搜尋指令等訊息不轉發至 Discord
        if (this.getServer().getPluginManager().getPlugin("DiscordSRV") != null) {
            this.discordSrvHook = new com.rui.techproject.listener.DiscordSrvHook(this);
            this.discordSrvHook.tryRegister();
        }

        // 定期自動存檔（每 5 分鐘），防止崩潰時資料遺失
        final long autoSaveInterval = 20L * 60L * 5L; // 6000 ticks = 5 分鐘
        this.safeScheduler.runGlobalTimer(task -> this.autoSave(), autoSaveInterval, autoSaveInterval);

        this.getLogger().info("TechProject enabled: " + this.techRegistry.summaryLine());
    }

    /**
     * 將 JAR 內建的 config.yml 預設值合併到磁碟上的 config.yml，
     * 只補齊缺少的 key，不會覆蓋使用者已設定的值。
     */
    private void mergeDefaultConfig() {
        try (final InputStream defaultStream = this.getResource("config.yml")) {
            if (defaultStream == null) {
                return;
            }
            final YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            boolean changed = false;
            for (final String key : defaults.getKeys(true)) {
                if (!defaults.isConfigurationSection(key) && !this.getConfig().isSet(key)) {
                    this.getConfig().set(key, defaults.get(key));
                    changed = true;
                }
            }
            if (changed) {
                this.saveConfig();
                this.getLogger().info("config.yml 已自動補齊新增的預設設定項。");
            }
        } catch (final IOException exception) {
            this.getLogger().warning("合併預設設定失敗：" + exception.getMessage());
        }
    }

    private void syncManagedDataFiles() {
        int created = 0;
        int updated = 0;
        for (final String file : MANAGED_DATA_FILES) {
            final Path target = this.getDataFolder().toPath().resolve(file);
            try (InputStream stream = this.getResource(file)) {
                if (stream == null) {
                    continue;
                }
                final byte[] bundled = stream.readAllBytes();
                if (Files.notExists(target)) {
                    Files.createDirectories(target.getParent());
                    Files.write(target, bundled);
                    created++;
                } else {
                    final byte[] existing = Files.readAllBytes(target);
                    if (!java.util.Arrays.equals(existing, bundled)) {
                        Files.write(target, bundled);
                        updated++;
                    }
                }
            } catch (final IOException exception) {
                this.getLogger().warning("同步資料檔失敗 " + file + "：" + exception.getMessage());
            }
        }
        if (created > 0 || updated > 0) {
            this.getLogger().info("已同步資料檔：新增 " + created + "，更新 " + updated + "。");
        }
    }

    private void installBundledPlanetDatapack() {
        if (this.getServer().getWorlds().isEmpty()) {
            return;
        }
        final Path primaryWorld = this.getServer().getWorlds().get(0).getWorldFolder().toPath();
        final Path datapackRoot = primaryWorld.resolve("datapacks").resolve("techproject_dimensions");
        boolean installed = false;
        try {
            for (final String resource : BUNDLED_DATAPACK_RESOURCES) {
                final String relative = resource.substring("datapacks/techproject_dimensions/".length());
                installed |= this.copyBundledResource(resource, datapackRoot.resolve(relative));
            }
        } catch (final IOException exception) {
            this.getLogger().warning("無法安裝星球自訂維度資料包：" + exception.getMessage());
            return;
        }
        if (installed) {
            this.getLogger().info("已安裝星球維度資料包到 " + datapackRoot + "，完整重啟後即可使用精緻天空、霧感與自訂維度類型設定。");
        }
    }

    private boolean copyBundledResource(final String resourcePath, final Path target) throws IOException {
        try (InputStream stream = this.getResource(resourcePath)) {
            if (stream == null) {
                return false;
            }
            Files.createDirectories(target.getParent());
            Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING);
            return true;
        }
    }

    public void reloadProjectData() {
        // 重載前先關閉所有科技書選單，防止玩家趁機拿取物品
        if (this.techBookService != null) {
            this.techBookService.closeAllBookViews();
        }
        this.syncManagedDataFiles();
        if (this.techRegistry != null) {
            this.techRegistry.reload(this);
        }
        if (this.itemFactory != null) {
            this.itemFactory.reloadGuiConfig();
        }
        if (this.blueprintService != null) {
            this.blueprintService.reload(this);
        }
        if (this.techBookService != null) {
            this.techBookService.reload(this);
        }
        if (this.cookingService != null) {
            this.cookingService.reloadRecipes();
        }
        if (this.titleService != null) {
            this.titleService.reload();
        }
        // 補註冊 PlaceholderAPI（若熱載順序導致 onEnable 時尚未載入 PAPI）
        if (this.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new com.rui.techproject.service.TechPlaceholderExpansion(this).register();
        }
    }

    /**
     * 執行完整熱重載：複製新 JAR → 停用舊插件 → 清除註冊 → 載入新版本。
     * 若 hot-swap-jar 未設定，回退成僅重載設定檔。
     */
    @SuppressWarnings("unchecked")
    public void performHotReload(final CommandSender sender) {
        final String jarPath = this.getConfig().getString("hot-swap-jar", "");
        if (jarPath == null || jarPath.isBlank()) {
            this.reloadConfig();
            this.reloadProjectData();
            if (this.planetService != null) this.planetService.reloadRuntimeConfig();
            sender.sendMessage(net.kyori.adventure.text.Component.text(
                    "科技專案設定已重新載入（未設定 hot-swap-jar，僅重載設定）。",
                    net.kyori.adventure.text.format.NamedTextColor.GREEN));
            return;
        }

        final Path source = Path.of(jarPath);
        if (!Files.isRegularFile(source)) {
            sender.sendMessage(net.kyori.adventure.text.Component.text(
                    "找不到 JAR：" + jarPath,
                    net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }

        // 取得目前插件的 JAR 檔案
        File currentJar;
        try {
            final java.lang.reflect.Method m = JavaPlugin.class.getDeclaredMethod("getFile");
            m.setAccessible(true);
            currentJar = (File) m.invoke(this);
        } catch (final Exception exception) {
            sender.sendMessage(net.kyori.adventure.text.Component.text(
                    "無法取得插件 JAR 路徑：" + exception.getMessage(),
                    net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }

        // 複製新 JAR 覆蓋舊 JAR
        try {
            Files.copy(source, currentJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (final IOException exception) {
            sender.sendMessage(net.kyori.adventure.text.Component.text(
                    "複製 JAR 失敗：" + exception.getMessage(),
                    net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }

        sender.sendMessage(net.kyori.adventure.text.Component.text(
                "已更新 JAR，正在重載插件…",
                net.kyori.adventure.text.format.NamedTextColor.YELLOW));

        final String pluginName = this.getName();
        final File jarFile = currentJar;
        final PluginManager pm = this.getServer().getPluginManager();

        // 1. 停用舊插件（觸發 onDisable → 存檔、清除顯示實體）
        pm.disablePlugin(this);

        // 2. 取消事件註冊
        HandlerList.unregisterAll((Plugin) this);

        // 3. 從 PluginManager 內部集合移除舊引用
        try {
            final Field pluginsField = pm.getClass().getDeclaredField("plugins");
            pluginsField.setAccessible(true);
            ((java.util.List<Plugin>) pluginsField.get(pm)).remove(this);

            final Field lookupField = pm.getClass().getDeclaredField("lookupNames");
            lookupField.setAccessible(true);
            ((java.util.Map<String, Plugin>) lookupField.get(pm)).remove(pluginName.toLowerCase(java.util.Locale.ROOT));
        } catch (final Exception exception) {
            sender.sendMessage(net.kyori.adventure.text.Component.text(
                    "⚠ 移除舊插件引用失敗：" + exception.getMessage(),
                    net.kyori.adventure.text.format.NamedTextColor.YELLOW));
        }

        // 4. 移除舊指令
        try {
            final Field cmdMapField = pm.getClass().getDeclaredField("commandMap");
            cmdMapField.setAccessible(true);
            final SimpleCommandMap commandMap = (SimpleCommandMap) cmdMapField.get(pm);
            final java.util.Map<String, Command> known = commandMap.getKnownCommands();
            known.entrySet().removeIf(entry ->
                    entry.getValue() instanceof PluginCommand pc
                            && pc.getPlugin().getName().equals(pluginName));
        } catch (final Exception exception) {
            sender.sendMessage(net.kyori.adventure.text.Component.text(
                    "⚠ 移除舊指令失敗：" + exception.getMessage(),
                    net.kyori.adventure.text.format.NamedTextColor.YELLOW));
        }

        // 5. 關閉舊 ClassLoader 釋放 JAR 檔案控制
        try {
            final ClassLoader cl = this.getClass().getClassLoader();
            if (cl instanceof java.io.Closeable closeable) {
                closeable.close();
            }
        } catch (final Exception ignored) { }

        // 6. 載入並啟用新版本
        try {
            final Plugin newPlugin = pm.loadPlugin(jarFile);
            if (newPlugin == null) {
                sender.sendMessage(net.kyori.adventure.text.Component.text(
                        "載入新插件失敗（loadPlugin 回傳 null）",
                        net.kyori.adventure.text.format.NamedTextColor.RED));
                return;
            }
            newPlugin.onLoad();
            pm.enablePlugin(newPlugin);
            sender.sendMessage(net.kyori.adventure.text.Component.text(
                    "熱載完成！插件已使用新版本代碼。",
                    net.kyori.adventure.text.format.NamedTextColor.GREEN));
        } catch (final Exception exception) {
            sender.sendMessage(net.kyori.adventure.text.Component.text(
                    "載入新版本失敗：" + exception.getMessage(),
                    net.kyori.adventure.text.format.NamedTextColor.RED));
        }
    }

    @Override
    public void onDisable() {
        // 關閉所有科技書選單
        if (this.techBookService != null) {
            this.techBookService.closeAllBookViews();
        }
        if (this.discordSrvHook != null) {
            this.discordSrvHook.tryUnregister();
        }
        if (this.meteorService != null) {
            this.meteorService.shutdown();
        }
        if (this.cookingService != null) {
            this.cookingService.shutdown();
        }
        if (this.planetService != null) {
            this.planetService.shutdown();
        }
        if (this.machineService != null) {
            this.machineService.removeAllDisplays();
            this.machineService.saveAll();
        }
        if (this.placedTechBlockService != null) {
            this.placedTechBlockService.saveAll();
        }
        if (this.techCropService != null) {
            this.techCropService.saveAll();
        }
        if (this.planetService != null) {
            this.planetService.saveAll();
        }
        if (this.playerProgressService != null) {
            this.playerProgressService.saveAll();
        }
        if (this.storageManager != null) {
            this.storageManager.shutdown();
        }
    }

    private void autoSave() {
        this.safeScheduler.runAsync(() -> {
            final long start = System.currentTimeMillis();
            try {
                if (this.machineService != null) this.machineService.saveAll();
                if (this.placedTechBlockService != null) this.placedTechBlockService.saveAll();
                if (this.techCropService != null) this.techCropService.saveAll();
                if (this.planetService != null) this.planetService.saveAll();
                if (this.playerProgressService != null) this.playerProgressService.saveAll();
            } catch (final Exception exception) {
                this.getLogger().log(java.util.logging.Level.WARNING, "自動存檔時發生錯誤", exception);
            }
            final long elapsed = System.currentTimeMillis() - start;
            if (elapsed > 500L) {
                this.getLogger().info("自動存檔完成，耗時 " + elapsed + "ms。");
            }
        });
    }

    public TechRegistry getTechRegistry() {
        return this.techRegistry;
    }

    public SafeScheduler getSafeScheduler() {
        return this.safeScheduler;
    }

    public ItemFactoryUtil getItemFactory() {
        return this.itemFactory;
    }

    public PlayerProgressService getPlayerProgressService() {
        return this.playerProgressService;
    }

    public BlueprintService getBlueprintService() {
        return this.blueprintService;
    }

    public TechBookService getTechBookService() {
        return this.techBookService;
    }

    public TechAddonService getTechAddonService() {
        return this.techAddonService;
    }

    public AchievementService getAchievementService() {
        return this.achievementService;
    }

    public MachineService getMachineService() {
        return this.machineService;
    }

    public PlacedTechBlockService getPlacedTechBlockService() {
        return this.placedTechBlockService;
    }

    public TechCropService getTechCropService() {
        return this.techCropService;
    }

    public PlanetService getPlanetService() {
        return this.planetService;
    }

    public CookingService getCookingService() {
        return this.cookingService;
    }

    public AchievementGuiService getAchievementGuiService() {
        return this.achievementGuiService;
    }

    public ItemSearchService getItemSearchService() {
        return this.itemSearchService;
    }

    public MeteorService getMeteorService() {
        return this.meteorService;
    }

    public StorageManager getStorageManager() {
        return this.storageManager;
    }

    public TitleService getTitleService() {
        return this.titleService;
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(final String worldName, final String id) {
        return PlanetService.generatorForWorld(worldName, id);
    }

    private void bootstrapAddonApi() {
        this.techAddonService.registerInteraction(new TechInteractionDefinition(
                "arcane_circuit_circle",
                "奧術電路法陣",
                TechInteractionType.RITUAL_CIRCLE,
            GuideCategory.NETWORK,
                TechTier.TIER3,
                "machine:laser_engraver&item:advanced_circuit",
                java.util.List.of(
                        "擴展接口：供外部擴展模組掛入自訂法陣儀式",
                        "可透過擴展模組定義祭壇 / 法陣 / 多方塊互動",
                        "由擴展模組自行監聽並實現具體邏輯"
                ),
                java.util.List.of(
                        "擴展模組可透過此接口定義祭壇 / 法陣 / 多方塊互動",
                        "由擴展模組自行監聽並實現具體邏輯"
                )
        ));
                this.techAddonService.registerInteraction(new TechInteractionDefinition(
                    "storm_matrix_obelisk",
                    "風暴矩陣方尖碑",
                    TechInteractionType.MULTIBLOCK,
                    GuideCategory.ENERGY,
                    TechTier.TIER4,
                    "machine:storm_turbine&item:fusion_core",
                    java.util.List.of(
                        "擴展接口：供外部擴展模組掛入多方塊能源結構",
                        "可透過擴展模組定義爆發式發電結構",
                        "由擴展模組自行監聽並實現具體邏輯"
                    ),
                    java.util.List.of(
                        "擴展模組可透過此接口定義爆發式發電結構",
                        "由擴展模組自行監聽並實現具體邏輯"
                    )
                ));
                this.techAddonService.registerInteraction(new TechInteractionDefinition(
                    "quantum_gate_lattice",
                    "量子門晶格",
                    TechInteractionType.MACHINE_LINK,
                    GuideCategory.LOGISTICS,
                    TechTier.TIER3,
                    "machine:industrial_bus&item:quantum_chip",
                    java.util.List.of(
                        "擴展接口：供外部擴展模組掛入跨區域物流連結",
                        "可透過擴展模組定義遠端物流 / 傳送陣",
                        "由擴展模組自行監聽並實現具體邏輯"
                    ),
                    java.util.List.of(
                        "擴展模組可透過此接口定義遠端物流 / 傳送陣",
                        "由擴展模組自行監聯並實現具體邏輯"
                    )
                ));
    }
}
