package org.starset.deltaforcestrike.item;

import org.bukkit.NamespacedKey;
import org.starset.deltaforcestrike.DeltaForceStrike;

/**
 * 物品 PDC 键。全部为 static final，避免点击/清扫等热路径反复分配。
 */
public final class ItemKeys {

    private ItemKeys() {}

    public static final NamespacedKey ID =
            new NamespacedKey(DeltaForceStrike.getInstance(), "item_id");
    public static final NamespacedKey TYPE =
            new NamespacedKey(DeltaForceStrike.getInstance(), "item_type");
    public static final NamespacedKey ACTION =
            new NamespacedKey(DeltaForceStrike.getInstance(), "item_action");
    public static final NamespacedKey UNDROPPABLE =
            new NamespacedKey(DeltaForceStrike.getInstance(), "undroppable");
    /** 每件独立实例，防止堆叠 / isSimilar 合并 */
    public static final NamespacedKey INSTANCE_ID =
            new NamespacedKey(DeltaForceStrike.getInstance(), "instance_id");

    // 兼容旧调用点的方法形式
    public static NamespacedKey id() { return ID; }
    public static NamespacedKey type() { return TYPE; }
    public static NamespacedKey action() { return ACTION; }
    public static NamespacedKey undroppable() { return UNDROPPABLE; }
    public static NamespacedKey instanceId() { return INSTANCE_ID; }
}
