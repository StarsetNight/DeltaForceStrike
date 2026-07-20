package org.starset.deltaforcestrike.bomb;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.item.ItemKeys;
import org.starset.deltaforcestrike.match.Match;
import org.starset.deltaforcestrike.match.PlayerSession;
import org.starset.deltaforcestrike.util.Worlds;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 地上改造 TNT：实体发光 + 红名；加入各 T 玩家计分板红队，轮廓对 T 更醒目。
 */
public final class BombDropGlowService implements Listener {

    private static final String GLOW_TEAM = "dfs_bomb_glow";

    private final DeltaForceStrike plugin;
    private final Map<UUID, Item> tracked = new ConcurrentHashMap<>();
    private BukkitTask task;

    public BombDropGlowService(DeltaForceStrike plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        tracked.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        if (!Worlds.isArena(item.getWorld())) {
            return;
        }
        if (isPlantBomb(item.getItemStack())) {
            markBombDrop(item);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Item item = event.getItemDrop();
        if (!Worlds.isArena(event.getPlayer())) {
            return;
        }
        if (isPlantBomb(item.getItemStack())) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (item.isValid()) {
                    markBombDrop(item);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        untrack(event.getItem());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDespawn(ItemDespawnEvent event) {
        untrack(event.getEntity());
    }

    private void markBombDrop(Item item) {
        if (item == null || !item.isValid()) {
            return;
        }
        item.setGlowing(true);
        item.setCustomNameVisible(true);
        item.customName(Component.text("改造TNT", NamedTextColor.RED, TextDecoration.BOLD));
        item.getPersistentDataContainer().set(
                ItemKeys.type(), PersistentDataType.STRING, "bomb_drop");
        tracked.put(item.getUniqueId(), item);
        attachToTTeams(item);
    }

    private void untrack(Item item) {
        if (item == null) {
            return;
        }
        tracked.remove(item.getUniqueId());
        detachFromTTeams(item);
    }

    private void tick() {
        Iterator<Map.Entry<UUID, Item>> it = tracked.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Item> e = it.next();
            Item item = e.getValue();
            if (item == null || !item.isValid() || item.isDead()) {
                it.remove();
                continue;
            }
            if (!isPlantBomb(item.getItemStack())) {
                item.setGlowing(false);
                detachFromTTeams(item);
                it.remove();
                continue;
            }
            item.setGlowing(true);
            attachToTTeams(item);
        }
    }

    private void attachToTTeams(Item item) {
        Match match = plugin.getMatchManager().getMatch();
        if (match == null) {
            return;
        }
        String entry = entityTeamEntry(item);
        for (Player p : match.onlinePlayers()) {
            PlayerSession s = match.getSession(p.getUniqueId());
            if (s == null || s.getTeam() != org.starset.deltaforcestrike.match.Team.T) {
                continue;
            }
            Scoreboard board = p.getScoreboard();
            var mgr = Bukkit.getScoreboardManager();
            if (board == null || mgr == null || board.equals(mgr.getMainScoreboard())) {
                continue;
            }
            Team team = board.getTeam(GLOW_TEAM);
            if (team == null) {
                team = board.registerNewTeam(GLOW_TEAM);
                team.color(NamedTextColor.RED);
                team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
            }
            if (!team.hasEntry(entry)) {
                team.addEntry(entry);
            }
        }
    }

    private void detachFromTTeams(Item item) {
        String entry = entityTeamEntry(item);
        Match match = plugin.getMatchManager().getMatch();
        if (match == null) {
            return;
        }
        for (Player p : match.onlinePlayers()) {
            Scoreboard board = p.getScoreboard();
            if (board == null) {
                continue;
            }
            Team team = board.getTeam(GLOW_TEAM);
            if (team != null && team.hasEntry(entry)) {
                team.removeEntry(entry);
            }
        }
    }

    /** 实体 UUID 字符串可作为 scoreboard entry（发光染色用） */
    private static String entityTeamEntry(Item item) {
        return item.getUniqueId().toString();
    }

    private boolean isPlantBomb(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        if (plugin.getBombManager() != null && plugin.getBombManager().isPlantBomb(stack)) {
            return true;
        }
        String id = plugin.getItemManager().getItemId(stack);
        String type = plugin.getItemManager().getItemType(stack);
        if ("bomb".equalsIgnoreCase(type)) {
            return true;
        }
        return id != null && id.toLowerCase().contains("plant-bomb");
    }
}
