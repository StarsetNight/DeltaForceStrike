package org.starset.deltaforcestrike.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.item.ItemKeys;
import org.starset.deltaforcestrike.match.MatchState;
import org.starset.deltaforcestrike.match.Team;
import org.starset.deltaforcestrike.match.TeamSelectHolder;
import org.starset.deltaforcestrike.util.TeamSelectUI;
import org.starset.deltaforcestrike.util.Worlds;

/**
 * 选队书右键 + 选队 GUI 点击。
 */
public class TeamSelectListener implements Listener {

    private final DeltaForceStrike plugin;

    public TeamSelectListener(DeltaForceStrike plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBookUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action a = event.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!Worlds.isArena(player) || !plugin.getMatchManager().isInMatch(player)) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!TeamSelectUI.isTeamSelectBook(hand)) {
            return;
        }
        event.setCancelled(true);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);

        var match = plugin.getMatchManager().getMatch();
        if (match == null) {
            return;
        }
        MatchState st = match.getState();
        if (st != MatchState.WAITING && st != MatchState.COUNTDOWN) {
            player.sendMessage("§c当前不能选队。");
            return;
        }
        TeamSelectUI.open(player);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof TeamSelectHolder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }

        ItemStack cur = event.getCurrentItem();
        if (cur == null || !cur.hasItemMeta()) {
            return;
        }
        String raw = cur.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.id(), PersistentDataType.STRING);
        if (raw == null || !raw.startsWith("team_select:")) {
            return;
        }
        String side = raw.substring("team_select:".length());
        Team team = switch (side.toLowerCase()) {
            case "t" -> Team.T;
            case "ct" -> Team.CT;
            default -> Team.NONE;
        };
        if (team == Team.NONE) {
            return;
        }
        plugin.getMatchManager().trySelectTeam(player, team);
        // trySelectTeam 内会 refreshOpenGuis
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof TeamSelectHolder) {
            event.setCancelled(true);
        }
    }
}
