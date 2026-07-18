package org.starset.deltaforcestrike.item;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.starset.deltaforcestrike.DeltaForceStrike;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

public class ItemManager {

    private final DeltaForceStrike plugin;
    private final Map<String, GameItem> items = new HashMap<>();

    public ItemManager(DeltaForceStrike plugin) {
        this.plugin = plugin;
    }

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
                    plugin.getLogger().info("  - " + id + " ($" + items.get(id).getPrice() + ")")
            );
        }
    }

    private void loadSection(ConfigurationSection section, String path) {
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (!(value instanceof ConfigurationSection child)) {
                continue;
            }

            String currentPath = path.isEmpty() ? key : path + "." + key;

            // 叶子节点：有 name，且有 material 或 armor 套装
            if (child.contains("name") && (child.contains("material") || child.contains("armor"))) {
                try {
                    GameItem item = GameItem.from(currentPath, child);
                    items.put(currentPath, item);
                    // 短 id 别名（不覆盖已有长路径/其它条目）
                    items.putIfAbsent(key, item);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "加载物品失败: " + currentPath, e);
                }
            } else {
                loadSection(child, currentPath);
            }
        }
    }

    public GameItem getGameItem(String id) {
        return items.get(id);
    }

    public Map<String, GameItem> getAll() {
        return Collections.unmodifiableMap(items);
    }

    /** 创建单件物品（非护甲套装） */
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
            meta.displayName(color(def.getName()));
            meta.getPersistentDataContainer().set(ItemKeys.id(), PersistentDataType.STRING, def.getId());
            meta.getPersistentDataContainer().set(ItemKeys.type(), PersistentDataType.STRING, def.getType());
            if (def.getAction() != null && !def.getAction().isEmpty()) {
                meta.getPersistentDataContainer().set(ItemKeys.action(), PersistentDataType.STRING, def.getAction());
            }
            if (def.isUndroppable()) {
                meta.getPersistentDataContainer().set(ItemKeys.undroppable(), PersistentDataType.BYTE, (byte) 1);
            }
            stack.setItemMeta(meta);
        }

        applyEnchantments(stack, def);
        return stack;
    }

    /** 发放护甲套装 */
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
        return true;
    }

    private ItemStack taggedArmor(Material material, GameItem def) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(color(def.getName()));
            meta.getPersistentDataContainer().set(ItemKeys.id(), PersistentDataType.STRING, def.getId());
            meta.getPersistentDataContainer().set(ItemKeys.type(), PersistentDataType.STRING, "armor");
            meta.getPersistentDataContainer().set(ItemKeys.undroppable(), PersistentDataType.BYTE, (byte) 1);

            if (plugin.getConfig().getBoolean("player.armor-vanish", true)) {
                Enchantment vanishing = resolveEnchantment("vanishing_curse");
                if (vanishing != null) {
                    meta.addEnchant(vanishing, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private void applyEnchantments(ItemStack item, GameItem def) {
        for (Map.Entry<String, Integer> e : def.getEnchantments().entrySet()) {
            Enchantment enchant = resolveEnchantment(e.getKey());
            if (enchant != null) {
                item.addUnsafeEnchantment(enchant, e.getValue());
            } else {
                plugin.getLogger().warning("未知附魔: " + e.getKey() + " @ " + def.getId());
            }
        }
    }

    /**
     * Paper 1.21+：使用 RegistryAccess，避免弃用的 Registry.ENCHANTMENT。
     */
    private Enchantment resolveEnchantment(String key) {
        String k = key.toLowerCase(Locale.ROOT).trim();
        NamespacedKey namespacedKey = NamespacedKey.minecraft(k);
        return RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.ENCHANTMENT)
                .get(namespacedKey);
    }

    public String getItemId(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(ItemKeys.id(), PersistentDataType.STRING);
    }

    public boolean isUndroppable(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        Byte v = stack.getItemMeta().getPersistentDataContainer().get(ItemKeys.undroppable(), PersistentDataType.BYTE);
        return v != null && v == 1;
    }

    public void reload() {
        loadItems();
    }

    private static Component color(String input) {
        return LegacyComponentSerializer.legacySection().deserialize(input);
    }
}
