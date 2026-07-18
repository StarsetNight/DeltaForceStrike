package org.starset.deltaforcestrike.spectator;

import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.match.Match;
import org.starset.deltaforcestrike.match.MatchState;
import org.starset.deltaforcestrike.match.PlayerSession;
import org.starset.deltaforcestrike.match.Team;
import org.starset.deltaforcestrike.round.RoundState;
import org.starset.deltaforcestrike.util.Worlds;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 局内死亡旁观：强制附着友方存活玩家。
 * 购买阶段不锁，便于回合复活取消旁观。
 */
public class SpectatorLockService {

    private final DeltaForceStrike plugin;

    public SpectatorLockService(DeltaForceStrike plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("spectator.lock-to-teammates", true);
    }

    /**
     * 是否强制锁观战。
     * BUY 阶段：false（全体应在场上）。
     * 仅 COMBAT / BOMB_PLANTED 且 session 已阵亡：true。
     */
    public boolean shouldLock(Player player) {
        if (!isEnabled() || player == null || !player.isOnline()) {
            return false;
        }
        if (!Worlds.isArena(player)) {
            return false;
        }
        if (!plugin.getMatchManager().isInMatch(player)) {
            return false;
        }

        Match match = plugin.getMatchManager().getMatch();
        if (match == null || match.getState() != MatchState.IN_PROGRESS) {
            return false;
        }

        RoundState rs = match.getRoundManager().getState();
        // ★ 购买 / 回合结算：不锁旁观
        if (rs == RoundState.BUY || rs == RoundState.ROUND_END || rs == RoundState.IDLE) {
            return false;
        }

        PlayerSession session = match.getSession(player.getUniqueId());
        if (session == null || !session.hasTeam()) {
            return false;
        }

        // 只有明确阵亡才锁
        return !session.isAlive();
    }

    public void onEnterSpectator(Player dead) {
        if (!shouldLock(dead)) {
            return;
        }
        dead.setGameMode(GameMode.SPECTATOR);
        Player target = findBestTeammate(dead);
        if (target != null) {
            attach(dead, target);
            dead.sendActionBar(net.kyori.adventure.text.Component.text(
                    "§7观战队友: §f" + target.getName() + " §8| 滚轮可切换"));
        } else {
            dead.setSpectatorTarget(null);
            dead.sendActionBar(net.kyori.adventure.text.Component.text("§7暂无存活队友可观战"));
        }
    }

    public void tickPlayer(Player spectator) {
        if (!shouldLock(spectator)) {
            return;
        }
        if (spectator.getGameMode() != GameMode.SPECTATOR) {
            spectator.setGameMode(GameMode.SPECTATOR);
        }

        Entity current = spectator.getSpectatorTarget();
        Player curPlayer = current instanceof Player p ? p : null;

        if (curPlayer != null && isValidTeammateTarget(spectator, curPlayer)) {
            return;
        }

        Player next = findBestTeammate(spectator);
        if (next != null) {
            attach(spectator, next);
        } else {
            spectator.setSpectatorTarget(null);
            softLimitVelocity(spectator);
        }
    }

    public void tickAll() {
        if (!isEnabled()) {
            return;
        }
        Match match = plugin.getMatchManager().getMatch();
        if (match == null || match.getState() != MatchState.IN_PROGRESS) {
            return;
        }
        // BUY 阶段：确保不把人锁回旁观
        if (match.getRoundManager().getState() == RoundState.BUY) {
            return;
        }

        for (Player p : match.onlinePlayers()) {
            PlayerSession s = match.getSession(p.getUniqueId());
            if (s != null && !s.isAlive()) {
                tickPlayer(p);
            }
        }
    }

    private void attach(Player spectator, Player target) {
        try {
            spectator.setSpectatorTarget(target);
        } catch (Throwable t) {
            spectator.teleport(target.getLocation());
        }
    }

    private boolean isValidTeammateTarget(Player spectator, Player target) {
        if (target == null || !target.isOnline()) {
            return false;
        }
        if (target.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        Match match = plugin.getMatchManager().getMatch();
        if (match == null) {
            return false;
        }
        PlayerSession a = match.getSession(spectator.getUniqueId());
        PlayerSession b = match.getSession(target.getUniqueId());
        if (a == null || b == null) {
            return false;
        }
        if (!a.hasTeam() || a.getTeam() != b.getTeam()) {
            return false;
        }
        return b.isAlive();
    }

    public Player findBestTeammate(Player spectator) {
        Match match = plugin.getMatchManager().getMatch();
        if (match == null) {
            return null;
        }
        PlayerSession self = match.getSession(spectator.getUniqueId());
        if (self == null || !self.hasTeam()) {
            return null;
        }
        Team team = self.getTeam();

        List<Player> candidates = new ArrayList<>();
        for (Player p : match.onlinePlayers()) {
            if (p.getUniqueId().equals(spectator.getUniqueId())) {
                continue;
            }
            PlayerSession s = match.getSession(p.getUniqueId());
            if (s == null || s.getTeam() != team || !s.isAlive()) {
                continue;
            }
            if (p.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            candidates.add(p);
        }
        if (candidates.isEmpty()) {
            return null;
        }

        return candidates.stream()
                .min(Comparator.comparingDouble(p ->
                        p.getLocation().distanceSquared(spectator.getLocation())))
                .orElse(candidates.get(0));
    }

    public void cycleTeammate(Player spectator, int direction) {
        if (!shouldLock(spectator)) {
            return;
        }
        Match match = plugin.getMatchManager().getMatch();
        if (match == null) {
            return;
        }
        PlayerSession self = match.getSession(spectator.getUniqueId());
        if (self == null) {
            return;
        }

        List<Player> list = new ArrayList<>();
        for (Player p : match.onlinePlayers()) {
            if (p.equals(spectator)) {
                continue;
            }
            PlayerSession s = match.getSession(p.getUniqueId());
            if (s != null && s.getTeam() == self.getTeam() && s.isAlive()
                    && p.getGameMode() != GameMode.SPECTATOR) {
                list.add(p);
            }
        }
        if (list.isEmpty()) {
            return;
        }
        list.sort(Comparator.comparing(Player::getName));

        Entity cur = spectator.getSpectatorTarget();
        int idx = 0;
        if (cur instanceof Player cp) {
            int i = list.indexOf(cp);
            if (i >= 0) {
                idx = i;
            }
        }
        idx = Math.floorMod(idx + direction, list.size());
        Player next = list.get(idx);
        attach(spectator, next);
        spectator.sendActionBar(net.kyori.adventure.text.Component.text(
                "§7观战: §f" + next.getName() + " §8(" + (idx + 1) + "/" + list.size() + ")"));
    }

    private void softLimitVelocity(Player spectator) {
        Vector v = spectator.getVelocity();
        if (v.lengthSquared() > 1.0) {
            spectator.setVelocity(v.multiply(0.3));
        }
    }

    /**
     * 回合复活 / 购买阶段：解除附着。
     */
    public void clear(Player player) {
        if (player == null) {
            return;
        }
        try {
            player.setSpectatorTarget(null);
        } catch (Throwable ignored) {
        }
    }
}
