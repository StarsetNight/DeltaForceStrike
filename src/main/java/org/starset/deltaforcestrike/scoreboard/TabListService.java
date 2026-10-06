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

        player.setPlayerListName("[" + tag + "] " + player.getName() + " " + s.getKills() + "/" + s.getDeaths());
        player.setDisplayName(player.getName());
        player.setPlayerListHeader("DeltaForceStrike");
        player.setPlayerListFooter("T " + match.getScoreT() + " - " + match.getScoreCT() + " CT");
    }

    public void updateAll(Match match) {
        if (match == null) return;
        for (Player p : match.onlinePlayers()) update(p);
    }

    public void reset(Player player) {
        player.setPlayerListName(player.getName());
        player.setDisplayName(player.getName());
        player.setPlayerListHeader("");
        player.setPlayerListFooter("");
    }
}
