package com.rui.techproject.command;

import com.rui.techproject.TechProjectPlugin;
import com.rui.techproject.service.DungeonService;
import com.rui.techproject.util.RichText;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class DungeonCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN_PERMISSION = "techdungeon.admin";

    private final TechProjectPlugin plugin;

    public DungeonCommand(final TechProjectPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull final CommandSender sender,
                             @NotNull final Command command,
                             @NotNull final String label,
                             @NotNull final String[] args) {
        if (!(sender instanceof Player player)) {
            this.tell(sender, "<#F87171>只有玩家可以使用副本系統。</#F87171>");
            return true;
        }

        final DungeonService dungeonService = this.plugin.getDungeonService();
        if (dungeonService == null) {
            this.tell(player, "<#F87171>副本系統尚未載入。</#F87171>");
            return true;
        }

        if (args.length < 1) {
            this.sendHelp(player);
            return true;
        }

        final String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> this.sendHelp(player);
            case "list" -> {
                this.tell(player, "<gradient:#22D3EE:#3B82F6><bold>=== 可用副本 ===</bold></gradient>");
                for (final var def : dungeonService.definitions().values()) {
                    final String status;
                    final var pd = dungeonService.getPlayerData(player.getUniqueId());
                    if (pd != null && pd.hasCleared(def.id())) {
                        status = "<#34D399>✔ 已通關";
                    } else {
                        status = "<#94A3B8>未通關";
                    }
                    this.tell(player,
                            "<#FDE68A>" + def.id() + " <#E5E7EB>- " + def.displayName()
                                    + " <#94A3B8>[" + def.minPlayers() + "-" + def.maxPlayers() + "人] " + status);
                }
            }
            case "join", "start" -> {
                if (args.length < 2) {
                    this.tell(player, "<#F87171>用法：/dungeon join [副本ID]</#F87171>");
                    return true;
                }
                dungeonService.startDungeon(player, args[1].toLowerCase(Locale.ROOT));
            }
            case "leave", "quit" -> dungeonService.leaveDungeon(player);
            case "party" -> dungeonService.createParty(player);
            case "invite" -> {
                if (args.length < 2) {
                    this.tell(player, "<#F87171>用法：/dungeon invite [玩家]</#F87171>");
                    return true;
                }
                final Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    this.tell(player, "<#F87171>找不到玩家。</#F87171>");
                    return true;
                }
                dungeonService.inviteToParty(player, target);
            }
            case "accept" -> dungeonService.acceptInvite(player);
            case "ready" -> dungeonService.handleReady(player);
            case "stuck" -> dungeonService.handleStuck(player);
            case "info" -> {
                if (args.length < 2) {
                    this.tell(player, "<#F87171>用法：/dungeon info [副本ID]</#F87171>");
                    return true;
                }
                final var def = dungeonService.definitions().get(args[1].toLowerCase(Locale.ROOT));
                if (def == null) {
                    this.tell(player, "<#F87171>找不到副本：" + args[1] + "</#F87171>");
                    return true;
                }
                this.tell(player, "<gradient:#22D3EE:#3B82F6><bold>=== " + def.displayName() + " ===</bold></gradient>");
                this.tell(player, "<#94A3B8>" + def.description());
                this.tell(player, "<#E5E7EB>人數：<#FDE68A>" + def.minPlayers() + " ~ " + def.maxPlayers());
                if (def.timeLimitSeconds() > 0) {
                    this.tell(player, "<#E5E7EB>限時：<#FDE68A>" + def.timeLimitSeconds() / 60 + " 分鐘");
                }
                this.tell(player, "<#E5E7EB>波次：<#FDE68A>" + def.waves().size() + " 波");
                this.tell(player, "<#E5E7EB>Boss：<#FDE68A>" + def.bosses().size() + " 隻");
                if (def.cooldownSeconds() > 0) {
                    this.tell(player, "<#E5E7EB>冷卻：<#FDE68A>" + def.cooldownSeconds() / 60 + " 分鐘");
                }
                if (def.dailyLimit() > 0) {
                    this.tell(player, "<#E5E7EB>每日上限：<#FDE68A>" + def.dailyLimit() + " 次");
                }
                this.tell(player, "<#E5E7EB>分類：<#FDE68A>" + def.category());
            }
            case "top", "leaderboard", "rank" -> {
                if (args.length < 2) {
                    this.tell(player, "<#F87171>用法：/dungeon top [副本ID]</#F87171>");
                    return true;
                }
                final String dungeonId = args[1].toLowerCase(Locale.ROOT);
                final var lb = dungeonService.getLeaderboard(dungeonId);
                if (lb.isEmpty()) {
                    this.tell(player, "<#FDE68A>該副本尚無通關記錄。</#FDE68A>");
                    return true;
                }
                this.tell(player, "<gradient:#22D3EE:#3B82F6><bold>=== " + dungeonId + " 排行榜 ===</bold></gradient>");
                for (int i = 0; i < lb.size(); i++) {
                    final var entry = lb.get(i);
                    this.tell(player, "<#FDE68A>#" + (i + 1) + " <#E5E7EB>" + entry.name()
                            + " <#94A3B8>- <#34D399>" + entry.seconds() / 60 + ":" + String.format("%02d", entry.seconds() % 60));
                }
            }
            case "reload" -> {
                if (!sender.hasPermission(ADMIN_PERMISSION)) {
                    this.tell(sender, "<#F87171>缺少權限。</#F87171>");
                    return true;
                }
                dungeonService.reload();
                this.tell(sender, "<#34D399>副本設定已重新載入。</#34D399>");
            }
            case "forceclose" -> {
                if (!sender.hasPermission(ADMIN_PERMISSION)) {
                    this.tell(sender, "<#F87171>缺少權限。</#F87171>");
                    return true;
                }
                final var active = dungeonService.activeInstances();
                if (active.isEmpty()) {
                    this.tell(sender, "<#FDE68A>沒有活躍的副本實例。</#FDE68A>");
                    return true;
                }
                this.tell(sender, "<#FDE68A>正在關閉 " + active.size() + " 個副本實例...</#FDE68A>");
                dungeonService.shutdown();
                this.tell(sender, "<#34D399>所有副本已關閉。</#34D399>");
            }
            case "create" -> {
                if (!sender.hasPermission(ADMIN_PERMISSION)) {
                    this.tell(sender, "<#F87171>缺少權限。</#F87171>");
                    return true;
                }
                if (args.length < 2) {
                    this.tell(player, "<#F87171>用法：/dungeon create [副本ID]</#F87171>");
                    return true;
                }
                dungeonService.adminCreate(player, args[1].toLowerCase(Locale.ROOT));
            }
            case "edit" -> {
                if (!sender.hasPermission(ADMIN_PERMISSION)) {
                    this.tell(sender, "<#F87171>缺少權限。</#F87171>");
                    return true;
                }
                if (args.length < 2) {
                    this.tell(player, "<#F87171>用法：/dungeon edit [副本ID]</#F87171>");
                    return true;
                }
                dungeonService.adminEdit(player, args[1].toLowerCase(Locale.ROOT));
            }
            case "setspawn" -> {
                if (!sender.hasPermission(ADMIN_PERMISSION)) {
                    this.tell(sender, "<#F87171>缺少權限。</#F87171>");
                    return true;
                }
                dungeonService.adminSetSpawn(player);
            }
            case "setexit" -> {
                if (!sender.hasPermission(ADMIN_PERMISSION)) {
                    this.tell(sender, "<#F87171>缺少權限。</#F87171>");
                    return true;
                }
                dungeonService.adminSetExit(player);
            }
            case "setlobby" -> {
                if (!sender.hasPermission(ADMIN_PERMISSION)) {
                    this.tell(sender, "<#F87171>缺少權限。</#F87171>");
                    return true;
                }
                dungeonService.adminSetLobby(player);
            }
            case "setname" -> {
                if (!sender.hasPermission(ADMIN_PERMISSION)) {
                    this.tell(sender, "<#F87171>缺少權限。</#F87171>");
                    return true;
                }
                if (args.length < 2) {
                    this.tell(player, "<#F87171>用法：/dungeon setname [顯示名稱]</#F87171>");
                    return true;
                }
                final String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                dungeonService.adminSetName(player, name);
            }
            case "settime" -> {
                if (!sender.hasPermission(ADMIN_PERMISSION)) {
                    this.tell(sender, "<#F87171>缺少權限。</#F87171>");
                    return true;
                }
                if (args.length < 2) {
                    this.tell(player, "<#F87171>用法：/dungeon settime [秒數]</#F87171>");
                    return true;
                }
                try {
                    dungeonService.adminSetTime(player, Integer.parseInt(args[1]));
                } catch (final NumberFormatException e) {
                    this.tell(player, "<#F87171>請輸入有效的秒數。</#F87171>");
                }
            }
            case "setplayers" -> {
                if (!sender.hasPermission(ADMIN_PERMISSION)) {
                    this.tell(sender, "<#F87171>缺少權限。</#F87171>");
                    return true;
                }
                if (args.length < 3) {
                    this.tell(player, "<#F87171>用法：/dungeon setplayers [最少] [最多]</#F87171>");
                    return true;
                }
                try {
                    dungeonService.adminSetPlayers(player, Integer.parseInt(args[1]), Integer.parseInt(args[2]));
                } catch (final NumberFormatException e) {
                    this.tell(player, "<#F87171>請輸入有效的數字。</#F87171>");
                }
            }
            case "setcooldown" -> {
                if (!sender.hasPermission(ADMIN_PERMISSION)) {
                    this.tell(sender, "<#F87171>缺少權限。</#F87171>");
                    return true;
                }
                if (args.length < 2) {
                    this.tell(player, "<#F87171>用法：/dungeon setcooldown [秒數]</#F87171>");
                    return true;
                }
                try {
                    dungeonService.adminSetCooldown(player, Integer.parseInt(args[1]));
                } catch (final NumberFormatException e) {
                    this.tell(player, "<#F87171>請輸入有效的秒數。</#F87171>");
                }
            }
            case "save" -> {
                if (!sender.hasPermission(ADMIN_PERMISSION)) {
                    this.tell(sender, "<#F87171>缺少權限。</#F87171>");
                    return true;
                }
                dungeonService.adminSave(player);
            }
            case "cancel" -> {
                if (!sender.hasPermission(ADMIN_PERMISSION)) {
                    this.tell(sender, "<#F87171>缺少權限。</#F87171>");
                    return true;
                }
                dungeonService.adminCancel(player);
            }
            case "delete" -> {
                if (!sender.hasPermission(ADMIN_PERMISSION)) {
                    this.tell(sender, "<#F87171>缺少權限。</#F87171>");
                    return true;
                }
                if (args.length < 2) {
                    this.tell(player, "<#F87171>用法：/dungeon delete [副本ID]</#F87171>");
                    return true;
                }
                dungeonService.adminDelete(player, args[1].toLowerCase(Locale.ROOT));
            }
            case "import" -> {
                if (!sender.hasPermission(ADMIN_PERMISSION)) {
                    this.tell(sender, "<#F87171>缺少權限。</#F87171>");
                    return true;
                }
                if (args.length < 2) {
                    this.tell(player, "<#F87171>用法：/dungeon import [地圖資料夾名稱]</#F87171>");
                    return true;
                }
                dungeonService.adminImport(player, args[1].toLowerCase(Locale.ROOT));
            }
            case "adminlist" -> {
                if (!sender.hasPermission(ADMIN_PERMISSION)) {
                    this.tell(sender, "<#F87171>缺少權限。</#F87171>");
                    return true;
                }
                dungeonService.adminList(player);
            }
            default -> this.tell(player, "<#F87171>未知副本子命令：" + sub + "</#F87171>");
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull final CommandSender sender,
                                                @NotNull final Command command,
                                                @NotNull final String alias,
                                                @NotNull final String[] args) {
        if (args.length == 1) {
            final List<String> options = new ArrayList<>(List.of(
                    "list", "join", "leave", "party", "invite", "accept", "ready", "stuck", "info", "top"
            ));
            if (sender.hasPermission(ADMIN_PERMISSION)) {
                options.addAll(List.of(
                        "create", "edit", "setspawn", "setexit", "setlobby", "setname",
                        "settime", "setplayers", "setcooldown", "save", "cancel", "delete",
                        "import", "adminlist", "reload", "forceclose"
                ));
            }
            return options;
        }

        if (args.length == 2) {
            final String sub = args[0].toLowerCase(Locale.ROOT);
            final DungeonService ds = this.plugin.getDungeonService();
            if (ds == null) {
                return List.of();
            }

            if (sub.equals("join") || sub.equals("start") || sub.equals("info") || sub.equals("top")
                    || sub.equals("edit") || sub.equals("delete")) {
                return new ArrayList<>(ds.definitions().keySet());
            }

            if (sub.equals("import")) {
                final java.io.File serverRoot = Bukkit.getWorldContainer();
                final java.io.File[] dirs = serverRoot.listFiles(java.io.File::isDirectory);
                if (dirs == null) {
                    return List.of();
                }
                final List<String> folders = new ArrayList<>();
                for (final java.io.File d : dirs) {
                    if (new java.io.File(d, "region").isDirectory()) {
                        folders.add(d.getName());
                    }
                }
                return folders;
            }

            if (sub.equals("invite")) {
                return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            }
        }

        return List.of();
    }

    private void sendHelp(final Player player) {
        this.tell(player, "<gradient:#22D3EE:#3B82F6><bold>=== 副本系統 ===</bold></gradient>");
        this.tell(player, "<#FDE68A>/dungeon list</#FDE68A> <#94A3B8>- 瀏覽可用副本</#94A3B8>");
        this.tell(player, "<#FDE68A>/dungeon join [副本ID]</#FDE68A> <#94A3B8>- 進入副本</#94A3B8>");
        this.tell(player, "<#FDE68A>/dungeon leave</#FDE68A> <#94A3B8>- 離開副本</#94A3B8>");
        this.tell(player, "<#FDE68A>/dungeon party</#FDE68A> <#94A3B8>- 建立隊伍</#94A3B8>");
        this.tell(player, "<#FDE68A>/dungeon invite [玩家]</#FDE68A> <#94A3B8>- 邀請隊友</#94A3B8>");
        this.tell(player, "<#FDE68A>/dungeon accept</#FDE68A> <#94A3B8>- 接受邀請</#94A3B8>");
        this.tell(player, "<#FDE68A>/dungeon ready</#FDE68A> <#94A3B8>- 大廳準備</#94A3B8>");
        this.tell(player, "<#FDE68A>/dungeon stuck</#FDE68A> <#94A3B8>- 傳送到安全點</#94A3B8>");
        if (player.hasPermission(ADMIN_PERMISSION)) {
            this.tell(player, "<gradient:#86EFAC:#22C55E><bold>--- 管理員 ---</bold></gradient>");
            this.tell(player, "<#86EFAC>/dungeon create [ID]</#86EFAC>");
            this.tell(player, "<#86EFAC>/dungeon edit [ID]</#86EFAC>");
            this.tell(player, "<#86EFAC>/dungeon setspawn | setexit | setlobby</#86EFAC>");
            this.tell(player, "<#86EFAC>/dungeon save | cancel | adminlist</#86EFAC>");
        }
    }

    private void tell(final CommandSender sender, final String text) {
        sender.sendMessage(RichText.parse(text));
    }
}
