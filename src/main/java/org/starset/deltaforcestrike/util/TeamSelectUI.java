package org.starset.deltaforcestrike.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.match.Match;
import org.starset.deltaforcestrike.match.Team;

/**
 * 聊天栏可点击选边（T / CT）。
 */
public final class TeamSelectUI {

    private TeamSelectUI() {}

    /** 发给单个玩家 */
    public static void send(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        Match match = DeltaForceStrike.getInstance().getMatchManager().getMatch();
        long tCount = match == null ? 0 : match.countTeam(Team.T);
        long ctCount = match == null ? 0 : match.countTeam(Team.CT);
        int cap = ConfigKeys.teamSize();

        player.sendMessage("");
        player.sendMessage(Component.text("──────── 选择队伍 ────────", NamedTextColor.GOLD));

        Component line = Component.empty()
                .append(Component.text("  "))
                .append(buttonT(tCount, cap))
                .append(Component.text("    ", NamedTextColor.DARK_GRAY))
                .append(buttonCT(ctCount, cap));

        player.sendMessage(line);
        player.sendMessage(Component.text("  点击上方按钮即可选边（也可输入 /dfs team t|ct）", NamedTextColor.GRAY));
        player.sendMessage(Component.text(
                "  当前人数  T " + tCount + "/" + cap + "  ·  CT " + ctCount + "/" + cap,
                NamedTextColor.DARK_GRAY
        ));
        player.sendMessage(Component.text("────────────────────────", NamedTextColor.GOLD));
        player.sendMessage("");
    }

    /** 广播给对局里所有人 */
    public static void broadcast(Match match) {
        if (match == null) {
            return;
        }
        for (Player p : match.onlinePlayers()) {
            send(p);
        }
    }

    private static Component buttonT(long count, int cap) {
        boolean full = count >= cap;
        NamedTextColor color = full ? NamedTextColor.DARK_GRAY : NamedTextColor.RED;

        Component btn = Component.text("[ 加入 T 进攻 ]", color, TextDecoration.BOLD);
        if (!full) {
            btn = btn
                    .clickEvent(ClickEvent.runCommand("/dfs team t"))
                    .hoverEvent(HoverEvent.showText(Component.text(
                            "点击加入进攻方 T\n当前 " + count + "/" + cap,
                            NamedTextColor.RED
                    )));
        } else {
            btn = btn.hoverEvent(HoverEvent.showText(Component.text("进攻方已满", NamedTextColor.GRAY)));
        }
        return btn;
    }

    private static Component buttonCT(long count, int cap) {
        boolean full = count >= cap;
        NamedTextColor color = full ? NamedTextColor.DARK_GRAY : NamedTextColor.AQUA;

        Component btn = Component.text("[ 加入 CT 防守 ]", color, TextDecoration.BOLD);
        if (!full) {
            btn = btn
                    .clickEvent(ClickEvent.runCommand("/dfs team ct"))
                    .hoverEvent(HoverEvent.showText(Component.text(
                            "点击加入防守方 CT\n当前 " + count + "/" + cap,
                            NamedTextColor.AQUA
                    )));
        } else {
            btn = btn.hoverEvent(HoverEvent.showText(Component.text("防守方已满", NamedTextColor.GRAY)));
        }
        return btn;
    }

    /** 选边成功后的短确认（可选） */
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
}
