package org.starset.deltaforcestrike.match;

import org.bukkit.entity.Player;

import java.util.UUID;

public class PlayerSession {

    private final UUID uuid;
    private final String name;
    private Team team = Team.NONE;
    private String operatorId;
    private int money;
    private int kills;
    private int deaths;
    private boolean alive = true;
    private int consecutiveLosses;
    private boolean connected = true;
    private boolean survivedLastRound = true;

    public PlayerSession(Player player, int startMoney) {
        this.uuid = player.getUniqueId();
        this.name = player.getName();
        this.money = startMoney;
    }

    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
    public Team getTeam() { return team; }
    public void setTeam(Team team) { this.team = team == null ? Team.NONE : team; }
    public boolean hasTeam() { return team == Team.T || team == Team.CT; }
    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    public int getMoney() { return money; }
    public void setMoney(int money) { this.money = Math.max(0, money); }
    public void addMoney(int amount) { setMoney(this.money + amount); }
    public boolean spend(int amount) {
        if (money < amount) return false;
        money -= amount;
        return true;
    }
    public int getKills() { return kills; }
    public void addKill() { kills++; }
    public int getDeaths() { return deaths; }
    public void addDeath() { deaths++; }
    public boolean isAlive() { return alive; }
    public void setAlive(boolean alive) { this.alive = alive; }
    public int getConsecutiveLosses() { return consecutiveLosses; }
    public void setConsecutiveLosses(int n) { this.consecutiveLosses = Math.max(0, n); }
    public boolean isConnected() { return connected; }
    public void setConnected(boolean connected) { this.connected = connected; }
    public boolean isSurvivedLastRound() { return survivedLastRound; }
    public void setSurvivedLastRound(boolean v) { this.survivedLastRound = v; }
}
