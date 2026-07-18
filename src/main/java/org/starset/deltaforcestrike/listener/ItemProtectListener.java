package org.starset.deltaforcestrike.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.item.ItemKeys;
import org.starset.deltaforcestrike.item.ItemManager;
import org.starset.deltaforcestrike.util.Worlds;

public class ItemProtectListener implements Listener {

    private final DeltaForceStrike plugin;

    public ItemProtectListener(DeltaForceStrike plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (!Worlds.isArena(event.getEntity())) return;
        if (plugin.getConfig().getBoolean("player.drop-equipment", false)) {
            return;
        }
        ItemManager items = plugin.getItemManager();
        event.getDrops().removeIf(stack -> shouldRemoveFromDrops(items, stack));
    }

    private boolean shouldRemoveFromDrops(ItemManager items, ItemStack stack) {
        if (items.isUndroppable(stack) || items.isShield(stack)) {
            return true;
        }
        if (!stack.hasItemMeta()) {
            return false;
        }
        String type = stack.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.type(), PersistentDataType.STRING);
        if (type == null) {
            return false;
        }
        String t = type.toLowerCase();
        return t.equals("armor") || t.equals("shield") || t.contains("skill");
    }
}
