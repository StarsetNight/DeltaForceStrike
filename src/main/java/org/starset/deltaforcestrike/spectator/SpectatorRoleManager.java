package org.starset.deltaforcestrike.spectator;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.match.Match;
import org.starset.deltaforcestrike.match.MatchState;
import org.starset.deltaforcestrike.match.PlayerSession;
import org.starset.deltaforcestrike.match.Team;
import org.starset.deltaforcestrike.round.RoundState;
import org.starset.deltaforcestrike.util.Worlds;

/**
 * 管理 SPECTATOR / OBSERVER 角色行为：
 *
 * <ul>
 *   <li>SPECTATOR：占用房间总名额（spectator + T + CT = max-players），观战 UI 与比赛选手一致，
 *       战斗阶段自动观战，购买阶段可离场但不能干扰。</li>
 *   <li>OBSERVER：导播，不占用任何名额，全程自由飞行（SPECTATOR 模式 + 无附身），
 *       接收 Live UI 数据，不参与胜负判定、不被 SpectatorLock 限制。</li>
 * </ul>
 *
 * <p>本类仅处理角色行为；名额（入队与否）由 {@link
 * org.starset.deltaforcestrike.match.MatchManager} 通过 PlayerSession.role 控制。</p>
 */
public final class SpectatorRoleManager {

    private final DeltaForceStrike plugin;

    public SpectatorRoleManager(DeltaForceStrike plugin) {
        this.plugin = plugin;
    }

    public void applyRole(Player player) {
        if (player == null || !player.isOnline() || !Worlds.isArena(player)) return;
        Match match = plugin.getMatchManager().getMatch();
        if (match == null || !match.contains(player.getUniqueId())) return;
        PlayerSession s = match.getSession(player.getUniqueId());
        if (s == null) return;

        switch (s.getRole()) {
            case PLAYING -> { /* 由 RoundManager / SpectatorLockService 处理 */ }
            case SPECTATOR -> setupSpectator(player, false);
            case OBSERVER -> setupSpectator(player, true);
        }
    }

    /** 设置为旁观/导播模式 */
    private void setupSpectator(Player player, boolean observer) {
        // 离开任何锁定旁观附着
        if (plugin.getSpectatorLockService() != null) {
            plugin.getSpectatorLockService().clear(player);
        }
        try { player.setSpectatorTarget(null); } catch (Throwable ignored) {}

        player.getInventory().clear();
        player.getInventory().setHelmet(null);
        player.getInventory().setChestplate(null);
        player.getInventory().setLeggings(null);
        player.getInventory().setBoots(null);
        player.getInventory().setItemInOffHand(null);
        for (var pe : player.getActivePotionEffects()) {
            player.removePotionEffect(pe.getType());
        }
        player.setFireTicks(0);
        player.setFallDistance(0f);

        player.setGameMode(GameMode.SPECTATOR);
        player.setInvulnerable(true);
        player.setFlying(true);
        player.setAllowFlight(true);

        if (observer) {
            player.sendMessage("§b[DFS] 已进入导播模式 §7(自由飞行·不占名额)");
        } else {
            player.sendMessage("§7[DFS] 已进入观战模式 §7(占名额·观战队友)");
        }
    }

    /** 每回合刷新：购买阶段开始时把非参赛者重新设为旁观/导播 */
    public void onRoundStart() {
        Match match = plugin.getMatchManager().getMatch();
        if (match == null) return;
        for (Player p : match.onlinePlayers()) {
            PlayerSession s = match.getSession(p.getUniqueId());
            if (s == null) continue;
            if (s.getRole() != SpectatorRole.PLAYING) {
                applyRole(p);
            }
        }
    }

    /** 战斗阶段：旁观者观战任意存活选手；导播自由（不做附身强制） */
    public void tick() {
        Match match = plugin.getMatchManager().getMatch();
        if (match == null || match.getState() != MatchState.IN_PROGRESS) return;
        RoundState rs = match.getRoundManager().getState();
        if (rs == RoundState.BUY || rs == RoundState.IDLE || rs == RoundState.ROUND_END) return;

        for (Player p : match.onlinePlayers()) {
            PlayerSession s = match.getSession(p.getUniqueId());
            if (s == null) continue;
            if (s.getRole() == SpectatorRole.SPECTATOR) {
                ensureSpectatorMode(p);
                if (p.getSpectatorTarget() == null) {
                    attachToAnyAlive(p, match);
                }
            } else if (s.getRole() == SpectatorRole.OBSERVER) {
                ensureSpectatorMode(p);
                // 导播不强制附身，保留自由飞行
            }
        }
    }

    private void ensureSpectatorMode(Player p) {
        if (p.getGameMode() != GameMode.SPECTATOR) {
            p.setGameMode(GameMode.SPECTATOR);
            p.setFlying(true);
            p.setAllowFlight(true);
        }
    }

    private void attachToAnyAlive(Player p, Match match) {
        Player target = null;
        for (Player c : match.onlinePlayers()) {
            PlayerSession cs = match.getSession(c.getUniqueId());
            if (cs == null || !cs.isPlaying() || !cs.isAlive()) continue;
            if (c.getGameMode() == GameMode.SPECTATOR) continue;
            target = c;
            break;
        }
        if (target != null) {
            try { p.setSpectatorTarget(target); } catch (Throwable ignored) {}
        }
    }

    /** 退场时由 MatchManager 调用：清理 role */
    public void clear(Player player) {
        if (player == null) return;
        try { player.setSpectatorTarget(null); } catch (Throwable ignored) {}
        try {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                player.setGameMode(GameMode.ADVENTURE);
            }
        } catch (Throwable ignored) {}
    }

    /** 把某人提升为导播 */
    public boolean setRole(Player player, SpectatorRole role) {
        Match match = plugin.getMatchManager().getMatch();
        if (match == null || !match.contains(player.getUniqueId())) return false;
        PlayerSession s = match.getSession(player.getUniqueId());
        if (s == null) return false;
        if (s.getRole() == role) {
            player.sendMessage("§7你已是该角色。");
            return true;
        }
        // 比赛进行中：参赛选手不能切为观战/导播（避免影响对局）
        if (s.isPlaying() && role != SpectatorRole.PLAYING
                && match.getState() == MatchState.IN_PROGRESS) {
            player.sendMessage("§c[DFS] 比赛进行中，选手不能切换为观战/导播。");
            return false;
        }
        s.setRole(role);
        // 切换到非参赛角色时，队伍清掉以避免影响 countTeam 的 T/CT 计算
        if (role != SpectatorRole.PLAYING) {
            s.setTeam(Team.NONE);
            s.setAlive(false);
        } else {
            s.setAlive(true);
        }
        applyRole(player);
        match.broadcast("§7" + player.getName() + " → "
                + (role == SpectatorRole.OBSERVER ? "§b导播"
                        : role == SpectatorRole.SPECTATOR ? "§7观战" : "§a选手"));
        return true;
    }
}
