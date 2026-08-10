package org.starset.deltaforcestrike.tournament;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.util.Worlds;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 赛事模式：开启后只允许已握手的玩家进入竞技世界；超时未握手者踢出。
 *
 * <p>握手协议走自定义 plugin channel {@link #CHANNEL}，payload 为简单文本行：
 * 客户端连上后发送 "DFS_HANDSHAKE " + protocolVersion + " " + modVersion。
 * 服务端校验：协议号匹配 + 期望 mod 名 (可选)，回应 "DFS_OK"。</p>
 *
 * <p>握手通过后写入 {@link #verified}；未通过且超时 -> kick。</p>
 */
public final class TournamentService {

    /** 握手频道（双向） */
    public static final String CHANNEL = "deltaforcestrike:tournament";
    /** 当前服务端要求的握手协议 */
    public static final int PROTOCOL = 1;
    /** 客户端需发送的前缀 */
    public static final String HANDSHAKE_PREFIX = "DFS_HANDSHAKE";
    public static final String OK_PREFIX = "DFS_OK";
    public static final String FAIL_PREFIX = "DFS_FAIL";

    private final DeltaForceStrike plugin;
    private final Set<UUID> verified = new HashSet<>();

    public TournamentService(DeltaForceStrike plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("tournament.enabled", false);
    }

    public boolean isVerified(Player p) {
        return p != null && verified.contains(p.getUniqueId());
    }

    public void markVerified(Player p) {
        if (p != null) verified.add(p.getUniqueId());
    }

    public void onJoin(Player p) {
        if (!isEnabled() || p == null) return;
        if (!Worlds.isArena(p)) return;
        if (isVerified(p)) {
            sendOk(p);
            return;
        }
        // 启动超时检查任务
        scheduleTimeoutCheck(p);
    }

    private void scheduleTimeoutCheck(Player p) {
        if (p == null) return;
        final UUID id = p.getUniqueId();
        int timeoutTicks = Math.max(20, plugin.getConfig().getInt("tournament.timeout-seconds", 10) * 20);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player cur = Bukkit.getPlayer(id);
            if (cur == null || !cur.isOnline()) return;
            if (!Worlds.isArena(cur)) return;
            if (isVerified(cur)) return;
            String msg = plugin.getConfig().getString("tournament.kick-message",
                    "§c[赛事] 未检测到合规客户端，请安装 DeltaForceStrike ClientUI mod 后再加入。");
            cur.kick(net.kyori.adventure.text.Component.text(msg));
        }, timeoutTicks);
    }

    public void onQuit(Player p) {
        if (p == null) return;
        verified.remove(p.getUniqueId());
    }

    /** 处理来自客户端的 plugin message */
    public byte[] handleIncoming(Player p, byte[] data) {
        if (!isEnabled() || p == null || data == null) return null;
        String text = decodePayload(data);
        // text: DFS_HANDSHAKE <protocol> <modVersion> <modId>
        if (!text.startsWith(HANDSHAKE_PREFIX)) {
            return fail("invalid_prefix");
        }
        String[] parts = text.split("\\s+");
        int proto = parts.length >= 2 ? parseInt(parts[1]) : 0;
        String modVer = parts.length >= 3 ? parts[2] : "";
        String modId = parts.length >= 4 ? parts[3] : "";

        if (proto != PROTOCOL) {
            return fail("protocol_mismatch:" + proto + "!=" + PROTOCOL);
        }
        List<String> allowedModIds = plugin.getConfig().getStringList("tournament.allowed-mod-ids");
        if (!allowedModIds.isEmpty() && !allowedModIds.contains(modId)) {
            return fail("modid_not_allowed:" + modId);
        }
        String minVer = plugin.getConfig().getString("tournament.min-mod-version", "");
        if (!minVer.isBlank() && modVer != null
                && compareVer(modVer, minVer) < 0) {
            return fail("mod_too_old:" + modVer + "<" + minVer);
        }

        markVerified(p);
        sendOk(p);
        p.sendMessage("§a[赛事] 客户端握手通过 §7(" + modId + " " + modVer + ")");
        return null;
    }

    /**
     * 解码客户端 payload：
     * <p>Fabric {@code CustomPacketPayload} 的 StreamCodec 会先写 4 字节长度再写 UTF-8 bytes；
     * 兼容纯文本（无长度头）的情况。</p>
     */
    private static String decodePayload(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        // 尝试带长度头：int(4 bytes) + bytes
        if (data.length >= 4) {
            try {
                java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(data);
                java.io.DataInputStream in = new java.io.DataInputStream(bais);
                int len = in.readInt();
                if (len > 0 && len <= 32768 && 4 + len <= data.length) {
                    byte[] body = new byte[len];
                    in.readFully(body);
                    return new String(body, StandardCharsets.UTF_8).trim();
                }
            } catch (Throwable ignored) {
            }
        }
        // 纯文本
        return new String(data, StandardCharsets.UTF_8).trim();
    }

    private void sendOk(Player p) {
        p.sendPluginMessage(plugin, CHANNEL, encodeResponse(OK_PREFIX + " " + PROTOCOL));
    }

    private byte[] fail(String reason) {
        return encodeResponse(FAIL_PREFIX + " " + reason);
    }

    /** 与客户端 TournamentHandshakeResponsePayload.CODEC 对齐：int 长度 + UTF-8 */
    private static byte[] encodeResponse(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.io.DataOutputStream out = new java.io.DataOutputStream(baos);
        try {
            out.writeInt(bytes.length);
            out.write(bytes);
            out.flush();
        } catch (Throwable ignored) {
        }
        return baos.toByteArray();
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    private static int compareVer(String a, String b) {
        String[] as = a.split("\\.");
        String[] bs = b.split("\\.");
        int len = Math.max(as.length, bs.length);
        for (int i = 0; i < len; i++) {
            int ai = i < as.length ? parseInt(as[i]) : 0;
            int bi = i < bs.length ? parseInt(bs[i]) : 0;
            if (ai != bi) return Integer.compare(ai, bi);
        }
        return 0;
    }

    public int verifiedCount() {
        return verified.size();
    }

    public void clearAll() {
        verified.clear();
    }

    /** kick all arena players not yet verified — used when tournament mode just turned on */
    public void sweep() {
        if (!isEnabled()) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!Worlds.isArena(p)) continue;
            if (!isVerified(p)) scheduleTimeoutCheck(p);
        }
    }
}
