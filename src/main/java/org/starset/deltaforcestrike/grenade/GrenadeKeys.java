package org.starset.deltaforcestrike.grenade;

import org.bukkit.NamespacedKey;
import org.starset.deltaforcestrike.DeltaForceStrike;

/**
 * 投掷物 PDC 键。static final，避免高频路径反复分配。
 */
public final class GrenadeKeys {

    private GrenadeKeys() {}

    public static final NamespacedKey TYPE =
            new NamespacedKey(DeltaForceStrike.getInstance(), "grenade_type");
    public static final NamespacedKey THROWER =
            new NamespacedKey(DeltaForceStrike.getInstance(), "grenade_thrower");

    public static NamespacedKey type() { return TYPE; }
    public static NamespacedKey thrower() { return THROWER; }
}
