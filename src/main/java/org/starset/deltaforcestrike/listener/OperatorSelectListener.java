package org.starset.deltaforcestrike.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.item.ItemKeys;
import org.starset.deltaforcestrike.match.MatchState;
import org.starset.deltaforcestrike.operator.OperatorSelectHolder;
import org.starset.deltaforcestrike.util.OperatorSelectUI;

/**
 * 干员选择 GUI 点击。
 */
public class OperatorSelectListener implements Listener {

    private final DeltaForceStrike plugin;

    public OperatorSelectListener(DeltaForceStrike plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof OperatorSelectHolder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }

        var match = plugin.getMatchManager().getMatch();
        if (match == null || !match.contains(player.getUniqueId())) {
            player.closeInventory();
            return;
        }
        MatchState st = match.getState();
        if (st != MatchState.WAITING
                && st != MatchState.COUNTDOWN
                && st != MatchState.AGENT_SELECT) {
            player.closeInventory();
            player.sendMessage("§c当前不能选择干员。");
            return;
        }

        ItemStack cur = event.getCurrentItem();
        if (cur == null || !cur.hasItemMeta()) {
            return;
        }
        String raw = cur.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.id(), PersistentDataType.STRING);
        if (raw == null || !raw.startsWith("op_select:")) {
            return;
        }
        String opId = raw.substring("op_select:".length());
        boolean ok = plugin.getMatchManager().trySelectOperator(player, opId);
        if (ok) {
            // 刷新自己 GUI；不关闭（可改选）
            OperatorSelectUI.refreshOpenGuis(match);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof OperatorSelectHolder) {
            event.setCancelled(true);
        }
    }
}
