package org.starset.deltaforcestrike.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scoreboard.Team;
import net.kyori.adventure.title.Title;

import java.util.List;
import java.util.Objects;

/** Small bridge for rendering Adventure text through the Spigot API. */
public final class SpigotCompat {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private SpigotCompat() {
    }

    public static String legacy(Component component) {
        return component == null ? "" : LEGACY.serialize(component);
    }

    public static void sendMessage(CommandSender sender, Component component) {
        sender.sendMessage(legacy(component));
    }

    public static void actionBar(Player player, Component component) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(legacy(component)));
    }

    public static void title(Player player, Component title, Component subtitle,
                             int fadeIn, int stay, int fadeOut) {
        player.sendTitle(legacy(title), legacy(subtitle), fadeIn, stay, fadeOut);
    }

    public static void title(Player player, Title title) {
        if (title == null) return;
        int fadeIn = 10, stay = 70, fadeOut = 20;
        if (title.times() != null) {
            fadeIn = ticks(title.times().fadeIn());
            stay = ticks(title.times().stay());
            fadeOut = ticks(title.times().fadeOut());
        }
        title(player, title.title(), title.subtitle(), fadeIn, stay, fadeOut);
    }

    private static int ticks(java.time.Duration duration) {
        return duration == null ? 0 : (int) Math.max(0, duration.toMillis() / 50L);
    }

    public static void itemName(ItemMeta meta, Component name) {
        meta.setDisplayName(legacy(name));
    }

    public static void itemLore(ItemMeta meta, List<? extends Component> lore) {
        meta.setLore(lore == null ? null : lore.stream().map(SpigotCompat::legacy).toList());
    }

    public static void customName(org.bukkit.entity.Entity entity, Component name) {
        entity.setCustomName(legacy(name));
    }

    public static void teamColor(Team team, net.kyori.adventure.text.format.NamedTextColor color) {
        team.setColor(ChatColor.WHITE);
    }

    public static void teamPrefix(Team team, Component prefix) {
        team.setPrefix(legacy(prefix));
    }

    public static org.bukkit.boss.BossBar bossBar(Component title) {
        return Bukkit.createBossBar(legacy(title), BarColor.RED, BarStyle.SOLID);
    }
}
