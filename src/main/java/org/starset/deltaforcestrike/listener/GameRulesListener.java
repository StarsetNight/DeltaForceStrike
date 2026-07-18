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

    private boolean appliesFoodAndRegen(Player player) {
        if (!Worlds.isArena(player)) return false;
        return plugin.getMatchManager().isInMatch(player);
    }

    private boolean appliesLethalSpectator(Player player) {
        if (!Worlds.isArena(player)) return false;
        if (!plugin.getMatchManager().isInMatch(player)) return false;
        Match match = plugin.getMatchManager().getMatch();
        return match != null
                && match.getState() == MatchState.IN_PROGRESS
                && (match.getRoundManager().getState() == RoundState.COMBAT
                || match.getRoundManager().getState() == RoundState.BOMB_PLANTED);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        if (Worlds.isArena(event.getWorld())) {
            rules.applyToWorld(event.getWorld());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (Worlds.isArena(event.getPlayer())) {
            rules.applyToWorld(event.getPlayer().getWorld());
        }
    }

    @EventHandler
    public void onChangeWorld(PlayerChangedWorldEvent event) {
        if (Worlds.isArena(event.getPlayer())) {
            rules.applyToWorld(event.getPlayer().getWorld());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!appliesFoodAndRegen(player)) return;
        switch (event.getRegainReason()) {
            case SATIATED, REGEN, MAGIC_REGEN -> event.setCancelled(true);
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!appliesFoodAndRegen(player)) return;
        event.setCancelled(true);
        rules.fillFood(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!appliesLethalSpectator(player)) return;

        if (player.getGameMode() == GameMode.SPECTATOR) {
            event.setCancelled(true);
            return;
        }

        double finalHealth = player.getHealth() - event.getFinalDamage();
        if (finalHealth > 0.0) return;

        event.setCancelled(true);

        Player killer = null;
        if (event instanceof EntityDamageByEntityEvent by) {
            killer = findKillerPlayer(by);
        }

        KillInfo killInfo = resolveKillInfo(event, killer);

        // 先掉落再旁观
        DeathDrops.dropAndClearLoadout(player);

        showDeathTitle(player, killInfo);
        broadcastKill(player, killInfo);
        enterSpectator(player);

        plugin.getMatchManager().onPlayerEliminated(player, killer);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!appliesLethalSpectator(player)) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && appliesLethalSpectator(player)) {
                player.setGameMode(GameMode.SPECTATOR);
                rules.fillFood(player);
            }
        });
    }

    private Player findKillerPlayer(EntityDamageByEntityEvent by) {
        Entity damager = by.getDamager();
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
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
            if (broadcastSameWorldOnly && !viewer.getWorld().equals(world)) continue;
            if (!Worlds.isArena(viewer)) continue;
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
        if (stack == null || stack.getType().isAir()) return "空手";
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
                    if (plain != null && !plain.isEmpty()) return plain;
                }
            }
        }
        String n = stack.getType().name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    private String causeLabel(EntityDamageEvent.DamageCause cause) {
        if (cause == null) return "未知原因";
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
    }

    private static final class KillInfo {
        String killerName;
        String weaponName;
        String causeLabel;
    }
}
