package org.starset.deltaforcestrike.operator;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.starset.deltaforcestrike.DeltaForceStrike;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class OperatorRegistry {

    private final DeltaForceStrike plugin;
    private final Map<String, OperatorDefinition> byId = new LinkedHashMap<>();
    private YamlConfiguration raw;

    public OperatorRegistry(DeltaForceStrike plugin) {
        this.plugin = plugin;
    }

    public void load() {
        byId.clear();
        File file = new File(plugin.getDataFolder(), "operators.yml");
        if (!file.exists()) {
            plugin.saveResource("operators.yml", false);
        }
        raw = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = raw.getConfigurationSection("operators");
        if (root == null) {
            plugin.getLogger().warning("operators.yml 缺少 operators 节点");
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec == null) continue;
            OperatorDefinition def = OperatorDefinition.from(key.toLowerCase(Locale.ROOT), sec);
            byId.put(def.getId(), def);
            // 中文名别名
            byId.putIfAbsent(def.getDisplayName(), def);
            if (def.getEnglishName() != null) {
                byId.putIfAbsent(def.getEnglishName().toLowerCase(Locale.ROOT), def);
            }
        }
        plugin.getLogger().info("[Operator] 已加载干员: " + byId.values().stream().distinct().count());
    }

    public void reload() {
        load();
    }

    public OperatorDefinition get(String idOrName) {
        if (idOrName == null) return null;
        OperatorDefinition d = byId.get(idOrName.toLowerCase(Locale.ROOT));
        if (d != null) return d;
        return byId.get(idOrName);
    }

    public Collection<OperatorDefinition> allUnique() {
        return byId.values().stream().distinct().toList();
    }

    public Map<String, OperatorDefinition> asMap() {
        return Collections.unmodifiableMap(byId);
    }

    public int getUltimatePointsPerKill() {
        return raw == null ? 1 : raw.getInt("settings.ultimate-points-per-kill", 1);
    }

    public int getUltimatePointsPerRound() {
        return raw == null ? 1 : raw.getInt("settings.ultimate-points-per-round", 1);
    }

    public YamlConfiguration getRaw() {
        return raw;
    }
}
