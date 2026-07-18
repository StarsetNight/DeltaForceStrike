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

import java.time.Duration;

/**
 * 难度 / 回血 / 饱食 / 立即旁观 + 死亡 Title + 全图击杀广播。
 */
public class GameRulesListener implements Listener {

    private final DeltaForceStrike plugin;
    private final GameRulesService rules;

    /** 测试期 true；接入比赛后可改为仅 isInMatch */
    private boolean always = true;

    /**
     * true = 仅同世界玩家看到击杀；false = 全服。
     */
    private boolean broadcastSameWorldOnly = true;

    public GameRulesListener(DeltaForceStrike plugin, GameRulesService rules) {
        this.plugin = plugin;
        this.rules = rules;
    }

    public void setAlways(boolean always) {
        this.always = always;
    }

    public void setBroadcastSameWorldOnly(boolean broadcastSameWorldOnly) {
        this.broadcastSameWorldOnly = broadcastSameWorldOnly;
    }

    private boolean applies(Player player) {
        if (always) {
            return true;
        }
        return plugin.getGameManager().getMatchManager().isInMatch(player);
    }

    // ------------------------------------------------------------------
    // 世界 / 加入
    // ------------------------------------------------------------------

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        rules.applyToWorld(event.getWorld());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        rules.applyToWorld(player.getWorld());
        rules.fillFood(player);
    }

    @EventHandler
    public void onChangeWorld(PlayerChangedWorldEvent event) {
        rules.applyToWorld(event.getPlayer().getWorld());
        rules.fillFood(event.getPlayer());
    }

    // ------------------------------------------------------------------
    // 无自然回血（主手段）
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!applies(player)) {
            return;
        }

        switch (event.getRegainReason()) {
            case SATIATED, REGEN, MAGIC_REGEN -> event.setCancelled(true);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------
    // 饱食始终满
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!applies(player)) {
            return;
        }
        event.setCancelled(true);
        rules.fillFood(player);
    }

    // ------------------------------------------------------------------
    // 致死 → Title（死者）+ 广播（全图）→ 旁观
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!applies(player)) {
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

        event.setCancelled(true);

        KillInfo killInfo = resolveKillInfo(player, event);

        // 死者：大 Title
        showDeathTitle(player, killInfo);

        // 全图/全服：击杀信息
        broadcastKill(player, killInfo);

        enterSpectator(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!applies(player)) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && applies(player)) {
                player.setGameMode(GameMode.SPECTATOR);
                rules.fillFood(player);
            }
        });
    }

    // ------------------------------------------------------------------
    // 死者 Title
    // ------------------------------------------------------------------

    private void showDeathTitle(Player victim, KillInfo info) {
        Component title = Component.text("死亡", NamedTextColor.RED)
                .decorate(TextDecoration.BOLD);

        Component subtitle = buildKillSubtitle(info);

        Title.Times times = Title.Times.times(
                Duration.ofMillis(100),
                Duration.ofSeconds(3),
                Duration.ofMillis(500)
        );

        victim.showTitle(Title.title(title, subtitle, times));
    }

    /**
     * 副标题 / 广播共用文案逻辑。
     */
    private Component buildKillSubtitle(KillInfo info) {
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

    /**
     * 给其他人看的第三人称击杀行。
     * 例：Steve 被 Alex 以 游龙 击杀
     */
    private Component buildKillBroadcast(Player victim, KillInfo info) {
        Component prefix = Component.text("[击杀] ", NamedTextColor.DARK_RED);
        Component victimName = Component.text(victim.getName(), NamedTextColor.RED);

        if (info.killerName != null && info.weaponName != null) {
            return prefix
                    .append(victimName)
                    .append(Component.text(" 被 ", NamedTextColor.GRAY))
                    .append(Component.text(info.killerName, NamedTextColor.YELLOW))
                    .append(Component.text(" 以 ", NamedTextColor.GRAY))
                    .append(Component.text(info.weaponName, NamedTextColor.GOLD))
                    .append(Component.text(" 击杀", NamedTextColor.GRAY));
        }
        if (info.killerName != null) {
            return prefix
                    .append(victimName)
                    .append(Component.text(" 被 ", NamedTextColor.GRAY))
                    .append(Component.text(info.killerName, NamedTextColor.YELLOW))
                    .append(Component.text(" 击杀", NamedTextColor.GRAY));
        }
        if (info.causeLabel != null) {
            return prefix
                    .append(victimName)
                    .append(Component.text(" 死于 ", NamedTextColor.GRAY))
                    .append(Component.text(info.causeLabel, NamedTextColor.WHITE));
        }
        return prefix
                .append(victimName)
                .append(Component.text(" 已阵亡", NamedTextColor.GRAY));
    }

    /**
     * 全图（同世界）或全服广播；死者也会收到聊天行（Title 另发）。
     */
    private void broadcastKill(Player victim, KillInfo info) {
        Component message = buildKillBroadcast(victim, info);
        World world = victim.getWorld();

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (broadcastSameWorldOnly && !viewer.getWorld().equals(world)) {
                continue;
            }
            viewer.sendMessage(message);

            // 其他人：ActionBar 再闪一下（死者已有 Title，可跳过）
            if (!viewer.getUniqueId().equals(victim.getUniqueId())) {
                viewer.sendActionBar(message);
            }
        }

        if (plugin.getConfig().getBoolean("debug.enabled", false)) {
            plugin.getLogger().info(PlainTextComponentSerializer.plainText().serialize(message));
        }
    }

    // ------------------------------------------------------------------
    // 击杀信息解析
    // ------------------------------------------------------------------

    private KillInfo resolveKillInfo(Player victim, EntityDamageEvent event) {
        KillInfo info = new KillInfo();
        info.causeLabel = causeLabel(event.getCause());

        if (!(event instanceof EntityDamageByEntityEvent by)) {
            return info;
        }

        Entity damager = by.getDamager();
        Player killerPlayer = null;
        ItemStack weapon = null;

        if (damager instanceof Player p) {
            killerPlayer = p;
            weapon = p.getInventory().getItemInMainHand();
        } else if (damager instanceof Projectile proj) {
            ProjectileSource src = proj.getShooter();
            if (src instanceof Player p) {
                killerPlayer = p;
                weapon = p.getInventory().getItemInMainHand();
                if (weapon == null || weapon.getType().isAir()) {
                    info.weaponName = projectileLabel(proj);
                }
            } else if (src instanceof Entity e) {
                info.killerName = safeEntityName(e);
            }
        } else {
            info.killerName = safeEntityName(damager);
        }

        if (killerPlayer != null) {
            info.killerName = killerPlayer.getName();
            if (info.weaponName == null) {
                info.weaponName = weaponDisplay(weapon);
            }
        }

        return info;
    }

    private String safeEntityName(Entity entity) {
        if (entity == null) {
            return "未知";
        }
        String name = entity.getName();
        return name == null || name.isEmpty() ? entity.getType().name() : name;
    }

    private String weaponDisplay(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return "空手";
        }

        ItemManager items = plugin.getItemManager();
        String id = items.getItemId(stack);
        if (id != null) {
            var gi = items.getGameItem(id);
            if (gi != null && gi.getName() != null && !gi.getName().isEmpty()) {
                return stripLegacy(gi.getName());
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

        return prettyMaterial(stack.getType());
    }

    private String stripLegacy(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
    }

    private String prettyMaterial(Material mat) {
        if (mat == null) {
            return "未知武器";
        }
        String n = mat.name().toLowerCase().replace('_', ' ');
        if (n.isEmpty()) {
            return mat.name();
        }
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    private String projectileLabel(Projectile proj) {
        if (proj == null) {
            return "弹射物";
        }
        return proj.getType().name().toLowerCase().replace('_', ' ');
    }

    /**
     * 不使用已弃用的 HOT_FLOOR（26.2+）。
     * 岩浆/火焰类合并描述；未知 cause → 战损。
     */
    private String causeLabel(EntityDamageEvent.DamageCause cause) {
        if (cause == null) {
            return "未知原因";
        }
        return switch (cause) {
            case FALL -> "跌落";
            case FIRE, FIRE_TICK, LAVA -> "灼烧";
            // HOT_FLOOR 已弃用：若运行时仍是该 cause，会进 default
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
            default -> {
                // 兼容运行时仍出现的 HOT_FLOOR 等弃用枚举，不在源码里引用常量名
                String name = cause.name();
                if ("HOT_FLOOR".equals(name)) {
                    yield "熔岩块";
                }
                yield "战损";
            }
        };
    }

    public void enterSpectator(Player player) {
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

        if (plugin.getConfig().getBoolean("debug.enabled", false)) {
            plugin.getLogger().info("[Rules] " + player.getName() + " → SPECTATOR");
        }
    }

    private static final class KillInfo {
        String killerName;
        String weaponName;
        String causeLabel;
    }
}
