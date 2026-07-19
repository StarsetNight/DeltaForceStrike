package org.starset.deltaforcestrike.bomb;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.item.ItemKeys;
import org.starset.deltaforcestrike.item.ItemManager;
import org.starset.deltaforcestrike.match.Match;
import org.starset.deltaforcestrike.match.MatchState;
import org.starset.deltaforcestrike.match.PlayerSession;
import org.starset.deltaforcestrike.match.Team;
import org.starset.deltaforcestrike.round.RoundState;
import org.starset.deltaforcestrike.util.BombSites;
import org.starset.deltaforcestrike.util.InventorySlots;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 改造 TNT：包点安装 / 引信读秒 / 拆除。
 * 引信：fuseLeft 为剩余整秒；每 20 tick 减 1，减到 0 当帧 explode。
 * 滴滴：安放点音符盒音效；&gt;10s 半径 50；≤10s 加速且听音半径扩至 200。
 */
public class BombManager {

    private final DeltaForceStrike plugin;

    private boolean planted;
    private Location plantLocation;
    private TNTPrimed primed;
    private BukkitTask fuseTask;
    private BukkitTask beepTask;
    private int fuseLeft = -1;
    /** 距下次滴滴的 tick 倒计时 */
    private int beepCooldownTicks;

    private final Map<UUID, BukkitTask> channelTasks = new HashMap<>();
    private final Map<UUID, BossBar> channelBars = new HashMap<>();

    public BombManager(DeltaForceStrike plugin) {
        this.plugin = plugin;
    }

    public boolean isPlanted() {
        return planted;
    }

    /** 剩余整秒；未安包为 -1 */
    public int getFuseLeft() {
        return planted ? fuseLeft : -1;
    }

    public Location getPlantLocation() {
        return plantLocation == null ? null : plantLocation.clone();
    }

    public void reset() {
        cancelFuse();
        clearAllChannels();
        if (primed != null && primed.isValid()) {
            primed.remove();
        }
        primed = null;
        planted = false;
        plantLocation = null;
        fuseLeft = -1;
    }

    // =====================================================================
    // 安装
    // =====================================================================

    public boolean tryBeginPlant(Player player, Block against) {
        Match match = plugin.getMatchManager().getMatch();
        if (cannotPlant(player, match)) {
            return false;
        }
        if (planted) {
            player.sendActionBar(Component.text("炸弹已安装", NamedTextColor.RED));
            return true;
        }
        if (channelTasks.containsKey(player.getUniqueId())) {
            return true;
        }

        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack bombSlot = player.getInventory().getItem(InventorySlots.BOMB);
        if (!isPlantBomb(main) && !isPlantBomb(bombSlot)) {
            return false;
        }

        Location plantLoc = against.getRelative(0, 1, 0).getLocation().add(0.5, 0, 0.5);
        if (!BombSites.isInAnySite(player.getLocation()) && !BombSites.isInAnySite(plantLoc)) {
            player.sendMessage("§c只能在包点安装改造TNT！§7 包点: " + BombSites.describeSites());
            player.sendActionBar(Component.text("不在包点范围内", NamedTextColor.RED));
            return true;
        }

        int plantTime = plugin.getConfig().getInt("bomb.plant-time", 3);
        Location base = plantLoc;

        startChannel(player, plantTime,
                Component.text("安装改造TNT…", NamedTextColor.RED),
                () -> {
                    if (cannotPlant(player, plugin.getMatchManager().getMatch()) || planted) {
                        return;
                    }
                    if (!BombSites.isInAnySite(player.getLocation())) {
                        player.sendMessage("§c已离开包点，安装失败。");
                        return;
                    }
                    finishPlant(player, base);
                });
        return true;
    }

    /** @return true 表示当前不可安装 */
    private boolean cannotPlant(Player player, Match match) {
        if (match == null || match.getState() != MatchState.IN_PROGRESS) {
            return true;
        }
        if (match.getRoundManager().getState() != RoundState.COMBAT) {
            return true;
        }
        PlayerSession s = match.getSession(player.getUniqueId());
        return s == null || s.getTeam() != Team.T || !s.isAlive();
    }

    private void finishPlant(Player player, Location loc) {
        Match match = plugin.getMatchManager().getMatch();
        if (match == null) {
            return;
        }

        consumePlantBomb(player);
        planted = true;
        plantLocation = loc.clone();
        fuseLeft = plugin.getConfig().getInt("bomb.explosion-time", 40);

        // 实体仅展示；逻辑 explode 为准。引信略长，避免原版先炸
        primed = loc.getWorld().spawn(loc, TNTPrimed.class, tnt -> {
            tnt.setFuseTicks((fuseLeft + 2) * 20);
            tnt.setYield(0f);
            tnt.setIsIncendiary(false);
            tnt.setSource(player);
            tnt.customName(Component.text("改造TNT " + fuseLeft + "s", NamedTextColor.RED));
            tnt.setCustomNameVisible(true);
        });

        PlayerSession planter = match.getSession(player.getUniqueId());
        if (planter != null) {
            planter.addMoney(plugin.getConfig().getInt("economy.plant-reward", 300));
        }

        match.broadcast("§4§l[DFS] 改造TNT已安装！§c" + fuseLeft + " §4秒后爆炸！");
        match.getRoundManager().onBombPlanted();

        cancelFuse();
        startBeepLoop();
        // 安装瞬间先滴一声
        playPlantBeep();

        /*
         * 时间线（explosion-time=40）：
         * t=0  安装，显示 40
         * t=1s 40→39 … t=40s 1→0 当帧 explode
         */
        fuseTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!planted) {
                cancelFuse();
                return;
            }

            fuseLeft--;

            if (fuseLeft <= 0) {
                fuseLeft = 0;
                if (primed != null && primed.isValid()) {
                    primed.customName(Component.text("改造TNT 0s", NamedTextColor.RED));
                }
                cancelFuse();
                explode();
                return;
            }

            if (primed != null && primed.isValid()) {
                primed.setFuseTicks(Math.max(1, fuseLeft) * 20 + 10);
                primed.customName(Component.text("改造TNT " + fuseLeft + "s", NamedTextColor.RED));
                primed.getWorld().spawnParticle(
                        Particle.SMOKE, primed.getLocation(), 5, 0.2, 0.2, 0.2, 0.01);
            }
        }, 20L, 20L);
    }

    // =====================================================================
    // 滴滴音效（音符盒 · 与引信并行）
    // =====================================================================

    private void startBeepLoop() {
        cancelBeep();
        beepCooldownTicks = 0;
        beepTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!planted || plantLocation == null) {
                cancelBeep();
                return;
            }
            if (beepCooldownTicks > 0) {
                beepCooldownTicks--;
                return;
            }
            playPlantBeep();
            beepCooldownTicks = Math.max(1, currentBeepIntervalTicks() - 1);
        }, 1L, 1L);
    }

    private void cancelBeep() {
        if (beepTask != null) {
            beepTask.cancel();
            beepTask = null;
        }
        beepCooldownTicks = 0;
    }

    /**
     * &gt;10s：约 1 次/秒；≤10s：间隔从 ~1s 线性加速到 ~0.1s。
     */
    private int currentBeepIntervalTicks() {
        int early = plugin.getConfig().getInt("bomb.beep.early-interval-ticks", 20);
        int lateMin = plugin.getConfig().getInt("bomb.beep.late-min-interval-ticks", 2);
        int threshold = plugin.getConfig().getInt("bomb.beep.late-threshold-seconds", 10);
        early = Math.max(2, early);
        lateMin = Math.max(1, Math.min(lateMin, early));
        threshold = Math.max(1, threshold);

        if (fuseLeft > threshold) {
            return early;
        }
        // fuseLeft: threshold → 0  时 interval: early → lateMin
        double t = Math.max(0.0, Math.min(1.0, (double) fuseLeft / threshold));
        return Math.max(lateMin, (int) Math.round(lateMin + (early - lateMin) * t));
    }

    /** 听音半径：早期 50；最后 threshold 秒内线性扩到 200 */
    private double currentBeepRadius() {
        double earlyR = plugin.getConfig().getDouble("bomb.beep.early-radius", 50.0);
        double lateR = plugin.getConfig().getDouble("bomb.beep.late-max-radius", 200.0);
        int threshold = plugin.getConfig().getInt("bomb.beep.late-threshold-seconds", 10);
        threshold = Math.max(1, threshold);
        if (fuseLeft > threshold) {
            return earlyR;
        }
        double t = Math.max(0.0, Math.min(1.0, 1.0 - (double) fuseLeft / threshold));
        return earlyR + (lateR - earlyR) * t;
    }

    private float currentBeepPitch() {
        int threshold = plugin.getConfig().getInt("bomb.beep.late-threshold-seconds", 10);
        threshold = Math.max(1, threshold);
        if (fuseLeft > threshold) {
            return 0.9f;
        }
        double t = Math.max(0.0, Math.min(1.0, 1.0 - (double) fuseLeft / threshold));
        return (float) (0.9 + 0.9 * t); // 0.9 → 1.8
    }

    private void playPlantBeep() {
        if (!plugin.getConfig().getBoolean("bomb.beep.enabled", true)) {
            return;
        }
        Location loc = plantLocation;
        if (loc == null && primed != null && primed.isValid()) {
            loc = primed.getLocation();
        }
        if (loc == null || loc.getWorld() == null) {
            return;
        }

        Sound sound = resolveBeepSound();
        double radius = currentBeepRadius();
        float pitch = currentBeepPitch();
        // volume 按半径放大，保证远处仍能听清方向
        float volume = (float) Math.max(1.0, radius / 16.0);

        World world = loc.getWorld();
        double rSq = radius * radius;
        Match match = plugin.getMatchManager().getMatch();

        for (Player p : world.getPlayers()) {
            if (match != null && !match.contains(p.getUniqueId())) {
                continue;
            }
            if (p.getLocation().distanceSquared(loc) > rSq) {
                continue;
            }
            // 在安放点播放，保留方位感
            p.playSound(loc, sound, SoundCategory.BLOCKS, volume, pitch);
        }
    }

    /**
     * 通过 Registry 解析音效（避免 1.21.3+ 已弃用的 Sound.valueOf）。
     * 配置可写：block.note_block.pling / BLOCK_NOTE_BLOCK_PLING / pling
     */
    private Sound resolveBeepSound() {
        Sound fallback = registrySound("block.note_block.pling");
        String raw = plugin.getConfig().getString("bomb.beep.sound", "block.note_block.pling");
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        Sound sound = registrySound(toSoundPath(raw));
        return sound != null ? sound : fallback;
    }

    private static Sound registrySound(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        try {
            String p = path.toLowerCase(Locale.ROOT).replace("minecraft:", "").trim();
            NamespacedKey nk = NamespacedKey.minecraft(p);
            return RegistryAccess.registryAccess()
                    .getRegistry(RegistryKey.SOUND_EVENT)
                    .get(nk);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 枚举名 / 路径 → minecraft 音效路径 */
    private static String toSoundPath(String raw) {
        String s = raw.trim().toLowerCase(Locale.ROOT)
                .replace("minecraft:", "")
                .replace('-', '_')
                .replace(' ', '_');
        if (s.isEmpty() || "pling".equals(s)) {
            return "block.note_block.pling";
        }
        // 已是 registry 路径
        if (s.contains(".")) {
            return s.replace("block.note.block.", "block.note_block.");
        }
        // BLOCK_NOTE_BLOCK_PLING 风格
        if (s.startsWith("block_note_block_")) {
            return "block.note_block." + s.substring("block_note_block_".length());
        }
        if (s.startsWith("block_")) {
            String rest = s.substring("block_".length());
            int last = rest.lastIndexOf('_');
            if (last > 0) {
                return "block." + rest.substring(0, last) + "." + rest.substring(last + 1);
            }
            return "block." + rest;
        }
        if (s.startsWith("entity_")) {
            String rest = s.substring("entity_".length());
            int last = rest.lastIndexOf('_');
            if (last > 0) {
                return "entity." + rest.substring(0, last) + "." + rest.substring(last + 1);
            }
            return "entity." + rest;
        }
        if (s.startsWith("ui_")) {
            return "ui." + s.substring("ui_".length()).replace('_', '.');
        }
        if (s.startsWith("note_block_")) {
            return "block.note_block." + s.substring("note_block_".length());
        }
        return "block.note_block." + s;
    }

    private void explode() {
        Match match = plugin.getMatchManager().getMatch();
        Location loc = plantLocation;
        if (primed != null && primed.isValid()) {
            loc = primed.getLocation();
            primed.remove();
        }
        primed = null;
        planted = false;
        fuseLeft = -1;

        if (loc != null && loc.getWorld() != null) {
            try {
                loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 3);
            } catch (Throwable t) {
                loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 4);
            }
            loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2.5f, 0.7f);

            double radius = plugin.getConfig().getDouble("bomb.damage-radius", 18.0);
            double maxDamage = plugin.getConfig().getDouble("bomb.damage", 36.0);

            for (Player p : loc.getWorld().getPlayers()) {
                if (!plugin.getMatchManager().isInMatch(p)) {
                    continue;
                }
                if (p.getGameMode() == GameMode.SPECTATOR) {
                    continue;
                }
                PlayerSession s = match != null ? match.getSession(p.getUniqueId()) : null;
                if (s != null && !s.isAlive()) {
                    continue;
                }

                double dist = p.getLocation().distance(loc);
                if (dist > radius) {
                    continue;
                }

                double dmg = damageByDistance(dist, radius, maxDamage);
                if (dmg <= 0) {
                    continue;
                }

                p.damage(dmg);
                try {
                    Vector knock = p.getLocation().toVector().subtract(loc.toVector());
                    if (knock.lengthSquared() < 0.01) {
                        knock = p.getLocation().getDirection().multiply(-1);
                    }
                    knock.normalize();
                    double kbScale = Math.max(0.25, 1.0 - dist / radius);
                    p.setVelocity(knock.multiply(1.5 * kbScale).setY(0.35 + 0.4 * kbScale));
                } catch (Throwable ignored) {
                }
                p.setFallDistance(0f);
            }
        }

        plantLocation = null;

        if (match != null) {
            RoundState rs = match.getRoundManager().getState();
            if (rs != RoundState.ROUND_END && rs != RoundState.IDLE) {
                match.broadcast("§4§l[DFS] 改造TNT爆炸！进攻方胜利！");
                match.getRoundManager().endRound(Team.T, "炸弹爆炸");
            }
        }
    }

    /** 0-20% 100% | 20-40% 75% | 40-60% 50% | 60-80% 30% | 80-100% 15% */
    private double damageByDistance(double dist, double radius, double maxDamage) {
        if (radius <= 0 || dist > radius) {
            return 0;
        }
        double ratio = dist / radius;
        double mult;
        if (ratio <= 0.20) {
            mult = 1.00;
        } else if (ratio <= 0.40) {
            mult = 0.75;
        } else if (ratio <= 0.60) {
            mult = 0.50;
        } else if (ratio <= 0.80) {
            mult = 0.30;
        } else {
            mult = 0.15;
        }
        return Math.max(2.0, maxDamage * mult);
    }

    // =====================================================================
    // 拆除
    // =====================================================================

    public boolean tryBeginDefuse(Player player) {
        Match match = plugin.getMatchManager().getMatch();
        if (cannotDefuse(player, match)) {
            return false;
        }
        if (!planted || plantLocation == null) {
            return false;
        }
        if (channelTasks.containsKey(player.getUniqueId())) {
            return true;
        }
        if (player.getLocation().distanceSquared(plantLocation) > 9.0) {
            player.sendActionBar(Component.text("靠近改造TNT才能拆除", NamedTextColor.RED));
            return false;
        }

        boolean kit = hasDefuseKit(player);
        int time = kit
                ? plugin.getConfig().getInt("bomb.defuse-time-with-kit", 5)
                : plugin.getConfig().getInt("bomb.defuse-time", 10);

        startChannel(player, time,
                Component.text(kit ? "拆除中（拆除钳）…" : "拆除中（空手）…", NamedTextColor.GREEN),
                () -> {
                    if (!planted || cannotDefuse(player, plugin.getMatchManager().getMatch())) {
                        return;
                    }
                    if (plantLocation != null
                            && player.getLocation().distanceSquared(plantLocation) > 9.0) {
                        return;
                    }
                    finishDefuse(player);
                });
        return true;
    }

    /** @return true 表示当前不可拆除 */
    private boolean cannotDefuse(Player player, Match match) {
        if (match == null || match.getState() != MatchState.IN_PROGRESS) {
            return true;
        }
        RoundState rs = match.getRoundManager().getState();
        if (rs != RoundState.BOMB_PLANTED && rs != RoundState.COMBAT) {
            return true;
        }
        PlayerSession s = match.getSession(player.getUniqueId());
        return s == null || s.getTeam() != Team.CT || !s.isAlive();
    }

    private void finishDefuse(Player player) {
        Match match = plugin.getMatchManager().getMatch();
        if (match == null || !planted) {
            return;
        }

        cancelFuse();
        if (primed != null && primed.isValid()) {
            primed.remove();
        }
        primed = null;
        planted = false;
        plantLocation = null;
        fuseLeft = -1;

        PlayerSession defuser = match.getSession(player.getUniqueId());
        if (defuser != null) {
            defuser.addMoney(plugin.getConfig().getInt("economy.defuse-reward", 200));
        }
        match.broadcast("§b§l[DFS] " + player.getName() + " 拆除了改造TNT！");
        match.getRoundManager().endRound(Team.CT, "拆除炸弹");
    }

    // =====================================================================
    // 读条
    // =====================================================================

    private void startChannel(Player player, int seconds, Component barName, Runnable onDone) {
        cancelChannel(player);
        BossBar bar = BossBar.bossBar(barName, 1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        player.showBossBar(bar);
        channelBars.put(player.getUniqueId(), bar);

        Location start = player.getLocation().clone();
        int total = Math.max(1, seconds) * 20;
        final int[] tick = {0};

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                cancelChannel(player);
                return;
            }
            if (player.getLocation().distanceSquared(start) > 0.36) {
                player.sendActionBar(Component.text("动作被打断", NamedTextColor.RED));
                cancelChannel(player);
                return;
            }
            tick[0]++;
            bar.progress(Math.max(0f, 1f - (float) tick[0] / total));
            if (tick[0] >= total) {
                cancelChannel(player);
                onDone.run();
            }
        }, 1L, 1L);
        channelTasks.put(player.getUniqueId(), task);
    }

    public void cancelChannel(Player player) {
        UUID id = player.getUniqueId();
        BukkitTask t = channelTasks.remove(id);
        if (t != null) {
            t.cancel();
        }
        BossBar bar = channelBars.remove(id);
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    public void clearAllChannels() {
        for (UUID id : new HashMap<>(channelTasks).keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                cancelChannel(p);
            } else {
                BukkitTask t = channelTasks.remove(id);
                if (t != null) {
                    t.cancel();
                }
                channelBars.remove(id);
            }
        }
    }

    private void cancelFuse() {
        if (fuseTask != null) {
            fuseTask.cancel();
            fuseTask = null;
        }
        cancelBeep();
    }

    public boolean isOurPrimed(TNTPrimed tnt) {
        return primed != null && primed.equals(tnt);
    }

    public boolean isPlantBomb(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        ItemManager items = plugin.getItemManager();
        if ("bomb".equalsIgnoreCase(items.getItemType(stack))) {
            return true;
        }
        String id = items.getItemId(stack);
        return id != null && id.contains("plant-bomb");
    }

    private boolean hasDefuseKit(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || !stack.hasItemMeta()) {
                continue;
            }
            String action = stack.getItemMeta().getPersistentDataContainer()
                    .get(ItemKeys.action(), PersistentDataType.STRING);
            String id = plugin.getItemManager().getItemId(stack);
            if ("defuse".equalsIgnoreCase(action) || (id != null && id.contains("defuse"))) {
                return true;
            }
        }
        return false;
    }

    private void consumePlantBomb(Player player) {
        ItemStack slot = player.getInventory().getItem(InventorySlots.BOMB);
        if (isPlantBomb(slot)) {
            player.getInventory().setItem(InventorySlots.BOMB, null);
            return;
        }
        ItemStack main = player.getInventory().getItemInMainHand();
        if (isPlantBomb(main)) {
            player.getInventory().setItemInMainHand(null);
            return;
        }
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            if (isPlantBomb(player.getInventory().getItem(i))) {
                player.getInventory().setItem(i, null);
                return;
            }
        }
    }
}
