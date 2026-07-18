package org.starset.deltaforcestrike.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.item.ItemManager;

public class ItemProtectListener implements Listener {

    private final DeltaForceStrike plugin;

    public ItemProtectListener(DeltaForceStrike plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        ItemManager items = plugin.getItemManager();
        ItemStack stack = event.getItemDrop().getItemStack();

        if (items.isUndroppable(stack)) {
            event.setCancelled(true);
            String id = items.getItemId(stack);
            event.getPlayer().sendMessage("§c该物品不可丢弃" + (id != null ? "（" + id + "）" : "") + "。");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        // 配置：护甲/盾不掉落（死亡消失，靠消失诅咒 + 清理 drops）
        if (plugin.getConfig().getBoolean("player.drop-equipment", false)) {
            return;
        }

        ItemManager items = plugin.getItemManager();
        event.getDrops().removeIf(stack -> {
            if (items.isUndroppable(stack)) {
                return true;
            }
            String type = null;
            if (stack.hasItemMeta()) {
                type = stack.getItemMeta().getPersistentDataContainer()
                        .get(org.starset.deltaforcestrike.item.ItemKeys.type(),
                                org.bukkit.persistence.PersistentDataType.STRING);
            }
            return "armor".equalsIgnoreCase(type) || "shield".equalsIgnoreCase(type);
        });
    }
}
