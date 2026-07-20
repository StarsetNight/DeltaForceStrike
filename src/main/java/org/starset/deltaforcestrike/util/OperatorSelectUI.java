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
import org.starset.deltaforcestrike.operator.OperatorDefinition;
import org.starset.deltaforcestrike.operator.OperatorSelectHolder;
import org.starset.deltaforcestrike.operator.OperatorService;
import org.starset.deltaforcestrike.operator.SkillDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * 干员选择：箱子 GUI。
 * 上方：干员（hover 技能说明）
 * 下方：队伍当前选择（玩家头）
 */
public final class OperatorSelectUI {

    public static final Component TITLE = Component.text("选择干员", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD);
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    /** 干员槽：第二行 10-16 */
    private static final int[] OPERATOR_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    /** 队伍展示：第四～五行 */
    private static final int[] ROSTER_SLOTS = {
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private OperatorSelectUI() {}

    public static void open(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        DeltaForceStrike plugin = DeltaForceStrike.getInstance();
        OperatorService ops = plugin.getOperatorService();
        if (ops == null) {
            return;
        }
        Match match = plugin.getMatchManager().getMatch();
        if (match == null || !match.contains(player.getUniqueId())) {
            return;
        }

        OperatorSelectHolder holder = new OperatorSelectHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, TITLE);
        holder.setInventory(inv);

        // 玻璃分隔
        ItemStack pane = pane(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, pane);
            inv.setItem(18 + i, pane);
            inv.setItem(45 + i, pane);
        }
        inv.setItem(4, pane(Material.PURPLE_STAINED_GLASS_PANE, "§d§l干员"));
        inv.setItem(22, pane(Material.CYAN_STAINED_GLASS_PANE, "§b§l队伍选择"));

        // 干员
        List<OperatorDefinition> defs = new ArrayList<>(ops.getRegistry().allUnique());
        PlayerSession self = match.getSession(player.getUniqueId());
        String mine = self == null ? null : self.getOperatorId();
        for (int i = 0; i < defs.size() && i < OPERATOR_SLOTS.length; i++) {
            inv.setItem(OPERATOR_SLOTS[i], operatorIcon(defs.get(i), mine));
        }

        // 队伍
        int r = 0;
        for (Player p : match.onlinePlayers()) {
            if (r >= ROSTER_SLOTS.length) {
                break;
            }
            inv.setItem(ROSTER_SLOTS[r++], rosterHead(p, match, ops));
        }

        inv.setItem(49, tipItem());
        player.openInventory(inv);
    }

    public static void send(Player player) {
        open(player);
    }

    public static void broadcast(Match match) {
        if (match == null) {
            return;
        }
        for (Player p : match.onlinePlayers()) {
            open(p);
        }
    }

    /** 仅广播谁选了谁，不刷全表、不重开 GUI（避免挡按钮） */
    public static void broadcastPick(Match match, Player who, OperatorDefinition def) {
        if (match == null || who == null || def == null) {
            return;
        }
        match.broadcast(Component.text("[干员] ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(who.getName(), NamedTextColor.WHITE))
                .append(Component.text(" 选择了 ", NamedTextColor.GRAY))
                .append(Component.text(def.getDisplayName(), NamedTextColor.YELLOW)));
        // 已打开 GUI 的人刷新下方队伍区
        refreshOpenGuis(match);
    }

    public static void sendSelected(Player player, OperatorDefinition def) {
        if (player == null || def == null) {
            return;
        }
        player.sendMessage(Component.text("✔ 已选择干员 ", NamedTextColor.GREEN)
                .append(Component.text(def.getDisplayName(), NamedTextColor.YELLOW, TextDecoration.BOLD)));
    }

    public static void refreshOpenGuis(Match match) {
        if (match == null) {
            return;
        }
        OperatorService ops = DeltaForceStrike.getInstance().getOperatorService();
        if (ops == null) {
            return;
        }
        for (Player p : match.onlinePlayers()) {
            if (!(p.getOpenInventory().getTopInventory().getHolder() instanceof OperatorSelectHolder)) {
                continue;
            }
            Inventory inv = p.getOpenInventory().getTopInventory();
            // 刷新干员高亮
            List<OperatorDefinition> defs = new ArrayList<>(ops.getRegistry().allUnique());
            PlayerSession self = match.getSession(p.getUniqueId());
            String mine = self == null ? null : self.getOperatorId();
            for (int i = 0; i < OPERATOR_SLOTS.length; i++) {
                if (i < defs.size()) {
                    inv.setItem(OPERATOR_SLOTS[i], operatorIcon(defs.get(i), mine));
                } else {
                    inv.setItem(OPERATOR_SLOTS[i], null);
                }
            }
            for (int slot : ROSTER_SLOTS) {
                inv.setItem(slot, null);
            }
            int r = 0;
            for (Player other : match.onlinePlayers()) {
                if (r >= ROSTER_SLOTS.length) {
                    break;
                }
                inv.setItem(ROSTER_SLOTS[r++], rosterHead(other, match, ops));
            }
        }
    }

    private static ItemStack operatorIcon(OperatorDefinition def, String selectedId) {
        Material mat = switch (def.getType() == null ? "" : def.getType().name()) {
            case "ASSAULT" -> Material.IRON_SWORD;
            case "ENGINEER" -> Material.TNT;
            case "MEDIC" -> Material.GOLDEN_APPLE;
            case "SCOUT" -> Material.SPECTRAL_ARROW;
            default -> Material.NETHER_STAR;
        };
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        boolean selected = selectedId != null && selectedId.equalsIgnoreCase(def.getId());
        meta.displayName(LEGACY.deserialize(
                (selected ? "§a✔ " : "§d") + def.getDisplayName()
                        + " §7(" + def.getEnglishName() + ")"));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("职业: " + def.getType(), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(skillLine("被动", def.getPassive()));
        lore.add(skillLine("招牌", def.getSignature()));
        lore.add(skillLine("购买", def.getPurchasable()));
        lore.add(skillLine("大招", def.getUltimate()));
        lore.add(Component.empty());
        lore.add(Component.text(selected ? "已选择" : "点击选择",
                selected ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(
                ItemKeys.id(), PersistentDataType.STRING, "op_select:" + def.getId());
        stack.setItemMeta(meta);
        return stack;
    }

    private static Component skillLine(String label, SkillDefinition skill) {
        if (skill == null) {
            return Component.text("· " + label + ": §8无", NamedTextColor.DARK_GRAY);
        }
        String desc = skill.getDescription() == null ? "" : skill.getDescription();
        return LEGACY.deserialize("§7· §f" + label + " §e" + skill.getName()
                + (desc.isEmpty() ? "" : " §8- §7" + desc));
    }

    private static ItemStack rosterHead(Player p, Match match, OperatorService ops) {
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
        String team = s == null || !s.hasTeam() ? "未选队" : s.getTeam().name();
        String op = "未选择";
        if (s != null && s.getOperatorId() != null) {
            OperatorDefinition d = ops.getRegistry().get(s.getOperatorId());
            op = d == null ? s.getOperatorId() : d.getDisplayName();
        }
        NamedTextColor teamColor = switch (s == null ? null : s.getTeam()) {
            case T -> NamedTextColor.RED;
            case CT -> NamedTextColor.AQUA;
            case null, default -> NamedTextColor.GRAY;
        };
        meta.displayName(Component.text(p.getName(), NamedTextColor.WHITE));
        meta.lore(List.of(
                Component.text("队伍: " + team, teamColor),
                Component.text("干员: " + op, NamedTextColor.LIGHT_PURPLE)
        ));
        head.setItemMeta(meta);
        return head;
    }

    private static ItemStack tipItem() {
        ItemStack stack = new ItemStack(Material.BOOK);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("说明", NamedTextColor.YELLOW));
        meta.lore(List.of(
                Component.text("上方点击干员选择", NamedTextColor.GRAY),
                Component.text("下方为队伍选择情况", NamedTextColor.GRAY),
                Component.text("全员选完立即开打", NamedTextColor.DARK_GRAY)
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
