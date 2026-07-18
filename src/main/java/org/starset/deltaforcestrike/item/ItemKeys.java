package org.starset.deltaforcestrike.item;

import org.bukkit.NamespacedKey;
import org.starset.deltaforcestrike.DeltaForceStrike;

public final class ItemKeys {

    private ItemKeys() {}

    public static NamespacedKey id() {
        return new NamespacedKey(DeltaForceStrike.getInstance(), "item_id");
    }

    public static NamespacedKey type() {
        return new NamespacedKey(DeltaForceStrike.getInstance(), "item_type");
    }

    public static NamespacedKey action() {
        return new NamespacedKey(DeltaForceStrike.getInstance(), "item_action");
    }

    public static NamespacedKey undroppable() {
        return new NamespacedKey(DeltaForceStrike.getInstance(), "undroppable");
    }

    /** 每件独立实例，防止堆叠 / isSimilar 合并 */
    public static NamespacedKey instanceId() {
        return new NamespacedKey(DeltaForceStrike.getInstance(), "instance_id");
    }
}
