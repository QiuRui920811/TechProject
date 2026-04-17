package com.rui.techproject.command;

import com.rui.techproject.TechMCPlugin;
import com.rui.techproject.service.TechRegistry;
import com.rui.techproject.util.ItemFactoryUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

public final class TechCommand implements CommandExecutor, TabCompleter {
    private final TechMCPlugin plugin;
    private final TechRegistry registry;
    private final ItemFactoryUtil itemFactory;

    public TechCommand(final TechMCPlugin plugin, final TechRegistry registry, final ItemFactoryUtil itemFactory) {
        this.plugin = plugin;
        this.registry = registry;
        this.itemFactory = itemFactory;
    }

    @Override
    public boolean onCommand(@NotNull final CommandSender sender,
                             @NotNull final Command command,
                             @NotNull final String label,
                             @NotNull final String[] args) {
        // 區域系統指令提前攔截
        if (args.length >= 1) {
            final String s = args[0].toLowerCase(Locale.ROOT);
            if (s.equals("tool") || s.equals("create") || s.equals("region") || (s.equals("set") && args.length >= 2 && args[1].equalsIgnoreCase("points"))) {
                return this.handleRegionCommands(sender, args);
            }
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("book")) {
            if (args.length >= 2 && args[1].equalsIgnoreCase("getall")) {
                if (!sender.hasPermission("techproject.admin")) {
                    sender.sendMessage(Component.text("缺少權限。", NamedTextColor.RED));
                    return true;
                }

                final Player target;
                if (args.length >= 3) {
                    target = Bukkit.getPlayerExact(args[2]);
                } else if (sender instanceof Player player) {
                    target = player;
                } else {
                    sender.sendMessage(Component.text("控制台需指定玩家。", NamedTextColor.RED));
                    return true;
                }

                if (target == null) {
                    sender.sendMessage(Component.text("找不到玩家。", NamedTextColor.RED));
                    return true;
                }

                target.getInventory().addItem(this.itemFactory.buildFullUnlockBook());
                sender.sendMessage(Component.text("已給予 " + target.getName() + "：全解鎖書", NamedTextColor.GREEN));
                if (sender != target) {
                    target.sendMessage(Component.text("你收到了一本全解鎖書。", NamedTextColor.GOLD));
                }
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("只有玩家可以開啟科技書。", NamedTextColor.RED));
                return true;
            }
            if (args.length >= 2 && args[1].equalsIgnoreCase("get")) {
                player.getInventory().addItem(this.itemFactory.buildTechBook());
                player.sendMessage(Component.text("已補發一本科技書。", NamedTextColor.GREEN));
                return true;
            }
            if (args.length >= 2 && args[1].equalsIgnoreCase("remember")) {
                final boolean newState = this.plugin.getTechBookService().toggleRememberPage(player.getUniqueId());
                if (newState) {
                    player.sendMessage(Component.text("✔ 已開啟「記住頁面」：下次打開科技書會回到上次瀏覽的頁面。", NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.text("✖ 已關閉「記住頁面」：打開科技書會固定回到首頁。", NamedTextColor.YELLOW));
                }
                return true;
            }
            if (args.length >= 3 && args[1].equalsIgnoreCase("open")) {
                // Reconstruct the action string from remaining args (action may contain spaces... but normally it doesn't)
                final StringBuilder actionBuilder = new StringBuilder(args[2]);
                for (int i = 3; i < args.length; i++) {
                    actionBuilder.append(' ').append(args[i]);
                }
                this.plugin.getTechBookService().handleAction(player, actionBuilder.toString());
                return true;
            }
            this.plugin.getTechBookService().openDefaultBook(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("list")) {
            sender.sendMessage(Component.text("科技資料總覽：" + this.registry.summaryLine(), NamedTextColor.YELLOW));
            return true;
        }

        if (args[0].equalsIgnoreCase("wrench")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("只有玩家可以取得科技扳手。", NamedTextColor.RED));
                return true;
            }
            if (args.length >= 2 && args[1].equalsIgnoreCase("get")) {
                player.getInventory().addItem(this.itemFactory.buildWrench());
                player.sendMessage(Component.text("已補發一把科技扳手。", NamedTextColor.GREEN));
                return true;
            }
            player.sendMessage(Component.text("用法：/tech wrench get", NamedTextColor.YELLOW));
            return true;
        }

        if (args[0].equalsIgnoreCase("stats")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("只有玩家可以查看科技進度。", NamedTextColor.RED));
                return true;
            }

            final var progress = this.plugin.getPlayerProgressService();
            final var uuid = player.getUniqueId();
            sender.sendMessage(Component.text("=== 科技進度統計 ===", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("已解鎖物品：" + progress.unlockedItemCount(uuid) + " / " + this.registry.allItems().size(), NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("已解鎖機器：" + progress.unlockedMachineCount(uuid) + " / " + this.registry.allMachines().size(), NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("已完成成就：" + progress.unlockedAchievementCount(uuid) + " / " + this.registry.allAchievements().size(), NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("研究等級：Lv." + progress.getTechLevel(uuid) + "  (" + progress.getXpIntoCurrentLevel(uuid) + " / " + progress.getXpForNextLevel(uuid) + ")", NamedTextColor.LIGHT_PURPLE));
            sender.sendMessage(Component.text("研究點數：可用 " + progress.getAvailableTechXp(uuid) + " / 累計 " + progress.getTechXpTotal(uuid) + " / 已花費 " + progress.getTechXpSpent(uuid), NamedTextColor.LIGHT_PURPLE));
            sender.sendMessage(Component.text("累計發電：" + progress.getStat(uuid, "energy_generated") + " EU", NamedTextColor.AQUA));
            sender.sendMessage(Component.text("最大在線機器：" + progress.getStat(uuid, "max_active_machines"), NamedTextColor.AQUA));
            sender.sendMessage(Component.text("收成作物：" + progress.getStat(uuid, "farm_harvested"), NamedTextColor.GREEN));
            sender.sendMessage(Component.text("回收物品：" + progress.getStat(uuid, "recycled_items"), NamedTextColor.GREEN));
            sender.sendMessage(Component.text("自動物流：" + progress.getStat(uuid, "items_transferred"), NamedTextColor.GREEN));
            return true;
        }

        if (args[0].equalsIgnoreCase("research")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("只有玩家可以開啟研究台。", NamedTextColor.RED));
                return true;
            }
            this.plugin.getTechBookService().openResearchDesk(player);
            return true;
        }

        // ── 新系統指令：/tech skill | quest | top | event ──
        if (args[0].equalsIgnoreCase("skill") || args[0].equalsIgnoreCase("skills")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("只有玩家可以開啟技能選單。", NamedTextColor.RED));
                return true;
            }
            if (this.plugin.getSkillService() == null) {
                sender.sendMessage(Component.text("技能系統尚未初始化。", NamedTextColor.RED));
                return true;
            }
            this.plugin.getSkillService().openSkillMenu(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("talent") || args[0].equalsIgnoreCase("talents")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("只有玩家可以開啟天賦樹。", NamedTextColor.RED));
                return true;
            }
            if (this.plugin.getTalentGuiService() == null) {
                sender.sendMessage(Component.text("天賦系統尚未初始化。", NamedTextColor.RED));
                return true;
            }
            // /tech talent                  → 開 COMBAT 樹（預設）
            // /tech talent <skillId>        → 開指定樹
            // /tech talent reset <skillId>  → 重置指定樹（消耗科技經驗）
            if (args.length >= 2 && args[1].equalsIgnoreCase("reset")) {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("用法：/tech talent reset <skillId>",
                            NamedTextColor.RED));
                    return true;
                }
                final com.rui.techproject.service.SkillService.Skill resetSkill =
                        com.rui.techproject.service.SkillService.Skill.byId(args[2]);
                if (resetSkill == null) {
                    sender.sendMessage(Component.text("未知的技能：" + args[2], NamedTextColor.RED));
                    return true;
                }
                if (this.plugin.getTalentService() != null
                        && this.plugin.getTalentService().resetTree(player, resetSkill)) {
                    this.plugin.getTalentGuiService().openTree(player, resetSkill);
                }
                return true;
            }
            final com.rui.techproject.service.SkillService.Skill openSkill;
            if (args.length >= 2) {
                openSkill = com.rui.techproject.service.SkillService.Skill.byId(args[1]);
                if (openSkill == null) {
                    sender.sendMessage(Component.text("未知的技能：" + args[1], NamedTextColor.RED));
                    return true;
                }
            } else {
                openSkill = com.rui.techproject.service.SkillService.Skill.COMBAT;
            }
            this.plugin.getTalentGuiService().openTree(player, openSkill);
            return true;
        }
        if (args[0].equalsIgnoreCase("talentpoint") || args[0].equalsIgnoreCase("tp")) {
            // /tech talentpoint <player> <skill|all> <amount>
            // 管理員發放天賦點。skill 可填 combat/exploration/gathering/engineering/research/resonance/all。
            if (!sender.hasPermission("techproject.admin")) {
                sender.sendMessage(Component.text("缺少權限。", NamedTextColor.RED));
                return true;
            }
            if (this.plugin.getTalentService() == null) {
                sender.sendMessage(Component.text("天賦系統尚未初始化。", NamedTextColor.RED));
                return true;
            }
            if (args.length < 4) {
                sender.sendMessage(Component.text(
                        "用法：/tech talentpoint <玩家> <skill|all> <數量>",
                        NamedTextColor.RED));
                return true;
            }
            final Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(Component.text("找不到線上玩家：" + args[1], NamedTextColor.RED));
                return true;
            }
            final int amount;
            try {
                amount = Integer.parseInt(args[3]);
            } catch (final NumberFormatException ignored) {
                sender.sendMessage(Component.text("數量必須是整數。", NamedTextColor.RED));
                return true;
            }
            if (amount <= 0) {
                sender.sendMessage(Component.text("數量必須大於 0（要扣點請用 /tech talent reset）。",
                        NamedTextColor.RED));
                return true;
            }
            final String skillArg = args[2];
            if ("all".equalsIgnoreCase(skillArg) || "*".equals(skillArg)) {
                for (final com.rui.techproject.service.SkillService.Skill s
                        : com.rui.techproject.service.SkillService.Skill.values()) {
                    this.plugin.getTalentService().grantPoint(target, s, amount);
                }
                sender.sendMessage(Component.text(
                        "已為 " + target.getName() + " 每棵樹 +" + amount + " 天賦點（共 6 棵）",
                        NamedTextColor.GREEN));
            } else {
                final com.rui.techproject.service.SkillService.Skill skill =
                        com.rui.techproject.service.SkillService.Skill.byId(skillArg);
                if (skill == null) {
                    sender.sendMessage(Component.text("未知的技能：" + skillArg
                            + "（可用：combat/exploration/gathering/engineering/research/resonance/all）",
                            NamedTextColor.RED));
                    return true;
                }
                this.plugin.getTalentService().grantPoint(target, skill, amount);
                sender.sendMessage(Component.text(
                        "已為 " + target.getName() + " 的 " + skill.displayName
                                + " 樹 +" + amount + " 天賦點",
                        NamedTextColor.GREEN));
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("geo")) {
            // /tech geo <player> — 發放地質掃描儀
            if (!sender.hasPermission("techproject.admin")) {
                sender.sendMessage(Component.text("缺少權限。", NamedTextColor.RED));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(Component.text("用法：/tech geo <玩家>", NamedTextColor.RED));
                return true;
            }
            final Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(Component.text("找不到線上玩家：" + args[1], NamedTextColor.RED));
                return true;
            }
            final org.bukkit.inventory.ItemStack scanner = this.plugin.getItemFactory().buildGeoScanner();
            final var overflow = target.getInventory().addItem(scanner);
            for (final org.bukkit.inventory.ItemStack leftover : overflow.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), leftover);
            }
            target.sendMessage(Component.text("◆ 你獲得了一把地質掃描儀。", NamedTextColor.AQUA));
            sender.sendMessage(Component.text(
                    "已發放地質掃描儀給 " + target.getName(), NamedTextColor.GREEN));
            return true;
        }
        if (args[0].equalsIgnoreCase("hazmat")) {
            // /tech hazmat <player> — 發放 Hazmat 四件套
            if (!sender.hasPermission("techproject.admin")) {
                sender.sendMessage(Component.text("缺少權限。", NamedTextColor.RED));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(Component.text("用法：/tech hazmat <玩家>", NamedTextColor.RED));
                return true;
            }
            final Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(Component.text("找不到線上玩家：" + args[1], NamedTextColor.RED));
                return true;
            }
            final com.rui.techproject.util.ItemFactoryUtil factory = this.plugin.getItemFactory();
            for (final String slotId : new String[] {
                    com.rui.techproject.service.RadiationService.HELMET_ID,
                    com.rui.techproject.service.RadiationService.CHESTPLATE_ID,
                    com.rui.techproject.service.RadiationService.LEGGINGS_ID,
                    com.rui.techproject.service.RadiationService.BOOTS_ID }) {
                final org.bukkit.inventory.ItemStack piece = factory.buildHazmatPiece(slotId);
                final var overflow = target.getInventory().addItem(piece);
                for (final org.bukkit.inventory.ItemStack leftover : overflow.values()) {
                    target.getWorld().dropItemNaturally(target.getLocation(), leftover);
                }
            }
            target.sendMessage(Component.text("☢ 你獲得了一整套 Hazmat 防護衣。", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text(
                    "已發放 Hazmat 四件套給 " + target.getName(), NamedTextColor.GREEN));
            return true;
        }
        if (args[0].equalsIgnoreCase("quest") || args[0].equalsIgnoreCase("quests")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("只有玩家可以開啟任務選單。", NamedTextColor.RED));
                return true;
            }
            if (this.plugin.getDailyQuestService() == null) {
                sender.sendMessage(Component.text("任務系統尚未初始化。", NamedTextColor.RED));
                return true;
            }
            this.plugin.getDailyQuestService().openQuestMenu(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("top") || args[0].equalsIgnoreCase("leaderboard")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("只有玩家可以查看排行榜。", NamedTextColor.RED));
                return true;
            }
            if (this.plugin.getLeaderboardService() == null) {
                sender.sendMessage(Component.text("排行榜尚未初始化。", NamedTextColor.RED));
                return true;
            }
            this.plugin.getLeaderboardService().openLeaderboardMenu(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("xp")) {
            final var progress = this.plugin.getPlayerProgressService();
            if (args.length == 1) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("控制台請使用：/tech xp add <數量> <玩家>", NamedTextColor.RED));
                    return true;
                }
                final var uuid = player.getUniqueId();
                sender.sendMessage(Component.text("研究等級：Lv." + progress.getTechLevel(uuid), NamedTextColor.LIGHT_PURPLE));
                sender.sendMessage(Component.text("研究點數：可用 " + progress.getAvailableTechXp(uuid) + " / 累計 " + progress.getTechXpTotal(uuid) + " / 已花費 " + progress.getTechXpSpent(uuid), NamedTextColor.LIGHT_PURPLE));
                sender.sendMessage(Component.text("下級需求：" + progress.getXpIntoCurrentLevel(uuid) + " / " + progress.getXpForNextLevel(uuid), NamedTextColor.LIGHT_PURPLE));
                return true;
            }
            if (!args[1].equalsIgnoreCase("add")) {
                sender.sendMessage(Component.text("用法：/tech xp add <數量> [玩家]", NamedTextColor.RED));
                return true;
            }
            if (!sender.hasPermission("techproject.admin")) {
                sender.sendMessage(Component.text("缺少權限。", NamedTextColor.RED));
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(Component.text("用法：/tech xp add <數量> [玩家]", NamedTextColor.RED));
                return true;
            }
            final long amount;
            try {
                amount = Long.parseLong(args[2]);
            } catch (final NumberFormatException ignored) {
                sender.sendMessage(Component.text("研究點數必須是整數。", NamedTextColor.RED));
                return true;
            }
            if (amount <= 0L) {
                sender.sendMessage(Component.text("研究點數必須大於 0。", NamedTextColor.RED));
                return true;
            }

            final Player target;
            if (args.length >= 4) {
                target = Bukkit.getPlayerExact(args[3]);
            } else if (sender instanceof Player player) {
                target = player;
            } else {
                sender.sendMessage(Component.text("控制台需指定玩家。", NamedTextColor.RED));
                return true;
            }
            if (target == null) {
                sender.sendMessage(Component.text("找不到玩家。", NamedTextColor.RED));
                return true;
            }

            final long total = progress.addTechXp(target.getUniqueId(), amount);
            sender.sendMessage(Component.text("已為 " + target.getName() + " 增加研究點數：+" + amount, NamedTextColor.GREEN));
            target.sendMessage(Component.text("你獲得了研究點數：+" + amount + "（目前可用 " + progress.getAvailableTechXp(target.getUniqueId()) + "）", NamedTextColor.LIGHT_PURPLE));
            sender.sendMessage(Component.text("累計研究點數：" + total, NamedTextColor.LIGHT_PURPLE));
            return true;
        }

        if (args[0].equalsIgnoreCase("achievements")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("只有玩家可以查看成就。", NamedTextColor.RED));
                return true;
            }
            this.plugin.getAchievementGuiService().openAchievementGui(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("title")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("只有玩家可以管理稱號。", NamedTextColor.RED));
                return true;
            }
            final var titleService = this.plugin.getTitleService();
            if (titleService == null) {
                sender.sendMessage(Component.text("稱號系統尚未載入。", NamedTextColor.RED));
                return true;
            }
            if (args.length >= 2 && args[1].equalsIgnoreCase("clear")) {
                titleService.clearTitle(player.getUniqueId());
                player.sendMessage(Component.text("✖ 已取消稱號。", NamedTextColor.GRAY));
                return true;
            }
            if (args.length >= 2 && args[1].equalsIgnoreCase("list")) {
                final var uuid = player.getUniqueId();
                player.sendMessage(Component.text("=== 已解鎖的稱號 ===", NamedTextColor.GOLD));
                int count = 0;
                for (final String titleId : titleService.allTitleIds()) {
                    if (this.plugin.getPlayerProgressService().hasAchievementUnlocked(uuid, titleId)) {
                        final String display = titleService.getTitleDisplay(titleId);
                        final boolean equipped = titleId.equals(this.plugin.getPlayerProgressService().getSelectedTitle(uuid));
                        player.sendMessage(Component.text((equipped ? " ★ " : "   ") + display + " §7(" + titleId + ")", NamedTextColor.YELLOW));
                        count++;
                    }
                }
                player.sendMessage(Component.text("共 " + count + " / " + titleService.totalTitleCount() + " 個稱號已解鎖。", NamedTextColor.LIGHT_PURPLE));
                return true;
            }
            if (args.length >= 2) {
                final String titleId = args[1].toLowerCase(Locale.ROOT);
                if (titleService.setTitle(player.getUniqueId(), titleId)) {
                    player.sendMessage(Component.text("✔ 已裝備稱號：" + titleService.getTitleDisplay(titleId), NamedTextColor.GOLD));
                } else {
                    player.sendMessage(Component.text("找不到該稱號或尚未解鎖。", NamedTextColor.RED));
                }
                return true;
            }
            final String currentTitle = titleService.getPlayerTitle(player.getUniqueId());
            if (currentTitle.isBlank()) {
                player.sendMessage(Component.text("目前沒有裝備稱號。使用 /tech title list 查看可用稱號。", NamedTextColor.YELLOW));
            } else {
                player.sendMessage(Component.text("目前稱號：" + currentTitle, NamedTextColor.GOLD));
                player.sendMessage(Component.text("使用 /tech title clear 取消，/tech title list 查看全部。", NamedTextColor.GRAY));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("search")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("只有玩家可以搜尋。", NamedTextColor.RED));
                return true;
            }
            if (args.length < 2) {
                // 沒給關鍵字 → 打開鐵砧搜尋 GUI
                this.plugin.getItemSearchService().openAnvilGui(player);
                return true;
            }
            final StringBuilder query = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                if (i > 1) query.append(' ');
                query.append(args[i]);
            }
            // 有關鍵字 → 直接打開圖鑑搜尋結果
            this.plugin.getItemSearchService().openSearch(player, query.toString());
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("techproject.admin")) {
                sender.sendMessage(Component.text("缺少權限。", NamedTextColor.RED));
                return true;
            }
            this.plugin.performHotReload(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("planet")) {
            if (!sender.hasPermission("techproject.admin")) {
                sender.sendMessage(Component.text("你沒有權限使用星球指令。", NamedTextColor.RED));
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("只有玩家可以使用星球傳送。", NamedTextColor.RED));
                return true;
            }
            if (args.length >= 2 && args[1].equalsIgnoreCase("locateruin")) {
                final org.bukkit.Location ruinLoc = this.plugin.getPlanetService().locateRuinCore(player);
                if (ruinLoc == null) {
                    player.sendMessage(Component.text("你目前不在星球世界，無法定位遺跡。", NamedTextColor.RED));
                    return true;
                }
                final int x = ruinLoc.getBlockX();
                final int y = ruinLoc.getBlockY();
                final int z = ruinLoc.getBlockZ();
                final double distance = player.getLocation().distance(ruinLoc);
                player.sendMessage(Component.text("▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂", NamedTextColor.DARK_AQUA));
                player.sendMessage(Component.text("🗺 遺跡核心位置：" + x + ", " + y + ", " + z, NamedTextColor.AQUA));
                player.sendMessage(Component.text("📏 距離你約 " + String.format("%.0f", distance) + " 格", NamedTextColor.YELLOW));
                player.sendMessage(Component.text("💡 遺跡核心周圍有海燈籠發光標示，非常好認。", NamedTextColor.GRAY));
                player.sendMessage(Component.text("▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂", NamedTextColor.DARK_AQUA));
                return true;
            }
            if (args.length >= 2 && args[1].equalsIgnoreCase("regenerate")) {
                if (this.plugin.getPlanetService().regenerateSpawnStructures(player)) {
                    player.sendMessage(Component.text("✔ 已重新生成此星球的出生點結構（降落台 + 遺跡核心 + 尖塔等）。", NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.text("你目前不在星球世界。", NamedTextColor.RED));
                }
                return true;
            }
            if (args.length >= 2 && args[1].equalsIgnoreCase("info")) {
                sender.sendMessage(Component.text("=== 星球資訊 ===", NamedTextColor.AQUA));
                for (final String line : this.plugin.getPlanetService().planetInfoLines()) {
                    sender.sendMessage(Component.text(line, NamedTextColor.GOLD));
                }
                return true;
            }
            if (args.length >= 2 && args[1].equalsIgnoreCase("debug")) {
                for (final String line : this.plugin.getPlanetService().planetDebugLines(player)) {
                    sender.sendMessage(Component.text(line));
                }
                return true;
            }
            if (args.length >= 2 && args[1].equalsIgnoreCase("spawntest")) {
                for (final String line : this.plugin.getPlanetService().planetSpawnTestLines(player)) {
                    sender.sendMessage(Component.text(line));
                }
                return true;
            }
            final String target = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "aurelia";
            if (!this.plugin.getPlanetService().teleportToPlanet(player, target)) {
                final String message = this.plugin.getPlanetService().isWorldCreationUnsupported()
                        ? "星球世界尚未就緒；目前伺服器不支援熱載入時動態建世界，請完整重啟伺服器後再試。"
                        : "星球世界尚未就緒。";
                sender.sendMessage(Component.text(message, NamedTextColor.RED));
                return true;
            }
            sender.sendMessage(Component.text("已傳送至星球：" + this.plugin.getPlanetService().planetDisplayName(target), NamedTextColor.GREEN));
            return true;
        }

        if (args[0].equalsIgnoreCase("maze")) {
            if (!sender.hasPermission("techproject.admin")) {
                sender.sendMessage(Component.text("缺少權限。", NamedTextColor.RED));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(Component.text("用法：/tech maze <opengate|closegate>", NamedTextColor.RED));
                return true;
            }
            if (args[1].equalsIgnoreCase("opengate")) {
                this.plugin.getMazeService().forceOpenGates();
                sender.sendMessage(Component.text("✔ 已強制開啟 Glade 大門（30 秒後自動關閉）。", NamedTextColor.GREEN));
                return true;
            }
            if (args[1].equalsIgnoreCase("closegate")) {
                this.plugin.getMazeService().forceCloseGates();
                sender.sendMessage(Component.text("✔ 已強制關閉 Glade 大門。", NamedTextColor.GREEN));
                return true;
            }
            sender.sendMessage(Component.text("未知子指令。用法：/tech maze <opengate|closegate>", NamedTextColor.RED));
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("techproject.admin")) {
                sender.sendMessage(Component.text("缺少權限。", NamedTextColor.RED));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(Component.text("用法：/tech give <物品代碼> [玩家]", NamedTextColor.RED));
                return true;
            }

            final String id = args[1].toLowerCase(Locale.ROOT);
            final Player target;
            if (args.length >= 3) {
                target = Bukkit.getPlayerExact(args[2]);
            } else if (sender instanceof Player player) {
                target = player;
            } else {
                sender.sendMessage(Component.text("控制台需指定玩家。", NamedTextColor.RED));
                return true;
            }

            if (target == null) {
                sender.sendMessage(Component.text("找不到玩家。", NamedTextColor.RED));
                return true;
            }

            if (this.registry.getItem(id) != null) {
                target.getInventory().addItem(this.itemFactory.buildTechItem(this.registry.getItem(id)));
            } else if (this.registry.getMachine(id) != null) {
                target.getInventory().addItem(this.itemFactory.buildMachineItem(this.registry.getMachine(id)));
            } else {
                sender.sendMessage(Component.text("未知 ID：" + id, NamedTextColor.RED));
                return true;
            }
            sender.sendMessage(Component.text("已給予 " + target.getName() + "：" + id, NamedTextColor.GREEN));
            return true;
        }

        if (args[0].equalsIgnoreCase("trust")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("只有玩家可以使用信任指令。", NamedTextColor.RED));
                return true;
            }
            if (args.length < 2) {
                player.sendMessage(Component.text("用法：/tech trust <玩家名稱>　—　信任該玩家操作你所有的機器", NamedTextColor.YELLOW));
                return true;
            }
            @SuppressWarnings("deprecation")
            final OfflinePlayer trusted = Bukkit.getOfflinePlayer(args[1]);
            if (!trusted.hasPlayedBefore() && !trusted.isOnline()) {
                player.sendMessage(Component.text("找不到玩家：" + args[1], NamedTextColor.RED));
                return true;
            }
            if (trusted.getUniqueId().equals(player.getUniqueId())) {
                player.sendMessage(Component.text("你不需要信任自己。", NamedTextColor.YELLOW));
                return true;
            }
            if (!this.plugin.getMachineService().addGlobalTrust(player.getUniqueId(), trusted.getUniqueId())) {
                player.sendMessage(Component.text(trusted.getName() + " 已經在你的信任清單中了。", NamedTextColor.YELLOW));
            } else {
                player.sendMessage(Component.text("✔ 已將 " + trusted.getName() + " 加入信任清單，你的所有機器（含未來放置的）都會開放給他操作。", NamedTextColor.GREEN));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("untrust")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("只有玩家可以使用信任指令。", NamedTextColor.RED));
                return true;
            }
            if (args.length < 2) {
                player.sendMessage(Component.text("用法：/tech untrust <玩家名稱>　—　取消信任該玩家", NamedTextColor.YELLOW));
                return true;
            }
            @SuppressWarnings("deprecation")
            final OfflinePlayer trusted = Bukkit.getOfflinePlayer(args[1]);
            if (!this.plugin.getMachineService().removeGlobalTrust(player.getUniqueId(), trusted.getUniqueId())) {
                player.sendMessage(Component.text((trusted.getName() != null ? trusted.getName() : args[1]) + " 不在你的信任清單中。", NamedTextColor.YELLOW));
            } else {
                player.sendMessage(Component.text("✔ 已將 " + (trusted.getName() != null ? trusted.getName() : args[1]) + " 從信任清單移除，他將無法再操作你的機器。", NamedTextColor.GREEN));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("trustlist")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("只有玩家可以查看信任清單。", NamedTextColor.RED));
                return true;
            }
            final java.util.Set<UUID> trusted = this.plugin.getMachineService().getGlobalTrustedPlayers(player.getUniqueId());
            if (trusted.isEmpty()) {
                player.sendMessage(Component.text("你尚未信任任何玩家。", NamedTextColor.YELLOW));
                return true;
            }
            player.sendMessage(Component.text("=== 你的信任清單 ===", NamedTextColor.GOLD));
            for (final UUID uuid : trusted) {
                final OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                final String name = op.getName() != null ? op.getName() : uuid.toString();
                player.sendMessage(Component.text(" - " + name, NamedTextColor.AQUA));
            }
            player.sendMessage(Component.text("共 " + trusted.size() + " 位信任玩家，你的所有機器都會開放給以上玩家操作。", NamedTextColor.GRAY));
            return true;
        }

        if (args[0].equalsIgnoreCase("cleandisplay")) {
            if (!sender.hasPermission("techproject.admin")) {
                sender.sendMessage(Component.text("缺少權限。", NamedTextColor.RED));
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("只有玩家可以使用此指令。", NamedTextColor.RED));
                return true;
            }
            final int radius = args.length >= 2 ? Math.max(1, Math.min(200, Integer.parseInt(args[1]))) : 16;
            final World world = player.getWorld();
            int removed = 0;
            for (final Entity entity : world.getNearbyEntities(player.getLocation(), radius, radius, radius)) {
                if (entity instanceof ItemDisplay || entity instanceof org.bukkit.entity.TextDisplay) {
                    entity.remove();
                    removed++;
                }
            }
            sender.sendMessage(Component.text("已清除半徑 " + radius + " 內 " + removed + " 個展示實體。", NamedTextColor.GREEN));
            return true;
        }

        if (args[0].equalsIgnoreCase("debugtree")) {
            if (!sender.hasPermission("techproject.admin")) {
                sender.sendMessage(Component.text("缺少權限。", NamedTextColor.RED));
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("只有玩家可以使用此指令。", NamedTextColor.RED));
                return true;
            }
            final Block target = player.getTargetBlockExact(8);
            if (target == null) {
                sender.sendMessage(Component.text("請對準一個方塊（距離 8 格以內）", NamedTextColor.RED));
                return true;
            }
            sender.sendMessage(Component.text("=== 樹木偵測 Debug ===", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("目標方塊: " + target.getType() + " @" + target.getX() + "," + target.getY() + "," + target.getZ(), NamedTextColor.GRAY));
            final var lines = this.plugin.getMachineService().debugTreeDetection(target);
            for (final String line : lines) {
                sender.sendMessage(Component.text(line.replace("§a", "").replace("§c", "").replace("§7", ""),
                        line.startsWith("§a") ? NamedTextColor.GREEN : line.startsWith("§c") ? NamedTextColor.RED : NamedTextColor.GRAY));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("android")) {
            // /tech android program <add|set|remove|clear|list|help> [args...]
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("只有玩家可以使用此指令。", NamedTextColor.RED));
                return true;
            }
            if (args.length < 2 || !args[1].equalsIgnoreCase("program")) {
                sender.sendMessage(Component.text("用法：/tech android program <add|set|remove|clear|list|help> ...", NamedTextColor.YELLOW));
                return true;
            }
            final String action = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "list";
            if (action.equals("help")) {
                sender.sendMessage(Component.text("=== 自訂安卓程式 ===", NamedTextColor.GOLD));
                sender.sendMessage(Component.text(com.rui.techproject.service.AndroidProgrammingService.helpText(), NamedTextColor.AQUA));
                sender.sendMessage(Component.text("對準安卓工作站方塊後輸入：", NamedTextColor.GRAY));
                sender.sendMessage(Component.text("  /tech android program list", NamedTextColor.GRAY));
                sender.sendMessage(Component.text("  /tech android program add MOVE N", NamedTextColor.GRAY));
                sender.sendMessage(Component.text("  /tech android program add HARVEST", NamedTextColor.GRAY));
                sender.sendMessage(Component.text("  /tech android program set <行> <指令...>", NamedTextColor.GRAY));
                sender.sendMessage(Component.text("  /tech android program remove <行>", NamedTextColor.GRAY));
                sender.sendMessage(Component.text("  /tech android program clear", NamedTextColor.GRAY));
                sender.sendMessage(Component.text("執行時把「安卓自訂程序」放入工作站程序槽。", NamedTextColor.GRAY));
                return true;
            }
            final Block looked = player.getTargetBlockExact(8);
            if (looked == null) {
                sender.sendMessage(Component.text("請對準一個安卓工作站方塊（8 格內）。", NamedTextColor.RED));
                return true;
            }
            final var placed = this.plugin.getMachineService().placedMachineAt(looked);
            if (placed == null || !"android_station".equalsIgnoreCase(placed.machineId())) {
                sender.sendMessage(Component.text("所指的方塊不是安卓工作站。", NamedTextColor.RED));
                return true;
            }
            if (!placed.owner().equals(player.getUniqueId()) && !sender.hasPermission("techproject.admin")) {
                sender.sendMessage(Component.text("這不是你的安卓工作站。", NamedTextColor.RED));
                return true;
            }
            final var prog = this.plugin.getAndroidProgrammingService();
            if (prog == null) {
                sender.sendMessage(Component.text("自訂程式服務未啟用。", NamedTextColor.RED));
                return true;
            }
            final var key = placed.locationKey();
            switch (action) {
                case "list" -> {
                    final var lines = prog.getProgram(key);
                    sender.sendMessage(Component.text("=== 自訂程式（" + lines.size() + "/" + com.rui.techproject.service.AndroidProgrammingService.MAX_LINES + "） ===", NamedTextColor.GOLD));
                    if (lines.isEmpty()) {
                        sender.sendMessage(Component.text("（空） 使用 /tech android program add 新增指令。", NamedTextColor.GRAY));
                    } else {
                        for (int i = 0; i < lines.size(); i++) {
                            sender.sendMessage(Component.text(String.format("%02d: %s", i, lines.get(i)), NamedTextColor.AQUA));
                        }
                    }
                }
                case "add" -> {
                    if (args.length < 4) {
                        sender.sendMessage(Component.text("用法：/tech android program add <指令...>", NamedTextColor.RED));
                        return true;
                    }
                    final StringBuilder sb = new StringBuilder();
                    for (int i = 3; i < args.length; i++) {
                        if (i > 3) sb.append(' ');
                        sb.append(args[i]);
                    }
                    if (prog.addLine(key, sb.toString())) {
                        sender.sendMessage(Component.text("✔ 已新增：" + com.rui.techproject.service.AndroidProgrammingService.normalizeLine(sb.toString()), NamedTextColor.GREEN));
                    } else {
                        sender.sendMessage(Component.text("✖ 指令無效或程式已滿。" + com.rui.techproject.service.AndroidProgrammingService.helpText(), NamedTextColor.RED));
                    }
                }
                case "set" -> {
                    if (args.length < 5) {
                        sender.sendMessage(Component.text("用法：/tech android program set <行> <指令...>", NamedTextColor.RED));
                        return true;
                    }
                    try {
                        final int idx = Integer.parseInt(args[3]);
                        final StringBuilder sb = new StringBuilder();
                        for (int i = 4; i < args.length; i++) {
                            if (i > 4) sb.append(' ');
                            sb.append(args[i]);
                        }
                        if (prog.setLine(key, idx, sb.toString())) {
                            sender.sendMessage(Component.text("✔ 第 " + idx + " 行已更新。", NamedTextColor.GREEN));
                        } else {
                            sender.sendMessage(Component.text("✖ 行號超出範圍或指令無效。", NamedTextColor.RED));
                        }
                    } catch (final NumberFormatException ex) {
                        sender.sendMessage(Component.text("行號必須是整數。", NamedTextColor.RED));
                    }
                }
                case "remove" -> {
                    if (args.length < 4) {
                        sender.sendMessage(Component.text("用法：/tech android program remove <行>", NamedTextColor.RED));
                        return true;
                    }
                    try {
                        final int idx = Integer.parseInt(args[3]);
                        if (prog.removeLine(key, idx)) {
                            sender.sendMessage(Component.text("✔ 第 " + idx + " 行已移除。", NamedTextColor.GREEN));
                        } else {
                            sender.sendMessage(Component.text("✖ 行號超出範圍。", NamedTextColor.RED));
                        }
                    } catch (final NumberFormatException ex) {
                        sender.sendMessage(Component.text("行號必須是整數。", NamedTextColor.RED));
                    }
                }
                case "clear" -> {
                    prog.clear(key);
                    sender.sendMessage(Component.text("✔ 已清空程式。", NamedTextColor.GREEN));
                }
                default -> sender.sendMessage(Component.text("未知動作：" + action + "（試試 help）", NamedTextColor.RED));
            }
            return true;
        }

        sender.sendMessage(Component.text("未知子命令。", NamedTextColor.RED));
        return true;
    }

    // ══════════════════ 區域系統指令 ══════════════════

    private boolean handleRegionCommands(final CommandSender sender, final String[] args) {
        final String sub = args[0].toLowerCase(Locale.ROOT);

        // /tech tool — 給予選取工具
        if (sub.equals("tool")) {
            if (!sender.hasPermission("techproject.admin")) {
                sender.sendMessage(Component.text("缺少權限。", NamedTextColor.RED));
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("只有玩家可以使用。", NamedTextColor.RED));
                return true;
            }
            final org.bukkit.inventory.ItemStack wand = new org.bukkit.inventory.ItemStack(Material.BLAZE_ROD);
            final org.bukkit.inventory.meta.ItemMeta meta = wand.getItemMeta();
            meta.displayName(Component.text("§6區域選取工具"));
            meta.getPersistentDataContainer().set(
                    new org.bukkit.NamespacedKey(this.plugin, "region_wand"),
                    org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
            wand.setItemMeta(meta);
            player.getInventory().addItem(wand);
            player.sendMessage(Component.text("✔ 已給予區域選取工具（左鍵=位置1，右鍵=位置2）", NamedTextColor.GREEN));
            return true;
        }

        // /tech create <id>
        if (sub.equals("create")) {
            if (!sender.hasPermission("techproject.admin")) {
                sender.sendMessage(Component.text("缺少權限。", NamedTextColor.RED));
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("只有玩家可以使用。", NamedTextColor.RED));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(Component.text("用法: /tech create <區域ID>", NamedTextColor.RED));
                return true;
            }
            final var regionService = this.plugin.getRegionService();
            if (!regionService.hasSelection(player)) {
                sender.sendMessage(Component.text("請先用選取工具點選兩個位置。", NamedTextColor.RED));
                return true;
            }
            if (regionService.createRegion(player, args[1])) {
                sender.sendMessage(Component.text("✔ 區域 '" + args[1] + "' 已建立。", NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("區域 ID 已存在。", NamedTextColor.RED));
            }
            return true;
        }

        // /tech region <id> action <action> | /tech region <id> delete
        if (sub.equals("region")) {
            if (!sender.hasPermission("techproject.admin")) {
                sender.sendMessage(Component.text("缺少權限。", NamedTextColor.RED));
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(Component.text("用法: /tech region <ID> action <rtp> | /tech region <ID> delete", NamedTextColor.RED));
                return true;
            }
            final String regionId = args[1];
            final String action = args[2].toLowerCase(Locale.ROOT);
            if (action.equals("delete")) {
                if (this.plugin.getRegionService().deleteRegion(regionId)) {
                    sender.sendMessage(Component.text("✔ 區域 '" + regionId + "' 已刪除。", NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("找不到區域。", NamedTextColor.RED));
                }
                return true;
            }
            if (action.equals("action") && args.length >= 4) {
                if (this.plugin.getRegionService().setAction(regionId, args[3])) {
                    sender.sendMessage(Component.text("✔ 區域 '" + regionId + "' 動作設為 '" + args[3] + "'。", NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("找不到區域。", NamedTextColor.RED));
                }
                return true;
            }
            sender.sendMessage(Component.text("用法: /tech region <ID> action <rtp> | delete", NamedTextColor.RED));
            return true;
        }

        // /tech set points <regionId>
        if (sub.equals("set") && args.length >= 3 && args[1].equalsIgnoreCase("points")) {
            if (!sender.hasPermission("techproject.admin")) {
                sender.sendMessage(Component.text("缺少權限。", NamedTextColor.RED));
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("只有玩家可以使用。", NamedTextColor.RED));
                return true;
            }
            if (this.plugin.getRegionService().setSpawnPoint(args[2], player.getLocation())) {
                sender.sendMessage(Component.text("✔ 區域 '" + args[2] + "' 傳送點已設定為你的位置。", NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("找不到區域。", NamedTextColor.RED));
            }
            return true;
        }

        return false;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull final CommandSender sender,
                                                @NotNull final Command command,
                                                @NotNull final String alias,
                                                @NotNull final String[] args) {
        if (args.length == 1) {
            final List<String> base = new ArrayList<>(List.of("book", "wrench", "research", "list", "stats", "achievements", "title", "search", "trust", "untrust", "trustlist", "skill", "talent", "quest", "top", "event", "android"));
            if (sender.hasPermission("techproject.admin")) {
                base.addAll(List.of("planet", "xp", "give", "talentpoint", "hazmat", "geo", "reload", "cleandisplay", "debugtree", "tool", "create", "region", "set"));
            }
            return base;
        }
        if (args[0].equalsIgnoreCase("talentpoint") || args[0].equalsIgnoreCase("tp")) {
            if (!sender.hasPermission("techproject.admin")) return List.of();
            if (args.length == 2) {
                final List<String> names = new ArrayList<>();
                for (final Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
                return names;
            }
            if (args.length == 3) {
                return List.of("combat", "exploration", "gathering", "engineering", "research", "resonance", "all");
            }
            if (args.length == 4) {
                return List.of("1", "5", "10", "43", "50");
            }
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("event") && sender.hasPermission("techproject.admin")) {
            return List.of("HUNTER_WAVE", "TREASURE_CHEST", "RESONANCE", "RIFT_ELITE", "SILENT_GUARDIAN");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("hazmat") && sender.hasPermission("techproject.admin")) {
            final List<String> names = new ArrayList<>();
            for (final Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return names;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("geo") && sender.hasPermission("techproject.admin")) {
            final List<String> names = new ArrayList<>();
            for (final Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return names;
        }
        if (args[0].equalsIgnoreCase("android")) {
            if (args.length == 2) return List.of("program");
            if (args.length == 3 && args[1].equalsIgnoreCase("program")) {
                return List.of("list", "add", "set", "remove", "clear", "help");
            }
            if (args.length == 4 && args[1].equalsIgnoreCase("program")
                    && (args[2].equalsIgnoreCase("add") || args[2].equalsIgnoreCase("set"))) {
                return List.of("MOVE", "HARVEST", "CHOP", "ATTACK", "SALVAGE", "WAIT", "RESET", "JUMP");
            }
            if (args.length == 5 && args[1].equalsIgnoreCase("program")
                    && (args[2].equalsIgnoreCase("add") || (args[2].equalsIgnoreCase("set") && args.length == 5))
                    && args[args.length - 2].equalsIgnoreCase("MOVE")) {
                return List.of("N", "E", "S", "W", "U", "D");
            }
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("wrench")) {
            return List.of("get");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("title")) {
            return List.of("clear", "list");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("book")) {
            return List.of("get", "getall", "remember", "open");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("book") && args[1].equalsIgnoreCase("getall")) {
            final List<String> names = new ArrayList<>();
            for (final Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return names;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("xp")) {
            return List.of("add");
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("xp") && args[1].equalsIgnoreCase("add")) {
            final List<String> names = new ArrayList<>();
            for (final Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return names;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return this.registry.allIds();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("search")) {
            return this.registry.allIds();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("planet")) {
            final List<String> options = new ArrayList<>(this.plugin.getPlanetService().planetIds());
            options.add("info");
            options.add("debug");
            options.add("spawntest");
            options.add("locateruin");
            options.add("regenerate");
            return options;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            final List<String> names = new ArrayList<>();
            for (final Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return names;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("trust") || args[0].equalsIgnoreCase("untrust"))) {
            final List<String> names = new ArrayList<>();
            for (final Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return names;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("cleandisplay")) {
            return List.of("8", "16", "32", "64");
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("create") || args[0].equalsIgnoreCase("region"))) {
            return new ArrayList<>(this.plugin.getRegionService().regionIds());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("region")) {
            return List.of("action", "delete");
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("region") && args[2].equalsIgnoreCase("action")) {
            return List.of("rtp");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            return List.of("points");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("set") && args[1].equalsIgnoreCase("points")) {
            return new ArrayList<>(this.plugin.getRegionService().regionIds());
        }
        return List.of();
    }
}
