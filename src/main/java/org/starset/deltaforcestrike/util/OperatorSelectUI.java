package org.starset.deltaforcestrike.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.match.Match;
import org.starset.deltaforcestrike.match.PlayerSession;
import org.starset.deltaforcestrike.operator.OperatorDefinition;
import org.starset.deltaforcestrike.operator.OperatorService;

/**
 * 聊天栏可点击选择干员。
 */
public final class OperatorSelectUI {

    private OperatorSelectUI() {}

    public static void send(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        OperatorService ops = DeltaForceStrike.getInstance().getOperatorService();
        if (ops == null) {
            return;
        }

        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("──────── 选择干员 ────────", NamedTextColor.LIGHT_PURPLE));
        player.sendMessage(Component.text("  点击下方名称即可选择（全员选完立即开打）", NamedTextColor.GRAY));

        Component line = Component.text("  ");
        boolean first = true;
        for (OperatorDefinition def : ops.getRegistry().allUnique()) {
            if (!first) {
                line = line.append(Component.text("  ", NamedTextColor.DARK_GRAY));
            }
            first = false;
            line = line.append(button(def));
        }
        player.sendMessage(line);

        // 自己当前选择
        Match match = DeltaForceStrike.getInstance().getMatchManager().getMatch();
        if (match != null) {
            PlayerSession self = match.getSession(player.getUniqueId());
            String mine = self == null || self.getOperatorId() == null ? "未选择" : self.getOperatorId();
            player.sendMessage(Component.text("  你当前: ", NamedTextColor.GRAY)
                    .append(Component.text(displayOf(ops, mine), NamedTextColor.YELLOW)));
            player.sendMessage(Component.text("  ────── 队伍选择 ──────", NamedTextColor.DARK_GRAY));
            sendTeamRoster(player, match, ops);
        }

        player.sendMessage(Component.text("────────────────────────", NamedTextColor.LIGHT_PURPLE));
        player.sendMessage(Component.empty());
    }

    public static void broadcast(Match match) {
        if (match == null) {
            return;
        }
        for (Player p : match.onlinePlayers()) {
            send(p);
        }
    }

    /** 广播/私聊：谁选了什么干员 */
    public static void sendTeamRoster(Player viewer, Match match, OperatorService ops) {
        if (match == null || ops == null) {
            return;
        }
        for (Player p : match.onlinePlayers()) {
            PlayerSession s = match.getSession(p.getUniqueId());
            String opId = s == null ? null : s.getOperatorId();
            String opName = displayOf(ops, opId);
            String team = s == null || !s.hasTeam() ? "-" : s.getTeam().name();

            NamedTextColor teamColor = switch (s == null ? null : s.getTeam()) {
                case T -> NamedTextColor.RED;
                case CT -> NamedTextColor.AQUA;
                case null, default -> NamedTextColor.GRAY;
            };

            boolean self = viewer.getUniqueId().equals(p.getUniqueId());
            Component msg = Component.text("  ")
                    .append(Component.text("[" + team + "] ", teamColor))
                    .append(Component.text(p.getName(), self ? NamedTextColor.GREEN : NamedTextColor.WHITE))
                    .append(Component.text(" → ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(opName,
                            opId == null ? NamedTextColor.DARK_GRAY : NamedTextColor.LIGHT_PURPLE));
            viewer.sendMessage(msg);
        }
    }

    /** 有人新选干员时：全队看到更新 */
    public static void broadcastPick(Match match, Player who, OperatorDefinition def) {
        if (match == null || who == null || def == null) {
            return;
        }
        Component msg = Component.text("[干员] ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(who.getName(), NamedTextColor.WHITE))
                .append(Component.text(" 选择了 ", NamedTextColor.GRAY))
                .append(Component.text(def.getDisplayName(), NamedTextColor.YELLOW))
                .append(Component.text(" (" + def.getType() + ")", NamedTextColor.DARK_GRAY));
        match.broadcast(msg);

        OperatorService ops = DeltaForceStrike.getInstance().getOperatorService();
        // 给每人刷一行简表（避免刷太长，只刷简表）
        for (Player p : match.onlinePlayers()) {
            p.sendMessage(Component.text("  —— 当前选择 ——", NamedTextColor.DARK_GRAY));
            sendTeamRoster(p, match, ops);
        }
    }

    public static void sendSelected(Player player, OperatorDefinition def) {
        if (player == null || def == null) {
            return;
        }
        player.sendMessage(Component.text("✔ 已选择干员 ", NamedTextColor.GREEN)
                .append(Component.text(def.getDisplayName(), NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text(" · " + def.getEnglishName(), NamedTextColor.GRAY)));
    }

    private static Component button(OperatorDefinition def) {
        String cmd = "/dfs agent " + def.getId();
        return Component.text("[" + def.getDisplayName() + "]", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand(cmd))
                .hoverEvent(HoverEvent.showText(
                        Component.text(def.getDisplayName() + " / " + def.getEnglishName(), NamedTextColor.YELLOW)
                                .append(Component.newline())
                                .append(Component.text("职业: " + def.getType(), NamedTextColor.GRAY))
                                .append(Component.newline())
                                .append(Component.text("点击选择 · " + cmd, NamedTextColor.DARK_GRAY))
                ));
    }

    private static String displayOf(OperatorService ops, String opId) {
        if (opId == null || opId.isEmpty()) {
            return "未选择";
        }
        OperatorDefinition d = ops.getRegistry().get(opId);
        return d == null ? opId : d.getDisplayName();
    }
}
