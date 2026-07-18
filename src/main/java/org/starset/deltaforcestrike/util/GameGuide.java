package org.starset.deltaforcestrike.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.starset.deltaforcestrike.DeltaForceStrike;

/**
 * 入队玩法说明。勿把 § 字符串直接丢进 Component.text(..., NamedTextColor)。
 */
public final class GameGuide {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacySection();

    private GameGuide() {}

    public static void sendOnJoin(Player player) {
        if (player == null) {
            return;
        }
        if (!DeltaForceStrike.getInstance().getConfig().getBoolean("guide.send-on-join", true)) {
            return;
        }
        send(player);
    }

    public static void send(Player player) {
        if (player == null) {
            return;
        }

        int plant = DeltaForceStrike.getInstance().getConfig().getInt("bomb.plant-time", 3);
        int defuse = DeltaForceStrike.getInstance().getConfig().getInt("bomb.defuse-time", 10);
        int defuseKit = DeltaForceStrike.getInstance().getConfig().getInt("bomb.defuse-time-with-kit", 5);

        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("═══════════════════════════", NamedTextColor.GOLD));
        player.sendMessage(
                Component.text("  友谊之约：反制行动", NamedTextColor.GOLD, TextDecoration.BOLD)
                        .append(Component.text("  · 玩法说明", NamedTextColor.YELLOW))
        );
        player.sendMessage(Component.text("═══════════════════════════", NamedTextColor.GOLD));
        player.sendMessage(Component.empty());

        player.sendMessage(Component.text("【目标】", NamedTextColor.AQUA, TextDecoration.BOLD));
        player.sendMessage(line(
                Component.text("T 进攻方", NamedTextColor.RED),
                "：安装改造TNT 并保护至爆炸，或歼灭 CT"
        ));
        player.sendMessage(line(
                Component.text("CT 防守方", NamedTextColor.AQUA),
                "：阻止安包、拆除炸弹，或歼灭 T"
        ));
        player.sendMessage(Component.empty());

        player.sendMessage(Component.text("【流程】", NamedTextColor.AQUA, TextDecoration.BOLD));
        player.sendMessage(plain("  1. 排队满人 → 倒计时点聊天选边，或 /dfs team t | ct"));
        player.sendMessage(plain("  2. 购买阶段：出生点附近活动，/dfs shop 买枪/道具"));
        player.sendMessage(plain("  3. 战斗阶段：击败敌人或完成爆破目标"));
        player.sendMessage(Component.empty());

        player.sendMessage(Component.text("【改造TNT · 下包】", NamedTextColor.RED, TextDecoration.BOLD));
        player.sendMessage(plain("  · 每回合仅 1 名 T 随机携带改造TNT（热键第 3 格）"));
        player.sendMessage(plain("  · 进入包点范围后，手持 TNT 右键方块"));
        player.sendMessage(plain("  · 站立读条 " + plant + " 秒（移动会打断）"));
        player.sendMessage(plain("  · 安装成功后进入爆炸倒计时，T 需护包"));
        player.sendMessage(Component.empty());

        player.sendMessage(Component.text("【改造TNT · 拆包】", NamedTextColor.BLUE, TextDecoration.BOLD));
        player.sendMessage(plain("  · 仅 CT 可拆；靠近已安装的炸弹"));
        player.sendMessage(plain("  · 潜行（Shift）靠近即可开始拆除"));
        player.sendMessage(plain("  · 空手约 " + defuse + " 秒；持拆除钳（热键第 3 格）约 " + defuseKit + " 秒"));
        player.sendMessage(plain("  · 移动会打断拆包"));
        player.sendMessage(Component.empty());

        player.sendMessage(Component.text("【其它】", NamedTextColor.AQUA, TextDecoration.BOLD));
        player.sendMessage(plain("  · 道具：战斗中右键投掷（烟雾/凋零/高爆）"));
        player.sendMessage(plain("  · 死亡变为旁观，下回合购买阶段复活"));
        player.sendMessage(plain("  · 命令：/dfs info  状态 · /dfs shop  商店 · /dfs guide  重看说明"));
        player.sendMessage(Component.empty());

        player.sendMessage(
                Component.text("  ▶ 点击此处再次查看玩法", NamedTextColor.GREEN, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.runCommand("/dfs guide"))
                        .hoverEvent(HoverEvent.showText(Component.text("执行 /dfs guide", NamedTextColor.GRAY)))
        );
        player.sendMessage(Component.text("═══════════════════════════", NamedTextColor.GOLD));
        player.sendMessage(Component.empty());
    }

    /** 纯灰色说明行（无 §） */
    private static Component plain(String text) {
        return Component.text(text, NamedTextColor.GRAY);
    }

    private static Component line(Component prefix, String suffix) {
        return Component.text("  ")
                .append(prefix)
                .append(Component.text(suffix, NamedTextColor.GRAY));
    }

    /**
     * 若别处必须用 § 字符串，用这个，不要 Component.text("§c...", NamedTextColor.GRAY)。
     */
    public static Component legacy(String sectionColored) {
        if (sectionColored == null || sectionColored.isEmpty()) {
            return Component.empty();
        }
        return LEGACY.deserialize(sectionColored);
    }
}
