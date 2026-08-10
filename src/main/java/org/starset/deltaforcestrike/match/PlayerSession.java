package org.starset.deltaforcestrike.match;

import org.bukkit.entity.Player;
import org.starset.deltaforcestrike.spectator.SpectatorRole;
import org.starset.deltaforcestrike.util.ConfigKeys;

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
    /** 本条命是否已计入死亡（防重复） */
    private boolean deathCounted;
    /** 在对局中的角色：参赛 / 占名额观战 / 不占名额导播 */
    private SpectatorRole role = SpectatorRole.PLAYING;

    public PlayerSession(Player player, int startMoney) {
        this.uuid = player.getUniqueId();
        this.name = player.getName();
        this.money = startMoney;
    }

    public SpectatorRole getRole() {
        return role == null ? SpectatorRole.PLAYING : role;
    }

    public void setRole(SpectatorRole role) {
        this.role = role == null ? SpectatorRole.PLAYING : role;
    }

    /** 是否为参赛选手（占 T/CT 名额） */
    public boolean isPlaying() {
        return getRole() == SpectatorRole.PLAYING;
    }

    /** 是否为占名额的旁观者（ spectator + T + CT = max ） */
    public boolean isSpectatorSlot() {
        return getRole() == SpectatorRole.SPECTATOR;
    }

    /** 是否为导播（不占任何名额，自由飞行观战） */
    public boolean isObserver() {
        return getRole() == SpectatorRole.OBSERVER;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public Team getTeam() {
        return team;
    }

    public void setTeam(Team team) {
        this.team = team == null ? Team.NONE : team;
    }

    public boolean hasTeam() {
        return team == Team.T || team == Team.CT;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    public int getMoney() {
        return money;
    }

    public void setMoney(int money) {
        int cap = ConfigKeys.maxMoney();
        // 任何途径都不能超过经济上限
        this.money = Math.max(0, Math.min(cap, money));
    }

    public void addMoney(int amount) {
        if (amount <= 0) {
            setMoney(this.money + amount);
            return;
        }
        setMoney(this.money + amount);
    }

    public boolean spend(int amount) {
        if (amount <= 0) {
            return true;
        }
        if (money < amount) {
            return false;
        }
        // 扣款后仍夹在 [0, max]
        setMoney(money - amount);
        return true;
    }

    public int getKills() {
        return kills;
    }

    public void addKill() {
        kills++;
    }

    public int getDeaths() {
        return deaths;
    }

    public void addDeath() {
        deaths++;
    }

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
        if (alive) {
            deathCounted = false;
        }
    }

    /**
     * @return true 表示本次是第一次计入死亡
     */
    public boolean markDeathCounted() {
        if (deathCounted) {
            return false;
        }
        deathCounted = true;
        return true;
    }

    public void resetDeathCounted() {
        deathCounted = false;
    }

    public boolean isDeathCountedMark() {
        return deathCounted;
    }

    public void setDeathCounted(boolean v) {
        deathCounted = v;
    }

    public int getConsecutiveLosses() {
        return consecutiveLosses;
    }

    public void setConsecutiveLosses(int n) {
        this.consecutiveLosses = Math.max(0, n);
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }
}
