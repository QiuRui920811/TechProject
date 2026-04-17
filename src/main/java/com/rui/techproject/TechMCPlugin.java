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
import com.rui.techproject.service.DailyQuestService;
import com.rui.techproject.service.LeaderboardService;
import com.rui.techproject.service.MeteorService;
import com.rui.techproject.service.ItemSearchService;
import com.rui.techproject.service.RegionService;
import com.rui.techproject.service.MachineService;
import com.rui.techproject.service.PlacedTechBlockService;
import com.rui.techproject.service.MazeService;
import com.rui.techproject.service.PlanetService;
import com.rui.techproject.service.PlayerProgressService;

import com.rui.techproject.service.SkillService;
import com.rui.techproject.service.TechBookService;
import com.rui.techproject.service.TechCropService;
import com.rui.techproject.service.TechRegistry;
import com.rui.techproject.service.TitleMsgService;
import com.rui.techproject.service.TitleService;
import com.rui.techproject.service.TutorialChainService;
import com.rui.techproject.service.RadiationService;
import com.rui.techproject.service.GeoResourceService;
import com.rui.techproject.service.AltarService;
import com.rui.techproject.service.AndroidProgrammingService;
import com.rui.techproject.service.MultiblockCraftingService;
import com.rui.techproject.storage.MigrationManager;
import com.rui.techproject.storage.StorageManager;
import com.rui.techproject.util.ItemFactoryUtil;
import com.rui.techproject.util.SafeScheduler;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class TechMCPlugin extends JavaPlugin {
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
                "datapacks/techproject_dimensions/data/techproject/worldgen/noise_settings/tempest_surface_noise.json",
                "datapacks/techproject_dimensions/data/techproject/dimension_type/labyrinth_abyss.json",
                "datapacks/techproject_dimensions/data/techproject/dimension/labyrinth_surface.json",
                "datapacks/techproject_dimensions/data/techproject/worldgen/biome/labyrinth_maze.json",
                "datapacks/techproject_dimensions/data/techproject/worldgen/noise_settings/labyrinth_surface_noise.json"
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
    private MazeService mazeService;
    private CookingService cookingService;
    private AchievementGuiService achievementGuiService;
    private ItemSearchService itemSearchService;
    private MeteorService meteorService;
    private StorageManager storageManager;
    private TitleService titleService;
    private TitleMsgService titleMsgService;
    private RegionService regionService;
    // 新增：技能 / 任務 / 排行 / 引導
    private SkillService skillService;
    private DailyQuestService dailyQuestService;
    private LeaderboardService leaderboardService;
    private TutorialChainService tutorialChainService;
    private RadiationService radiationService;
    private GeoResourceService geoResourceService;
    private AltarService altarService;
    private AndroidProgrammingService androidProgrammingService;
    private MultiblockCraftingService multiblockCraftingService;
    // 天賦樹 + 法術系統
    private com.rui.techproject.service.talent.TalentService talentService;
    private com.rui.techproject.service.talent.ManaService manaService;
    private com.rui.techproject.service.talent.SpellExecutor spellExecutor;
    private com.rui.techproject.service.talent.SpellCastService spellCastService;
    private com.rui.techproject.service.talent.TalentGuiService talentGuiService;
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
        this.mazeService = new MazeService(this, this.safeScheduler, this.itemFactory);
        this.machineService = new MachineService(this, this.techRegistry, this.playerProgressService, this.achievementService, this.safeScheduler, this.itemFactory, this.techCropService);
        this.cookingService = new CookingService(this, this.techRegistry, this.itemFactory, this.safeScheduler);
        this.cookingService.purgeOrphanedDisplays();
        this.achievementGuiService = new AchievementGuiService(this, this.techRegistry, this.playerProgressService, this.itemFactory);
        this.titleService = new TitleService(this, this.playerProgressService);
        this.achievementGuiService.setTitleService(this.titleService);
        this.titleMsgService = new TitleMsgService(this, this.safeScheduler);
        this.itemSearchService = new ItemSearchService(this);
        this.meteorService = new MeteorService(this, this.safeScheduler, this.itemFactory);
        this.regionService = new RegionService(this);

        // 新增系統：技能 / 任務 / 排行 / 新手引導
        this.skillService = new SkillService(this, this.playerProgressService, this.itemFactory);
        this.dailyQuestService = new DailyQuestService(this, this.playerProgressService,
                this.itemFactory, this.skillService);
        this.leaderboardService = new LeaderboardService(this, this.playerProgressService,
                this.skillService, this.itemFactory);
        this.tutorialChainService = new TutorialChainService(this, this.playerProgressService,
                this.skillService, this.itemFactory);
        this.radiationService = new RadiationService(this, this.playerProgressService);
        this.geoResourceService = new GeoResourceService(this);
        this.altarService = new AltarService(this);
        this.androidProgrammingService = new AndroidProgrammingService(this);
        this.multiblockCraftingService = new MultiblockCraftingService(this);

        // 天賦樹 + 法術系統（必須在 skillService 之後）
        this.talentService = new com.rui.techproject.service.talent.TalentService(
                this, this.playerProgressService, this.skillService);
        this.skillService.setTalentService(this.talentService);
        this.manaService = new com.rui.techproject.service.talent.ManaService(
                this, this.playerProgressService, this.skillService);
        this.spellExecutor = new com.rui.techproject.service.talent.SpellExecutor(
                this, this.skillService);
        this.spellCastService = new com.rui.techproject.service.talent.SpellCastService(
                this, this.skillService, this.manaService, this.talentService, this.spellExecutor);
        this.talentGuiService = new com.rui.techproject.service.talent.TalentGuiService(
                this, this.talentService, this.playerProgressService, this.skillService);

        final com.rui.techproject.storage.StorageBackend backend = this.storageManager.getBackend();
        this.playerProgressService.setStorageBackend(backend);
        this.placedTechBlockService.setStorageBackend(backend);
        this.techCropService.setStorageBackend(backend);
        this.planetService.setStorageBackend(backend);
        this.machineService.setStorageBackend(backend);

        this.getServer().getPluginManager().registerEvents(new TechListener(this), this);
        this.getServer().getPluginManager().registerEvents(
                new com.rui.techproject.listener.SpellCastListener(this.spellCastService), this);
        final TechCommand techCommand = new TechCommand(this, this.techRegistry, this.itemFactory);
        if (this.getCommand("tech") != null) {
            this.getCommand("tech").setExecutor(techCommand);
            this.getCommand("tech").setTabCompleter(techCommand);
        }
        if (this.getCommand("helper") != null) {
            this.getCommand("helper").setExecutor(
                    new com.rui.techproject.command.HelperCommand(this, this.itemFactory));
        }

        this.machineService.start();
        this.machineService.purgeOrphanDisplays();
        this.planetService.start();
        this.mazeService.startTimers();
        this.meteorService.start();
        this.manaService.start();
        this.radiationService.start();
        this.geoResourceService.start();
        this.altarService.start();

        if (this.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new com.rui.techproject.service.TechPlaceholderExpansion(this).register();
            this.getLogger().info("PlaceholderAPI 已挂接，請使用 %techproject_title% 等佔位符。");
        }

        // DiscordSRV 挂接：攤截搜尋指令等訊息不轉發至 Discord
        if (this.getServer().getPluginManager().getPlugin("DiscordSRV") != null) {
            this.discordSrvHook = new com.rui.techproject.listener.DiscordSrvHook(this);
            this.discordSrvHook.tryRegister();
        }

        // InventorySorter/超級烽火台 挂接：全部科技機器位置繞過烽火台面板
        this.hookBeaconAPI();

        // DebugWand 挂接：除錯棒無法修改科技機器方塊
        this.hookDebugWandAPI();

        // SuperHopper 挂接：全部科技機器位置繞過超級漏斗
        this.hookSuperHopperAPI();

        // 定期自動存檔（每 5 分鐘），防止崩潰時資料遺失
        final long autoSaveInterval = 20L * 60L * 5L; // 6000 ticks = 5 分鐘
        this.safeScheduler.runGlobalTimer(task -> this.autoSave(), autoSaveInterval, autoSaveInterval);

        this.getLogger().info("TechMC enabled: " + this.techRegistry.summaryLine());
    }

    private void hookBeaconAPI() {
        try {
            if (this.getServer().getPluginManager().getPlugin("InventorySorter") == null) return;
            // 已載入的機器全部加入繞過
            for (final org.bukkit.Location loc : this.machineService.allMachineLocations()) {
                tw.rui.egg.inventorysorter.beacon.BeaconAPI.addBypassLocation(loc);
            }
            this.getLogger().info("BeaconAPI 已挂接，" + this.machineService.machineCount() + " 個機器位置已繞過超級烽火台。");
        } catch (final NoClassDefFoundError ignored) {
            // InventorySorter 未安裝或版本不符
        }
    }

    private void hookDebugWandAPI() {
        try {
            if (this.getServer().getPluginManager().getPlugin("DebugWand") == null) return;
            com.debugstick.debugwand.api.DebugWandAPI.registerProtection(this, block ->
                    this.machineService.isManagedMachine(block)
                    || this.placedTechBlockService.isTrackedBlock(block));
            this.getLogger().info("DebugWandAPI 已挂接，科技機器方塊受除錯棒保護。");
        } catch (final NoClassDefFoundError ignored) {
            // DebugWand 未安裝或版本不符
        }
    }

    private void hookSuperHopperAPI() {
        try {
            if (this.getServer().getPluginManager().getPlugin("SuperHopper") == null) return;
            for (final org.bukkit.Location loc : this.machineService.allMachineLocations()) {
                com.ruisisz.superhopper.api.SuperHopperAPI.addBypassLocation(loc);
            }
            this.getLogger().info("SuperHopperAPI 已挂接，" + this.machineService.machineCount() + " 個機器位置已繞過超級漏斗。");
        } catch (final NoClassDefFoundError ignored) {
            // SuperHopper 未安裝或版本不符
        }
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
     * 執行完整熱重載：廣播通知 → 安全關閉 GUI → 儲存資料 → 複製新 JAR → 委託 PlugManX 卸載+重載。
     * 若 hot-swap-jar 未設定，回退成僅重載設定檔。
     *
     * <p>搭配 TechGuard 插件使用：TechGuard 會在偵測到 TechProject 卸載時
     * 凍結所有玩家互動（方塊破壞/放置/互動），直到 TechProject 重新啟用。</p>
     */
    public void performHotReload(final CommandSender sender) {
        final String jarPath = this.getConfig().getString("hot-swap-jar", "");
        if (jarPath == null || jarPath.isBlank()) {
            this.reloadConfig();
            this.reloadProjectData();
            if (this.planetService != null) this.planetService.reloadRuntimeConfig();
            sender.sendMessage(net.kyori.adventure.text.Component.text(
                    "TechMC 設定已重新載入（未設定 hot-swap-jar，僅重載設定）。",
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

        // ── 第 1 步：全服廣播 TitleMsg 通知 ──
        final net.kyori.adventure.text.Component titleComp = net.kyori.adventure.text.Component.text(
                "⚙ 科技熱重啟", net.kyori.adventure.text.format.NamedTextColor.GOLD,
                net.kyori.adventure.text.format.TextDecoration.BOLD);
        final net.kyori.adventure.text.Component subtitleComp = net.kyori.adventure.text.Component.text(
                "正在安全卸載，請稍候…", net.kyori.adventure.text.format.NamedTextColor.GRAY);
        if (this.titleMsgService != null) {
            for (final org.bukkit.entity.Player player : this.getServer().getOnlinePlayers()) {
                this.titleMsgService.send(player, titleComp, subtitleComp, 40L,
                        org.bukkit.Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE);
            }
        }

        // ── 第 2 步：安全關閉所有科技 GUI ──
        if (this.techBookService != null) {
            this.techBookService.closeAllBookViews();
        }
        if (this.machineService != null) {
            this.machineService.closeAllMachineViews();
        }
        // 關閉其他科技介面（成就、搜尋）
        for (final org.bukkit.entity.Player player : this.getServer().getOnlinePlayers()) {
            if (this.achievementGuiService != null) {
                this.achievementGuiService.clearState(player.getUniqueId());
            }
            if (this.itemSearchService != null) {
                this.itemSearchService.clearState(player.getUniqueId());
            }
        }

        // ── 第 3 步：儲存所有資料 ──
        sender.sendMessage(net.kyori.adventure.text.Component.text(
                "正在儲存所有資料…", net.kyori.adventure.text.format.NamedTextColor.YELLOW));
        if (this.machineService != null) this.machineService.saveAll();
        if (this.placedTechBlockService != null) this.placedTechBlockService.saveAll();
        if (this.techCropService != null) this.techCropService.saveAll();
        if (this.planetService != null) this.planetService.saveAll();
        if (this.playerProgressService != null) this.playerProgressService.saveAll();
        if (this.androidProgrammingService != null) this.androidProgrammingService.save();

        // ── 第 4 步：複製新 JAR 覆蓋舊 JAR ──
        try {
            Files.copy(source, currentJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (final IOException exception) {
            sender.sendMessage(net.kyori.adventure.text.Component.text(
                    "複製 JAR 失敗：" + exception.getMessage(),
                    net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }

        sender.sendMessage(net.kyori.adventure.text.Component.text(
                "已更新 JAR，正在透過 PlugManX 重載… (TechGuard 將凍結玩家操作)",
                net.kyori.adventure.text.format.NamedTextColor.YELLOW));

        // ── 第 5 步：委託 PlugManX 執行卸載+重載 ──
        // TechGuard 會在 PluginDisableEvent 時自動啟動凍結保護
        this.safeScheduler.runGlobal(task -> {
            try {
                this.getServer().dispatchCommand(
                        this.getServer().getConsoleSender(),
                        "plugman reload TechProject"
                );
            } catch (final Exception exception) {
                // PlugManX 內部的 CraftScheduler 呼叫在 Folia 上會拋例外，但重載本身已完成
                this.getLogger().fine("PlugManX reload 附帶排程警告（可忽略）：" + exception.getMessage());
            }
        });
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
        // DebugWand 解除
        try {
            if (this.getServer().getPluginManager().getPlugin("DebugWand") != null) {
                com.debugstick.debugwand.api.DebugWandAPI.unregisterProtection(this);
            }
        } catch (final NoClassDefFoundError ignored) { }
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
            // Folia: onDisable 在主控台線程，無法操作實體，Display 實體伺服器關閉後自動消失
            // this.machineService.removeAllDisplays();
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
        if (this.geoResourceService != null) {
            this.geoResourceService.shutdown();
        }
        if (this.androidProgrammingService != null) {
            this.androidProgrammingService.save();
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

    public MazeService getMazeService() {
        return this.mazeService;
    }

    public StorageManager getStorageManager() {
        return this.storageManager;
    }

    public TitleService getTitleService() {
        return this.titleService;
    }

    public TitleMsgService getTitleMsgService() {
        return this.titleMsgService;
    }

    public RegionService getRegionService() {
        return this.regionService;
    }

public SkillService getSkillService() {
        return this.skillService;
    }

    public DailyQuestService getDailyQuestService() {
        return this.dailyQuestService;
    }

    public LeaderboardService getLeaderboardService() {
        return this.leaderboardService;
    }

    public TutorialChainService getTutorialChainService() {
        return this.tutorialChainService;
    }

    public com.rui.techproject.service.talent.TalentService getTalentService() {
        return this.talentService;
    }

    public com.rui.techproject.service.talent.ManaService getManaService() {
        return this.manaService;
    }

    public RadiationService getRadiationService() {
        return this.radiationService;
    }

    public GeoResourceService getGeoResourceService() {
        return this.geoResourceService;
    }

    public AltarService getAltarService() {
        return this.altarService;
    }

    public AndroidProgrammingService getAndroidProgrammingService() {
        return this.androidProgrammingService;
    }

    public MultiblockCraftingService getMultiblockCraftingService() {
        return this.multiblockCraftingService;
    }

    public com.rui.techproject.service.talent.SpellExecutor getSpellExecutor() {
        return this.spellExecutor;
    }

    public com.rui.techproject.service.talent.SpellCastService getSpellCastService() {
        return this.spellCastService;
    }

    public com.rui.techproject.service.talent.TalentGuiService getTalentGuiService() {
        return this.talentGuiService;
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
