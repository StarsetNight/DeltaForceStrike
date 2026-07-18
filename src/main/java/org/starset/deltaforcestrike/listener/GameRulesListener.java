package org.starset.deltaforcestrike.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
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
        switch (event.getRegainReason()) {
            case SATIATED, REGEN, MAGIC_REGEN -> event.setCancelled(true);
            default -> {
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
        if (event instanceof EntityDamageByEntityEvent by) {
            killer = findKillerPlayer(by);
        }

        KillInfo killInfo = resolveKillInfo(event, killer);

        DeathDrops.dropAndClearLoadout(player);
        showDeathTitle(player, killInfo);
        broadcastKill(player, killInfo);
        enterSpectator(player);

        plugin.getMatchManager().onPlayerEliminated(player, killer);
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
                    info.weaponName = (hand == null || hand.getType().isAir())
                            ? proj.getType().name().toLowerCase().replace('_', ' ')
                            : weaponDisplay(hand);
                } else if (src instanceof Entity e) {
                    info.killerName = e.getName();
                }
            } else if (damager != null) {
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
                    if (plain != null && !plain.isEmpty()) {
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
