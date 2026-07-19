package org.starset.deltaforcestrike.operator;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 技能静态定义（从 operators.yml 加载）。
 * kind 区分被动/招牌/购买/终极；handlerId 映射 SkillHandler；
 * params 存放其余可配置数值，供 handler 通过 getInt/getDouble/getBoolean 读取。
 */
public final class SkillDefinition {

    private static final Set<String> RESERVED_KEYS = Set.of(
            "id", "name", "description", "handler",
            "max-charges", "initial-charges",
            "recharge-seconds", "kill-cooldown-reduction",
            "cost", "effects"
    );

    private final String id;
    private final String name;
    private final String description;
    private final SkillKind kind;
    private final String handlerId;
    private final int maxCharges;
    private final int initialCharges;
    private final int rechargeSeconds;
    private final int killCooldownReduction;
    private final int ultimateCost;
    private final Map<String, Object> params;
    private final List<PotionSpec> effects;

    private SkillDefinition(Builder b) {
        this.id = b.id;
        this.name = b.name;
        this.description = b.description;
        this.kind = b.kind;
        this.handlerId = b.handlerId;
        this.maxCharges = Math.max(0, b.maxCharges);
        this.initialCharges = Math.max(0, Math.min(b.initialCharges, this.maxCharges == 0 ? b.initialCharges : this.maxCharges));
        this.rechargeSeconds = Math.max(0, b.rechargeSeconds);
        this.killCooldownReduction = Math.max(0, b.killCooldownReduction);
        this.ultimateCost = Math.max(0, b.ultimateCost);
        this.params = Map.copyOf(b.params);
        this.effects = List.copyOf(b.effects);
    }

    /**
     * 从配置节加载技能。
     *
     * @param sec  配置节，可为 null
     * @param kind 技能类型
     * @return 定义；sec 为 null 时返回 null
     */
    public static SkillDefinition from(ConfigurationSection sec, SkillKind kind) {
        if (sec == null) {
            return null;
        }

        Builder b = new Builder();
        b.kind = kind == null ? SkillKind.SIGNATURE : kind;

        String defaultId = b.kind.name().toLowerCase(Locale.ROOT);
        b.id = sec.getString("id", defaultId);
        b.name = sec.getString("name", b.id);
        b.description = sec.getString("description", "");
        b.handlerId = sec.getString("handler", "none");

        b.maxCharges = sec.getInt("max-charges", kind == SkillKind.PASSIVE ? 0 : 1);
        // 招牌默认满充能；购买/大招默认 0（购买靠商店，大招靠击杀/回合）
        int defaultInitial = switch (kind == null ? SkillKind.SIGNATURE : kind) {
            case SIGNATURE -> b.maxCharges;
            case PURCHASABLE, ULTIMATE, PASSIVE -> 0;
        };
        b.initialCharges = sec.getInt("initial-charges", defaultInitial);
        b.rechargeSeconds = sec.getInt("recharge-seconds", 60);
        b.killCooldownReduction = sec.getInt("kill-cooldown-reduction", 30);
        b.ultimateCost = sec.getInt("cost", 4);

        // 其余键 → params（供 handler 读取）
        for (String key : sec.getKeys(false)) {
            if (RESERVED_KEYS.contains(key)) {
                continue;
            }
            // 跳过子配置节对象本身若已是 effects（已处理）
            Object value = sec.get(key);
            if (value != null) {
                b.params.put(key, value);
            }
        }

        // effects: List<Map>
        loadEffects(sec, b);

        return new SkillDefinition(b);
    }

    private static void loadEffects(ConfigurationSection sec, Builder b) {
        b.effects.clear();

        // 标准：effects 为 map 列表
        List<Map<?, ?>> mapList = sec.getMapList("effects");
        if (mapList != null && !mapList.isEmpty()) {
            for (Map<?, ?> map : mapList) {
                PotionSpec ps = PotionSpec.fromMap(map);
                if (ps != null) {
                    b.effects.add(ps);
                }
            }
            return;
        }

        // 备选：effects 为配置节（effects.0 / effects.1 或 effects.speed）
        ConfigurationSection effSec = sec.getConfigurationSection("effects");
        if (effSec != null) {
            for (String key : effSec.getKeys(false)) {
                ConfigurationSection child = effSec.getConfigurationSection(key);
                if (child != null) {
                    // 若子节没有 potion 字段，把 key 当 potion 名
                    if (!child.contains("potion")) {
                        MemoryConfiguration mem = new MemoryConfiguration();
                        for (String ck : child.getKeys(false)) {
                            mem.set(ck, child.get(ck));
                        }
                        mem.set("potion", key);
                        PotionSpec ps = PotionSpec.from(mem);
                        if (ps != null) {
                            b.effects.add(ps);
                        }
                    } else {
                        PotionSpec ps = PotionSpec.from(child);
                        if (ps != null) {
                            b.effects.add(ps);
                        }
                    }
                } else {
                    // effects.SPEED: 0 这种简写
                    Object amp = effSec.get(key);
                    MemoryConfiguration mem = new MemoryConfiguration();
                    mem.set("potion", key);
                    if (amp instanceof Number n) {
                        mem.set("amplifier", n.intValue());
                    }
                    PotionSpec ps = PotionSpec.from(mem);
                    if (ps != null) {
                        b.effects.add(ps);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // params 访问
    // ------------------------------------------------------------------

    public int getInt(String key, int def) {
        Object v = params.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v != null) {
            try {
                return Integer.parseInt(v.toString().trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    public double getDouble(String key, double def) {
        Object v = params.get(key);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v != null) {
            try {
                return Double.parseDouble(v.toString().trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    public boolean getBoolean(String key, boolean def) {
        Object v = params.get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        if (v != null) {
            return Boolean.parseBoolean(v.toString().trim());
        }
        return def;
    }

    public String getString(String key, String def) {
        Object v = params.get(key);
        if (v == null) {
            return def;
        }
        String s = v.toString();
        return s.isEmpty() ? def : s;
    }

    public boolean hasParam(String key) {
        return params.containsKey(key);
    }

    // ------------------------------------------------------------------
    // Getters
    // ------------------------------------------------------------------

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public SkillKind getKind() {
        return kind;
    }

    public String getHandlerId() {
        return handlerId == null ? "none" : handlerId;
    }

    public int getMaxCharges() {
        return maxCharges;
    }

    public int getInitialCharges() {
        return initialCharges;
    }

    public int getRechargeSeconds() {
        return rechargeSeconds;
    }

    public int getKillCooldownReduction() {
        return killCooldownReduction;
    }

    /** 终极技能所需击杀充能点数 */
    public int getUltimateCost() {
        return ultimateCost;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public List<PotionSpec> getEffects() {
        return effects;
    }

    @Override
    public String toString() {
        return "SkillDefinition{id=" + id
                + ", kind=" + kind
                + ", handler=" + handlerId
                + ", charges=" + initialCharges + "/" + maxCharges
                + ", cd=" + rechargeSeconds
                + ", cost=" + ultimateCost
                + "}";
    }

    // ------------------------------------------------------------------
    // Builder
    // ------------------------------------------------------------------

    private static final class Builder {
        String id;
        String name;
        String description;
        String handlerId = "none";
        SkillKind kind = SkillKind.SIGNATURE;
        int maxCharges = 1;
        int initialCharges = 1;
        int rechargeSeconds = 60;
        int killCooldownReduction = 30;
        int ultimateCost = 4;
        final Map<String, Object> params = new HashMap<>();
        final List<PotionSpec> effects = new ArrayList<>();
    }
}
