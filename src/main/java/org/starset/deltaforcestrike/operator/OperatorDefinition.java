package org.starset.deltaforcestrike.operator;

import org.bukkit.configuration.ConfigurationSection;

public final class OperatorDefinition {

    private final String id;
    private final String displayName;
    private final String englishName;
    private final OperatorType type;
    private final SkillDefinition passive;
    private final SkillDefinition signature;
    private final SkillDefinition purchasable;
    private final SkillDefinition ultimate;

    public OperatorDefinition(String id, String displayName, String englishName, OperatorType type,
                              SkillDefinition passive, SkillDefinition signature,
                              SkillDefinition purchasable, SkillDefinition ultimate) {
        this.id = id;
        this.displayName = displayName;
        this.englishName = englishName;
        this.type = type;
        this.passive = passive;
        this.signature = signature;
        this.purchasable = purchasable;
        this.ultimate = ultimate;
    }

    public static OperatorDefinition from(String id, ConfigurationSection sec) {
        OperatorType type = OperatorType.ASSAULT;
        try {
            type = OperatorType.valueOf(sec.getString("type", "ASSAULT").toUpperCase());
        } catch (Exception ignored) {
        }
        return new OperatorDefinition(
                id,
                sec.getString("display-name", id),
                sec.getString("english-name", id),
                type,
                SkillDefinition.from(sec.getConfigurationSection("passive"), SkillKind.PASSIVE),
                SkillDefinition.from(sec.getConfigurationSection("signature"), SkillKind.SIGNATURE),
                SkillDefinition.from(sec.getConfigurationSection("purchasable"), SkillKind.PURCHASABLE),
                SkillDefinition.from(sec.getConfigurationSection("ultimate"), SkillKind.ULTIMATE)
        );
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getEnglishName() { return englishName; }
    public OperatorType getType() { return type; }
    public SkillDefinition getPassive() { return passive; }
    public SkillDefinition getSignature() { return signature; }
    public SkillDefinition getPurchasable() { return purchasable; }
    public SkillDefinition getUltimate() { return ultimate; }
}
