package org.starset.deltaforcestrike.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.projectiles.ProjectileSource;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.game.GameRulesService;
import org.starset.deltaforcestrike.item.ItemManager;
import org.starset.deltaforcestrike.match.Match;
import org.starset.deltaforcestrike.match.MatchState;
import org.starset.deltaforcestrike.match.PlayerSession;
import org.starset.deltaforcestrike.round.RoundState;
import org.starset.deltaforcestrike.util.DeathDrops;
import org.starset.deltaforcestrike.util.Worlds;

import java.time.Duration;

public class GameRulesListener implements Listener {

    private final DeltaForceStrike plugin;
    private final GameRulesService rules;
    private boolean broadcastSameWorldOnly = true;

    public GameRulesListener(DeltaForceStrike plugin, GameRulesService rules) {
        this.plugin = plugin;
        this.rules = rules;
    }

    private boolean inMatch(Player player) {
        return Worlds.isArena(player)
                && plugin.getMatchManager().isInMatch(player)
                && plugin.getMatchManager().getMatch() != null
                && plugin.getMatchManager().getMatch().getState() == MatchState.IN_PROGRESS;
    }

    // ------------------------------------------------------------------
    // 世界 / 加入
    // ------------------------------------------------------------------

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        if (Worlds.isArena(event.getWorld())) {
            rules.applyToWorld(event.getWorld(), true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (Worlds.isArena(event.getPlayer())) {
            rules.applyToWorld(event.getPlayer().getWorld(), false);
        }
    }

    @EventHandler
    public void onChangeWorld(PlayerChangedWorldEvent event) {
        if (Worlds.isArena(event.getPlayer())) {
            rules.applyToWorld(event.getPlayer().getWorld(), false);
        }
    }

    // ------------------------------------------------------------------
    // 自然回血 / 饱食
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!inMatch(player) && !(Worlds.isArena(player) && plugin.getMatchManager().isInMatch(player))) {
            return;
        }
        if (!plugin.getMatchManager().isInMatch(player)) {
            return;
        }
        // 仅禁止自然回血（饱食）；允许药水再生（生命恩典 REGEN / MAGIC_REGEN）
        switch (event.getRegainReason()) {
            case SATIATED -> event.setCancelled(true);
            default -> {
                // REGEN, MAGIC_REGEN, MAGIC, WITHER, CUSTOM 等放行
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!plugin.getMatchManager().isInMatch(player) || !Worlds.isArena(player)) {
            return;
        }
        event.setCancelled(true);
        rules.fillFood(player);
    }

    // ------------------------------------------------------------------
    // 致死处理
    // ------------------------------------------------------------------

    /**
     * 队列 / 倒计时 / 选干员 / 结算：禁止一切 PvP。
     * 对局进行中：再按 friendly-fire 处理友伤。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFriendlyFire(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (!Worlds.isArena(victim)) {
            return;
        }
        Player attacker = findKillerPlayer(event);
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        if (!Worlds.isArena(attacker)) {
            return;
        }

        Match match = plugin.getMatchManager().getMatch();
        MatchState state = match == null ? null : match.getState();

        // 未开打 / 已结束：竞技世界内玩家互伤一律取消
        if (state == null
                || state == MatchState.WAITING
                || state == MatchState.COUNTDOWN
                || state == MatchState.AGENT_SELECT
                || state == MatchState.ENDING) {
            event.setCancelled(true);
            return;
        }

        // 对局进行中：仅处理双方都在局内的情况
        if (state != MatchState.IN_PROGRESS) {
            event.setCancelled(true);
            return;
        }
        if (!plugin.getMatchManager().isInMatch(victim)
                || !plugin.getMatchManager().isInMatch(attacker)) {
            // 旁观等待区的人也不能被打 / 打人
            event.setCancelled(true);
            return;
        }
        if (plugin.getConfig().getBoolean("match.friendly-fire", false)) {
            return;
        }
        PlayerSession vs = match.getSession(victim.getUniqueId());
        PlayerSession as = match.getSession(attacker.getUniqueId());
        if (vs == null || as == null || !vs.hasTeam() || !as.hasTeam()) {
            event.setCancelled(true);
            return;
        }
        if (vs.getTeam() == as.getTeam()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!Worlds.isArena(player) || !plugin.getMatchManager().isInMatch(player)) {
            return;
        }

        Match match = plugin.getMatchManager().getMatch();
        if (match == null || match.getState() != MatchState.IN_PROGRESS) {
            return;
        }

        if (player.getGameMode() == GameMode.SPECTATOR) {
            event.setCancelled(true);
            return;
        }

        double finalHealth = player.getHealth() - event.getFinalDamage();
        if (finalHealth > 0.0) {
            return;
        }

        RoundState rs = match.getRoundManager().getState();
        PlayerSession self = match.getSession(player.getUniqueId());

        // ----- 购买 / 结算：原地复活，不进旁观 -----
        if (rs == RoundState.BUY || rs == RoundState.ROUND_END) {
            event.setCancelled(true);
            rules.fillHealth(player);
            rules.fillFood(player);
            player.setFireTicks(0);
            player.setFallDistance(0f);
            player.setGameMode(GameMode.ADVENTURE);
            if (self != null) {
                self.setAlive(true);
                if (self.hasTeam()) {
                    plugin.getMatchManager().teleportTeamSpawn(player, self.getTeam());
                }
            }
            return;
        }

        // ----- 仅战斗 / 拆弹 伪死亡 -----
        if (rs != RoundState.COMBAT && rs != RoundState.BOMB_PLANTED) {
            event.setCancelled(true);
            return;
        }

        if (self == null || !self.isAlive()) {
            event.setCancelled(true);
            return;
        }

        // 先标记，防同 tick 重复
        self.setAlive(false);
        event.setCancelled(true);

        Player killer = null;
        boolean meleeKill = false;
        if (event instanceof EntityDamageByEntityEvent by) {
            killer = findKillerPlayer(by);
            // 近战击杀：伤害被 cancel 后原版不扣耐久，需手动扣
            meleeKill = by.getDamager() instanceof Player;
        }

        KillInfo killInfo = resolveKillInfo(event, killer);

        if (meleeKill && killer != null) {
            damageWeaponOnMeleeKill(killer);
        }

        DeathDrops.dropAndClearLoadout(player);
        showDeathTitle(player, killInfo);
        broadcastKill(player, killInfo);
        enterSpectator(player);

        plugin.getMatchManager().onPlayerEliminated(player, killer);
    }

    /**
     * 伪死亡 cancel 了致死伤害，近战武器不会掉耐久。
     * 与原版斧击实体一致：模拟扣 2 点耐久（eco 金斧 max-durability:2 时击杀即碎）。
     */
    private void damageWeaponOnMeleeKill(Player killer) {
        applyMeleeWeaponDurability(killer, 2);
    }

    /**
     * 对主手近战武器施加 durability 点损伤；达到上限则打碎。
     */
    private void applyMeleeWeaponDurability(Player killer, int durabilityLoss) {
        if (killer == null || !killer.isOnline() || durabilityLoss <= 0) {
            return;
        }
        ItemStack hand = killer.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            return;
        }
        if (hand.getType().getMaxDurability() <= 0) {
            return;
        }
        ItemMeta meta = hand.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return;
        }

        int max;
        try {
            Object m = damageable.getClass().getMethod("getMaxDamage").invoke(damageable);
            max = m instanceof Integer i ? i : hand.getType().getMaxDurability();
            if (max <= 0) {
                max = hand.getType().getMaxDurability();
            }
        } catch (Throwable t) {
            max = hand.getType().getMaxDurability();
        }
        if (max <= 0) {
            return;
        }

        int dmg = damageable.getDamage() + durabilityLoss;
        if (dmg >= max) {
            killer.getInventory().setItemInMainHand(null);
            try {
                killer.playSound(killer.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            } catch (Throwable ignored) {
            }
            return;
        }
        damageable.setDamage(dmg);
        hand.setItemMeta(meta);
        killer.getInventory().setItemInMainHand(hand);
    }

    /** 对局内若仍触发原版死亡：压消息 */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVanillaDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        if (!Worlds.isArena(p) || !plugin.getMatchManager().isInMatch(p)) {
            return;
        }
        event.deathMessage(null);
        event.setKeepInventory(false);
        // 掉落已由伪死亡处理；真死时清空 drops 避免双掉
        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!Worlds.isArena(player) || !plugin.getMatchManager().isInMatch(player)) {
            return;
        }
        Match match = plugin.getMatchManager().getMatch();
        if (match == null || match.getState() != MatchState.IN_PROGRESS) {
            return;
        }

        PlayerSession s = match.getSession(player.getUniqueId());
        RoundState rs = match.getRoundManager().getState();

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            // 购买阶段：回场
            if (rs == RoundState.BUY || (s != null && s.isAlive())) {
                player.setGameMode(GameMode.ADVENTURE);
                rules.fillFood(player);
                if (s != null && s.hasTeam()) {
                    plugin.getMatchManager().teleportTeamSpawn(player, s.getTeam());
                }
                return;
            }
            // 战斗阵亡：旁观
            if (s != null && !s.isAlive()) {
                player.setGameMode(GameMode.SPECTATOR);
                rules.fillFood(player);
                if (plugin.getSpectatorLockService() != null) {
                    plugin.getSpectatorLockService().onEnterSpectator(player);
                }
            }
        });
    }

    // ------------------------------------------------------------------
    // 击杀展示
    // ------------------------------------------------------------------

    private Player findKillerPlayer(EntityDamageByEntityEvent by) {
        Entity damager = by.getDamager();
        if (damager instanceof Player p) {
            return p;
        }
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            return p;
        }
        return null;
    }

    private void showDeathTitle(Player victim, KillInfo info) {
        Component title = Component.text("死亡", NamedTextColor.RED).decorate(TextDecoration.BOLD);
        Component subtitle = buildVictimSubtitle(info);
        victim.showTitle(Title.title(title, subtitle, Title.Times.times(
                Duration.ofMillis(100), Duration.ofSeconds(3), Duration.ofMillis(500)
        )));
    }

    private Component buildVictimSubtitle(KillInfo info) {
        if (info.killerName != null && info.weaponName != null) {
            return Component.text("你被 ", NamedTextColor.GRAY)
                    .append(Component.text(info.killerName, NamedTextColor.YELLOW))
                    .append(Component.text(" 以 ", NamedTextColor.GRAY))
                    .append(Component.text(info.weaponName, NamedTextColor.GOLD))
                    .append(Component.text(" 击杀", NamedTextColor.GRAY));
        }
        if (info.killerName != null) {
            return Component.text("你被 ", NamedTextColor.GRAY)
                    .append(Component.text(info.killerName, NamedTextColor.YELLOW))
                    .append(Component.text(" 击杀", NamedTextColor.GRAY));
        }
        if (info.causeLabel != null) {
            return Component.text("你死于 ", NamedTextColor.GRAY)
                    .append(Component.text(info.causeLabel, NamedTextColor.WHITE));
        }
        return Component.text("你已阵亡", NamedTextColor.GRAY);
    }

    private Component buildKillBroadcast(Player victim, KillInfo info) {
        Component prefix = Component.text("[击杀] ", NamedTextColor.DARK_RED);
        Component victimName = Component.text(victim.getName(), NamedTextColor.RED);
        if (info.killerName != null && info.weaponName != null) {
            return prefix.append(victimName)
                    .append(Component.text(" 被 ", NamedTextColor.GRAY))
                    .append(Component.text(info.killerName, NamedTextColor.YELLOW))
                    .append(Component.text(" 以 ", NamedTextColor.GRAY))
                    .append(Component.text(info.weaponName, NamedTextColor.GOLD))
                    .append(Component.text(" 击杀", NamedTextColor.GRAY));
        }
        if (info.killerName != null) {
            return prefix.append(victimName)
                    .append(Component.text(" 被 ", NamedTextColor.GRAY))
                    .append(Component.text(info.killerName, NamedTextColor.YELLOW))
                    .append(Component.text(" 击杀", NamedTextColor.GRAY));
        }
        if (info.causeLabel != null) {
            return prefix.append(victimName)
                    .append(Component.text(" 死于 ", NamedTextColor.GRAY))
                    .append(Component.text(info.causeLabel, NamedTextColor.WHITE));
        }
        return prefix.append(victimName).append(Component.text(" 已阵亡", NamedTextColor.GRAY));
    }

    private void broadcastKill(Player victim, KillInfo info) {
        Component message = buildKillBroadcast(victim, info);
        World world = victim.getWorld();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (broadcastSameWorldOnly && !viewer.getWorld().equals(world)) {
                continue;
            }
            if (!Worlds.isArena(viewer)) {
                continue;
            }
            viewer.sendMessage(message);
            if (!viewer.getUniqueId().equals(victim.getUniqueId())) {
                viewer.sendActionBar(message);
            }
        }
        pushLiveKill(victim, info);
    }

    /** 导播覆盖层击杀滚动 */
    private void pushLiveKill(Player victim, KillInfo info) {
        var feed = plugin.getLiveKillFeedService();
        if (feed == null || victim == null || info == null) {
            return;
        }
        Match match = plugin.getMatchManager().getMatch();
        PlayerSession vs = match == null ? null : match.getSession(victim.getUniqueId());
        String vTeam = vs != null && vs.getTeam() != null ? vs.getTeam().name() : "";
        String kTeam = "";
        if (info.killerName != null && match != null) {
            for (PlayerSession s : match.getSessions().values()) {
                if (info.killerName.equals(s.getName())) {
                    kTeam = s.getTeam() != null ? s.getTeam().name() : "";
                    break;
                }
            }
        }
        // 炸弹爆炸致死：显示「改造TNT 被击杀者」
        boolean bombExploding = plugin.getBombManager() != null && plugin.getBombManager().isExploding();
        String cause = info.causeLabel == null ? "" : info.causeLabel;
        if (bombExploding || (info.killerName == null && "爆炸".equals(cause))) {
            feed.pushBombKill(victim.getName(), vTeam);
            return;
        }
        if (info.killerName != null && info.weaponName != null) {
            feed.pushPlayerKill(info.killerName, info.weaponName, victim.getName(), kTeam, vTeam);
        } else if (info.killerName != null) {
            feed.pushPlayerKill(info.killerName, "击杀", victim.getName(), kTeam, vTeam);
        } else if (info.causeLabel != null) {
            feed.pushWorldKill(info.causeLabel, victim.getName(), vTeam);
        } else {
            feed.pushWorldKill("战损", victim.getName(), vTeam);
        }
    }

    private KillInfo resolveKillInfo(EntityDamageEvent event, Player killerPlayer) {
        KillInfo info = new KillInfo();
        info.causeLabel = causeLabel(event.getCause());
        if (killerPlayer != null) {
            info.killerName = killerPlayer.getName();
            info.weaponName = weaponDisplay(killerPlayer.getInventory().getItemInMainHand());
            return info;
        }
        if (event instanceof EntityDamageByEntityEvent by) {
            Entity damager = by.getDamager();
            if (damager instanceof Projectile proj) {
                ProjectileSource src = proj.getShooter();
                if (src instanceof Player p) {
                    info.killerName = p.getName();
                    ItemStack hand = p.getInventory().getItemInMainHand();
                    info.weaponName = hand.getType().isAir()
                            ? proj.getType().name().toLowerCase().replace('_', ' ')
                            : weaponDisplay(hand);
                } else if (src instanceof Entity e) {
                    info.killerName = e.getName();
                }
            } else {
                info.killerName = damager.getName();
            }
        }
        return info;
    }

    private String weaponDisplay(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return "空手";
        }
        ItemManager items = plugin.getItemManager();
        String id = items.getItemId(stack);
        if (id != null) {
            var gi = items.getGameItem(id);
            if (gi != null && gi.getName() != null) {
                return gi.getName().replaceAll("§[0-9a-fk-orA-FK-OR]", "");
            }
        }
        if (stack.hasItemMeta()) {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                Component display = meta.displayName();
                if (display != null) {
                    String plain = PlainTextComponentSerializer.plainText().serialize(display);
                    if (!plain.isEmpty()) {
                        return plain;
                    }
                }
            }
        }
        String n = stack.getType().name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    private String causeLabel(EntityDamageEvent.DamageCause cause) {
        if (cause == null) {
            return "未知原因";
        }
        return switch (cause) {
            case FALL -> "跌落";
            case FIRE, FIRE_TICK, LAVA -> "灼烧";
            case DROWNING -> "溺水";
            case VOID -> "虚空";
            case POISON -> "中毒";
            case WITHER -> "凋零";
            case STARVATION -> "饥饿";
            case SUFFOCATION -> "窒息";
            case CONTACT -> "方块伤害";
            case ENTITY_EXPLOSION, BLOCK_EXPLOSION -> "爆炸";
            case LIGHTNING -> "雷击";
            case MAGIC -> "魔法";
            case FREEZE -> "冰冻";
            case SONIC_BOOM -> "音波";
            case WORLD_BORDER -> "世界边界";
            case KILL, SUICIDE -> "处决";
            case ENTITY_ATTACK, ENTITY_SWEEP_ATTACK, PROJECTILE -> "战斗";
            default -> "HOT_FLOOR".equals(cause.name()) ? "熔岩块" : "战损";
        };
    }

    private void enterSpectator(Player player) {
        try {
            rules.fillHealth(player);
        } catch (IllegalArgumentException ex) {
            player.setHealth(1.0);
        }
        rules.fillFood(player);
        player.setFireTicks(0);
        player.setFallDistance(0f);
        player.setGameMode(GameMode.SPECTATOR);

        if (player.getLocation().getY() < player.getWorld().getMinHeight() + 2) {
            player.teleport(player.getWorld().getSpawnLocation());
        }

        if (plugin.getSpectatorLockService() != null) {
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    plugin.getSpectatorLockService().onEnterSpectator(player));
        }
    }

    private static final class KillInfo {
        String killerName;
        String weaponName;
        String causeLabel;
    }
}
