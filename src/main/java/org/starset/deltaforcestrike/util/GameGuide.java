package org.starset.deltaforcestrike.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.starset.deltaforcestrike.DeltaForceStrike;

/** In-game help text. Kept on Adventure internally and rendered through SpigotCompat. */
public final class GameGuide {
    private GameGuide() {}

    public static void sendOnJoin(Player player) {
        if (player != null && DeltaForceStrike.getInstance().getConfig().getBoolean("guide.send-on-join", true)) {
            send(player);
        }
    }

    public static void send(Player player) {
        if (player == null) return;
        int plant = DeltaForceStrike.getInstance().getConfig().getInt("bomb.plant-time", 3);
        int defuse = DeltaForceStrike.getInstance().getConfig().getInt("bomb.defuse-time", 10);
        int defuseKit = DeltaForceStrike.getInstance().getConfig().getInt("bomb.defuse-time-with-kit", 5);
        line(player, "═══════════════════════════", NamedTextColor.GOLD);
        line(player, "友谊之约：反制行动 · 玩法说明", NamedTextColor.GOLD, true);
        line(player, "═══════════════════════════", NamedTextColor.GOLD);
        line(player, "【目标】", NamedTextColor.AQUA, true);
        line(player, "T 进攻方：安装改造 TNT 并保护至爆炸，或歼灭 CT", NamedTextColor.GRAY);
        line(player, "CT 防守方：阻止安包、拆除炸弹，或歼灭 T", NamedTextColor.GRAY);
        line(player, "【流程】", NamedTextColor.AQUA, true);
        line(player, "1. 排队满人后倒计时选边，或使用 /dfs team t|ct", NamedTextColor.GRAY);
        line(player, "2. 购买阶段使用 /dfs shop 购买武器和道具", NamedTextColor.GRAY);
        line(player, "3. 战斗阶段击败敌人或完成爆破目标", NamedTextColor.GRAY);
        line(player, "【改造 TNT】", NamedTextColor.RED, true);
        line(player, "TNT 每回合随机交给一名 T，进入包点后右键方块安装", NamedTextColor.GRAY);
        line(player, "安装读条 " + plant + " 秒；移动会打断。CT 潜行靠近炸弹即可拆除", NamedTextColor.GRAY);
        line(player, "空手拆除约 " + defuse + " 秒，持拆除钳约 " + defuseKit + " 秒", NamedTextColor.GRAY);
        line(player, "【其它】", NamedTextColor.AQUA, true);
        line(player, "道具战斗中右键投掷；死亡后观战，下回合购买阶段复活", NamedTextColor.GRAY);
        line(player, "命令：/dfs info 状态 · /dfs shop 商店 · /dfs guide 重看说明", NamedTextColor.GRAY);
        line(player, "═══════════════════════════", NamedTextColor.GOLD);
    }

    private static void line(Player player, String text, NamedTextColor color) {
        line(player, text, color, false);
    }

    private static void line(Player player, String text, NamedTextColor color, boolean bold) {
        Component component = Component.text(text, color);
        if (bold) component = component.decorate(TextDecoration.BOLD);
        SpigotCompat.sendMessage(player, component);
    }
}
