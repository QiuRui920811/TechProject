package com.rui.techproject.command;

import com.rui.techproject.TechProjectPlugin;
import com.rui.techproject.service.TechRegistry;
import com.rui.techproject.util.ItemFactoryUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TechCommand implements CommandExecutor, TabCompleter {
    private final TechProjectPlugin plugin;
    private final TechRegistry registry;
    private final ItemFactoryUtil itemFactory;

    public TechCommand(final TechProjectPlugin plugin, final TechRegistry registry, final ItemFactoryUtil itemFactory) {
        this.plugin = plugin;
        this.registry = registry;
        this.itemFactory = itemFactory;
    }

    @Override
    public boolean onCommand(@NotNull final CommandSender sender,
                             @NotNull final Command command,
                             @NotNull final String label,
                             @NotNull final String[] args) {
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
            this.plugin.getTechBookService().openDefaultBook(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("list")) {
            sender.sendMessage(Component.text("科技資料總覽：" + this.registry.summaryLine(), NamedTextColor.YELLOW));
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
            sender.sendMessage(Component.text("已解鎖物品：" + progress.unlockedItems(uuid).size() + " / " + this.registry.allItems().size(), NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("已解鎖機器：" + progress.unlockedMachines(uuid).size() + " / " + this.registry.allMachines().size(), NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("已完成成就：" + progress.unlockedAchievements(uuid).size() + " / " + this.registry.allAchievements().size(), NamedTextColor.YELLOW));
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
                sender.sendMessage(Component.text("用法：/tech xp add <amount> [player]", NamedTextColor.RED));
                return true;
            }
            if (!sender.hasPermission("techproject.admin")) {
                sender.sendMessage(Component.text("缺少權限。", NamedTextColor.RED));
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(Component.text("用法：/tech xp add <amount> [player]", NamedTextColor.RED));
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
            this.plugin.reloadConfig();
            this.plugin.reloadProjectData();
            this.plugin.getPlanetService().reloadRuntimeConfig();
            sender.sendMessage(Component.text("科技專案設定已重新載入。", NamedTextColor.GREEN));
            return true;
        }

        if (args[0].equalsIgnoreCase("planet")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("只有玩家可以使用星球傳送。", NamedTextColor.RED));
                return true;
            }
            if (args.length >= 2 && args[1].equalsIgnoreCase("info")) {
                sender.sendMessage(Component.text("=== 星球資訊 ===", NamedTextColor.AQUA));
                for (final String line : this.plugin.getPlanetService().planetInfoLines()) {
                    sender.sendMessage(Component.text(line, NamedTextColor.GOLD));
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

        if (args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("techproject.admin")) {
                sender.sendMessage(Component.text("缺少權限。", NamedTextColor.RED));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(Component.text("用法：/tech give <id> [player]", NamedTextColor.RED));
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

        sender.sendMessage(Component.text("未知子命令。", NamedTextColor.RED));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull final CommandSender sender,
                                                @NotNull final Command command,
                                                @NotNull final String alias,
                                                @NotNull final String[] args) {
        if (args.length == 1) {
            return List.of("book", "research", "planet", "list", "stats", "xp", "achievements", "title", "search", "give", "reload");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("title")) {
            return List.of("clear", "list");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("book")) {
            return List.of("get", "getall");
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
            return options;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            final List<String> names = new ArrayList<>();
            for (final Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return names;
        }
        return List.of();
    }
}
