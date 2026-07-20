package org.starset.deltaforcestrike.item;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class GameItem {

    private final String id;
    private final String name;
    private final String type;
    private final String kind;
    private final Material material;
    private final int price;
    private final String action;
    private final String slot;
    private final boolean undroppable;
    /** 自定义最大耐久；≤0 表示使用材质默认 */
    private final int maxDurability;
    /** 攻击伤害覆盖；≤0 表示不覆盖（走材质+附魔） */
    private final double attackDamage;
    private final Map<String, Integer> enchantments;
    private final Map<String, Material> armorPieces;

    private GameItem(Builder b) {
        this.id = b.id;
        this.name = b.name;
        this.type = b.type;
        this.kind = b.kind;
        this.material = b.material;
        this.price = b.price;
        this.action = b.action;
        this.slot = b.slot;
        this.undroppable = b.undroppable;
        this.maxDurability = b.maxDurability;
        this.attackDamage = b.attackDamage;
        this.enchantments = Collections.unmodifiableMap(b.enchantments);
        this.armorPieces = Collections.unmodifiableMap(b.armorPieces);
    }

    public static GameItem from(String id, ConfigurationSection section) {
        Builder b = new Builder();
        b.id = id;
        b.name = section.getString("name", id);
        b.type = section.getString("type", "misc");
        b.kind = section.getString("kind", section.contains("armor") ? "armor_set" : "item");
        b.price = section.getInt("price", 0);
        b.action = section.getString("action", "");
        b.slot = section.getString("slot", "");
        b.undroppable = section.getBoolean("undroppable", false);
        b.maxDurability = section.getInt("max-durability", 0);
        b.attackDamage = section.getDouble("attack-damage", 0);

        String mat = section.getString("material");
        if (mat != null && !mat.isEmpty()) {
            b.material = Material.matchMaterial(mat);
        }

        ConfigurationSection ench = section.getConfigurationSection("enchantments");
        if (ench != null) {
            for (String key : ench.getKeys(false)) {
                b.enchantments.put(key.toLowerCase(Locale.ROOT), ench.getInt(key));
            }
        }

        ConfigurationSection armor = section.getConfigurationSection("armor");
        if (armor != null) {
            for (String part : armor.getKeys(false)) {
                Material m = Material.matchMaterial(armor.getString(part, ""));
                if (m != null) {
                    b.armorPieces.put(part.toLowerCase(Locale.ROOT), m);
                }
            }
        }

        return new GameItem(b);
    }

    public boolean isArmorSet() {
        return "armor_set".equalsIgnoreCase(kind);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getType() { return type; }
    public String getKind() { return kind; }
    public Material getMaterial() { return material; }
    public int getPrice() { return price; }
    public String getAction() { return action; }
    public String getSlot() { return slot; }
    public boolean isUndroppable() { return undroppable; }
    /** ≤0 = 默认耐久 */
    public int getMaxDurability() { return maxDurability; }
    /** ≤0 = 不覆盖攻击伤害 */
    public double getAttackDamage() { return attackDamage; }
    public Map<String, Integer> getEnchantments() { return enchantments; }
    public Map<String, Material> getArmorPieces() { return armorPieces; }

    @Override
    public String toString() {
        return "GameItem{id=" + id
                + ", kind=" + kind
                + ", type=" + type
                + ", slot=" + slot
                + ", price=" + price
                + "}";
    }

    private static final class Builder {
        String id, name, type, kind = "item", action, slot;
        Material material;
        int price;
        boolean undroppable;
        int maxDurability;
        double attackDamage;
        Map<String, Integer> enchantments = new HashMap<>();
        Map<String, Material> armorPieces = new HashMap<>();
    }
}
