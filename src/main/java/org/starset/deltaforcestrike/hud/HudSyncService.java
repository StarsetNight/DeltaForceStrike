package org.starset.deltaforcestrike.hud;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.bomb.BombManager;
import org.starset.deltaforcestrike.item.ItemManager;
import org.starset.deltaforcestrike.match.Match;
import org.starset.deltaforcestrike.match.MatchState;
import org.starset.deltaforcestrike.match.PlayerSession;
import org.starset.deltaforcestrike.match.Team;
import org.starset.deltaforcestrike.round.RoundState;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 向安装了 ClientUI 的玩家推送 CS2 风格 HUD 数据。
 * 频道: {@value #CHANNEL}
 */
public final class HudSyncService implements PluginMessageListener {

    public static final String CHANNEL = "deltaforcestrike:hud";
    private static final byte PROTOCOL = 4;

    private final DeltaForceStrike plugin;

    public HudSyncService(DeltaForceStrike plugin) {
        this.plugin = plugin;
    }

    public void register() {
        var messenger = Bukkit.getMessenger();
        messenger.registerOutgoingPluginChannel(plugin, CHANNEL);
        messenger.registerIncomingPluginChannel(plugin, CHANNEL, this);
    }

    public void unregister() {
        var messenger = Bukkit.getMessenger();
        messenger.unregisterOutgoingPluginChannel(plugin, CHANNEL);
        messenger.unregisterIncomingPluginChannel(plugin, CHANNEL, this);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel) || player == null) {
            return;
        }
        sendTo(player);
    }

    public void tick() {
        Match match = plugin.getMatchManager().getMatch();
        if (match == null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                sendInactive(p);
            }
            return;
        }
        for (Player p : match.onlinePlayers()) {
            sendTo(p);
        }
    }

    public void sendTo(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Match match = plugin.getMatchManager().getMatch();
        if (match == null || !match.contains(player.getUniqueId())) {
            sendInactive(player);
            return;
        }
        PlayerSession self = match.getSession(player.getUniqueId());
        if (self == null) {
            sendInactive(player);
            return;
        }

        try {
            byte[] data = encodeActive(match, self);
            player.sendPluginMessage(plugin, CHANNEL, data);
        } catch (IOException e) {
            plugin.getLogger().warning("HUD 同步失败: " + e.getMessage());
        }
    }

    private void sendInactive(Player player) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(4);
            DataOutputStream out = new DataOutputStream(baos);
            out.writeByte(PROTOCOL);
            out.writeBoolean(false);
            player.sendPluginMessage(plugin, CHANNEL, baos.toByteArray());
        } catch (IOException ignored) {
        }
    }

    private byte[] encodeActive(Match match, PlayerSession self) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(256);
        DataOutputStream out = new DataOutputStream(baos);

        MatchState ms = match.getState();
        RoundState rs = match.getRoundManager().getState();
        int secondsLeft = match.getRoundManager().getSecondsLeft();
        boolean bombPlanted = plugin.getBombManager() != null && plugin.getBombManager().isPlanted();
        int fuseLeft = plugin.getBombManager() != null ? plugin.getBombManager().getFuseLeft() : -1;
        if (bombPlanted && fuseLeft >= 0) {
            secondsLeft = fuseLeft;
        }

        int winTarget = plugin.getConfig().getInt("match.win-target", 13);
        int halfRound = plugin.getConfig().getInt("match.half-round", 12);
        boolean halfSwapped = match.getRoundManager().isHalfTimeSwapped();

        // 下包/拆包：同步 active/类型/已进行tick/总tick；进度客户端本地匀速，用 elapsed 校准起点
        boolean channelActive = false;
        String channelType = "";
        String channelPlayer = "";
        int channelElapsedTicks = 0;
        int channelTotalTicks = 0;
        String planterName = "";
        if (plugin.getBombManager() != null) {
            var info = plugin.getBombManager().info();
            channelActive = info.channelActive;
            channelType = info.channelType == null ? "" : info.channelType;
            channelPlayer = info.channelPlayer == null ? "" : info.channelPlayer;
            channelElapsedTicks = info.channelElapsedTicks;
            channelTotalTicks = info.channelTotalTicks;
            planterName = info.planterName == null ? "" : info.planterName;
        }

        out.writeByte(PROTOCOL);
        out.writeBoolean(true);
        writeString(out, ms.name());
        writeString(out, rs.name());
        out.writeInt(match.getScoreT());
        out.writeInt(match.getScoreCT());
        out.writeInt(match.getCurrentRound());
        out.writeInt(Math.max(0, secondsLeft));
        out.writeBoolean(bombPlanted);
        out.writeInt(fuseLeft);
        writeString(out, self.getTeam().name());
        out.writeInt(self.getMoney());
        out.writeInt(self.getKills());
        out.writeInt(self.getDeaths());
        out.writeBoolean(self.isAlive());
        writeString(out, self.getOperatorId() == null ? "" : self.getOperatorId());
        out.writeInt(winTarget);
        out.writeInt(halfRound);
        out.writeBoolean(halfSwapped);
        Team lastWinner = match.getLastRoundWinner();
        writeString(out, lastWinner == null || lastWinner == Team.NONE ? "" : lastWinner.name());
        out.writeBoolean(channelActive);
        writeString(out, channelType);
        writeString(out, channelPlayer);
        out.writeInt(channelElapsedTicks);
        out.writeInt(channelTotalTicks);
        writeString(out, planterName);

        List<TeammateSnapshot> mates = collectTeammates(match, self);
        out.writeInt(mates.size());
        for (TeammateSnapshot m : mates) {
            writeString(out, m.name);
            out.writeInt(m.healthPercent);
            out.writeBoolean(m.alive);
            out.writeBoolean(m.hasBomb);
            out.writeBoolean(m.self);
            writeString(out, m.operatorId);
        }

        return baos.toByteArray();
    }

    private List<TeammateSnapshot> collectTeammates(Match match, PlayerSession self) {
        List<TeammateSnapshot> list = new ArrayList<>();
        if (self.getTeam() != Team.T && self.getTeam() != Team.CT) {
            list.add(snapshot(self, true));
            return list;
        }
        for (PlayerSession s : match.getSessions().values()) {
            if (s.getTeam() != self.getTeam()) {
                continue;
            }
            list.add(snapshot(s, s.getUuid().equals(self.getUuid())));
        }
        list.sort((a, b) -> {
            if (a.self != b.self) {
                return a.self ? -1 : 1;
            }
            return a.name.compareToIgnoreCase(b.name);
        });
        return list;
    }

    private TeammateSnapshot snapshot(PlayerSession s, boolean isSelf) {
        Player p = Bukkit.getPlayer(s.getUuid());
        int hp = 0;
        boolean hasBomb = false;
        if (p != null && p.isOnline() && s.isAlive() && s.isConnected()) {
            double max = 20.0;
            var attr = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            if (attr != null) {
                max = Math.max(1.0, attr.getValue());
            }
            hp = (int) Math.round(Math.max(0.0, Math.min(1.0, p.getHealth() / max)) * 100.0);
            hasBomb = carriesBomb(p);
        } else if (s.isAlive() && s.isConnected()) {
            hp = 100;
        }
        String op = s.getOperatorId() == null ? "" : s.getOperatorId();
        return new TeammateSnapshot(s.getName(), hp, s.isAlive() && s.isConnected(), hasBomb, isSelf, op);
    }

    private boolean carriesBomb(Player player) {
        ItemManager items = plugin.getItemManager();
        if (items == null || plugin.getBombManager() == null) {
            return false;
        }
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && plugin.getBombManager().isPlantBomb(stack)) {
                return true;
            }
        }
        return false;
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        if (value == null) {
            value = "";
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private record TeammateSnapshot(
            String name,
            int healthPercent,
            boolean alive,
            boolean hasBomb,
            boolean self,
            String operatorId
    ) {
    }
}
