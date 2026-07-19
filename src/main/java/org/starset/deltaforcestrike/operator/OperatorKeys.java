package org.starset.deltaforcestrike.operator;

import org.bukkit.NamespacedKey;
import org.starset.deltaforcestrike.DeltaForceStrike;

public final class OperatorKeys {
    private OperatorKeys() {}

    public static NamespacedKey skillKind() {
        return new NamespacedKey(DeltaForceStrike.getInstance(), "op_skill_kind");
    }

    public static NamespacedKey skillId() {
        return new NamespacedKey(DeltaForceStrike.getInstance(), "op_skill_id");
    }

    public static NamespacedKey reconArrow() {
        return new NamespacedKey(DeltaForceStrike.getInstance(), "recon_arrow");
    }

    public static NamespacedKey reconRadius() {
        return new NamespacedKey(DeltaForceStrike.getInstance(), "recon_radius");
    }

    public static NamespacedKey reconGlow() {
        return new NamespacedKey(DeltaForceStrike.getInstance(), "recon_glow");
    }

    /**
     * 技能投掷物类型标记（与 GrenadeKeys / 普通烟雾弹完全分离）。
     * 值例: "round_smoke"
     */
    public static NamespacedKey skillProjectile() {
        return new NamespacedKey(DeltaForceStrike.getInstance(), "op_skill_projectile");
    }

    public static NamespacedKey skillOwner() {
        return new NamespacedKey(DeltaForceStrike.getInstance(), "op_skill_owner");
    }

    public static NamespacedKey skillRadius() {
        return new NamespacedKey(DeltaForceStrike.getInstance(), "op_skill_radius");
    }

    public static NamespacedKey skillDuration() {
        return new NamespacedKey(DeltaForceStrike.getInstance(), "op_skill_duration");
    }
}
