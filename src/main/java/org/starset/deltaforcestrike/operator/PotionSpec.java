package org.starset.deltaforcestrike.operator;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;
import java.util.Map;

/** Spigot-compatible potion effect specification. */
public record PotionSpec(PotionEffectType type, int amplifier, boolean ambient) {
    public static PotionSpec from(ConfigurationSection sec) {
        if (sec == null) return null;
        PotionEffectType type = resolve(sec.getString("potion", "speed"));
        return type == null ? null : new PotionSpec(type, sec.getInt("amplifier", 0), sec.getBoolean("ambient", true));
    }

    public static PotionSpec fromMap(Map<?, ?> map) {
        if (map == null || map.isEmpty() || map.get("potion") == null) return null;
        PotionEffectType type = resolve(String.valueOf(map.get("potion")));
        if (type == null) return null;
        Object a = map.get("amplifier");
        int amplifier = a instanceof Number n ? n.intValue() : parseInt(a, 0);
        Object ambient = map.get("ambient");
        boolean isAmbient = ambient == null || Boolean.parseBoolean(String.valueOf(ambient));
        return new PotionSpec(type, amplifier, isAmbient);
    }

    public static PotionEffectType resolve(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String key = raw.trim().toUpperCase(Locale.ROOT).replace("MINECRAFT:", "").replace('-', '_');
        key = switch (key) {
            case "JUMP", "JUMPBOOST" -> "JUMP_BOOST";
            case "SLOW" -> "SLOWNESS";
            case "SLOW_DIGGING" -> "MINING_FATIGUE";
            case "INCREASE_DAMAGE" -> "STRENGTH";
            case "HEAL" -> "INSTANT_HEALTH";
            case "HARM" -> "INSTANT_DAMAGE";
            case "CONFUSION" -> "NAUSEA";
            case "DAMAGE_RESISTANCE" -> "RESISTANCE";
            case "FAST_DIGGING" -> "HASTE";
            default -> key;
        };
        PotionEffectType type = PotionEffectType.getByName(key);
        return type != null ? type : PotionEffectType.getByName(raw.trim().toUpperCase(Locale.ROOT));
    }

    private static int parseInt(Object value, int fallback) {
        try { return value == null ? fallback : Integer.parseInt(value.toString()); }
        catch (NumberFormatException ignored) { return fallback; }
    }
}
