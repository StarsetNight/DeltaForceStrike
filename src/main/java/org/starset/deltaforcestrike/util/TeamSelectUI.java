package org.starset.deltaforcestrike.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.item.ItemKeys;
import org.starset.deltaforcestrike.match.Match;
import org.starset.deltaforcestrike.match.PlayerSession;
import org.starset.deltaforcestrike.match.Team;
import org.starset.deltaforcestrike.match.TeamSelectHolder;

import java.util.ArrayList;
import java.util.List;

/**
 * 队伍选择：热键书 + 箱子 GUI。
 * 上方 T / CT，下方玩家阵营一览。
 */
public final class TeamSelectUI {

    public static final Component TITLE = Component.text("选择队伍", NamedTextColor.GOLD, TextDecoration.BOLD);
    public static final String BOOK_ID = "dfs:team_select_book";
    public static final int BOOK_SLOT = 0;

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final int SLOT_T = 11;
    private static final int SLOT_CT = 15;
    private static final int[] ROSTER_SLOTS = {
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private TeamSelectUI() {}

    /** 打开选队 GUI */
    public static void open(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Match match = DeltaForceStrike.getInstance().getMatchManager().getMatch();
        if (match == null || !match.contains(player.getUniqueId())) {
            return;
        }

        TeamSelectHolder holder = new TeamSelectHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, TITLE);
        holder.setInventory(inv);

        ItemStack pane = pane(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, pane);
            inv.setItem(18 + i, pane);
            inv.setItem(45 + i, pane);
        }
        inv.setItem(4, pane(Material.ORANGE_STAINED_GLASS_PANE, "§6§l选择阵营"));
        inv.setItem(22, pane(Material.WHITE_STAINED_GLASS_PANE, "§f§l当前选择"));

        long tCount = match.countTeam(Team.T);
        long ctCount = match.countTeam(Team.CT);
        int cap = ConfigKeys.teamSize();
        PlayerSession self = match.getSession(player.getUniqueId());

        inv.setItem(SLOT_T, teamButton(Team.T, tCount, cap, self));
        inv.setItem(SLOT_CT, teamButton(Team.CT, ctCount, cap, self));

        int r = 0;
        for (Player p : match.onlinePlayers()) {
            if (r >= ROSTER_SLOTS.length) {
                break;
            }
            inv.setItem(ROSTER_SLOTS[r++], rosterHead(p, match));
        }

        inv.setItem(49, tipBook());
        player.openInventory(inv);
    }

    /** 兼容旧调用：改为打开 GUI */
    public static void send(Player player) {
        open(player);
    }

    public static void broadcast(Match match) {
        if (match == null) {
            return;
        }
        for (Player p : match.onlinePlayers()) {
            giveBook(p);
            p.sendMessage(Component.text("[DFS] ", NamedTextColor.GOLD)
                    .append(Component.text("右键热键第1格「选择队伍」书打开选边", NamedTextColor.YELLOW)));
        }
    }

    /** 发放选队书到热键第 1 格 */
    public static void giveBook(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        ItemStack book = createBookItem();
        player.getInventory().setItem(BOOK_SLOT, book);
        player.updateInventory();
    }

    public static boolean isTeamSelectBook(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        String id = stack.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.id(), PersistentDataType.STRING);
        return BOOK_ID.equals(id);
    }

    public static ItemStack createBookItem() {
        ItemStack book = new ItemStack(Material.BOOK);
        ItemMeta meta = book.getItemMeta();
        meta.displayName(Component.text("选择队伍", NamedTextColor.GOLD, TextDecoration.BOLD));
        meta.lore(List.of(
                Component.text("右键打开选队界面", NamedTextColor.YELLOW),
                Component.text("T 进攻 · CT 防守", NamedTextColor.GRAY)
        ));
        meta.getPersistentDataContainer().set(ItemKeys.id(), PersistentDataType.STRING, BOOK_ID);
        meta.getPersistentDataContainer().set(ItemKeys.type(), PersistentDataType.STRING, "team_select");
        meta.getPersistentDataContainer().set(ItemKeys.undroppable(), PersistentDataType.BYTE, (byte) 1);
        try {
            meta.setMaxStackSize(1);
        } catch (Throwable ignored) {
        }
        book.setItemMeta(meta);
        return book;
    }

    public static void sendSelected(Player player, Team team) {
        if (player == null || team == null) {
            return;
        }
        if (team == Team.T) {
            player.sendMessage(Component.text("✔ 已加入 ", NamedTextColor.GREEN)
                    .append(Component.text("进攻方 T", NamedTextColor.RED, TextDecoration.BOLD)));
        } else if (team == Team.CT) {
            player.sendMessage(Component.text("✔ 已加入 ", NamedTextColor.GREEN)
                    .append(Component.text("防守方 CT", NamedTextColor.AQUA, TextDecoration.BOLD)));
        }
    }

    /** 刷新已打开的选队 GUI */
    public static void refreshOpenGuis(Match match) {
        if (match == null) {
            return;
        }
        for (Player p : match.onlinePlayers()) {
            if (!(p.getOpenInventory().getTopInventory().getHolder() instanceof TeamSelectHolder)) {
                continue;
            }
            // 重绘：关闭再开会闪，直接改槽位
            Inventory inv = p.getOpenInventory().getTopInventory();
            long tCount = match.countTeam(Team.T);
            long ctCount = match.countTeam(Team.CT);
            int cap = ConfigKeys.teamSize();
            PlayerSession self = match.getSession(p.getUniqueId());
            inv.setItem(SLOT_T, teamButton(Team.T, tCount, cap, self));
            inv.setItem(SLOT_CT, teamButton(Team.CT, ctCount, cap, self));
            for (int slot : ROSTER_SLOTS) {
                inv.setItem(slot, null);
            }
            int r = 0;
            for (Player other : match.onlinePlayers()) {
                if (r >= ROSTER_SLOTS.length) {
                    break;
                }
                inv.setItem(ROSTER_SLOTS[r++], rosterHead(other, match));
            }
        }
    }

    private static ItemStack teamButton(Team team, long count, int cap, PlayerSession self) {
        boolean full = count >= cap;
        boolean mine = self != null && self.getTeam() == team;
        Material mat = team == Team.T ? Material.RED_WOOL : Material.CYAN_WOOL;
        if (full && !mine) {
            mat = Material.GRAY_WOOL;
        }
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        String name = team == Team.T ? "进攻方 T" : "防守方 CT";
        NamedTextColor color = team == Team.T ? NamedTextColor.RED : NamedTextColor.AQUA;
        meta.displayName(Component.text(
                (mine ? "✔ " : "") + name + "  (" + count + "/" + cap + ")",
                mine ? NamedTextColor.GREEN : (full ? NamedTextColor.DARK_GRAY : color),
                TextDecoration.BOLD));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(team == Team.T ? "安装/保护改造TNT" : "阻止安包 / 拆除", NamedTextColor.GRAY));
        if (mine) {
            lore.add(Component.text("你已在此队伍", NamedTextColor.GREEN));
        } else if (full) {
            lore.add(Component.text("队伍已满", NamedTextColor.RED));
        } else {
            lore.add(Component.text("点击加入", NamedTextColor.YELLOW));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(
                ItemKeys.id(), PersistentDataType.STRING,
                "team_select:" + team.name().toLowerCase());
        stack.setItemMeta(meta);
        return stack;
    }

    private static ItemStack rosterHead(Player p, Match match) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        if (meta == null) {
            return head;
        }
        if (meta instanceof SkullMeta skull) {
            skull.setOwningPlayer(p);
            meta = skull;
        }
        PlayerSession s = match.getSession(p.getUniqueId());
        String teamLabel = "未选队";
        NamedTextColor teamColor = NamedTextColor.GRAY;
        if (s != null && s.hasTeam()) {
            if (s.getTeam() == Team.T) {
                teamLabel = "T 进攻";
                teamColor = NamedTextColor.RED;
            } else {
                teamLabel = "CT 防守";
                teamColor = NamedTextColor.AQUA;
            }
        }
        meta.displayName(Component.text(p.getName(), NamedTextColor.WHITE));
        meta.lore(List.of(Component.text("阵营: " + teamLabel, teamColor)));
        head.setItemMeta(meta);
        return head;
    }

    private static ItemStack tipBook() {
        ItemStack stack = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("说明", NamedTextColor.YELLOW));
        meta.lore(List.of(
                Component.text("上方点击 T / CT 加入", NamedTextColor.GRAY),
                Component.text("下方为全员当前阵营", NamedTextColor.GRAY)
        ));
        stack.setItemMeta(meta);
        return stack;
    }

    private static ItemStack pane(Material mat, String name) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(LEGACY.deserialize(name));
        stack.setItemMeta(meta);
        return stack;
    }
}
