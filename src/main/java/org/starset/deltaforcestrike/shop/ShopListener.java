package org.starset.deltaforcestrike.shop;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.item.GameItem;
import org.starset.deltaforcestrike.item.ItemKeys;
import org.starset.deltaforcestrike.match.Match;
import org.starset.deltaforcestrike.match.PlayerSession;
import org.starset.deltaforcestrike.round.RoundState;

public class ShopListener implements Listener {

    private final DeltaForceStrike plugin;

    public ShopListener(DeltaForceStrike plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof ShopHolder)) return;

        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }

        Match match = plugin.getMatchManager().getMatch();
        if (match == null || match.getRoundManager().getState() != RoundState.BUY) {
            player.closeInventory();
            player.sendMessage("§c购买阶段已结束");
            return;
        }

        ItemStack cur = event.getCurrentItem();
        if (cur == null || !cur.hasItemMeta()) return;

        String raw = cur.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.id(), PersistentDataType.STRING);
        if (raw == null || !raw.startsWith("shop:")) return;

        String itemId = raw.substring(5);
        GameItem gi = plugin.getItemManager().getGameItem(itemId);
        if (gi == null) {
            player.sendMessage("§c商品无效");
            return;
        }

        PlayerSession session = match.getSession(player.getUniqueId());
        if (session == null) return;

        int price = gi.getPrice();
        if (price > 0 && !session.spend(price)) {
            player.sendMessage("§c资金不足（需要 $" + price + "，当前 $" + session.getMoney() + "）");
            return;
        }

        boolean ok = plugin.getItemGiveService().give(player, itemId, true);
        if (!ok) {
            if (price > 0) session.addMoney(price);
            player.sendMessage("§c购买失败（槽位满？）");
            return;
        }

        player.sendMessage("§a购买成功 §7剩余 §e$" + session.getMoney());
        if (plugin.getScoreboardService() != null) {
            plugin.getScoreboardService().update(player);
        }
        ShopGUI.open(player);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ShopHolder) {
            event.setCancelled(true);
        }
    }
}
