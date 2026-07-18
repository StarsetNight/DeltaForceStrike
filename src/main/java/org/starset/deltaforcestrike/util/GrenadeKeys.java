package org.starset.deltaforcestrike.util;

import org.bukkit.NamespacedKey;
import org.starset.deltaforcestrike.DeltaForceStrike;

public final class GrenadeKeys {

    private GrenadeKeys() {}

    public static NamespacedKey type() {
        return new NamespacedKey(DeltaForceStrike.getInstance(), "grenade_type");
    }

    public static NamespacedKey thrower() {
        return new NamespacedKey(DeltaForceStrike.getInstance(), "grenade_thrower");
    }
}
