package org.starset.deltaforcestrike.operator;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;
import java.util.Map;

/**
 * 药水效果规格。使用 Registry 解析类型，避免已弃用的 PotionEffectType.getByName。
 */
public record PotionSpec(PotionEffectType type, int amplifier, boolean ambient) {

    public static PotionSpec from(ConfigurationSection sec) {
        if (sec == null) {
            return null;
        }
        String name = sec.getString("potion", "speed");
        PotionEffectType t = resolve(name);
        if (t == null) {
            return null;
        }
        return new PotionSpec(
                t,
                sec.getInt("amplifier", 0),
                sec.getBoolean("ambient", true)
        );
    }

    /**
     * 从 operators.yml 的 map 列表项构建。
     */
    public static PotionSpec fromMap(Map<?, ?> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        Object potionObj = map.get("potion");
        if (potionObj == null) {
            return null;
        }
        PotionEffectType t = resolve(String.valueOf(potionObj));
        if (t == null) {
            return null;
        }
        int amp = 0;
        Object a = map.get("amplifier");
        if (a instanceof Number n) {
            amp = n.intValue();
        } else if (a != null) {
            try {
                amp = Integer.parseInt(a.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        boolean ambient = true;
        Object amb = map.get("ambient");
        if (amb instanceof Boolean b) {
            ambient = b;
        } else if (amb != null) {
            ambient = Boolean.parseBoolean(amb.toString());
        }
        return new PotionSpec(t, amp, ambient);
    }

    /**
     * Paper 1.20.3+：Registry.POTION_EFFECT_TYPE + NamespacedKey。
     * 兼容 SPEED / speed / minecraft:speed 等写法。
     */
    public static PotionEffectType resolve(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String key = raw.trim().toLowerCase(Locale.ROOT)
                .replace("minecraft:", "")
                .replace('-', '_');

        // 旧配置名 → 现代 key
        key = switch (key) {
            case "jump", "jump_boost", "jumpboost" -> "jump_boost";
            case "slow", "slowness" -> "slowness";
            case "slow_digging", "mining_fatigue" -> "mining_fatigue";
            case "increase_damage", "strength" -> "strength";
            case "heal", "instant_health" -> "instant_health";
            case "harm", "instant_damage" -> "instant_damage";
            case "confusion", "nausea" -> "nausea";
            case "damage_resistance", "resistance" -> "resistance";
            case "fast_digging", "haste" -> "haste";
            default -> key;
        };

        NamespacedKey nk = NamespacedKey.minecraft(key);

        // 1) 现代 Registry
        try {
            PotionEffectType t = Registry.POTION_EFFECT_TYPE.get(nk);
            if (t != null) {
                return t;
            }
        } catch (Throwable ignored) {
        }

        // 2) Paper RegistryAccess（部分 1.21+ 构建）
        try {
            var registry = io.papermc.paper.registry.RegistryAccess.registryAccess()
                    .getRegistry(io.papermc.paper.registry.RegistryKey.MOB_EFFECT);
            // 若 API 为 MOB_EFFECT / POTION_EFFECT 名称不同，下面反射兜底
            Object got = registry.getClass().getMethod("get", NamespacedKey.class).invoke(registry, nk);
            if (got instanceof PotionEffectType pet) {
                return pet;
            }
        } catch (Throwable ignored) {
        }

        // 3) 遍历 Registry（最后手段）
        try {
            for (PotionEffectType type : Registry.POTION_EFFECT_TYPE) {
                NamespacedKey k = type.getKey();
                if (k.getKey().equalsIgnoreCase(key)) {
                    return type;
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }
}
