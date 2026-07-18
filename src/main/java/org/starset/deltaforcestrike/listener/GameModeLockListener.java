package org.starset.deltaforcestrike.listener;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.match.Match;
import org.starset.deltaforcestrike.match.MatchState;
import org.starset.deltaforcestrike.match.PlayerSession;
import org.starset.deltaforcestrike.round.RoundState;
import org.starset.deltaforcestrike.util.Worlds;

/**
 * 对局内游戏模式约束：
 * - 购买阶段：强制 ADVENTURE，允许从 SPECTATOR 切出（回合复活）
 * - 战斗中存活：强制 ADVENTURE
 * - 战斗中阵亡：只允许 SPECTATOR
 */
public class GameModeLockListener implements Listener {

    private final DeltaForceStrike plugin;

    public GameModeLockListener(DeltaForceStrike plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent event) {
        Player p = event.getPlayer();
        if (!Worlds.isArena(p)) {
            return;
        }
        if (!plugin.getMatchManager().isInMatch(p)) {
            return;
        }

        Match match = plugin.getMatchManager().getMatch();
        if (match == null || match.getState() != MatchState.IN_PROGRESS) {
            return;
        }

        PlayerSession s = match.getSession(p.getUniqueId());
        RoundState rs = match.getRoundManager().getState();
        GameMode neu = event.getNewGameMode();

        // ---------- 购买 / 结算：允许离开旁观，强制冒险 ----------
        if (rs == RoundState.BUY || rs == RoundState.ROUND_END) {
            if (neu == GameMode.SPECTATOR) {
                // 购买阶段禁止进入旁观
                event.setCancelled(true);
                p.setGameMode(GameMode.ADVENTURE);
                return;
            }
            if (neu != GameMode.ADVENTURE) {
                event.setCancelled(true);
                p.setGameMode(GameMode.ADVENTURE);
            }
            return;
        }

        // ---------- 战斗 / 拆弹 ----------
        boolean dead = s != null && !s.isAlive();

        if (dead) {
            // 阵亡只允许旁观
            if (neu != GameMode.SPECTATOR) {
                event.setCancelled(true);
                p.setGameMode(GameMode.SPECTATOR);
                if (plugin.getSpectatorLockService() != null) {
                    plugin.getServer().getScheduler().runTask(plugin, () ->
                            plugin.getSpectatorLockService().onEnterSpectator(p));
                }
            }
            return;
        }

        // 存活：禁止旁观，强制冒险
        if (neu == GameMode.SPECTATOR) {
            event.setCancelled(true);
            p.setGameMode(GameMode.ADVENTURE);
            return;
        }
        if (neu != GameMode.ADVENTURE) {
            event.setCancelled(true);
            p.setGameMode(GameMode.ADVENTURE);
        }
    }
}
