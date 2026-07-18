package org.starset.deltaforcestrike.shop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.item.GameItem;
import org.starset.deltaforcestrike.item.ItemKeys;
import org.starset.deltaforcestrike.item.ItemManager;
import org.starset.deltaforcestrike.match.Match;
import org.starset.deltaforcestrike.match.PlayerSession;
import org.starset.deltaforcestrike.round.RoundState;

import java.util.List;

public final class ShopGUI {

    public static final Component TITLE = Component.text("DFS 商店", NamedTextColor.DARK_GRAY);

    private ShopGUI() {}

    public static void open(Player player) {
        DeltaForceStrike plugin = DeltaForceStrike.getInstance();
        Match match = plugin.getMatchManager().getMatch();
        if (match == null || !match.contains(player.getUniqueId())) {
            player.sendMessage("§c不在对局中");
            return;
        }
        if (match.getRoundManager().getState() != RoundState.BUY) {
            player.sendMessage("§c只能在购买阶段打开商店");
            return;
        }
        PlayerSession session = match.getSession(player.getUniqueId());
        if (session == null) return;

        ShopHolder holder = new ShopHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, TITLE);
        holder.setInventory(inv);

        ItemManager im = plugin.getItemManager();
        place(inv, im, 0, "weapons.standard", "standard");
        place(inv, im, 9, "weapons.dragon", "dragon");
        place(inv, im, 18, "weapons.revolt", "revolt");
        place(inv, im, 27, "weapons.dominator", "dominator");
        place(inv, im, 36, "weapons.frenzy", "frenzy");

        place(inv, im, 1, "weapons.light-bow", "light-bow");
        place(inv, im, 10, "weapons.battle-crossbow", "battle-crossbow");
        place(inv, im, 19, "weapons.assault-bow", "assault-bow");
        place(inv, im, 28, "weapons.rapid-crossbow", "rapid-crossbow");
        place(inv, im, 37, "weapons.hunting-bow", "hunting-bow");

        place(inv, im, 2, "grenades.smoke", "smoke");
        place(inv, im, 11, "grenades.incendiary", "incendiary");
        place(inv, im, 20, "grenades.wither", "wither");
        place(inv, im, 29, "bomb.defuse-kit", "defuse-kit");
        place(inv, im, 38, "skill-charge.charge", "charge");

        place(inv, im, 3, "equipments.leather-armor", "leather-armor");
        place(inv, im, 12, "equipments.iron-armor", "iron-armor");

        ItemStack info = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = info.getItemMeta();
        meta.displayName(Component.text("资金: $" + session.getMoney(), NamedTextColor.GOLD));
        meta.lore(List.of(
                Component.text("点击商品购买", NamedTextColor.GRAY),
                Component.text("仅购买阶段", NamedTextColor.DARK_GRAY)
        ));
        info.setItemMeta(meta);
        inv.setItem(49, info);

        player.openInventory(inv);
    }

    private static void place(Inventory inv, ItemManager im, int slot, String... ids) {
        GameItem gi = null;
        String used = null;
        for (String id : ids) {
            gi = im.getGameItem(id);
            if (gi != null) {
                used = gi.getId();
                break;
            }
        }
        if (gi == null || used == null) return;

        ItemStack icon;
        if (gi.isArmorSet()) {
            icon = new ItemStack(used.contains("iron") ? Material.IRON_CHESTPLATE : Material.LEATHER_CHESTPLATE);
        } else {
            icon = im.createItem(used);
            if (icon == null && gi.getMaterial() != null) {
                icon = new ItemStack(gi.getMaterial());
            }
        }
        if (icon == null) return;

        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            String name = gi.getName() == null ? used : gi.getName().replaceAll("§[0-9a-fk-orA-FK-OR]", "");
            meta.displayName(Component.text(name + " $" + gi.getPrice(), NamedTextColor.WHITE));
            meta.lore(List.of(
                    Component.text("价格: $" + gi.getPrice(), NamedTextColor.YELLOW),
                    Component.text("点击购买", NamedTextColor.GRAY)
            ));
            meta.getPersistentDataContainer().set(ItemKeys.id(), PersistentDataType.STRING, "shop:" + used);
            icon.setItemMeta(meta);
        }
        inv.setItem(slot, icon);
    }
}
