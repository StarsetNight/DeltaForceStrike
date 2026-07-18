package org.starset.deltaforcestrike.match;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.round.RoundManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Match {

    private final UUID matchId = UUID.randomUUID();
    private MatchState state = MatchState.WAITING;
    private final Map<UUID, PlayerSession> sessions = new LinkedHashMap<>();
    private final RoundManager roundManager;

    private int scoreT;
    private int scoreCT;
    private int currentRound;

    public Match(DeltaForceStrike plugin) {
        this.roundManager = new RoundManager(plugin, this);
    }

    public UUID getMatchId() { return matchId; }
    public MatchState getState() { return state; }
    public void setState(MatchState state) { this.state = state; }
    public Map<UUID, PlayerSession> getSessions() { return sessions; }
    public PlayerSession getSession(UUID uuid) { return sessions.get(uuid); }
    public RoundManager getRoundManager() { return roundManager; }
    public int getScoreT() { return scoreT; }
    public int getScoreCT() { return scoreCT; }
    public int getCurrentRound() { return currentRound; }
    public void setCurrentRound(int currentRound) { this.currentRound = currentRound; }

    public void addScore(Team team) {
        if (team == Team.T) scoreT++;
        else if (team == Team.CT) scoreCT++;
    }

    public void swapScores() {
        int tmp = scoreT;
        scoreT = scoreCT;
        scoreCT = tmp;
    }

    public int size() { return sessions.size(); }
    public boolean isFull(int max) { return sessions.size() >= max; }
    public boolean contains(UUID uuid) { return sessions.containsKey(uuid); }

    public List<Player> onlinePlayers() {
        List<Player> list = new ArrayList<>();
        for (UUID id : sessions.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) list.add(p);
        }
        return list;
    }

    public long countTeam(Team team) {
        return sessions.values().stream().filter(s -> s.getTeam() == team).count();
    }

    public void broadcast(String msg) {
        for (Player p : onlinePlayers()) p.sendMessage(msg);
    }

    public void broadcast(Component component) {
        for (Player p : onlinePlayers()) p.sendMessage(component);
    }

    public boolean allDead(Team team) {
        boolean any = false;
        for (PlayerSession s : sessions.values()) {
            if (s.getTeam() != team || !s.isConnected()) continue;
            any = true;
            if (s.isAlive()) return false;
        }
        return any;
    }
}
