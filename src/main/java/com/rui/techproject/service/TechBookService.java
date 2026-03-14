package com.rui.techproject.service;

import com.rui.techproject.TechProjectPlugin;
import com.rui.techproject.addon.TechAddonService;
import com.rui.techproject.addon.TechInteractionDefinition;
import com.rui.techproject.addon.TechInteractionType;
import com.rui.techproject.model.GuideCategory;
import com.rui.techproject.model.MachineDefinition;
import com.rui.techproject.model.MachineRecipe;
import com.rui.techproject.model.TechItemDefinition;
import com.rui.techproject.model.TechTier;
import com.rui.techproject.util.ItemFactoryUtil;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class TechBookService {
    private static final InventoryHolder BOOK_VIEW_HOLDER = new InventoryHolder() {
        @Override
        public Inventory getInventory() {
            return null;
        }
    };
    private static final Key TECH_MENU_TITLE_FONT = Key.key("minecraft", "techproject_book");
    private static final String TECH_MENU_TITLE_SHIFT = "\uF102";
    private static final String TECH_MENU_TITLE_GLYPH = "\uF002";
    private static final String TECH_MENU_TITLE = TECH_MENU_TITLE_SHIFT + TECH_MENU_TITLE_GLYPH;
    private static final String STARTER_GUIDE_ID = "starter_path";
    private static final String ANDROID_GUIDE_ID = "android_system_overview";
    private static final String MAIN_TITLE = "科技書";
    private static final String HUB_TITLE = "科技分類";
    private static final String TIER_TITLE = "科技階級";
    private static final String DETAIL_PREFIX = "科技詳情:";
    private static final String RECIPE_PREFIX = "配方預覽:";
    private static final String TREE_PREFIX = "科技樹:";
    private static final String INTERACTION_PREFIX = "互動儀式:";
    private static final String RESEARCH_TITLE = "研究台";
    private static final String GUIDE_TITLE = "科技指南";
    private static final String SEARCH_TITLE = "科技搜尋";
        private static final List<String> CONTENT_STRUCTURE_FILES = List.of(
            "tech-content.yml",
            "tech-content-core.yml",
            "tech-content-expansion.yml",
            "tech-content-systems.yml",
            "tech-content-megastructures.yml"
        );
    private static final int[] TIER_SLOTS = {3, 4, 5, 6};
    private static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final GuideCategory[] HUB_ORDER = {
            GuideCategory.MACHINES,
            GuideCategory.ENERGY,
            GuideCategory.MATERIALS,
            GuideCategory.TOOLS,
            GuideCategory.AGRICULTURE,
            GuideCategory.FOOD,
            GuideCategory.LOGISTICS,
            GuideCategory.NETWORK,
            GuideCategory.SPECIAL
    };

    private final TechProjectPlugin plugin;
    private final TechRegistry registry;
    private final PlayerProgressService progressService;
    private final ItemFactoryUtil itemFactory;
    private final BlueprintService blueprintService;
    private final TechAddonService addonService;
    private final Map<String, Map<String, GuideEntry>> guidesByLocale = new LinkedHashMap<>();
    private final Map<String, StructurePreview> machineStructurePreviews = new LinkedHashMap<>();
    private final java.util.Set<UUID> pendingSearchInput = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Set<UUID> openBookViewPlayers = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<UUID, Inventory> openBookInventories = new java.util.concurrent.ConcurrentHashMap<>();

    public TechBookService(final TechProjectPlugin plugin,
                           final TechRegistry registry,
                           final PlayerProgressService progressService,
                           final ItemFactoryUtil itemFactory,
                           final BlueprintService blueprintService,
                           final TechAddonService addonService) {
                this.plugin = plugin;
        this.registry = registry;
        this.progressService = progressService;
        this.itemFactory = itemFactory;
        this.blueprintService = blueprintService;
        this.addonService = addonService;
        this.loadGuides(plugin);
        this.loadStructurePreviews(plugin);
    }

    public void openDefaultBook(final Player player) {
        this.playBookOpenSound(player);
        this.openCategoryHub(player);
    }

    public void reload(final TechProjectPlugin plugin) {
        this.guidesByLocale.clear();
        this.machineStructurePreviews.clear();
        this.loadGuides(plugin);
        this.loadStructurePreviews(plugin);
    }

    public void openResearchDesk(final Player player) {
        this.openResearchDesk(player, null, null, false, 0);
    }

    public void openCategoryHub(final Player player) {
        final Inventory inventory = this.createHudBookInventory();
        inventory.setItem(4, this.info(Material.ENCHANTED_BOOK, "功能分類", List.of("先選你要看的功能類別", "進去後直接看內容，想縮小再用階級篩選")));

        final GuideCategory[] order = HUB_ORDER;
        final int[] slots = {10, 12, 14, 16, 28, 30, 32, 34};
        for (int index = 0; index < Math.min(order.length, slots.length); index++) {
            inventory.setItem(slots[index], this.renderGuideCategoryCard(player, order[index]));
        }

        inventory.setItem(46, this.itemFactory.tagGuiAction(this.guiButton("hub-starter-guide", Material.KNOWLEDGE_BOOK, "新手起步", List.of("先看第一條生存科技流程")), "guide-direct:" + STARTER_GUIDE_ID));
        inventory.setItem(47, this.itemFactory.tagGuiAction(this.guiButton("hub-guide-list", Material.WRITABLE_BOOK, "科技指南", List.of("教學 / 配線 / 物流 / 升級")), "guide-list:0"));
        inventory.setItem(48, this.itemFactory.tagGuiAction(this.guiButton("hub-search", Material.COMPASS, "搜尋物品", List.of("點擊後在聊天輸入關鍵字", "可搜尋物品 / 機器 / 指南 / 儀式")), "search-prompt"));
        inventory.setItem(49, this.itemFactory.tagGuiAction(this.guiButton("hub-book-claim", Material.ENCHANTED_BOOK, "補發科技書", List.of("遺失時點這裡補一本文字書")), "book-claim"));
        inventory.setItem(50, this.info(Material.CRAFTING_TABLE, "如何使用", List.of("1. 先選功能分類", "2. 直接看內容", "3. 用搜尋快速找目標", "4. 需要時再用階級篩選")));
        inventory.setItem(51, this.itemFactory.tagGuiAction(this.guiButton("hub-achievements", Material.DIAMOND, "成就系統", List.of("查看所有成就進度", "包含分類篩選與獎勵")), "achievements"));
        inventory.setItem(52, this.researchOverviewIcon(player.getUniqueId(), null, null, false));
        inventory.setItem(53, this.itemFactory.tagGuiAction(this.guiButton("hub-refresh", Material.BOOK, "重新整理分類", List.of("更新分類解鎖狀態", "也能當成回首頁")), "hub"));
        this.openBookInventory(player, inventory);
    }

    public void openMainBook(final Player player, final GuideCategory category) {
        this.openMainBook(player, category, null, 0);
    }

    public void openMainBook(final Player player, final GuideCategory category, final int page) {
        this.openMainBook(player, category, null, page);
    }

    public void openTierHub(final Player player, final GuideCategory category) {
        final Inventory inventory = this.createHudBookInventory();
        inventory.setItem(4, this.info(category.icon(), category.displayName(), List.of(
                this.guideCategoryFlavor(category),
                "先決定你要推哪一個階級"
        )));
        final TechTier[] order = TechTier.values();
        final int[] slots = {19, 21, 23, 25};
        for (int index = 0; index < Math.min(order.length, slots.length); index++) {
            inventory.setItem(slots[index], this.renderTierCard(player.getUniqueId(), category, order[index]));
        }
        inventory.setItem(45, this.itemFactory.tagGuiAction(this.guiButton("tier-back-hub", Material.ARROW, "返回分類大廳", List.of("回到功能分類首頁")), "hub"));
        inventory.setItem(47, this.info(Material.NAME_TAG, "分類定位", this.guideCategoryRoleLines(category)));
        inventory.setItem(49, this.info(category.icon(), "推薦目標", this.guideCategoryKeyTargets(category)));
        inventory.setItem(51, this.info(Material.NETHER_STAR, "全局進度", List.of(
                this.tierProgressLine(player.getUniqueId(), TechTier.TIER1),
                this.tierProgressLine(player.getUniqueId(), TechTier.TIER2),
                this.tierProgressLine(player.getUniqueId(), TechTier.TIER3),
                this.tierProgressLine(player.getUniqueId(), TechTier.TIER4)
        )));
        inventory.setItem(53, this.itemFactory.tagGuiAction(this.guiButton("tier-refresh", Material.BOOK, "重新整理階級", List.of("重新整理該分類的階級狀態")), "category:" + category.name()));
        this.openBookInventory(player, inventory);
    }

    public void openMainBook(final Player player, final GuideCategory category, final TechTier tier) {
        this.openMainBook(player, category, tier, 0);
    }

    public void openMainBook(final Player player, final GuideCategory category, final TechTier tier, final int page) {
        if (tier != null && !this.isTierUnlocked(player.getUniqueId(), tier)) {
            player.sendMessage(this.itemFactory.warning("這個階級尚未解鎖，請先完成前一階內容。"));
            this.openTierHub(player, category);
            return;
        }
        if (tier != null && this.categoryTierContentCount(category, tier) <= 0) {
            final TechTier fallbackTier = this.nearestTierWithContent(category, tier);
            if (fallbackTier != null && fallbackTier != tier) {
                player.sendActionBar(this.itemFactory.warning("這個階級目前沒有內容，已切換到「" + fallbackTier.displayName() + "」。"));
                this.openMainBook(player, category, fallbackTier, 0);
                return;
            }
            if (fallbackTier == null) {
                player.sendActionBar(this.itemFactory.warning("這個分類目前沒有可顯示的內容。"));
                this.openMainBook(player, category, null, 0);
                return;
            }
        }
        final String tierLabel = tier == null ? "全部" : tier.shortName();
        final Inventory inventory = this.createHudBookInventory();
        inventory.setItem(1, this.buildConfiguredIcon(
            "main-overview",
            category.icon(),
            this.itemFactory.warning("科技總覽"),
            this.itemFactory.mutedLore(List.of("直接列出這個分類的內容", "右側可切換各階級篩選", this.guideCategoryFlavor(category))),
            true
        ));
        inventory.setItem(2, this.itemFactory.tagGuiAction(this.buildConfiguredIcon(
            "main-tier-all",
            Material.NETHER_STAR,
            this.itemFactory.warning(tier == null ? "◆ 全部階級" : "全部階級"),
            this.itemFactory.mutedLore(List.of("顯示此分類全部內容", tier == null ? "目前未套用階級篩選" : "點擊切回全部")),
            true
        ), "category:" + category.name()));
        int tierIndex = 0;
        for (final TechTier current : TechTier.values()) {
            final boolean unlocked = this.isTierUnlocked(player.getUniqueId(), current);
            final ItemStack icon = this.itemFactory.tagGuiAction(this.buildConfiguredIcon(
                this.mainTierKey(current),
                current.icon(),
                current == tier
                    ? this.itemFactory.hex("◆ " + current.displayName(), "#FFD166")
                    : this.itemFactory.hex((unlocked ? "• " : "🔒 ") + current.displayName(), unlocked ? "#A8B2C1" : "#7D8597"),
                List.of(this.itemFactory.muted(unlocked ? (current == tier ? "目前階級" : "點擊切換到此階級") : "前一個階級尚未完成")),
                true
            ), "tier:" + category.name() + ":" + current.name());
            if (tierIndex < TIER_SLOTS.length) {
                inventory.setItem(TIER_SLOTS[tierIndex], icon);
            }
            tierIndex++;
        }

        final UUID uuid = player.getUniqueId();
        final List<ItemStack> entries = new ArrayList<>();
        if (tier == null) {
            for (final TechTier current : TechTier.values()) {
                for (final TechItemDefinition item : this.registry.getItemsByGuideCategoryAndTier(category, current)) {
                    entries.add(this.renderItemEntry(uuid, item));
                }
                for (final MachineDefinition machine : this.registry.getMachinesByGuideCategoryAndTier(category, current)) {
                    entries.add(this.renderMachineEntry(uuid, machine));
                }
                for (final TechInteractionDefinition interaction : this.addonService.getInteractionsByCategoryAndTier(category, current)) {
                    entries.add(this.renderInteractionEntry(uuid, interaction));
                }
            }
        } else {
            for (final TechItemDefinition item : this.registry.getItemsByGuideCategoryAndTier(category, tier)) {
                entries.add(this.renderItemEntry(uuid, item));
            }
            for (final MachineDefinition machine : this.registry.getMachinesByGuideCategoryAndTier(category, tier)) {
                entries.add(this.renderMachineEntry(uuid, machine));
            }
            for (final TechInteractionDefinition interaction : this.addonService.getInteractionsByCategoryAndTier(category, tier)) {
                entries.add(this.renderInteractionEntry(uuid, interaction));
            }
        }

        final int maxPage = Math.max(0, (entries.size() - 1) / CONTENT_SLOTS.length);
        final int safePage = Math.max(0, Math.min(page, maxPage));
        final int startIndex = safePage * CONTENT_SLOTS.length;
        for (int index = 0; index < CONTENT_SLOTS.length && startIndex + index < entries.size(); index++) {
            inventory.setItem(CONTENT_SLOTS[index], entries.get(startIndex + index));
        }

        inventory.setItem(45, this.itemFactory.tagGuiAction(this.guiButton("main-page-prev", Material.ARROW, "上一頁", List.of("頁數 {page}/{max-page}"), this.placeholders("page", String.valueOf(safePage + 1), "max-page", String.valueOf(maxPage + 1))), "page:" + category.name() + ":" + (tier == null ? "ALL" : tier.name()) + ":" + Math.max(0, safePage - 1)));
        inventory.setItem(46, this.itemFactory.tagGuiAction(this.guiButton("main-starter-guide", Material.KNOWLEDGE_BOOK, "新手起步", List.of("先看第一條生存科技流程", "包含合成、擺放、接電順序")), "guide-direct:" + STARTER_GUIDE_ID));
        inventory.setItem(47, this.itemFactory.tagGuiAction(this.guiButton("main-guide-list", Material.WRITABLE_BOOK, "科技指南", List.of("教學 / 配線 / 物流 / 升級")), "guide-list:0"));
        inventory.setItem(48, this.itemFactory.tagGuiAction(this.guiButton("main-search", Material.COMPASS, "搜尋目標", List.of("點擊後在聊天輸入關鍵字", "會跨分類搜尋")), "search-prompt"));
        inventory.setItem(49, this.itemFactory.tagGuiAction(this.guiButton("main-back-hub", Material.BOOK, "返回分類首頁", List.of("分類：{category}", "篩選：{tier}"), this.placeholders("category", category.displayName(), "tier", tierLabel)), "hub"));
        inventory.setItem(50, this.info(tier == null ? Material.NETHER_STAR : tier.icon(), "目前篩選", List.of("分類：" + category.displayName(), tier == null ? this.categoryProgressLine(player.getUniqueId(), category) : this.categoryTierProgressLine(player.getUniqueId(), category, tier), tier == null ? "顯示全部內容" : (this.isTierUnlocked(player.getUniqueId(), tier) ? "已可正式推進" : "前一個階級尚未完成"))));
        inventory.setItem(52, this.researchOverviewIcon(player.getUniqueId(), category, tier, false));
        inventory.setItem(53, this.itemFactory.tagGuiAction(this.guiButton("main-page-next", Material.SPECTRAL_ARROW, "下一頁", List.of("頁數 {page}/{max-page}"), this.placeholders("page", String.valueOf(safePage + 1), "max-page", String.valueOf(maxPage + 1))), "page:" + category.name() + ":" + (tier == null ? "ALL" : tier.name()) + ":" + Math.min(maxPage, safePage + 1)));
        this.openBookInventory(player, inventory);
    }

    public void openResearchDesk(final Player player,
                                 final GuideCategory category,
                                 final TechTier tier,
                                 final boolean availableOnly,
                                 final int page) {
        if (!player.hasPermission("techproject.admin") && !this.progressService.hasMachineUnlocked(player.getUniqueId(), "research_desk")) {
            player.sendMessage(this.itemFactory.danger("你尚未解鎖研究台，不能使用這個介面。"));
            return;
        }
        final String categoryLabel = category == null ? "全部分類" : category.displayName();
        final String tierLabel = tier == null ? "全部階級" : tier.shortName();
        final String modeLabel = availableOnly ? "可研究" : "全部候選";
        final Inventory inventory = this.createHudBookInventory();

        final UUID uuid = player.getUniqueId();
        final List<ResearchCandidate> candidates = this.collectResearchCandidates(uuid, category, tier, availableOnly);
        final long readyCount = candidates.stream().filter(candidate -> candidate.meetsRequirement()).count();
        final long affordableCount = candidates.stream().filter(candidate -> candidate.meetsRequirement() && candidate.affordable()).count();

        inventory.setItem(4, this.info(Material.ENCHANTING_TABLE, "研究台總覽", List.of(
                "分類：" + categoryLabel,
                "階級：" + tierLabel,
                "模式：" + modeLabel,
                "可用研究點：" + this.progressService.getAvailableTechXp(uuid),
                "候選數：" + candidates.size() + " / 可研究：" + readyCount + " / 可直接點亮：" + affordableCount
        )));

        inventory.setItem(1, this.researchTierFilterIcon(category, null, availableOnly, "全部階級"));
        inventory.setItem(2, this.researchTierFilterIcon(category, TechTier.TIER1, availableOnly, "第一階"));
        inventory.setItem(3, this.researchTierFilterIcon(category, TechTier.TIER2, availableOnly, "第二階"));
        inventory.setItem(5, this.researchTierFilterIcon(category, TechTier.TIER3, availableOnly, "第三階"));
        inventory.setItem(6, this.researchTierFilterIcon(category, TechTier.TIER4, availableOnly, "第四階"));
        inventory.setItem(7, this.researchModeIcon(category, tier, availableOnly));

        final int maxPage = Math.max(0, (candidates.size() - 1) / CONTENT_SLOTS.length);
        final int safePage = Math.max(0, Math.min(page, maxPage));
        final int startIndex = safePage * CONTENT_SLOTS.length;
        for (int index = 0; index < CONTENT_SLOTS.length && startIndex + index < candidates.size(); index++) {
            inventory.setItem(CONTENT_SLOTS[index], this.renderResearchDeskEntry(candidates.get(startIndex + index)));
        }

        inventory.setItem(45, this.itemFactory.tagGuiAction(this.guiButton("research-page-prev", Material.ARROW, "上一頁", List.of("頁數 {page}/{max-page}"), this.placeholders("page", String.valueOf(safePage + 1), "max-page", String.valueOf(maxPage + 1))), this.researchDeskAction(category, tier, availableOnly, Math.max(0, safePage - 1))));
        inventory.setItem(46, this.itemFactory.tagGuiAction(this.guiButton("research-prev-category", Material.WRITABLE_BOOK, "上一個分類", List.of("目前：{category}"), this.placeholders("category", categoryLabel)), this.researchDeskAction(this.previousCategory(category), tier, availableOnly, 0)));
        inventory.setItem(47, this.researchCategoryIcon(category));
        inventory.setItem(48, this.itemFactory.tagGuiAction(this.guiButton("research-back-book", Material.BOOK, "返回科技書", List.of("回到科技分類首頁")), "hub"));
        inventory.setItem(49, this.researchOverviewIcon(uuid, category, tier, availableOnly));
        inventory.setItem(50, this.itemFactory.tagGuiAction(this.guiButton("research-refresh", Material.ENDER_CHEST, "重新整理研究台", List.of("重新排序目前候選", "也會刷新研究點 / 狀態")), this.researchDeskAction(category, tier, availableOnly, safePage)));
        inventory.setItem(52, this.itemFactory.tagGuiAction(this.guiButton("research-next-category", Material.WRITABLE_BOOK, "下一個分類", List.of("目前：{category}"), this.placeholders("category", categoryLabel)), this.researchDeskAction(this.nextCategory(category), tier, availableOnly, 0)));
        inventory.setItem(53, this.itemFactory.tagGuiAction(this.guiButton("research-page-next", Material.SPECTRAL_ARROW, "下一頁", List.of("頁數 {page}/{max-page}"), this.placeholders("page", String.valueOf(safePage + 1), "max-page", String.valueOf(maxPage + 1))), this.researchDeskAction(category, tier, availableOnly, Math.min(maxPage, safePage + 1))));
        this.openBookInventory(player, inventory);
    }

    public void openGuideList(final Player player, final int page) {
        final Inventory inventory = this.createHudBookInventory();
        inventory.setItem(4, this.info(Material.WRITABLE_BOOK, "科技指南總覽", List.of("章節式教學頁", "和主選單一樣使用對齊式框架")));
        final List<GuideEntry> entries = new ArrayList<>(this.guidesFor(player).values());
        final int maxPage = Math.max(0, (entries.size() - 1) / CONTENT_SLOTS.length);
        final int safePage = Math.max(0, Math.min(page, maxPage));
        final int startIndex = safePage * CONTENT_SLOTS.length;
        for (int index = 0; index < CONTENT_SLOTS.length && startIndex + index < entries.size(); index++) {
            final GuideEntry entry = entries.get(startIndex + index);
            inventory.setItem(CONTENT_SLOTS[index], this.itemFactory.tagGuiAction(this.info(Material.WRITABLE_BOOK, entry.title(), List.of(
                    "分類：" + entry.topic(),
                    entry.preview()
            )), "guide:" + entry.id() + ":" + safePage));
        }
        inventory.setItem(45, this.itemFactory.tagGuiAction(this.guiButton("guide-list-page-prev", Material.ARROW, "上一頁", List.of("頁數 {page}/{max-page}"), this.placeholders("page", String.valueOf(safePage + 1), "max-page", String.valueOf(maxPage + 1))), "guide-list:" + Math.max(0, safePage - 1)));
        inventory.setItem(49, this.itemFactory.tagGuiAction(this.guiButton("guide-list-back-hub", Material.CRAFTING_TABLE, "返回分類大廳", List.of("回到類型首頁")), "hub"));
        inventory.setItem(53, this.itemFactory.tagGuiAction(this.guiButton("guide-list-page-next", Material.SPECTRAL_ARROW, "下一頁", List.of("頁數 {page}/{max-page}"), this.placeholders("page", String.valueOf(safePage + 1), "max-page", String.valueOf(maxPage + 1))), "guide-list:" + Math.min(maxPage, safePage + 1)));
        this.openBookInventory(player, inventory);
    }

    public void openGuideDetail(final Player player, final String guideId, final int backPage) {
        final GuideEntry entry = this.guidesFor(player).get(guideId);
        if (entry == null) {
            this.openGuideList(player, backPage);
            return;
        }
        if (STARTER_GUIDE_ID.equalsIgnoreCase(guideId)) {
            this.progressService.incrementStat(player.getUniqueId(), "starter_guide_seen", 1L);
        }
        final Inventory inventory = this.createBookInventory(GUIDE_TITLE);
        this.decorateMenuFrame(inventory);
        inventory.setItem(4, this.info(Material.WRITABLE_BOOK, entry.title(), List.of("主題：" + entry.topic(), entry.preview())));
        final int[] guideSlots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
        int index = 0;
        for (final String line : entry.lines()) {
            if (index >= guideSlots.length) {
                break;
            }
            inventory.setItem(guideSlots[index++], this.info(Material.PAPER, "說明", List.of(line)));
        }
        inventory.setItem(49, this.itemFactory.tagGuiAction(this.guiButton("guide-detail-back", Material.ARROW, "返回指南", List.of("頁數：{page}"), this.placeholders("page", String.valueOf(backPage + 1))), "guide-list:" + backPage));
        this.fillEmptySlots(inventory, this.sectionPane(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
        this.openBookInventory(player, inventory);
    }

    public void openDetail(final Player player, final String targetId) {
        final Inventory inventory = this.createHudBookInventory();
        this.decorateMenuFrame(inventory);
        final TechItemDefinition item = this.registry.getItem(targetId);
        if (item != null) {
            inventory.setItem(4, this.info(Material.KNOWLEDGE_BOOK, this.itemFactory.displayNameForId(item.id()), List.of(
                "分類：" + item.guideCategory().displayName(),
                "階級：" + item.tier().displayName(),
                "用途：" + this.itemFactory.localizeInlineTerms(String.join(" / ", item.useCases()))
            )));
            inventory.setItem(12, this.sectionPane(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "取得", List.of("左欄固定放取得方式")));
            inventory.setItem(13, this.itemFactory.tagPreviewClaim(this.itemFactory.buildTechItem(item), "item:" + item.id()));
            inventory.setItem(14, this.sectionPane(Material.LIME_STAINED_GLASS_PANE, "用途", List.of("右欄固定放用途與配方")));
            final List<MachineRecipe> outputRecipes = this.registry.getRecipesForOutput(item.id());
            final List<String> acquisitionLines = new ArrayList<>();
            acquisitionLines.add("解鎖條件：" + this.itemFactory.formatUnlockRequirement(item.unlockRequirement()));
            acquisitionLines.addAll(this.baseItemAcquisitionHints(item));
            acquisitionLines.add("用途：" + this.itemFactory.localizeInlineTerms(String.join(" / ", item.useCases())));
            acquisitionLines.add("提示：可放入機器輸入欄或物流網路");
            inventory.setItem(21, this.info(Material.COMPASS, "取得方法", acquisitionLines));

            final List<String> recipeSummaryLines = new ArrayList<>();
            if (outputRecipes.isEmpty()) {
                recipeSummaryLines.add("目前沒有可顯示的機器配方");
            } else {
                for (int index = 0; index < Math.min(4, outputRecipes.size()); index++) {
                    recipeSummaryLines.add(this.describeRecipeFlow(outputRecipes.get(index)));
                }
            }
            inventory.setItem(22, this.info(Material.PAPER, "來源配方", recipeSummaryLines));
            inventory.setItem(23, this.info(Material.KNOWLEDGE_BOOK, "用途", item.useCases().stream().map(this.itemFactory::localizeInlineTerms).toList()));
            inventory.setItem(30, this.researchActionIcon(player, "item", item.id(), item.unlockRequirement(), this.researchCost(item), this.progressService.hasItemUnlocked(player.getUniqueId(), item.id())));
            if (!outputRecipes.isEmpty()) {
                inventory.setItem(31, this.itemFactory.tagGuiAction(this.guiButton("detail-view-recipe", Material.KNOWLEDGE_BOOK, "查看配方圖", List.of(
                    "共有 {count} 種取得方式",
                    "點擊後直接顯示九宮格 / 製程格"
                ), this.placeholders("count", String.valueOf(outputRecipes.size()))), "recipe-view:" + item.id() + ":0"));
            } else {
                inventory.setItem(31, this.info(Material.BARRIER, "暫無配方圖", List.of("這個物品目前沒有額外配方頁可查看")));
            }
            inventory.setItem(32, this.itemFactory.tagGuiAction(this.guiButton("detail-tech-tree", Material.MANGROVE_PROPAGULE, "科技樹前置線", this.buildTechTreeSummaryLines(item.id(), item.unlockRequirement()), this.indexedPlaceholders("line", this.buildTechTreeSummaryLines(item.id(), item.unlockRequirement()), 6)), "tree:item:" + item.id()));
            inventory.setItem(49, this.itemFactory.tagGuiAction(this.guiButton("detail-back-tier", Material.ARROW, "返回階級內容", List.of("{category}", "{tier}"), this.placeholders("category", item.guideCategory().displayName(), "tier", item.tier().displayName())), "back:" + item.guideCategory().name() + ":" + item.tier().name() + ":0"));
        }

        final MachineDefinition machine = this.registry.getMachine(targetId);
        if (machine != null) {
            inventory.setItem(4, this.info(Material.CRAFTING_TABLE, this.itemFactory.displayNameForId(machine.id()), List.of(
                "分類：" + machine.guideCategory().displayName(),
                "階級：" + machine.tier().displayName(),
                "功能：" + this.itemFactory.localizeInlineTerms(machine.effectDescription())
            )));
            inventory.setItem(12, this.sectionPane(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "前置材料", List.of("左欄固定放前置材料")));
            inventory.setItem(13, this.itemFactory.tagPreviewClaim(this.itemFactory.buildMachineGuiIcon(machine), "machine:" + machine.id()));
            inventory.setItem(14, this.sectionPane(Material.LIME_STAINED_GLASS_PANE, "主要產物", List.of("右欄固定放主要產物")));
            final List<String> inputIds = this.itemFactory.machineDisplayInputIds(machine);
            for (int index = 0; index < 3; index++) {
                inventory.setItem(19 + index, index < inputIds.size() ? this.clickableReference(inputIds.get(index), true) : this.recipeBlankPane());
            }
            final List<String> outputIds = this.itemFactory.machineDisplayOutputIds(machine);
            for (int index = 0; index < 3; index++) {
                inventory.setItem(23 + index, index < outputIds.size() ? this.clickableReference(outputIds.get(index), false) : this.recipeBlankPane());
            }
            final List<String> machineInfoLines = new ArrayList<>();
            machineInfoLines.addAll(this.itemFactory.machineIoSummaryLines(machine));
            machineInfoLines.addAll(this.itemFactory.machineEnergySummaryLines(machine));
            inventory.setItem(22, this.info(Material.REDSTONE, "機器資訊", machineInfoLines));
            final int recipeViewCount = this.recipeViewsFor(machine.id()).size();
            inventory.setItem(37, this.researchActionIcon(player, "machine", machine.id(), machine.unlockRequirement(), this.researchCost(machine), this.progressService.hasMachineUnlocked(player.getUniqueId(), machine.id())));
            if (recipeViewCount > 0) {
                inventory.setItem(40, this.itemFactory.tagGuiAction(this.guiButton("machine-detail-view-recipe", Material.KNOWLEDGE_BOOK, "查看製作配方", List.of(
                    "共有 {count} 種配方圖",
                    "{hint}"
                ), this.placeholders(
                    "count", String.valueOf(recipeViewCount),
                    "hint", "點擊後直接顯示九宮格 / 製程格"
                )), "recipe-view:" + machine.id() + ":0"));
            } else {
                inventory.setItem(40, this.info(Material.BARRIER, "暫無配方圖", List.of("這台機器目前沒有額外配方頁可查看")));
            }
            inventory.setItem(43, this.itemFactory.tagGuiAction(this.guiButton("detail-tech-tree", Material.MANGROVE_PROPAGULE, "科技樹前置線", this.buildTechTreeSummaryLines(machine.id(), machine.unlockRequirement()), this.indexedPlaceholders("line", this.buildTechTreeSummaryLines(machine.id(), machine.unlockRequirement()), 6)), "tree:machine:" + machine.id()));
            final TechInteractionDefinition relatedInteraction = this.firstInteractionFor(machine.id());
            this.renderMachineStructurePreview(inventory, machine);
            inventory.setItem(47, this.itemFactory.tagGuiAction(this.guiButton("detail-back-tier", Material.ARROW, "返回階級內容", List.of("{category}", "{tier}"), this.placeholders("category", machine.guideCategory().displayName(), "tier", machine.tier().displayName())), "back:" + machine.guideCategory().name() + ":" + machine.tier().name() + ":0"));
            inventory.setItem(48, this.researchActionIcon(player, "machine", machine.id(), machine.unlockRequirement(), this.researchCost(machine), this.progressService.hasMachineUnlocked(player.getUniqueId(), machine.id())));
            if (recipeViewCount > 0) {
                inventory.setItem(49, this.itemFactory.tagGuiAction(this.guiButton("machine-detail-view-recipe", Material.KNOWLEDGE_BOOK, "查看製作配方", List.of(
                    "共有 {count} 種配方圖",
                    "點擊查看完整配方頁"
                ), this.placeholders("count", String.valueOf(recipeViewCount))), "recipe-view:" + machine.id() + ":0"));
            }
            inventory.setItem(50, this.itemFactory.tagGuiAction(this.guiButton("detail-tech-tree", Material.MANGROVE_PROPAGULE, "科技樹前置線", this.buildTechTreeSummaryLines(machine.id(), machine.unlockRequirement()), this.indexedPlaceholders("line", this.buildTechTreeSummaryLines(machine.id(), machine.unlockRequirement()), 6)), "tree:machine:" + machine.id()));
            if (relatedInteraction != null) {
                inventory.setItem(51, this.itemFactory.tagGuiAction(this.guiButton("machine-detail-interaction", this.interactionIcon(relatedInteraction.type()), "互動儀式", List.of(
                    "{name}",
                    "{type}",
                    "點擊查看法陣 / 多方塊結構"
                ), this.placeholders("name", relatedInteraction.displayName(), "type", this.interactionTypeLabel(relatedInteraction.type()))), "interaction:" + relatedInteraction.id()));
            }
        }
        this.fillEmptySlots(inventory, this.sectionPane(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
        this.openBookInventory(player, inventory);
    }

    public void openRecipeViewer(final Player player, final String targetId, final int page) {
        final List<RecipeView> recipeViews = this.recipeViewsFor(targetId);
        if (recipeViews.isEmpty()) {
            this.openDetail(player, targetId);
            return;
        }
        final int maxPage = Math.max(0, recipeViews.size() - 1);
        final int safePage = Math.max(0, Math.min(page, maxPage));
        final RecipeView view = recipeViews.get(safePage);
        final Inventory inventory = this.createHudBookInventory();
        inventory.setItem(4, this.info(Material.CRAFTING_TABLE, "配方預覽", List.of(
                "結果：" + view.resultName(),
                "第 " + (safePage + 1) + " / " + (maxPage + 1) + " 種",
                view.stationLine()
        )));
        final int[] gridSlots = {19, 20, 21, 28, 29, 30, 37, 38, 39};
        for (int index = 0; index < gridSlots.length; index++) {
            inventory.setItem(gridSlots[index], view.inputs().size() > index && view.inputs().get(index) != null
                    ? view.inputs().get(index)
                    : this.recipeBlankPane());
        }
        inventory.setItem(24, view.station());
        inventory.setItem(33, view.result());
        if (!view.detailLines().isEmpty()) {
            inventory.setItem(42, this.info(Material.PAPER, "配方說明", view.detailLines()));
        }
        inventory.setItem(45, this.itemFactory.tagGuiAction(this.guiButton("recipe-view-prev", Material.LIME_DYE, "◀ 上一種配方", List.of("目前第 {page} / {max-page} 種", "{hint}"), this.placeholders("page", String.valueOf(safePage + 1), "max-page", String.valueOf(maxPage + 1), "hint", safePage <= 0 ? "已經是第一種" : "點擊查看上一種")), "recipe-view:" + targetId + ":" + Math.max(0, safePage - 1)));
        inventory.setItem(49, this.itemFactory.tagGuiAction(this.guiButton("recipe-view-back-detail", Material.ARROW, "返回詳情", List.of("{name}"), this.placeholders("name", view.resultName())), (this.registry.getMachine(targetId) != null ? "machine:" : "item:") + targetId));
        inventory.setItem(53, this.itemFactory.tagGuiAction(this.guiButton("recipe-view-next", Material.MAGENTA_DYE, "下一種配方 ▶", List.of("目前第 {page} / {max-page} 種", "{hint}"), this.placeholders("page", String.valueOf(safePage + 1), "max-page", String.valueOf(maxPage + 1), "hint", safePage >= maxPage ? "已經是最後一種" : "點擊查看下一種")), "recipe-view:" + targetId + ":" + Math.min(maxPage, safePage + 1)));
        this.openBookInventory(player, inventory);
    }

    public void openTechTree(final Player player, final String kind, final String targetId) {
        final String targetName = this.itemFactory.displayNameForId(targetId);
        final String requirement = switch (kind.toLowerCase(Locale.ROOT)) {
            case "item" -> this.registry.getItem(targetId) != null ? this.registry.getItem(targetId).unlockRequirement() : "";
            case "machine" -> this.registry.getMachine(targetId) != null ? this.registry.getMachine(targetId).unlockRequirement() : "";
            case "interaction" -> this.addonService.getInteraction(targetId) != null ? this.addonService.getInteraction(targetId).unlockRequirement() : "";
            default -> "";
        };
        final Inventory inventory = this.createHudBookInventory();
        inventory.setItem(4, this.info(Material.MANGROVE_PROPAGULE, "科技樹 / 前置線", List.of(
                "目標：" + targetName,
                "只保留前置與後續重點"
        )));
        if (kind.equalsIgnoreCase("interaction") && this.addonService.getInteraction(targetId) != null) {
            final TechInteractionDefinition definition = this.addonService.getInteraction(targetId);
            inventory.setItem(13, this.info(this.interactionIcon(definition.type()), this.itemFactory.displayNameForId(definition.id()), List.of(
                    "類型：" + this.interactionTypeLabel(definition.type()),
                    "這是互動式科技內容"
            )));
        } else {
            inventory.setItem(13, this.displayStack(targetId));
        }
        final List<String> chain = this.buildUnlockChain(requirement, 6);
        final int[] prereqSlots = {20, 21, 22, 23, 24, 25};
        for (int index = 0; index < prereqSlots.length; index++) {
            inventory.setItem(prereqSlots[index], index < chain.size() ? this.info(Material.PAPER, "前置 " + (index + 1), List.of(chain.get(index))) : this.recipeBlankPane());
        }
        inventory.setItem(31, this.info(Material.COMPASS, "解鎖條件", List.of(this.itemFactory.formatUnlockRequirement(requirement))));
        final List<String> downstream = this.downstreamLines(targetId);
        if (!downstream.isEmpty()) {
            inventory.setItem(40, this.info(Material.KNOWLEDGE_BOOK, "後續延伸", downstream));
        }
        inventory.setItem(49, this.itemFactory.tagGuiAction(this.guiButton("tree-back-detail", Material.ARROW, "返回詳情", List.of("{name}"), this.placeholders("name", targetName)), kind + ":" + targetId));
        this.openBookInventory(player, inventory);
    }

    public void openInteractionDetail(final Player player, final String interactionId) {
        final TechInteractionDefinition definition = this.addonService.getInteraction(interactionId);
        if (definition == null) {
            this.openCategoryHub(player);
            return;
        }
        final String displayName = this.itemFactory.displayNameForId(definition.id());
        final Inventory inventory = this.createBookInventory(INTERACTION_PREFIX + displayName);
        this.decorateMenuFrame(inventory);
        inventory.setItem(4, this.info(this.interactionIcon(definition.type()), displayName, List.of(
                "類型：" + this.interactionTypeLabel(definition.type()),
                "分類：" + definition.guideCategory().displayName(),
                "階級：" + definition.tier().displayName()
        )));
        inventory.setItem(13, this.info(this.interactionIcon(definition.type()), "互動核心", List.of(
                "解鎖：" + this.itemFactory.formatUnlockRequirement(definition.unlockRequirement()),
                "適合做成魔法 + 科技交互內容"
        )));
        final int[] stepSlots = {19, 20, 21, 22, 23, 24, 25};
        for (int index = 0; index < stepSlots.length; index++) {
            inventory.setItem(stepSlots[index], index < definition.steps().size()
                    ? this.info(Material.PAPER, "步驟 " + (index + 1), List.of(definition.steps().get(index)))
                    : this.recipeBlankPane());
        }
        final int[] effectSlots = {37, 38, 39, 40, 41, 42, 43};
        for (int index = 0; index < effectSlots.length; index++) {
            inventory.setItem(effectSlots[index], index < definition.effects().size()
                    ? this.info(Material.AMETHYST_SHARD, "效果 " + (index + 1), List.of(definition.effects().get(index)))
                    : this.recipeBlankPane());
        }
        inventory.setItem(31, this.itemFactory.tagGuiAction(this.guiButton("interaction-tech-tree", Material.MANGROVE_PROPAGULE, "查看前置科技樹", this.buildTechTreeSummaryLines(definition.id(), definition.unlockRequirement()), this.indexedPlaceholders("line", this.buildTechTreeSummaryLines(definition.id(), definition.unlockRequirement()), 6)), "tree:interaction:" + definition.id()));
        inventory.setItem(48, this.researchActionIcon(player, "interaction", definition.id(), definition.unlockRequirement(), this.researchCost(definition), this.progressService.hasInteractionUnlocked(player.getUniqueId(), definition.id())));
        inventory.setItem(49, this.itemFactory.tagGuiAction(this.guiButton("interaction-back-content", Material.ARROW, "返回內容頁", List.of("{category}", "{tier}"), this.placeholders("category", definition.guideCategory().displayName(), "tier", definition.tier().displayName())), "back:" + definition.guideCategory().name() + ":" + definition.tier().name() + ":0"));
        this.fillEmptySlots(inventory, this.sectionPane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        this.openBookInventory(player, inventory);
    }

    public boolean isBookView(final String title) {
        return title.startsWith(MAIN_TITLE)
                || title.startsWith(HUB_TITLE)
                || title.startsWith(TIER_TITLE)
                || title.startsWith(RESEARCH_TITLE)
                || title.startsWith(DETAIL_PREFIX)
                || title.startsWith(RECIPE_PREFIX)
                || title.startsWith(TREE_PREFIX)
                || title.startsWith(INTERACTION_PREFIX)
                || title.startsWith(SEARCH_TITLE)
                || title.startsWith(GUIDE_TITLE);
    }

    public boolean isBookViewOpen(final UUID uuid) {
        return uuid != null && this.openBookViewPlayers.contains(uuid);
    }

    public boolean isBookInventory(final Inventory inventory) {
        return inventory != null && inventory.getHolder() == BOOK_VIEW_HOLDER;
    }

    public boolean isBookViewOpen(final UUID uuid, final Inventory topInventory) {
        if (uuid == null || topInventory == null || !this.openBookViewPlayers.contains(uuid)) {
            return false;
        }
        return this.openBookInventories.get(uuid) == topInventory;
    }

    public void clearBookView(final UUID uuid) {
        if (uuid != null) {
            this.openBookViewPlayers.remove(uuid);
            this.openBookInventories.remove(uuid);
        }
    }

    public boolean isAwaitingSearchInput(final UUID uuid) {
        return uuid != null && this.pendingSearchInput.contains(uuid);
    }

    public boolean consumeSearchInput(final Player player, final String query) {
        if (player == null || query == null || !this.pendingSearchInput.remove(player.getUniqueId())) {
            return false;
        }
        final String normalized = query.trim();
        if (normalized.isBlank() || normalized.equalsIgnoreCase("cancel") || normalized.equalsIgnoreCase("取消")) {
            player.sendMessage(this.itemFactory.warning("已取消科技搜尋。"));
            this.plugin.getSafeScheduler().runEntity(player, () -> this.openCategoryHub(player));
            return true;
        }
        this.plugin.getSafeScheduler().runEntity(player, () -> this.openSearchResults(player, normalized, 0));
        return true;
    }

    public boolean tryGrantPreviewItem(final Player player, final ItemStack clickedItem) {
        if (clickedItem == null || clickedItem.getType() == Material.AIR || !player.hasPermission("techproject.admin")) {
            return false;
        }
        final String previewId = this.itemFactory.getPreviewClaimId(clickedItem);
        if (previewId == null || previewId.isBlank()) {
            return false;
        }
        final ItemStack reward = this.itemFactory.buildPreviewReward(previewId);
        if (reward == null || reward.getType() == Material.AIR) {
            return false;
        }
        player.getInventory().addItem(reward);
        player.sendMessage(this.itemFactory.success("已取得預覽物品：" + this.itemFactory.displayNameForId(previewId.contains(":") ? previewId.substring(previewId.indexOf(':') + 1) : previewId)));
        return true;
    }

    public void handleAction(final Player player, final String action) {
        if (action == null || action.isBlank()) {
            return;
        }
        final String[] split = action.split(":", 2);
        final String verb = split[0];
        final String argument = split.length > 1 ? split[1] : "";
        this.playBookActionSound(player, verb);
        switch (verb) {
            case "category" -> this.openMainBook(player, GuideCategory.valueOf(argument), 0);
            case "hub" -> this.openCategoryHub(player);
            case "search-prompt" -> this.promptSearch(player);
            case "search-results" -> {
                final String[] detail = argument.split(":", 2);
                final String query = detail.length > 0 ? detail[0] : "";
                final int page = detail.length > 1 ? this.parsePage(detail[1]) : 0;
                this.openSearchResults(player, query, page);
            }
            case "research-desk" -> {
                final String[] detail = argument.split(":");
                final GuideCategory category = detail.length > 0 && !detail[0].equalsIgnoreCase("ALL") ? GuideCategory.valueOf(detail[0]) : null;
                final TechTier tier = detail.length > 1 && !detail[1].equalsIgnoreCase("ALL") ? TechTier.valueOf(detail[1]) : null;
                final boolean availableOnly = detail.length > 2 && detail[2].equalsIgnoreCase("READY");
                final int page = detail.length > 3 ? this.parsePage(detail[3]) : 0;
                this.openResearchDesk(player, category, tier, availableOnly, page);
            }
            case "refresh", "back", "page" -> {
                final String[] detail = argument.split(":");
                final GuideCategory category = GuideCategory.valueOf(detail[0]);
                if (detail.length == 1) {
                    this.openMainBook(player, category, 0);
                    break;
                }
                final TechTier tier = detail[1].equalsIgnoreCase("ALL") ? null : TechTier.valueOf(detail[1]);
                final int page = detail.length > 2 ? this.parsePage(detail[2]) : 0;
                this.openMainBook(player, category, tier, page);
            }
            case "tier" -> {
                final String[] detail = argument.split(":");
                if (detail.length < 2) {
                    return;
                }
                final GuideCategory category = GuideCategory.valueOf(detail[0]);
                final TechTier selectedTier = TechTier.valueOf(detail[1]);
                if (!this.isTierUnlocked(player.getUniqueId(), selectedTier)) {
                    player.sendMessage(this.itemFactory.warning("這個階級尚未解鎖，請先完成前一階內容。"));
                    this.openTierHub(player, category);
                    return;
                }
                this.openMainBook(player, category, selectedTier, 0);
            }
            case "guide-list" -> this.openGuideList(player, this.parsePage(argument));
            case "guide-direct" -> this.openGuideDetail(player, argument, 0);
            case "guide" -> {
                final String[] detail = argument.split(":");
                final String guideId = detail[0];
                final int page = detail.length > 1 ? this.parsePage(detail[1]) : 0;
                this.openGuideDetail(player, guideId, page);
            }
            case "recipe-view" -> {
                final String[] detail = argument.split(":");
                final String targetId = detail[0];
                final int page = detail.length > 1 ? this.parsePage(detail[1]) : 0;
                this.openRecipeViewer(player, targetId, page);
            }
            case "tree" -> {
                final String[] detail = argument.split(":");
                if (detail.length < 2) {
                    return;
                }
                this.openTechTree(player, detail[0], detail[1]);
            }
            case "interaction" -> this.openInteractionDetail(player, argument);
            case "item", "machine" -> this.openDetail(player, argument);
            case "research" -> this.handleResearchAction(player, argument);
            case "book-claim" -> {
                if (!this.playerHasBook(player)) {
                    player.getInventory().addItem(this.itemFactory.buildTechBook());
                    player.sendMessage(this.itemFactory.success("已補發一本科技書。"));
                } else {
                    player.sendMessage(this.itemFactory.warning("你身上已經有科技書了。"));
                }
                this.openCategoryHub(player);
            }
            case "achievements" -> this.plugin.getAchievementGuiService().openAchievementGui(player);
            default -> {
            }
        }
    }

    private void promptSearch(final Player player) {
        this.pendingSearchInput.add(player.getUniqueId());
        player.closeInventory();
        player.sendMessage(this.itemFactory.secondary("請在聊天輸入要搜尋的關鍵字，例如：太陽能、物流、虛空、種子。"));
        player.sendMessage(this.itemFactory.warning("輸入 cancel 或 取消 可放棄搜尋。"));
    }

    public void openSearchResults(final Player player, final String query, final int page) {
        final String normalized = query == null ? "" : query.trim();
        final Inventory inventory = this.createHudBookInventory();
        final List<ItemStack> results = this.buildSearchEntries(player.getUniqueId(), normalized);
        final int maxPage = Math.max(0, (results.size() - 1) / CONTENT_SLOTS.length);
        final int safePage = Math.max(0, Math.min(page, maxPage));
        inventory.setItem(4, this.info(Material.COMPASS, "搜尋結果", List.of(
                "關鍵字：" + normalized,
                "命中：" + results.size() + " 筆",
                "可搜尋物品 / 機器 / 儀式 / 指南"
        )));
        final int startIndex = safePage * CONTENT_SLOTS.length;
        for (int index = 0; index < CONTENT_SLOTS.length && startIndex + index < results.size(); index++) {
            inventory.setItem(CONTENT_SLOTS[index], results.get(startIndex + index));
        }
        if (results.isEmpty()) {
            inventory.setItem(22, this.info(Material.BARRIER, "找不到結果", List.of("可以換中文、英文代號或用途關鍵字再試一次。")));
        }
        inventory.setItem(45, this.itemFactory.tagGuiAction(this.guiButton("search-prev", Material.ARROW, "上一頁", List.of("頁數 {page}/{max-page}"), this.placeholders("page", String.valueOf(safePage + 1), "max-page", String.valueOf(maxPage + 1))), "search-results:" + normalized + ":" + Math.max(0, safePage - 1)));
        inventory.setItem(49, this.itemFactory.tagGuiAction(this.guiButton("search-new", Material.COMPASS, "重新搜尋", List.of("再次輸入不同關鍵字")), "search-prompt"));
        inventory.setItem(53, this.itemFactory.tagGuiAction(this.guiButton("search-next", Material.SPECTRAL_ARROW, "下一頁", List.of("頁數 {page}/{max-page}"), this.placeholders("page", String.valueOf(safePage + 1), "max-page", String.valueOf(maxPage + 1))), "search-results:" + normalized + ":" + Math.min(maxPage, safePage + 1)));
        this.openBookInventory(player, inventory);
    }

    private List<ItemStack> buildSearchEntries(final UUID uuid, final String query) {
        final String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        final List<ItemStack> results = new ArrayList<>();
        if (normalized.isBlank()) {
            return results;
        }
        for (final TechItemDefinition item : this.registry.allItems()) {
            if (this.matchesSearch(item.id(), this.itemFactory.displayNameForId(item.id()), item.description(), item.useCases(), normalized)) {
                results.add(this.renderItemEntry(uuid, item));
            }
        }
        for (final MachineDefinition machine : this.registry.allMachines()) {
            if (this.matchesSearch(machine.id(), this.itemFactory.displayNameForId(machine.id()), machine.effectDescription(), machine.outputs(), normalized)) {
                results.add(this.renderMachineEntry(uuid, machine));
            }
        }
        for (final TechInteractionDefinition interaction : this.addonService.allInteractions()) {
            if (this.matchesSearch(interaction.id(), interaction.displayName(), String.join(" / ", interaction.effects()), interaction.steps(), normalized)) {
                results.add(this.renderInteractionEntry(uuid, interaction));
            }
        }
        for (final GuideEntry entry : this.guidesByLocale.getOrDefault("zh_tw", Map.of()).values()) {
            if (this.matchesSearch(entry.id(), entry.title(), entry.preview(), entry.lines(), normalized)) {
                results.add(this.itemFactory.tagGuiAction(this.info(Material.WRITABLE_BOOK, entry.title(), List.of("分類：" + entry.topic(), entry.preview())), "guide:" + entry.id() + ":0"));
            }
        }
        return results;
    }

    private boolean matchesSearch(final String id,
                                  final String title,
                                  final String description,
                                  final List<String> lines,
                                  final String query) {
        if (this.containsSearch(id, query) || this.containsSearch(title, query) || this.containsSearch(description, query)) {
            return true;
        }
        for (final String line : lines) {
            if (this.containsSearch(line, query)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsSearch(final String raw, final String query) {
        return raw != null && !raw.isBlank() && raw.toLowerCase(Locale.ROOT).contains(query);
    }

    private ItemStack renderGuideCategoryCard(final Player player, final GuideCategory category) {
        final UUID uuid = player.getUniqueId();
        final int total = this.registry.getItemsByGuideCategory(category).size() + this.registry.getMachinesByGuideCategory(category).size();
        final int unlocked = this.unlockedCount(uuid, this.registry.getItemsByGuideCategory(category), this.registry.getMachinesByGuideCategory(category));
        final String title = (unlocked >= total && total > 0 ? "◆ " : "▶ ") + category.displayName();
        final List<String> lines = new ArrayList<>();
        lines.add("分類進度：" + unlocked + " / " + total);
        lines.add(this.guideCategoryFlavor(category));
        lines.add("點擊直接查看這個分類的內容");
        return this.itemFactory.tagGuiAction(this.buildConfiguredIcon(
            this.categoryCardKey(category),
            category.icon(),
            this.itemFactory.warning(title),
            this.itemFactory.mutedLore(lines),
            true
        ), "category:" + category.name());
    }

    private ItemStack renderTierCard(final UUID uuid, final GuideCategory category, final TechTier tier) {
        final boolean unlocked = this.isTierUnlocked(uuid, tier);
        final int total = this.categoryTierContentCount(category, tier);
        final int unlockedCount = this.unlockedCount(uuid, this.registry.getItemsByGuideCategoryAndTier(category, tier), this.registry.getMachinesByGuideCategoryAndTier(category, tier));
        final boolean completed = total > 0 && unlockedCount >= total;
        return this.itemFactory.tagGuiAction(this.buildConfiguredIcon(
            this.tierCardKey(tier),
            tier.icon(),
            this.itemFactory.warning((total <= 0 ? "－ " : completed ? "◆ " : unlocked ? "▶ " : "🔒 ") + tier.displayName()),
            this.itemFactory.mutedLore(List.of(
                "分類：" + category.displayName(),
                "進度：" + unlockedCount + " / " + total,
                total <= 0 ? "目前這個階級沒有內容，點擊會自動跳到最近的有內容階級" : unlocked ? "點擊進入此階級內容" : "需先完成前一個階級"
            )),
            true
        ), "tier:" + category.name() + ":" + tier.name());
    }

    private int categoryTierContentCount(final GuideCategory category, final TechTier tier) {
        return this.registry.getItemsByGuideCategoryAndTier(category, tier).size()
                + this.registry.getMachinesByGuideCategoryAndTier(category, tier).size()
                + this.addonService.getInteractionsByCategoryAndTier(category, tier).size();
    }

    private TechTier nearestTierWithContent(final GuideCategory category, final TechTier requestedTier) {
        final TechTier[] values = TechTier.values();
        final int start = requestedTier == null ? 0 : requestedTier.ordinal();
        for (int offset = 0; offset < values.length; offset++) {
            final int forward = start + offset;
            if (forward >= 0 && forward < values.length && this.categoryTierContentCount(category, values[forward]) > 0) {
                return values[forward];
            }
            if (offset == 0) {
                continue;
            }
            final int backward = start - offset;
            if (backward >= 0 && backward < values.length && this.categoryTierContentCount(category, values[backward]) > 0) {
                return values[backward];
            }
        }
        return null;
    }

    private boolean isTierUnlocked(final UUID uuid, final TechTier tier) {
        final TechTier previous = this.previousTier(tier);
        return previous == null || this.isTierCompleted(uuid, previous);
    }

    private boolean isTierCompleted(final UUID uuid, final TechTier tier) {
        final List<TechItemDefinition> items = this.registry.getItemsByTier(tier);
        final List<MachineDefinition> machines = this.registry.getMachinesByTier(tier);
        if (items.isEmpty() && machines.isEmpty()) {
            return true;
        }
        for (final TechItemDefinition item : items) {
            if (!this.progressService.hasItemUnlocked(uuid, item.id())) {
                return false;
            }
        }
        for (final MachineDefinition machine : machines) {
            if (!this.progressService.hasMachineUnlocked(uuid, machine.id())) {
                return false;
            }
        }
        return true;
    }

    private String tierProgressLine(final UUID uuid, final TechTier tier) {
        final int total = this.registry.getItemsByTier(tier).size() + this.registry.getMachinesByTier(tier).size();
        return tier.displayName() + "：" + this.unlockedCount(uuid, this.registry.getItemsByTier(tier), this.registry.getMachinesByTier(tier)) + " / " + total;
    }

    private String categoryTierProgressLine(final UUID uuid, final GuideCategory category, final TechTier tier) {
        final int total = this.categoryTierContentCount(category, tier);
        return "進度：" + this.unlockedCount(uuid, this.registry.getItemsByGuideCategoryAndTier(category, tier), this.registry.getMachinesByGuideCategoryAndTier(category, tier)) + " / " + total;
    }

    private String categoryProgressLine(final UUID uuid, final GuideCategory category) {
        final int total = this.registry.getItemsByGuideCategory(category).size() + this.registry.getMachinesByGuideCategory(category).size();
        return "進度：" + this.unlockedCount(uuid, this.registry.getItemsByGuideCategory(category), this.registry.getMachinesByGuideCategory(category)) + " / " + total;
    }

    private List<String> lockedEntryLines(final UUID uuid, final String kind, final String requirement, final long researchCost) {
        final List<String> lines = new ArrayList<>();
        lines.add("需求：" + this.itemFactory.formatUnlockRequirement(requirement));
        lines.add("研究：" + researchCost + " 研究點");
        for (final String step : this.buildUnlockChain(requirement, 3)) {
            lines.add(step);
        }
        if (!this.progressService.meetsRequirement(uuid, requirement)) {
            lines.add("先完成前置，再回來研究");
        } else if (this.progressService.getAvailableTechXp(uuid) < researchCost) {
            lines.add("研究點不足：目前 " + this.progressService.getAvailableTechXp(uuid));
        } else {
            lines.add("已可研究：進入詳情頁即可解鎖");
        }
        lines.add("點擊可查看詳情 / 科技樹 / 研究按鈕");
        return lines;
    }

    private List<String> buildTechTreeSummaryLines(final String targetId, final String requirement) {
        final List<String> lines = new ArrayList<>();
        final List<String> chain = this.buildUnlockChain(requirement, 3);
        if (chain.isEmpty()) {
            lines.add("這個目標屬於起始科技或獨立支線");
        } else {
            lines.addAll(chain);
        }
        lines.add("點擊查看完整前置線與後續延伸");
        return lines;
    }

    private List<String> buildUnlockChain(final String requirement, final int maxLines) {
        final List<String> lines = new ArrayList<>();
        if (requirement == null || requirement.isBlank() || requirement.equalsIgnoreCase("initial")) {
            return lines;
        }
        final String[] tokens = requirement.split("[&|]");
        for (final String rawToken : tokens) {
            final String token = rawToken.trim();
            if (token.isBlank()) {
                continue;
            }
            lines.add("→ " + this.describeRequirementToken(token));
            if (lines.size() >= maxLines) {
                break;
            }
        }
        return lines;
    }

    private String describeRequirementToken(final String token) {
        if (token.regionMatches(true, 0, "item:", 0, 5)) {
            return "先解鎖物品「" + this.itemFactory.displayNameForId(token.substring(5)) + "」";
        }
        if (token.regionMatches(true, 0, "machine:", 0, 8)) {
            return "先解鎖機器「" + this.itemFactory.displayNameForId(token.substring(8)) + "」";
        }
        if (token.regionMatches(true, 0, "achievement:", 0, 12)) {
            return "先完成成就「" + this.itemFactory.displayNameForId(token.substring(12)) + "」";
        }
        if (token.regionMatches(true, 0, "interaction:", 0, 12)) {
            return "先完成互動「" + this.itemFactory.displayNameForId(token.substring(12)) + "」";
        }
        if (token.regionMatches(true, 0, "stat:", 0, 5)) {
            return "先達成統計條件「" + token.substring(5) + "」";
        }
        return "先完成「" + this.itemFactory.displayNameForId(token) + "」";
    }

    private List<String> downstreamLines(final String targetId) {
        final List<String> lines = new ArrayList<>();
        for (final TechItemDefinition item : this.registry.allItems()) {
            if (this.dependsOn(item.unlockRequirement(), targetId)) {
                lines.add("→ 物品：" + this.itemFactory.displayNameForId(item.id()));
                if (lines.size() >= 5) {
                    return lines;
                }
            }
        }
        for (final MachineDefinition machine : this.registry.allMachines()) {
            if (this.dependsOn(machine.unlockRequirement(), targetId)) {
                lines.add("→ 機器：" + this.itemFactory.displayNameForId(machine.id()));
                if (lines.size() >= 5) {
                    return lines;
                }
            }
        }
        for (final TechInteractionDefinition interaction : this.addonService.allInteractions()) {
            if (this.dependsOn(interaction.unlockRequirement(), targetId)) {
                lines.add("→ 互動：" + this.itemFactory.displayNameForId(interaction.id()));
                if (lines.size() >= 5) {
                    return lines;
                }
            }
        }
        return lines.isEmpty() ? List.of("目前沒有直接顯示的後續延伸") : lines;
    }

    private boolean dependsOn(final String requirement, final String targetId) {
        if (requirement == null || requirement.isBlank()) {
            return false;
        }
        final String normalized = targetId.toLowerCase(Locale.ROOT);
        for (final String rawToken : requirement.split("[&|]")) {
            final String token = rawToken.trim().toLowerCase(Locale.ROOT);
            if (token.equals(normalized) || token.equals("item:" + normalized) || token.equals("machine:" + normalized) || token.equals("interaction:" + normalized)) {
                return true;
            }
        }
        return false;
    }

    private TechInteractionDefinition firstInteractionFor(final String targetId) {
        for (final TechInteractionDefinition definition : this.addonService.allInteractions()) {
            if (this.dependsOn(definition.unlockRequirement(), targetId)) {
                return definition;
            }
        }
        return null;
    }

    private Material interactionIcon(final TechInteractionType type) {
        return switch (type) {
            case RITUAL_CIRCLE -> Material.ENCHANTING_TABLE;
            case MULTIBLOCK -> Material.STRUCTURE_BLOCK;
            case MACHINE_LINK -> Material.ENDER_EYE;
            case CUSTOM_MENU -> Material.KNOWLEDGE_BOOK;
            case FIELD_EVENT -> Material.BEACON;
        };
    }

    private String interactionTypeLabel(final TechInteractionType type) {
        return switch (type) {
            case RITUAL_CIRCLE -> "法陣儀式";
            case MULTIBLOCK -> "多方塊結構";
            case MACHINE_LINK -> "機器連結";
            case CUSTOM_MENU -> "自訂互動介面";
            case FIELD_EVENT -> "場域事件";
        };
    }

    private int unlockedCount(final UUID uuid, final List<TechItemDefinition> items, final List<MachineDefinition> machines) {
        int unlocked = 0;
        for (final TechItemDefinition item : items) {
            if (this.progressService.hasItemUnlocked(uuid, item.id())) {
                unlocked++;
            }
        }
        for (final MachineDefinition machine : machines) {
            if (this.progressService.hasMachineUnlocked(uuid, machine.id())) {
                unlocked++;
            }
        }
        return unlocked;
    }

    private TechTier previousTier(final TechTier tier) {
        final int ordinal = tier.ordinal();
        return ordinal <= 0 ? null : TechTier.values()[ordinal - 1];
    }

    private boolean playerHasBook(final Player player) {
        for (final ItemStack stack : player.getInventory().getContents()) {
            if (this.itemFactory.hasTechBookTag(stack)) {
                return true;
            }
        }
        return false;
    }

    private String guideCategoryFlavor(final GuideCategory category) {
        return switch (category) {
            case MACHINES -> "核心加工設備、製程站與裝配主線";
            case ENERGY -> "發電、儲能、輸電與能源擴張";
            case MATERIALS -> "粉末、板材、線材、電路與核心中間件";
            case TOOLS -> "玩家主動操作的裝備、工具與機動裝置";
            case AGRICULTURE -> "種子、果苗、果園、培育艙與作物採收系統";
            case FOOD -> "飲品、沙拉、果派、口糧與星球對策料理";
            case LOGISTICS -> "物流、儲運、分流、過濾與倉儲搬運";
            case NETWORK -> "節點、研究、控制與各類自動化設備";
            case SPECIAL -> "科技書、徽章、星門、遺物與特殊互動";
        };
    }

    private List<String> guideCategoryRoleLines(final GuideCategory category) {
        return switch (category) {
            case MACHINES -> List.of("建議先把基礎加工鏈做起來", "沒有這條，其他分類很容易卡料", "粉碎 → 熔煉 → 壓縮 → 組裝是主骨架");
            case ENERGY -> List.of("能源不足會讓整條科技線停轉", "先求穩定，再追求大輸出", "太陽能 + 電池庫是很好的起點");
            case MATERIALS -> List.of("這裡決定你後期能不能量產", "多數高階配方都仰賴這些中間件", "看清材料鏈比硬肝更重要");
            case TOOLS -> List.of("所有可手持操作的工具集中到這裡", "農務、牽引、爆發位移與特殊器具不再分散", "之後擴充噴射、採集、工程工具也會優先放這類");
            case AGRICULTURE -> List.of("種子、果苗、樹苗與可直接採收的原料先歸到這裡", "這裡偏向原料面，不把果汁與料理混進來", "比較像 Slimefun 的農業支線骨架");
            case FOOD -> List.of("專門收飲品、沙拉、果派、口糧與星球對策料理", "做料理鏈時不用再跟原始農作物混在一起", "普通食物大多直接放行，星球對策餐再用素材自然分流");
            case LOGISTICS -> List.of("物流做得好，工廠體感差很多", "適合中期開始整理主幹與支線", "倉儲、過濾、分流都集中在這裡");
            case NETWORK -> List.of("把研究、節點與自動化收在同一條線", "這樣分類不會被拆得太碎", "基地控制與野外設備都能在這裡找");
            case SPECIAL -> List.of("用來放指南、徽章、星門與探索系統", "不成熟的預留分類先不要再額外拆出去", "這類內容可穿插，不一定照主線走");
        };
    }

    private List<String> guideCategoryKeyTargets(final GuideCategory category) {
        return switch (category) {
            case MACHINES -> List.of("粉碎機", "壓縮機", "製造機");
            case ENERGY -> List.of("太陽能發電機", "電池庫", "聚變反應爐");
            case MATERIALS -> List.of("鋼板", "銅線", "量子晶片");
            case TOOLS -> List.of("收田鐮刀", "向量抓鉤", "風暴噴射背包");
            case AGRICULTURE -> List.of("番茄種子", "櫻桃樹苗", "作物收割機");
            case FOOD -> List.of("柑橘沙拉", "晨光果派", "輻晶蜜凍");
            case LOGISTICS -> List.of("物流節點", "過濾路由器", "工業總線");
            case NETWORK -> List.of("研究台", "採礦鑽機", "自動釣台");
            case SPECIAL -> List.of("科技書", "星門", "成就徽章 / 遺物");
        };
    }

    private void playBookActionSound(final Player player, final String verb) {
        final Sound sound = switch (verb) {
            case "category", "hub", "back" -> Sound.BLOCK_CHISELED_BOOKSHELF_PICKUP;
            case "page", "guide-list", "guide" -> Sound.ITEM_BOOK_PAGE_TURN;
            case "recipe-view", "item", "machine" -> Sound.UI_STONECUTTER_SELECT_RECIPE;
            case "book-claim" -> Sound.BLOCK_CRAFTER_CRAFT;
            default -> Sound.BLOCK_AMETHYST_BLOCK_CHIME;
        };
        player.playSound(player.getLocation(), sound, 0.6f, 1.15f);
    }

    private void playBookOpenSound(final Player player) {
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.75f, 0.95f);
        player.playSound(player.getLocation(), Sound.BLOCK_CHISELED_BOOKSHELF_PICKUP, 0.35f, 1.2f);
    }

    private void playResearchUnlockEffect(final Player player, final TechTier tier) {
        final Color color = switch (tier) {
            case TIER1 -> Color.fromRGB(124, 252, 154);
            case TIER2 -> Color.fromRGB(125, 219, 255);
            case TIER3 -> Color.fromRGB(197, 139, 255);
            case TIER4 -> Color.fromRGB(255, 209, 102);
        };
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.05f);
        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.9f, 1.35f);
        final Location burst = player.getLocation().add(0.0, 1.0, 0.0);
        player.getWorld().spawnParticle(Particle.FIREWORK, burst, 24, 0.35, 0.45, 0.35, 0.02);
        player.getWorld().spawnParticle(Particle.END_ROD, burst, 18, 0.4, 0.55, 0.4, 0.01);
        player.getWorld().spawnParticle(Particle.DUST, burst, 30, 0.45, 0.55, 0.45, new Particle.DustOptions(color, 1.4f));
        this.plugin.getSafeScheduler().runEntityDelayed(player, () -> {
            final Location followUp = player.getLocation().add(0.0, 1.2, 0.0);
            player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.7f, 1.2f);
            player.getWorld().spawnParticle(Particle.FIREWORK, followUp, 18, 0.45, 0.5, 0.45, 0.02);
            player.getWorld().spawnParticle(Particle.DUST, followUp, 24, 0.5, 0.6, 0.5, new Particle.DustOptions(Color.WHITE, 1.1f));
        }, 2L);
    }

    private void loadGuides(final TechProjectPlugin plugin) {
        this.loadGuideResource(plugin, "default", "tech-guides.yml");
        this.loadGuideResource(plugin, "zh_tw", "tech-guides_zh_tw.yml");
        this.loadGuideResource(plugin, "en_us", "tech-guides_en_us.yml");
    }

    private void loadStructurePreviews(final TechProjectPlugin plugin) {
        for (final String resourcePath : CONTENT_STRUCTURE_FILES) {
            final File externalFile = new File(plugin.getDataFolder(), resourcePath);
            if (externalFile.isFile()) {
                this.loadStructureEntries(YamlConfiguration.loadConfiguration(externalFile));
                continue;
            }
            final var resource = plugin.getResource(resourcePath);
            if (resource == null) {
                continue;
            }
            this.loadStructureEntries(YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8)));
        }
    }

    private void loadStructureEntries(final YamlConfiguration yaml) {
        final ConfigurationSection section = yaml.getConfigurationSection("machines");
        if (section == null) {
            return;
        }
        for (final String key : section.getKeys(false)) {
            final ConfigurationSection previewSection = section.getConfigurationSection(key + ".structure-preview");
            if (previewSection == null) {
                continue;
            }
            final Map<Character, String> symbolMap = new LinkedHashMap<>();
            final ConfigurationSection mapSection = previewSection.getConfigurationSection("map");
            if (mapSection != null) {
                for (final String symbolKey : mapSection.getKeys(false)) {
                    if (symbolKey == null || symbolKey.isBlank()) {
                        continue;
                    }
                    symbolMap.put(symbolKey.charAt(0), mapSection.getString(symbolKey, "AIR"));
                }
            }
            this.machineStructurePreviews.put(key.toLowerCase(Locale.ROOT), new StructurePreview(
                    previewSection.getString("title", "2D 架構"),
                    previewSection.getStringList("rows"),
                    symbolMap
            ));
        }
    }

    private void loadGuideResource(final TechProjectPlugin plugin, final String localeKey, final String resourcePath) {
        final File externalFile = new File(plugin.getDataFolder(), resourcePath);
        if (externalFile.isFile()) {
            final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(externalFile);
            this.loadGuideEntries(localeKey, yaml);
            return;
        }
        final var resource = plugin.getResource(resourcePath);
        if (resource == null) {
            return;
        }
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8));
        this.loadGuideEntries(localeKey, yaml);
    }

    private void loadGuideEntries(final String localeKey, final YamlConfiguration yaml) {
        final ConfigurationSection section = yaml.getConfigurationSection("guides");
        if (section == null) {
            return;
        }
        final Map<String, GuideEntry> guides = new LinkedHashMap<>();
        for (final String key : section.getKeys(false)) {
            final String path = "guides." + key;
            guides.put(key, new GuideEntry(
                    key,
                    yaml.getString(path + ".title", key),
                    yaml.getString(path + ".topic", "綜合"),
                    yaml.getString(path + ".preview", ""),
                    yaml.getStringList(path + ".lines")
            ));
        }
        this.guidesByLocale.put(localeKey, guides);
    }

    private Map<String, GuideEntry> guidesFor(final Player player) {
        final Locale playerLocale = player.locale();
        final String locale = playerLocale == null ? "default" : playerLocale.toLanguageTag().toLowerCase(Locale.ROOT).replace('-', '_');
        if (this.guidesByLocale.containsKey(locale)) {
            return this.guidesByLocale.get(locale);
        }
        if (locale.startsWith("zh") && this.guidesByLocale.containsKey("zh_tw")) {
            return this.guidesByLocale.get("zh_tw");
        }
        if (locale.startsWith("en") && this.guidesByLocale.containsKey("en_us")) {
            return this.guidesByLocale.get("en_us");
        }
        return this.guidesByLocale.getOrDefault("default", Collections.emptyMap());
    }

    private record GuideEntry(String id, String title, String topic, String preview, List<String> lines) {
    }

    private record StructurePreview(String title, List<String> rows, Map<Character, String> symbolMap) {
    }

    private int parsePage(final String input) {
        try {
            return Integer.parseInt(input);
        } catch (final NumberFormatException ignored) {
            return 0;
        }
    }

    private ItemStack renderItemEntry(final UUID uuid, final TechItemDefinition definition) {
        final boolean unlocked = this.progressService.hasItemUnlocked(uuid, definition.id());
        final ItemStack stack = unlocked ? this.itemFactory.buildTechItem(definition) : new ItemStack(Material.BARRIER);
        final ItemMeta meta = stack.getItemMeta();
        if (!unlocked) {
            meta.displayName(this.itemFactory.danger("未解鎖：" + this.itemFactory.displayNameForId(definition.id())));
            meta.lore(this.itemFactory.mutedLore(this.lockedEntryLines(uuid, "item", definition.unlockRequirement(), this.researchCost(definition))));
            stack.setItemMeta(meta);
            return this.itemFactory.tagGuiAction(stack, "item:" + definition.id());
        }
        return this.itemFactory.tagPreviewClaim(this.itemFactory.tagGuiAction(stack, "item:" + definition.id()), "item:" + definition.id());
    }

    private ItemStack renderMachineEntry(final UUID uuid, final MachineDefinition definition) {
        final boolean unlocked = this.progressService.hasMachineUnlocked(uuid, definition.id());
        final ItemStack stack = unlocked ? this.itemFactory.buildMachineGuiIcon(definition) : new ItemStack(Material.BARRIER);
        final ItemMeta meta = stack.getItemMeta();
        if (!unlocked) {
            meta.displayName(this.itemFactory.danger("未解鎖機器：" + this.itemFactory.displayNameForId(definition.id())));
            meta.lore(this.itemFactory.mutedLore(this.lockedEntryLines(uuid, "machine", definition.unlockRequirement(), this.researchCost(definition))));
            stack.setItemMeta(meta);
            return this.itemFactory.tagGuiAction(stack, "machine:" + definition.id());
        }
        return this.itemFactory.tagPreviewClaim(this.itemFactory.tagGuiAction(stack, "machine:" + definition.id()), "machine:" + definition.id());
    }

    private ItemStack renderInteractionEntry(final UUID uuid, final TechInteractionDefinition definition) {
        final boolean unlocked = this.progressService.hasInteractionUnlocked(uuid, definition.id());
        final ItemStack stack = new ItemStack(unlocked ? this.interactionIcon(definition.type()) : Material.BARRIER);
        final ItemMeta meta = stack.getItemMeta();
        final String displayName = this.itemFactory.displayNameForId(definition.id());
        if (!unlocked) {
            meta.displayName(this.itemFactory.danger("未解鎖互動：" + displayName));
            meta.lore(this.itemFactory.mutedLore(this.lockedEntryLines(uuid, "interaction", definition.unlockRequirement(), this.researchCost(definition))));
            stack.setItemMeta(meta);
            return this.itemFactory.tagGuiAction(stack, "interaction:" + definition.id());
        }
        meta.displayName(this.itemFactory.hex(displayName, "#C58BFF"));
        meta.lore(this.itemFactory.mutedLore(List.of(
                "類型：" + this.interactionTypeLabel(definition.type()),
                "解鎖：" + this.itemFactory.formatUnlockRequirement(definition.unlockRequirement()),
                "點擊查看法陣 / 多方塊 / 儀式細節"
        )));
        stack.setItemMeta(meta);
        return this.itemFactory.tagGuiAction(stack, "interaction:" + definition.id());
    }

    private List<ResearchCandidate> collectResearchCandidates(final UUID uuid,
                                                             final GuideCategory category,
                                                             final TechTier tier,
                                                             final boolean availableOnly) {
        final List<ResearchCandidate> candidates = new ArrayList<>();
        for (final TechItemDefinition item : this.registry.allItems()) {
            if (this.progressService.hasItemUnlocked(uuid, item.id()) || !this.matchesResearchFilter(category, tier, item.guideCategory(), item.tier())) {
                continue;
            }
            final long cost = this.researchCost(item);
            final boolean meets = this.progressService.meetsRequirement(uuid, item.unlockRequirement());
            if (!availableOnly || meets) {
                candidates.add(new ResearchCandidate("item", item.id(), this.itemFactory.displayNameForId(item.id()), item.guideCategory(), item.tier(), item.unlockRequirement(), cost, meets, this.progressService.getAvailableTechXp(uuid) >= cost));
            }
        }
        for (final MachineDefinition machine : this.registry.allMachines()) {
            if (this.progressService.hasMachineUnlocked(uuid, machine.id()) || !this.matchesResearchFilter(category, tier, machine.guideCategory(), machine.tier())) {
                continue;
            }
            final long cost = this.researchCost(machine);
            final boolean meets = this.progressService.meetsRequirement(uuid, machine.unlockRequirement());
            if (!availableOnly || meets) {
                candidates.add(new ResearchCandidate("machine", machine.id(), this.itemFactory.displayNameForId(machine.id()), machine.guideCategory(), machine.tier(), machine.unlockRequirement(), cost, meets, this.progressService.getAvailableTechXp(uuid) >= cost));
            }
        }
        for (final TechInteractionDefinition interaction : this.addonService.allInteractions()) {
            if (this.progressService.hasInteractionUnlocked(uuid, interaction.id()) || !this.matchesResearchFilter(category, tier, interaction.guideCategory(), interaction.tier())) {
                continue;
            }
            final long cost = this.researchCost(interaction);
            final boolean meets = this.progressService.meetsRequirement(uuid, interaction.unlockRequirement());
            if (!availableOnly || meets) {
                candidates.add(new ResearchCandidate("interaction", interaction.id(), this.itemFactory.displayNameForId(interaction.id()), interaction.guideCategory(), interaction.tier(), interaction.unlockRequirement(), cost, meets, this.progressService.getAvailableTechXp(uuid) >= cost));
            }
        }
        candidates.sort((left, right) -> {
            final int rankCompare = Integer.compare(this.researchRank(left), this.researchRank(right));
            if (rankCompare != 0) {
                return rankCompare;
            }
            final int tierCompare = Integer.compare(left.tier().ordinal(), right.tier().ordinal());
            if (tierCompare != 0) {
                return tierCompare;
            }
            final int categoryCompare = Integer.compare(left.category().ordinal(), right.category().ordinal());
            if (categoryCompare != 0) {
                return categoryCompare;
            }
            return left.displayName().compareToIgnoreCase(right.displayName());
        });
        return candidates;
    }

    private boolean matchesResearchFilter(final GuideCategory requestedCategory,
                                          final TechTier requestedTier,
                                          final GuideCategory actualCategory,
                                          final TechTier actualTier) {
        return (requestedCategory == null || requestedCategory == actualCategory)
                && (requestedTier == null || requestedTier == actualTier);
    }

    private int researchRank(final ResearchCandidate candidate) {
        if (candidate.meetsRequirement() && candidate.affordable()) {
            return 0;
        }
        if (candidate.meetsRequirement()) {
            return 1;
        }
        return 2;
    }

    private ItemStack renderResearchDeskEntry(final ResearchCandidate candidate) {
        final Material stateIcon;
        final String title;
        final List<String> lines = new ArrayList<>();
        final ItemStack baseStack;
        if (candidate.kind().equals("item")) {
            baseStack = this.itemFactory.buildTechItem(this.registry.getItem(candidate.id()));
        } else if (candidate.kind().equals("machine")) {
            baseStack = this.itemFactory.buildMachineGuiIcon(this.registry.getMachine(candidate.id()));
        } else {
            baseStack = new ItemStack(this.interactionIcon(this.addonService.getInteraction(candidate.id()).type()));
            final ItemMeta meta = baseStack.getItemMeta();
            meta.displayName(this.itemFactory.hex(candidate.displayName(), "#C58BFF"));
            baseStack.setItemMeta(meta);
        }

        if (candidate.meetsRequirement() && candidate.affordable()) {
            title = "◆ 可立即研究：" + candidate.displayName();
            lines.add("分類：" + candidate.category().displayName() + " / " + candidate.tier().displayName());
            lines.add("研究成本：" + candidate.cost() + " 研究點");
            lines.add("狀態：前置完成，可直接點擊研究");
            lines.add("點擊直接研究解鎖");
            final ItemMeta meta = baseStack.getItemMeta();
            meta.displayName(this.itemFactory.success(title));
            meta.lore(this.itemFactory.mutedLore(lines));
            baseStack.setItemMeta(meta);
            return this.itemFactory.tagGuiAction(baseStack, "research:" + candidate.kind() + ":" + candidate.id());
        }

        if (candidate.meetsRequirement()) {
            title = "• 待累積研究點：" + candidate.displayName();
            lines.add("分類：" + candidate.category().displayName() + " / " + candidate.tier().displayName());
            lines.add("研究成本：" + candidate.cost() + " 研究點");
            lines.add("狀態：前置完成，但研究點尚不足");
            lines.add("點擊查看詳情與研究按鈕");
            final ItemMeta meta = baseStack.getItemMeta();
            meta.displayName(this.itemFactory.warning(title));
            meta.lore(this.itemFactory.mutedLore(lines));
            baseStack.setItemMeta(meta);
            return this.itemFactory.tagGuiAction(baseStack, candidate.kind() + ":" + candidate.id());
        }

        stateIcon = Material.BARRIER;
        final ItemStack stack = new ItemStack(stateIcon);
        final ItemMeta meta = stack.getItemMeta();
        meta.displayName(this.itemFactory.danger("未達前置：" + candidate.displayName()));
        lines.add("分類：" + candidate.category().displayName() + " / " + candidate.tier().displayName());
        lines.add("研究成本：" + candidate.cost() + " 研究點");
        lines.addAll(this.buildUnlockChain(candidate.requirement(), 3));
        lines.add("點擊查看詳情 / 科技樹");
        meta.lore(this.itemFactory.mutedLore(lines));
        stack.setItemMeta(meta);
        return this.itemFactory.tagGuiAction(stack, candidate.kind() + ":" + candidate.id());
    }

    private ItemStack researchTierFilterIcon(final GuideCategory category,
                                             final TechTier tier,
                                             final boolean availableOnly,
                                             final String label) {
        final boolean selected = tier == null ? "全部".equals(label) : tier.shortName().equals(label);
        final Material icon = tier == null ? Material.NETHER_STAR : tier.icon();
        return this.itemFactory.tagGuiAction(this.buildConfiguredIcon(
            this.researchTierKey(tier),
            icon,
            this.itemFactory.warning((selected ? "◆ " : "• ") + label),
            this.itemFactory.mutedLore(List.of(
                selected ? "目前研究台正在看這個階級" : "點擊切換研究台階級",
                "篩選目前候選清單"
            )),
            true
        ), this.researchDeskAction(category, tier, availableOnly, 0));
    }

    private ItemStack researchModeIcon(final GuideCategory category,
                                       final TechTier tier,
                                       final boolean availableOnly) {
        return this.itemFactory.tagGuiAction(this.guiButton(
            availableOnly ? "research-mode-ready" : "research-mode-all",
            availableOnly ? Material.LIME_DYE : Material.GRAY_DYE,
            availableOnly ? "◆ 只看可研究" : "• 顯示全部候選",
            List.of(
                availableOnly ? "目前只顯示前置已完成的內容" : "目前也顯示尚未完成前置的內容",
                "點擊切換模式"
            )), this.researchDeskAction(category, tier, !availableOnly, 0));
    }

    private ItemStack researchCategoryIcon(final GuideCategory category) {
        final Material icon = category == null ? Material.KNOWLEDGE_BOOK : category.icon();
        final String title = category == null ? "目前：全部分類" : "目前：" + category.displayName();
        return this.buildConfiguredIcon(
                this.researchCategoryKey(category),
                icon,
                this.itemFactory.warning(title),
                this.itemFactory.mutedLore(List.of("左右兩邊按鈕可循環切換分類", "研究台會自動依分類重新排序")),
                true
        );
    }

    private ItemStack buildConfiguredIcon(final String key,
                                          final Material fallbackMaterial,
                                          final net.kyori.adventure.text.Component displayName,
                                          final List<net.kyori.adventure.text.Component> lore,
                                          final boolean infoCard) {
        return this.itemFactory.buildGuiIcon(key, fallbackMaterial, displayName, lore, infoCard);
    }

    private String categoryCardKey(final GuideCategory category) {
        return "category-card-" + category.name().toLowerCase(Locale.ROOT);
    }

    private String tierCardKey(final TechTier tier) {
        return "tier-card-" + tier.name().toLowerCase(Locale.ROOT);
    }

    private String mainTierKey(final TechTier tier) {
        return "main-tier-" + tier.name().toLowerCase(Locale.ROOT);
    }

    private String researchTierKey(final TechTier tier) {
        return tier == null ? "research-tier-all" : "research-tier-" + tier.name().toLowerCase(Locale.ROOT);
    }

    private String researchCategoryKey(final GuideCategory category) {
        return category == null ? "research-category-all" : "research-category-" + category.name().toLowerCase(Locale.ROOT);
    }

    private GuideCategory previousCategory(final GuideCategory current) {
        final GuideCategory[] values = GuideCategory.values();
        if (current == null) {
            return values[values.length - 1];
        }
        final int index = current.ordinal();
        return index <= 0 ? null : values[index - 1];
    }

    private GuideCategory nextCategory(final GuideCategory current) {
        final GuideCategory[] values = GuideCategory.values();
        if (current == null) {
            return values[0];
        }
        final int index = current.ordinal();
        return index >= values.length - 1 ? null : values[index + 1];
    }

    private String researchDeskAction(final GuideCategory category,
                                      final TechTier tier,
                                      final boolean availableOnly,
                                      final int page) {
        return "research-desk:"
                + (category == null ? "ALL" : category.name()) + ":"
                + (tier == null ? "ALL" : tier.name()) + ":"
                + (availableOnly ? "READY" : "ALL") + ":"
                + page;
    }

    private Component techMenuTitle() {
        return Component.text(TECH_MENU_TITLE, NamedTextColor.WHITE).font(TECH_MENU_TITLE_FONT);
    }

    private Component plainBookTitle(final String title) {
        return Component.text(title == null || title.isBlank() ? MAIN_TITLE : title, NamedTextColor.WHITE);
    }

    private Inventory createHudBookInventory() {
        return Bukkit.createInventory(BOOK_VIEW_HOLDER, 54, this.techMenuTitle());
    }

    private Inventory createBookInventory(final String title) {
        return Bukkit.createInventory(BOOK_VIEW_HOLDER, 54, this.plainBookTitle(title));
    }

    private void openBookInventory(final Player player, final Inventory inventory) {
        if (player == null || inventory == null) {
            return;
        }
        this.openBookViewPlayers.add(player.getUniqueId());
        this.openBookInventories.put(player.getUniqueId(), inventory);
        player.openInventory(inventory);
    }

    private record ResearchCandidate(String kind,
                                     String id,
                                     String displayName,
                                     GuideCategory category,
                                     TechTier tier,
                                     String requirement,
                                     long cost,
                                     boolean meetsRequirement,
                                     boolean affordable) {
    }

    private void handleResearchAction(final Player player, final String argument) {
        final String[] detail = argument.split(":", 2);
        if (detail.length < 2) {
            return;
        }
        final String kind = detail[0].toLowerCase(Locale.ROOT);
        final String id = detail[1];
        final UUID uuid = player.getUniqueId();
        final long cost;
        final String requirement;
        final String displayName;
        final TechTier tier;
        final boolean alreadyUnlocked;
        switch (kind) {
            case "item" -> {
                final TechItemDefinition definition = this.registry.getItem(id);
                if (definition == null) {
                    return;
                }
                cost = this.researchCost(definition);
                requirement = definition.unlockRequirement();
                displayName = this.itemFactory.displayNameForId(id);
                tier = definition.tier();
                alreadyUnlocked = this.progressService.hasItemUnlocked(uuid, id);
            }
            case "machine" -> {
                final MachineDefinition definition = this.registry.getMachine(id);
                if (definition == null) {
                    return;
                }
                cost = this.researchCost(definition);
                requirement = definition.unlockRequirement();
                displayName = this.itemFactory.displayNameForId(id);
                tier = definition.tier();
                alreadyUnlocked = this.progressService.hasMachineUnlocked(uuid, id);
            }
            case "interaction" -> {
                final TechInteractionDefinition definition = this.addonService.getInteraction(id);
                if (definition == null) {
                    return;
                }
                cost = this.researchCost(definition);
                requirement = definition.unlockRequirement();
                displayName = this.itemFactory.displayNameForId(id);
                tier = definition.tier();
                alreadyUnlocked = this.progressService.hasInteractionUnlocked(uuid, id);
            }
            default -> {
                return;
            }
        }

        if (alreadyUnlocked) {
            player.sendMessage(this.itemFactory.warning("這個科技已經研究完成。"));
        } else if (!this.progressService.meetsRequirement(uuid, requirement)) {
            player.sendMessage(this.itemFactory.warning("前置條件尚未完成，還不能研究。"));
        } else if (!this.progressService.spendTechXp(uuid, cost)) {
            player.sendMessage(this.itemFactory.warning("研究點不足，需要 " + cost + "。"));
        } else {
            switch (kind) {
                case "item" -> {
                    this.progressService.unlockItem(uuid, id);
                    this.progressService.unlockByRequirement(uuid, "item:" + id);
                }
                case "machine" -> {
                    this.progressService.unlockMachine(uuid, id);
                    this.progressService.unlockByRequirement(uuid, "machine:" + id);
                }
                case "interaction" -> {
                    this.progressService.unlockInteraction(uuid, id);
                    this.progressService.unlockByRequirement(uuid, "interaction:" + id);
                }
                default -> {
                }
            }
            player.sendMessage(this.itemFactory.success("研究完成：" + displayName + "  (-" + cost + " 研究點)"));
            this.playResearchUnlockEffect(player, tier);
        }

        switch (kind) {
            case "item", "machine" -> this.openDetail(player, id);
            case "interaction" -> this.openInteractionDetail(player, id);
            default -> {
            }
        }
    }

    private ItemStack researchOverviewIcon(final UUID uuid,
                                           final GuideCategory category,
                                           final TechTier tier,
                                           final boolean availableOnly) {
        return this.itemFactory.tagGuiAction(this.guiButton("research-overview", Material.EXPERIENCE_BOTTLE, "研究點", List.of(
            "等級：Lv.{level}",
            "目前可用：{available}",
            "累計取得：{total}",
            "本級進度：{progress} / {next}",
            "來源：成就、機器運轉、發電與採集里程碑",
            "物品 第一階～第四階：{item-costs}",
            "機器 第一階～第四階：{machine-costs}",
            "互動 第一階～第四階：{interaction-costs}",
            "點擊打開研究台"
        ), this.placeholders(
            "level", String.valueOf(this.progressService.getTechLevel(uuid)),
            "available", String.valueOf(this.progressService.getAvailableTechXp(uuid)),
            "total", String.valueOf(this.progressService.getTechXpTotal(uuid)),
            "progress", String.valueOf(this.progressService.getXpIntoCurrentLevel(uuid)),
            "next", String.valueOf(this.progressService.getXpForNextLevel(uuid)),
            "item-costs", this.baseResearchCost("items", TechTier.TIER1) + " / " + this.baseResearchCost("items", TechTier.TIER2) + " / " + this.baseResearchCost("items", TechTier.TIER3) + " / " + this.baseResearchCost("items", TechTier.TIER4),
            "machine-costs", this.baseResearchCost("machines", TechTier.TIER1) + " / " + this.baseResearchCost("machines", TechTier.TIER2) + " / " + this.baseResearchCost("machines", TechTier.TIER3) + " / " + this.baseResearchCost("machines", TechTier.TIER4),
            "interaction-costs", this.baseResearchCost("interactions", TechTier.TIER1) + " / " + this.baseResearchCost("interactions", TechTier.TIER2) + " / " + this.baseResearchCost("interactions", TechTier.TIER3) + " / " + this.baseResearchCost("interactions", TechTier.TIER4)
        )), this.researchDeskAction(category, tier, availableOnly, 0));
    }

    private ItemStack researchActionIcon(final Player player,
                                         final String kind,
                                         final String id,
                                         final String requirement,
                                         final long cost,
                                         final boolean unlocked) {
        final UUID uuid = player.getUniqueId();
        final boolean meetsRequirement = this.progressService.meetsRequirement(uuid, requirement);
        final long availableXp = this.progressService.getAvailableTechXp(uuid);
        final Material material = unlocked ? Material.LIME_DYE : meetsRequirement ? Material.EXPERIENCE_BOTTLE : Material.GRAY_DYE;
        final List<String> lines = new ArrayList<>();
        lines.add("研究成本：" + cost + " 研究點");
        lines.add("目前可用：" + availableXp + " 研究點");
        lines.add("前置條件：" + (meetsRequirement ? "已完成" : "未完成"));
        if (unlocked) {
            lines.add("這個科技已完成研究");
            return this.info(material, "研究狀態", lines);
        }
        if (!meetsRequirement) {
            lines.add("先完成前置科技，再消耗研究點研究");
            return this.info(material, "尚未可研究", lines);
        }
        if (availableXp < cost) {
            lines.add("研究點不足，先推進工廠與成就");
            return this.info(material, "研究點不足", lines);
        }
        lines.add("點擊立即研究解鎖");
        return this.itemFactory.tagGuiAction(this.info(material, "立即研究", lines), "research:" + kind + ":" + id);
    }

    private long researchCost(final TechItemDefinition definition) {
        final long base = this.baseResearchCost("items", definition.tier());
        final long perUseCase = this.plugin.getConfig().getLong("research.costs.items.use-case-bonus", 2L);
        final long maxBonus = this.plugin.getConfig().getLong("research.costs.items.use-case-max", 8L);
        return base + Math.min(maxBonus, Math.max(0L, definition.useCases().size() - 1L) * perUseCase);
    }

    private long researchCost(final MachineDefinition definition) {
        final long base = this.baseResearchCost("machines", definition.tier());
        final long divisor = Math.max(1L, this.plugin.getConfig().getLong("research.costs.machines.energy-divisor", 2L));
        final long maxBonus = this.plugin.getConfig().getLong("research.costs.machines.energy-max", 18L);
        return base + Math.min(maxBonus, Math.max(0L, definition.energyPerTick()) / divisor);
    }

    private long researchCost(final TechInteractionDefinition definition) {
        final long base = this.baseResearchCost("interactions", definition.tier());
        final long stepBonus = this.plugin.getConfig().getLong("research.costs.interactions.step-bonus", 3L);
        final long effectBonus = this.plugin.getConfig().getLong("research.costs.interactions.effect-bonus", 2L);
        final long maxBonus = this.plugin.getConfig().getLong("research.costs.interactions.extra-max", 20L);
        return base + Math.min(maxBonus, definition.steps().size() * stepBonus + definition.effects().size() * effectBonus);
    }

    private long baseResearchCost(final String kind, final TechTier tier) {
        return this.plugin.getConfig().getLong("research.costs." + kind + ".tiers." + tier.name(), switch (kind) {
            case "items" -> switch (tier) {
                case TIER1 -> 10L;
                case TIER2 -> 22L;
                case TIER3 -> 40L;
                case TIER4 -> 68L;
            };
            case "machines" -> switch (tier) {
                case TIER1 -> 24L;
                case TIER2 -> 42L;
                case TIER3 -> 72L;
                case TIER4 -> 110L;
            };
            case "interactions" -> switch (tier) {
                case TIER1 -> 18L;
                case TIER2 -> 36L;
                case TIER3 -> 64L;
                case TIER4 -> 100L;
            };
            default -> 0L;
        });
    }

    private ItemStack info(final Material material, final String title, final List<String> lines) {
        if (material != null && material.name().contains("GLASS_PANE")) {
            if ((title == null || title.isBlank()) && (lines == null || lines.isEmpty())) {
                return new ItemStack(Material.AIR);
            }
            return this.itemFactory.buildGuiPane(
                    Material.PAPER,
                    this.itemFactory.warning(this.itemFactory.localizeInlineTerms(title)),
                    this.itemFactory.mutedLore(lines.stream().map(this.itemFactory::localizeInlineTerms).toList()),
                    true
            );
        }
        final ItemStack stack = new ItemStack(this.itemFactory.safeItemMaterial(material));
        final ItemMeta meta = stack.getItemMeta();
        final List<String> localizedLines = lines.stream().map(this.itemFactory::localizeInlineTerms).toList();
        meta.displayName(this.itemFactory.warning(this.itemFactory.localizeInlineTerms(title)));
        meta.lore(this.itemFactory.mutedLore(localizedLines));
        this.itemFactory.applyGuiHudModel(meta, stack.getType(), true);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack guiButton(final String key,
                                final Material fallbackMaterial,
                                final String fallbackTitle,
                                final List<String> fallbackLore) {
        return this.itemFactory.buildGuiButton(key, fallbackMaterial, fallbackTitle, fallbackLore);
    }

    private ItemStack guiButton(final String key,
                                final Material fallbackMaterial,
                                final String fallbackTitle,
                                final List<String> fallbackLore,
                                final Map<String, String> placeholders) {
        return this.itemFactory.buildGuiButton(key, fallbackMaterial, fallbackTitle, fallbackLore, placeholders);
    }

    private Map<String, String> placeholders(final String... values) {
        final Map<String, String> placeholders = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            placeholders.put(values[index], values[index + 1]);
        }
        return placeholders;
    }

    private Map<String, String> indexedPlaceholders(final String prefix,
                                                    final List<String> lines,
                                                    final int maxCount) {
        final Map<String, String> placeholders = new LinkedHashMap<>();
        for (int index = 0; index < maxCount; index++) {
            placeholders.put(prefix + (index + 1), index < lines.size() ? lines.get(index) : "");
        }
        return placeholders;
    }

    private List<String> baseItemAcquisitionHints(final TechItemDefinition item) {
        return switch (item.id()) {
            case "copper_ingot" -> List.of("先做出粉碎機與科技熔爐。", "把原版銅礦丟進粉碎機變成銅粉，再進科技熔爐。", "原版銅錠不能直接當科技銅錠使用，兩條材料線已分流。");
            case "copper_dust" -> List.of("先取得粉碎機。", "把原版銅礦丟進粉碎機後才會得到銅粉。");
            case "iron_dust" -> List.of("先取得粉碎機。", "把原版鐵礦丟進粉碎機後才會得到鐵粉。");
            default -> List.of();
        };
    }

    private ItemStack clickableReference(final String id, final boolean recipeHint) {
        final ItemStack stack = this.displayStack(id);
        if (stack == null) {
            return this.recipeBlankPane();
        }
        if (this.registry.getMachine(id) != null) {
            return this.itemFactory.tagPreviewClaim(this.itemFactory.tagGuiAction(stack, "machine:" + id), "machine:" + id);
        }
        if (this.registry.getItem(id) != null) {
            return this.itemFactory.tagPreviewClaim(this.itemFactory.tagGuiAction(stack, "item:" + id), "item:" + id);
        }
        return this.itemFactory.tagPreviewClaim(stack, "material:" + id);
    }

    private void renderMachineStructurePreview(final Inventory inventory, final MachineDefinition machine) {
        final StructurePreview preview = this.machineStructurePreview(machine);
        final int[] slots = {28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
        for (int row = 0; row < 2; row++) {
            final String line = preview.rows().size() > row ? preview.rows().get(row) : "";
            for (int column = 0; column < 7; column++) {
                final char symbol = line.length() > column ? line.charAt(column) : ' ';
                final String token = symbol == ' ' ? null : preview.symbolMap().get(symbol);
                inventory.setItem(slots[row * 7 + column], token == null ? this.recipeBlankPane() : this.structureCell(token));
            }
        }
    }

    private StructurePreview machineStructurePreview(final MachineDefinition machine) {
        final StructurePreview configured = this.machineStructurePreviews.get(machine.id().toLowerCase(Locale.ROOT));
        if (configured != null && !configured.rows().isEmpty()) {
            return configured;
        }
        return new StructurePreview(
                "單方塊核心",
                List.of("       ", "   C   "),
                Map.of('C', "machine:" + machine.id())
        );
    }

    private ItemStack structureCell(final String token) {
        if (token == null || token.isBlank() || token.equalsIgnoreCase("air")) {
            return this.recipeBlankPane();
        }
        final ItemStack stack = this.clickableReference(token, false).clone();
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.lore(null);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private List<RecipeView> recipeViewsFor(final String targetId) {
        final List<RecipeView> views = new ArrayList<>();
        final MachineDefinition machine = this.registry.getMachine(targetId);
        final BlueprintService.BlueprintEntry blueprint = this.blueprintService.get(targetId);
        if (machine != null && blueprint != null && blueprint.registerRecipe()) {
            views.add(this.blueprintRecipeView(machine, blueprint));
        }
        for (final MachineRecipe recipe : this.registry.getRecipesForOutput(targetId)) {
            views.add(this.machineRecipeView(recipe));
        }
        return views;
    }

    private RecipeView blueprintRecipeView(final MachineDefinition machine, final BlueprintService.BlueprintEntry blueprint) {
        final List<ItemStack> inputs = new ArrayList<>();
        final Map<Character, ItemStack> symbolMap = new LinkedHashMap<>();
        if (blueprint.ingredientSection() != null) {
            for (final String key : blueprint.ingredientSection().getKeys(false)) {
                if (!key.isBlank()) {
                    symbolMap.put(key.charAt(0), this.displayStack(blueprint.ingredientSection().getString(key, "AIR")));
                }
            }
        }
        for (int row = 0; row < 3; row++) {
            final String shapeRow = blueprint.shape().size() > row ? blueprint.shape().get(row) : "";
            for (int column = 0; column < 3; column++) {
                final char symbol = shapeRow.length() > column ? shapeRow.charAt(column) : ' ';
                inputs.add(symbolMap.getOrDefault(symbol, null));
            }
        }
        return new RecipeView(
            this.itemFactory.displayNameForId(machine.id()),
                inputs,
            this.clickableReference(machine.id(), false),
                this.info(Material.CRAFTING_TABLE, "進階工作台", List.of("下方先墊鐵方塊", "上方放工作台後再照左側九宮格擺法")),
                "進階工作台（鐵方塊底座）",
                List.of(
                        "類型：進階工作台合成",
                        "結果：" + this.itemFactory.displayNameForId(machine.id()),
                        "結構：下方鐵方塊，上方工作台",
                        "左側九宮格就是實際放法"
                )
        );
    }

    private RecipeView machineRecipeView(final MachineRecipe recipe) {
        final List<ItemStack> inputs = new ArrayList<>();
        for (final String inputId : recipe.inputIds()) {
            if (inputs.size() >= 9) {
                break;
            }
            inputs.add(this.clickableReference(inputId, true));
        }
        while (inputs.size() < 9) {
            inputs.add(null);
        }
        return new RecipeView(
                this.itemFactory.displayNameForId(recipe.outputId()),
                inputs,
            this.clickableReference(recipe.outputId(), false),
            this.clickableReference(recipe.machineId(), false),
                this.itemFactory.displayNameForId(recipe.machineId()) + " • " + recipe.energyCost() + " EU",
                List.of(
                        "製作站：" + this.itemFactory.displayNameForId(recipe.machineId()),
                        "輸入：" + this.itemFactory.joinDisplayNames(recipe.inputIds(), " + "),
                    "耗能：" + recipe.energyCost() + " EU",
                    this.describeRecipeFlow(recipe)
                )
        );
    }

            private String describeRecipeFlow(final MachineRecipe recipe) {
            return this.itemFactory.joinDisplayNames(recipe.inputIds(), " + ") + " → "
                + this.itemFactory.displayNameForId(recipe.machineId()) + " → "
                + this.itemFactory.displayNameForId(recipe.outputId());
            }

    private ItemStack displayStack(final String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        final String normalized = id.trim().toLowerCase(Locale.ROOT);
        final String resolvedId;
        final String materialToken;
        if (normalized.startsWith("tech:")) {
            resolvedId = normalized.substring(5);
            materialToken = null;
        } else if (normalized.startsWith("machine:")) {
            resolvedId = normalized.substring(8);
            materialToken = null;
        } else if (normalized.startsWith("vanilla:")) {
            resolvedId = null;
            materialToken = normalized.substring(8);
        } else if (normalized.startsWith("minecraft:")) {
            resolvedId = null;
            materialToken = normalized.substring(10);
        } else {
            resolvedId = normalized;
            materialToken = normalized;
        }
        final TechItemDefinition item = resolvedId == null ? null : this.registry.getItem(resolvedId);
        if (item != null) {
            return this.itemFactory.tagPreviewClaim(this.itemFactory.buildTechItem(item), "item:" + item.id());
        }
        final MachineDefinition machine = resolvedId == null ? null : this.registry.getMachine(resolvedId);
        if (machine != null) {
            return this.itemFactory.tagPreviewClaim(this.itemFactory.buildMachineItem(machine), "machine:" + machine.id());
        }
        final String materialId = materialToken == null ? normalized : materialToken;
        final Material material = Material.matchMaterial(materialId.toUpperCase(Locale.ROOT));
        if (material == null || material == Material.AIR) {
            return this.info(Material.BARRIER, "未知材料", List.of(id));
        }
        final ItemStack stack = new ItemStack(this.itemFactory.safeItemMaterial(material));
        final ItemMeta meta = stack.getItemMeta();
        meta.displayName(this.itemFactory.warning(this.itemFactory.displayNameForId(materialId)));
        meta.lore(List.of(this.itemFactory.muted("原版材料")));
        stack.setItemMeta(meta);
        return this.itemFactory.tagPreviewClaim(stack, "material:" + materialId);
    }

    private ItemStack recipeBlankPane() {
        return new ItemStack(Material.AIR);
    }

    private ItemStack sectionPane(final Material material, final String title, final List<String> lines) {
        if (material != null && material.name().contains("GLASS_PANE")) {
            if ((title == null || title.isBlank()) && (lines == null || lines.isEmpty())) {
                return new ItemStack(Material.AIR);
            }
            return this.itemFactory.buildGuiPane(
                    Material.PAPER,
                    this.itemFactory.secondary(title),
                    this.itemFactory.mutedLore(lines),
                    false
            );
        }
        return this.itemFactory.buildGuiPane(
                material,
                this.itemFactory.secondary(title),
                this.itemFactory.mutedLore(lines),
                false
        );
    }

    private record RecipeView(String resultName,
                              List<ItemStack> inputs,
                              ItemStack result,
                              ItemStack station,
                              String stationLine,
                              List<String> detailLines) {
    }

    private void decorateMenuFrame(final Inventory inventory) {
        final ItemStack border = this.info(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        final int[] borderSlots = {
                0, 2, 6, 8,
                9, 17, 18, 26, 27, 35, 36, 44,
                46, 48, 50, 52
        };
        for (final int slot : borderSlots) {
            inventory.setItem(slot, border);
        }
        inventory.setItem(0, this.sectionPane(Material.GRAY_STAINED_GLASS_PANE, "←", List.of()));
        inventory.setItem(8, this.sectionPane(Material.GRAY_STAINED_GLASS_PANE, "→", List.of()));
        inventory.setItem(36, this.sectionPane(Material.GRAY_STAINED_GLASS_PANE, "◀", List.of()));
        inventory.setItem(44, this.sectionPane(Material.GRAY_STAINED_GLASS_PANE, "▶", List.of()));
    }

    private void fillEmptySlots(final Inventory inventory, final ItemStack filler) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            final ItemStack current = inventory.getItem(slot);
            if (current == null || current.getType() == Material.AIR) {
                inventory.setItem(slot, filler.clone());
            }
        }
    }
}
