package org.starset.deltaforcestrike.operator;

import org.bukkit.NamespacedKey;
import org.starset.deltaforcestrike.DeltaForceStrike;

/**
 * 干员系统 PDC 键。全部为 static final，避免高频路径反复分配。
 */
public final class OperatorKeys {
    private OperatorKeys() {}

    public static final NamespacedKey SKILL_KIND =
            new NamespacedKey(DeltaForceStrike.getInstance(), "op_skill_kind");
    public static final NamespacedKey SKILL_ID =
            new NamespacedKey(DeltaForceStrike.getInstance(), "op_skill_id");
    public static final NamespacedKey RECON_ARROW =
            new NamespacedKey(DeltaForceStrike.getInstance(), "recon_arrow");
    public static final NamespacedKey RECON_RADIUS =
            new NamespacedKey(DeltaForceStrike.getInstance(), "recon_radius");
    public static final NamespacedKey RECON_GLOW =
            new NamespacedKey(DeltaForceStrike.getInstance(), "recon_glow");
    /**
     * 技能投掷物类型标记（与 GrenadeKeys / 普通烟雾弹完全分离）。
     * 值例: "round_smoke"
     */
    public static final NamespacedKey SKILL_PROJECTILE =
            new NamespacedKey(DeltaForceStrike.getInstance(), "op_skill_projectile");
    public static final NamespacedKey SKILL_OWNER =
            new NamespacedKey(DeltaForceStrike.getInstance(), "op_skill_owner");
    public static final NamespacedKey SKILL_RADIUS =
            new NamespacedKey(DeltaForceStrike.getInstance(), "op_skill_radius");
    public static final NamespacedKey SKILL_DURATION =
            new NamespacedKey(DeltaForceStrike.getInstance(), "op_skill_duration");
    /** 召唤物标记（恼鬼等），回合结束时按此清除 */
    public static final NamespacedKey SUMMON_MARKER =
            new NamespacedKey(DeltaForceStrike.getInstance(), "op_summon");
    /** 召唤物归属者 UUID */
    public static final NamespacedKey SUMMON_OWNER =
            new NamespacedKey(DeltaForceStrike.getInstance(), "op_summon_owner");

    // ---- 兼容旧调用点的方法形式 ----

    public static NamespacedKey skillKind() { return SKILL_KIND; }
    public static NamespacedKey skillId() { return SKILL_ID; }
    public static NamespacedKey reconArrow() { return RECON_ARROW; }
    public static NamespacedKey reconRadius() { return RECON_RADIUS; }
    public static NamespacedKey reconGlow() { return RECON_GLOW; }
    public static NamespacedKey skillProjectile() { return SKILL_PROJECTILE; }
    public static NamespacedKey skillOwner() { return SKILL_OWNER; }
    public static NamespacedKey skillRadius() { return SKILL_RADIUS; }
    public static NamespacedKey skillDuration() { return SKILL_DURATION; }
    public static NamespacedKey summonMarker() { return SUMMON_MARKER; }
    public static NamespacedKey summonOwner() { return SUMMON_OWNER; }
}
