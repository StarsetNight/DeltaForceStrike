package org.starset.deltaforcestrike.item;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.starset.deltaforcestrike.DeltaForceStrike;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class ItemManager {

    private final DeltaForceStrike plugin;
    private final Map<String, GameItem> items = new HashMap<>();

    public ItemManager(DeltaForceStrike plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // 加载
    // ------------------------------------------------------------------

    public void loadItems() {
        items.clear();

        File file = new File(plugin.getDataFolder(), "items.yml");
        if (!file.exists()) {
            plugin.saveResource("items.yml", false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("items");
        if (root == null) {
            plugin.getLogger().warning("items.yml 缺少 items 节点");
            return;
        }

        loadSection(root, "");
        plugin.getLogger().info("已加载自定义物品: " + items.size());

        if (plugin.getConfig().getBoolean("debug.enabled", false)) {
            items.keySet().stream().sorted().forEach(id ->
                    plugin.getLogger().info("  - " + items.get(id))
            );
        }
    }

    public void reload() {
        loadItems();
    }

    private void loadSection(ConfigurationSection section, String path) {
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (!(value instanceof ConfigurationSection child)) {
                continue;
            }

            String currentPath = path.isEmpty() ? key : path + "." + key;

            if (child.contains("name") && (child.contains("material") || child.contains("armor"))) {
                try {
                    GameItem item = GameItem.from(currentPath, child);
                    items.put(currentPath, item);
                    items.putIfAbsent(key, item);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "加载物品失败: " + currentPath, e);
                }
            } else {
                loadSection(child, currentPath);
            }
        }
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    public GameItem getGameItem(String id) {
        return items.get(id);
    }

    public Map<String, GameItem> getAll() {
        return Collections.unmodifiableMap(items);
    }

    public String getItemId(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.id(), PersistentDataType.STRING);
    }

    public String getItemType(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.type(), PersistentDataType.STRING);
    }

    public boolean isUndroppable(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        Byte v = stack.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.undroppable(), PersistentDataType.BYTE);
        return v != null && v == 1;
    }

    /**
     * 识别是否为「守护」类物品（用于清理/锁定）。
     * 发放是否允许见 ConfigKeys.shieldEnabled()。
     */
    public boolean isShield(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        if ("shield".equalsIgnoreCase(getItemType(stack))) {
            return true;
        }
        String id = getItemId(stack);
        if (id != null && id.toLowerCase(Locale.ROOT).contains("shield")) {
            return true;
        }
        // 关闭盾时：原版 SHIELD 也当残留清掉
        return stack.getType() == Material.SHIELD;
    }


    // ------------------------------------------------------------------
    // 创建 / 发放
    // ------------------------------------------------------------------

    public ItemStack createItem(String id) {
        GameItem def = items.get(id);
        if (def == null) {
            plugin.getLogger().warning("不存在物品: " + id);
            return null;
        }
        if (def.isArmorSet()) {
            plugin.getLogger().warning("物品 " + id + " 是护甲套装，请用 giveArmorSet()");
            return null;
        }
        if (def.getMaterial() == null) {
            plugin.getLogger().warning("物品 " + id + " 无有效 material");
            return null;
        }

        ItemStack stack = new ItemStack(def.getMaterial());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            applyMetaTags(meta, def);
            stack.setItemMeta(meta);
        }

        applyEnchantments(stack, def);
        applyCustomDurability(stack, def);
        applyAttackDamage(stack, def);

        // 附魔后再次确保：诅咒 / 不堆叠 / 实例 ID / amount=1
        ItemMeta m2 = stack.getItemMeta();
        if (m2 != null) {
            if (shouldVanish(def)) {
                applyVanishingCurse(m2);
            }
            if (shouldBeUnique(def)) {
                m2.setMaxStackSize(1);
                if (!m2.getPersistentDataContainer().has(ItemKeys.instanceId(), PersistentDataType.STRING)) {
                    m2.getPersistentDataContainer().set(
                            ItemKeys.instanceId(),
                            PersistentDataType.STRING,
                            UUID.randomUUID().toString()
                    );
                }
            }
            boolean undroppable = def.isUndroppable()
                    || "shield".equalsIgnoreCase(def.getType())
                    || "armor".equalsIgnoreCase(def.getType());
            if (undroppable) {
                m2.getPersistentDataContainer()
                        .set(ItemKeys.undroppable(), PersistentDataType.BYTE, (byte) 1);
            }
            stack.setItemMeta(m2);
        }

        if (shouldBeUnique(def) && stack.getAmount() != 1) {
            stack.setAmount(1);
        }

        return stack;
    }

    public boolean giveArmorSet(Player player, String id) {
        GameItem def = items.get(id);
        if (def == null || !def.isArmorSet()) {
            return false;
        }

        Map<String, Material> pieces = def.getArmorPieces();
        if (pieces.containsKey("helmet")) {
            player.getInventory().setHelmet(taggedArmor(pieces.get("helmet"), def));
        }
        if (pieces.containsKey("chestplate")) {
            player.getInventory().setChestplate(taggedArmor(pieces.get("chestplate"), def));
        }
        if (pieces.containsKey("leggings")) {
            player.getInventory().setLeggings(taggedArmor(pieces.get("leggings"), def));
        }
        if (pieces.containsKey("boots")) {
            player.getInventory().setBoots(taggedArmor(pieces.get("boots"), def));
        }

        // 铁甲附带盾：也可只在 ItemGiveService 做一份，两处做一处即可
        // 这里不重复发盾，统一由 ItemGiveService 处理

        return true;
    }


    // ------------------------------------------------------------------
    // Meta / 附魔 / 消失诅咒 / 不堆叠
    // ------------------------------------------------------------------

    private void applyMetaTags(ItemMeta meta, GameItem def) {
        meta.displayName(color(def.getName()));
        meta.getPersistentDataContainer().set(ItemKeys.id(), PersistentDataType.STRING, def.getId());
        meta.getPersistentDataContainer().set(
                ItemKeys.type(),
                PersistentDataType.STRING,
                def.getType() == null ? "misc" : def.getType()
        );

        if (def.getAction() != null && !def.getAction().isEmpty()) {
            meta.getPersistentDataContainer()
                    .set(ItemKeys.action(), PersistentDataType.STRING, def.getAction());
        }

        boolean undroppable = def.isUndroppable()
                || "shield".equalsIgnoreCase(def.getType())
                || "armor".equalsIgnoreCase(def.getType());

        if (undroppable) {
            meta.getPersistentDataContainer()
                    .set(ItemKeys.undroppable(), PersistentDataType.BYTE, (byte) 1);
        }

        if (shouldVanish(def)) {
            applyVanishingCurse(meta);
        }

        if (shouldBeUnique(def)) {
            meta.setMaxStackSize(1);
            meta.getPersistentDataContainer().set(
                    ItemKeys.instanceId(),
                    PersistentDataType.STRING,
                    UUID.randomUUID().toString()
            );
        }
    }

    /**
     * 竞技物默认一件一格：武器、道具、炸弹、盾、技能。
     */
    public boolean shouldBeUnique(GameItem def) {
        if (def == null) {
            return false;
        }
        String type = def.getType() == null ? "" : def.getType().toLowerCase(Locale.ROOT);
        String slot = def.getSlot() == null ? "" : def.getSlot().toLowerCase(Locale.ROOT);
        Material mat = def.getMaterial();

        if (type.equals("melee") || type.equals("ranged")
                || type.equals("utility") || type.equals("bomb")
                || type.equals("shield") || type.equals("skill")
                || type.equals("skill_charge")) {
            return true;
        }
        if (slot.equals("melee") || slot.equals("ranged")
                || slot.equals("utility") || slot.equals("bomb")
                || slot.equals("shield")) {
            return true;
        }
        if (mat == null) {
            return false;
        }
        return mat == Material.FIREWORK_STAR
                || mat == Material.FIRE_CHARGE
                || mat == Material.WITHER_SKELETON_SKULL
                || mat == Material.SHEARS
                || mat == Material.TNT
                || mat == Material.SHIELD;
    }

    private ItemStack taggedArmor(Material material, GameItem def) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(color(def.getName()));
            meta.getPersistentDataContainer().set(ItemKeys.id(), PersistentDataType.STRING, def.getId());
            meta.getPersistentDataContainer().set(ItemKeys.type(), PersistentDataType.STRING, "armor");
            meta.getPersistentDataContainer().set(ItemKeys.undroppable(), PersistentDataType.BYTE, (byte) 1);
            meta.setMaxStackSize(1);
            meta.getPersistentDataContainer().set(
                    ItemKeys.instanceId(),
                    PersistentDataType.STRING,
                    UUID.randomUUID().toString()
            );
            if (plugin.getConfig().getBoolean("player.armor-vanish", true)) {
                applyVanishingCurse(meta);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private boolean shouldVanish(GameItem def) {
        if (!plugin.getConfig().getBoolean("player.armor-vanish", true)) {
            return false;
        }
        String type = def.getType() == null ? "" : def.getType().toLowerCase(Locale.ROOT);
        return def.isUndroppable()
                || def.isArmorSet()
                || "shield".equals(type)
                || "armor".equals(type);
    }

    private void applyVanishingCurse(ItemMeta meta) {
        Enchantment vanishing = resolveEnchantment("vanishing_curse");
        if (vanishing != null) {
            meta.addEnchant(vanishing, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        } else {
            plugin.getLogger().warning("无法解析附魔 vanishing_curse");
        }
    }

    private void applyEnchantments(ItemStack item, GameItem def) {
        for (Map.Entry<String, Integer> e : def.getEnchantments().entrySet()) {
            Enchantment enchant = resolveEnchantment(e.getKey());
            if (enchant != null) {
                // unsafe：允许快速装填 V、穿透 IV 等超限等级
                item.addUnsafeEnchantment(enchant, Math.max(1, e.getValue()));
            } else {
                plugin.getLogger().warning("未知附魔: " + e.getKey() + " @ " + def.getId()
                        + "（请用 registry 名如 quick_charge / piercing / multishot）");
            }
        }
    }

    /**
     * attack-damage: 覆盖主手攻击伤害为固定值（如 20）。
     * 使用 ADD_NUMBER 加到玩家基础 1 点上，故 amount = 目标伤害 - 1。
     * 会清除主手原有 ATTACK_DAMAGE 修饰符，避免与材质默认叠算。
     */
    private void applyAttackDamage(ItemStack stack, GameItem def) {
        if (stack == null || def == null || def.getAttackDamage() <= 0) {
            return;
        }
        double target = def.getAttackDamage();
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }

        Attribute attr = resolveAttackDamageAttribute();
        if (attr == null) {
            plugin.getLogger().warning("无法解析 ATTACK_DAMAGE 属性 @ " + def.getId());
            return;
        }

        try {
            meta.removeAttributeModifier(attr);
        } catch (Throwable ignored) {
        }

        // 玩家空手基础伤害 1.0；最终 ≈ 1 + amount
        double amount = target - 1.0;
        NamespacedKey key = new NamespacedKey(plugin, "dfs_atk_" + safeKey(def.getId()));

        AttributeModifier mod = null;
        // Paper 1.21+：NamespacedKey + EquipmentSlotGroup
        try {
            mod = new AttributeModifier(
                    key,
                    amount,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.MAINHAND
            );
        } catch (Throwable t1) {
            try {
                // 旧 API：UUID + EquipmentSlot
                mod = AttributeModifier.class
                        .getConstructor(UUID.class, String.class, double.class,
                                AttributeModifier.Operation.class,
                                org.bukkit.inventory.EquipmentSlot.class)
                        .newInstance(
                                UUID.nameUUIDFromBytes(key.toString().getBytes()),
                                "dfs_atk",
                                amount,
                                AttributeModifier.Operation.ADD_NUMBER,
                                org.bukkit.inventory.EquipmentSlot.HAND
                        );
            } catch (Throwable t2) {
                plugin.getLogger().warning("创建攻击伤害修饰符失败 @ " + def.getId()
                        + ": " + t2.getMessage());
                return;
            }
        }

        try {
            meta.addAttributeModifier(attr, mod);
            // 显示属性行，方便确认
            try {
                meta.removeItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            } catch (Throwable ignored) {
            }
            stack.setItemMeta(meta);
        } catch (Throwable t) {
            plugin.getLogger().warning("写入攻击伤害失败 @ " + def.getId() + ": " + t.getMessage());
        }
    }

    private static Attribute resolveAttackDamageAttribute() {
        // 现代 API（1.21+）
        try {
            return Attribute.ATTACK_DAMAGE;
        } catch (Throwable ignored) {
        }
        // Registry（无 valueOf 弃用警告）
        try {
            var reg = RegistryAccess.registryAccess()
                    .getRegistry(RegistryKey.ATTRIBUTE);
            Attribute a = reg.get(NamespacedKey.minecraft("attack_damage"));
            if (a != null) {
                return a;
            }
            a = reg.get(NamespacedKey.minecraft("generic.attack_damage"));
            if (a != null) {
                return a;
            }
        } catch (Throwable ignored) {
        }
        // 反射读旧常量，避免编译期 valueOf 弃用
        try {
            Object v = Attribute.class.getField("GENERIC_ATTACK_DAMAGE").get(null);
            if (v instanceof Attribute attr) {
                return attr;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String safeKey(String id) {
        if (id == null || id.isEmpty()) {
            return "item";
        }
        return id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
    }

    /**
     * max-durability: 剩余可用次数。
     * 优先 Paper setMaxDamage；否则用 damage = 材质默认耐久 - 配置值。
     */
    private void applyCustomDurability(ItemStack stack, GameItem def) {
        if (stack == null || def == null || def.getMaxDurability() <= 0) {
            return;
        }
        int want = Math.max(1, def.getMaxDurability());
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return;
        }
        // Paper 1.20.5+：直接设最大耐久
        try {
            damageable.getClass().getMethod("setMaxDamage", int.class).invoke(damageable, want);
            damageable.setDamage(0);
            stack.setItemMeta(meta);
            return;
        } catch (Throwable ignored) {
        }
        // 兼容：用已损值模拟「只剩 want 点」
        int typeMax = stack.getType().getMaxDurability();
        if (typeMax <= 0) {
            return;
        }
        int remaining = Math.min(want, typeMax);
        damageable.setDamage(Math.max(0, typeMax - remaining));
        stack.setItemMeta(meta);
    }

    private Enchantment resolveEnchantment(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String k = key.toLowerCase(Locale.ROOT).trim()
                .replace("minecraft:", "")
                .replace('-', '_')
                .replace(' ', '_');
        // 常见别名
        k = switch (k) {
            case "quickcharge", "quick_charge_i", "qc" -> "quick_charge";
            case "multi_shot", "multi-shot" -> "multishot";
            case "pierce" -> "piercing";
            case "durability", "unbreak" -> "unbreaking";
            case "vanishing", "curse_of_vanishing" -> "vanishing_curse";
            default -> k;
        };

        try {
            var reg = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
            Enchantment e = reg.get(NamespacedKey.minecraft(k));
            if (e != null) {
                return e;
            }
            // 遍历兜底（部分构建 key 写法不同）
            for (Enchantment ench : reg) {
                if (ench == null) {
                    continue;
                }
                NamespacedKey nk = ench.getKey();
                if (nk != null && nk.getKey().equalsIgnoreCase(k)) {
                    return ench;
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("解析附魔失败 " + key + ": " + t.getMessage());
        }
        return null;
    }

    private static Component color(String input) {
        if (input == null) {
            return Component.empty();
        }
        return LegacyComponentSerializer.legacySection().deserialize(input);
    }
}
