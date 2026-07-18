package org.starset.deltaforcestrike.scoreboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.match.Match;
import org.starset.deltaforcestrike.match.PlayerSession;

public class TabListService {

    private final DeltaForceStrike plugin;

    public TabListService(DeltaForceStrike plugin) {
        this.plugin = plugin;
    }

    public void update(Player player) {
        Match match = plugin.getMatchManager().getMatch();
        if (match == null || !match.contains(player.getUniqueId())) {
            reset(player);
            return;
        }
        PlayerSession s = match.getSession(player.getUniqueId());
        if (s == null) return;

        NamedTextColor color = switch (s.getTeam()) {
            case T -> NamedTextColor.RED;
            case CT -> NamedTextColor.AQUA;
            default -> NamedTextColor.GRAY;
        };
        String tag = switch (s.getTeam()) {
            case T -> "T";
            case CT -> "CT";
            default -> "-";
        };

        player.playerListName(
                Component.text("[" + tag + "] ", color)
                        .append(Component.text(player.getName() + " ", NamedTextColor.WHITE))
                        .append(Component.text(s.getKills() + "/" + s.getDeaths(), NamedTextColor.GRAY))
        );
        player.displayName(Component.text(player.getName(), color));
        player.sendPlayerListHeaderAndFooter(
                Component.text("友谊之约：反制行动", NamedTextColor.GOLD),
                Component.text("T " + match.getScoreT() + " - " + match.getScoreCT() + " CT", NamedTextColor.YELLOW)
        );
    }

    public void updateAll(Match match) {
        if (match == null) return;
        for (Player p : match.onlinePlayers()) update(p);
    }

    public void reset(Player player) {
        player.playerListName(null);
        player.displayName(Component.text(player.getName()));
        player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
    }
}
