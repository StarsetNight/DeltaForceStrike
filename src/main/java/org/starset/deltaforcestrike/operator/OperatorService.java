package org.starset.deltaforcestrike.operator;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.item.ItemKeys;
import org.starset.deltaforcestrike.match.Match;
import org.starset.deltaforcestrike.match.PlayerSession;
import org.starset.deltaforcestrike.operator.skill.SkillContext;
import org.starset.deltaforcestrike.operator.skill.SkillHandler;
import org.starset.deltaforcestrike.operator.skill.SkillHandlerRegistry;
import org.starset.deltaforcestrike.operator.skill.SkillResult;
import org.starset.deltaforcestrike.util.InventorySlots;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 干员运行时服务。
 * 槽位：6=招牌(第7格) 7=购买(第8格) 8=大招(第9格)
 */
public class OperatorService {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacySection();

    private final DeltaForceStrike plugin;
    private final OperatorRegistry registry;
    private final SkillHandlerRegistry handlers;
    private final Map<UUID, OperatorLoadout> loadouts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> skillClickCooldownUntil = new ConcurrentHashMap<>();

    public OperatorService(DeltaForceStrike plugin) {
        this.plugin = plugin;
        this.registry = new OperatorRegistry(plugin);
        this.handlers = new SkillHandlerRegistry();
        this.registry.load();
    }

    public OperatorRegistry getRegistry() {
        return registry;
    }

    public void reload() {
        registry.reload();
    }

    public OperatorLoadout getLoadout(UUID uuid) {
        return loadouts.get(uuid);
    }

    public OperatorLoadout ensureLoadout(Player player) {
        return loadouts.computeIfAbsent(player.getUniqueId(), OperatorLoadout::new);
    }

    public void clearPlayer(UUID uuid) {
        loadouts.remove(uuid);
    }

    // =====================================================================
    // 选人 / 开局
    // =====================================================================

    public boolean selectOperator(Player player, String idOrName) {
        OperatorDefinition def = registry.get(idOrName);
        if (def == null) {
            player.sendMessage("§c未知干员。可用: niko, bruo, aier, wulong");
            return false;
        }
        Match match = plugin.getMatchManager().getMatch();
        PlayerSession session = match == null ? null : match.getSession(player.getUniqueId());
        if (session != null) {
            session.setOperatorId(def.getId());
        }
        OperatorLoadout load = ensureLoadout(player);
        load.bind(def);
        player.sendMessage("§a[DFS] 已选择干员: §f" + def.getDisplayName()
                + " §7(" + def.getType() + ")");
        return true;
    }

    public void prepareMatch(Match match) {
        var list = registry.allUnique().stream().toList();
        if (list.isEmpty() || match == null) {
            return;
        }
        int i = 0;
        for (Player p : match.onlinePlayers()) {
            PlayerSession s = match.getSession(p.getUniqueId());
            OperatorLoadout load = ensureLoadout(p);
            OperatorDefinition def = null;
            if (s != null && s.getOperatorId() != null && !s.getOperatorId().isEmpty()) {
                def = registry.get(s.getOperatorId());
            }
            if (def == null) {
                def = list.get(i % list.size());
                i++;
                if (s != null) {
                    s.setOperatorId(def.getId());
                }
            }
            load.bind(def);
            p.sendMessage("§6[DFS] 本局干员: §e" + def.getDisplayName());
        }
    }

    // =====================================================================
    // 回合
    // =====================================================================

    public void onRoundStart(Match match) {
        if (match == null) {
            return;
        }
        for (Player p : match.onlinePlayers()) {
            OperatorLoadout load = ensureLoadout(p);
            if (load.getDefinition() == null) {
                continue;
            }
            // 回合开始：先清上回合大招 Buff，再刷新招牌
            clearUltimateBuffs(p);
            load.onRoundStart();
            applyPassive(p, load);

            final Player fp = p;
            final OperatorLoadout fl = load;
            // 连续写入：招牌回满；购买技能若仍 ready 则保留第 8 格
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (fp.isOnline()) {
                    fl.refreshSignatureChargesFull();
                    giveSkillHotbarItems(fp, fl);
                }
            });
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (fp.isOnline()) {
                    fl.refreshSignatureChargesFull();
                    writeSignatureSlot(fp, fl);
                    writePurchasableSlot(fp, fl);
                    writeUltimateSlot(fp, fl);
                }
            }, 2L);
        }
    }

    public void onRoundEnd(Match match) {
        if (match == null) {
            return;
        }
        int perRound = registry.getUltimatePointsPerRound();
        int ultCap = registry.getUltimatePointsMax();

        for (Player p : match.onlinePlayers()) {
            OperatorLoadout load = getLoadout(p.getUniqueId());
            if (load == null || load.getDefinition() == null) {
                continue;
            }

            load.onRoundEnd();

            // 回合结束强制清除大招 Buff（妮可狂暴等）
            clearUltimateBuffs(p);
            load.setUltimateActiveThisRound(false);

            load.addUltimatePoints(perRound, ultCap);

            // 购买技能跨回合保留，直到释放消耗；仅刷新槽位显示
            writePurchasableSlot(p, load);
            writeUltimateSlot(p, load);
            writeSignatureSlot(p, load);

            int cost = load.getDefinition().getUltimate() == null
                    ? 4 : load.getDefinition().getUltimate().getUltimateCost();
            p.sendMessage("§d[DFS] 回合结束，大招 §f+" + perRound
                    + " §8(" + load.getUltimatePoints() + "/" + Math.min(cost, ultCap) + ")");
        }
    }

    /** 半场换边：大招充能清零 + 清 Buff */
    public void onHalfTime(Match match) {
        if (match == null) {
            return;
        }
        for (Player p : match.onlinePlayers()) {
            OperatorLoadout load = getLoadout(p.getUniqueId());
            if (load == null) {
                continue;
            }
            clearUltimateBuffs(p);
            load.resetUltimatePoints();
            load.clearPurchasableCharge();
            p.getInventory().setItem(InventorySlots.PURCHASABLE, null);
            writeUltimateSlot(p, load);
            writePurchasableSlot(p, load);
            p.sendMessage("§6[DFS] 半场换边，大招充能与购买技能已重置。");
        }
        // 断线占位也清
        for (var e : loadouts.entrySet()) {
            OperatorLoadout load = e.getValue();
            if (load != null) {
                load.resetUltimatePoints();
                load.clearPurchasableCharge();
            }
        }
    }

    public void onKill(Player killer) {
        if (killer == null) {
            return;
        }
        OperatorLoadout load = getLoadout(killer.getUniqueId());
        if (load == null || load.getDefinition() == null) {
            return;
        }
        SkillDefinition sig = load.getDefinition().getSignature();
        if (sig != null) {
            load.reduceSignatureCooldown(sig.getKillCooldownReduction());
        }
        load.addUltimatePoints(registry.getUltimatePointsPerKill(), registry.getUltimatePointsMax());
        writeSignatureSlot(killer, load);
        writeUltimateSlot(killer, load);
    }

    // =====================================================================
    // 技能栏：严格 6 / 7 / 8
    // =====================================================================

    /**
     * 第7格(index6)=招牌  第8格(index7)=购买  第9格(index8)=大招
     * 先清空三格再写入，避免叠放/串格。
     */
    public void giveSkillHotbarItems(Player p, OperatorLoadout load) {
        if (p == null || load == null || load.getDefinition() == null) {
            return;
        }

        ItemStack cursor = p.getItemOnCursor();
        if (isSkillStack(cursor)) {
            p.setItemOnCursor(null);
        }

        p.getInventory().setItem(InventorySlots.SIGNATURE, null);
        p.getInventory().setItem(InventorySlots.PURCHASABLE, null);
        p.getInventory().setItem(InventorySlots.ULTIMATE, null);

        OperatorDefinition def = load.getDefinition();

        if (def.getSignature() != null) {
            forceSetSlot(p, InventorySlots.SIGNATURE,
                    createSkillItem(def.getSignature(), SkillKind.SIGNATURE, load));
        }

        // 仅 ready 且 uses>0 才给第 8 格
        if (load.isPurchasableReady()
                && load.getPurchasableUsesLeft() > 0
                && def.getPurchasable() != null) {
            forceSetSlot(p, InventorySlots.PURCHASABLE,
                    createSkillItem(def.getPurchasable(), SkillKind.PURCHASABLE, load));
        }

        if (def.getUltimate() != null) {
            forceSetSlot(p, InventorySlots.ULTIMATE,
                    createSkillItem(def.getUltimate(), SkillKind.ULTIMATE, load));
        }

        p.updateInventory();
    }

    /** 强制写入槽位；若被占用且不是技能则掉落占用物 */
    private void forceSetSlot(Player p, int slot, ItemStack item) {
        if (item == null) {
            p.getInventory().setItem(slot, null);
            return;
        }
        ItemStack old = p.getInventory().getItem(slot);
        if (old != null && !old.getType().isAir() && !isSkillStack(old)) {
            // 非技能占格：掉地上（不应发生）
            p.getWorld().dropItemNaturally(p.getLocation(), old);
        }
        p.getInventory().setItem(slot, item);
    }

    private void writeSignatureSlot(Player p, OperatorLoadout load) {
        if (p == null || load == null || load.getDefinition() == null
                || load.getDefinition().getSignature() == null) {
            if (p != null) {
                p.getInventory().setItem(InventorySlots.SIGNATURE, null);
            }
            return;
        }
        forceSetSlot(p, InventorySlots.SIGNATURE,
                createSkillItem(load.getDefinition().getSignature(), SkillKind.SIGNATURE, load));
    }

    private void writePurchasableSlot(Player p, OperatorLoadout load) {
        if (p == null) {
            return;
        }
        // 未购买或次数用尽 → 必须空
        if (load == null
                || load.getDefinition() == null
                || load.getDefinition().getPurchasable() == null
                || !load.isPurchasableReady()
                || load.getPurchasableUsesLeft() <= 0) {
            p.getInventory().setItem(InventorySlots.PURCHASABLE, null);
            return;
        }
        ItemStack buy = createSkillItem(
                load.getDefinition().getPurchasable(), SkillKind.PURCHASABLE, load);
        forceSetSlot(p, InventorySlots.PURCHASABLE, buy);
    }

    private void writeUltimateSlot(Player p, OperatorLoadout load) {
        if (p == null || load == null || load.getDefinition() == null
                || load.getDefinition().getUltimate() == null) {
            if (p != null) {
                p.getInventory().setItem(InventorySlots.ULTIMATE, null);
            }
            return;
        }
        forceSetSlot(p, InventorySlots.ULTIMATE,
                createSkillItem(load.getDefinition().getUltimate(), SkillKind.ULTIMATE, load));
    }

    /**
     * 商店技能充能 → 只写入第 8 格，并强制刷新 7/9。
     */
    public boolean grantPurchasableFromShop(Player player) {
        OperatorLoadout load = ensureLoadout(player);
        if (load.getDefinition() == null || load.getDefinition().getPurchasable() == null) {
            player.sendMessage("§c你还没有干员或该干员无购买技能。");
            return false;
        }
        if (load.isSilenced()) {
            player.sendMessage("§c技能已被禁用。");
            return false;
        }
        // 已持有未用完的购买技能：禁止重复买
        if (load.isPurchasableReady() && load.getPurchasableUsesLeft() > 0) {
            player.sendMessage("§c你已持有购买技能，使用后再购买。");
            return false;
        }

        SkillDefinition purch = load.getDefinition().getPurchasable();
        load.setPurchasableReady(true);

        int uses = purch.getInt("max-uses", 1);
        if ("emergency_beacon".equalsIgnoreCase(purch.getHandlerId())) {
            uses = Math.max(uses, 2);
        }
        load.setPurchasableUsesLeft(uses);

        // 同步写入三格，避免只写了逻辑没物品
        final Player fp = player;
        final OperatorLoadout fl = load;
        // 立即写一次
        giveSkillHotbarItems(fp, fl);
        // 再延迟 1 tick 写一次（防 sanitize/商店关 GUI 覆盖）
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (fp.isOnline()) {
                giveSkillHotbarItems(fp, fl);
            }
        });

        player.sendMessage("§d[DFS] 已获得购买技能: §f" + purch.getName() + " §7→ 热键第§e8§7格");
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.6f, 1.4f);
        return true;
    }

    // =====================================================================
    // 创建技能物品
    // =====================================================================

    public ItemStack createSkillItem(SkillDefinition skill, SkillKind kind, OperatorLoadout load) {
        if (skill == null || kind == null) {
            return null;
        }

        Material mat = materialFor(skill, kind);
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        String suffix = buildSuffix(skill, kind, load);
        meta.displayName(LEGACY.deserialize("§d" + skill.getName() + suffix));
        meta.lore(java.util.List.of(
                Component.text(skill.getDescription() == null ? "" : skill.getDescription(), NamedTextColor.GRAY),
                Component.text(slotHint(kind), NamedTextColor.DARK_GRAY),
                Component.text("右键使用", NamedTextColor.DARK_PURPLE)
        ));

        meta.getPersistentDataContainer().set(
                OperatorKeys.skillKind(), PersistentDataType.STRING, kind.name());
        meta.getPersistentDataContainer().set(
                OperatorKeys.skillId(), PersistentDataType.STRING, skill.getId());
        meta.getPersistentDataContainer().set(
                ItemKeys.type(), PersistentDataType.STRING, "skill");
        meta.getPersistentDataContainer().set(
                ItemKeys.id(), PersistentDataType.STRING,
                "skill." + kind.name().toLowerCase() + "." + skill.getId());
        // 技能不可丢弃，但可被系统 setItem 覆盖
        meta.getPersistentDataContainer().set(
                ItemKeys.undroppable(), PersistentDataType.BYTE, (byte) 1);

        try {
            meta.setMaxStackSize(1);
        } catch (Throwable ignored) {
        }

        stack.setItemMeta(meta);
        return stack;
    }

    private String slotHint(SkillKind kind) {
        return switch (kind) {
            case SIGNATURE -> "热键第7格 · 招牌技能";
            case PURCHASABLE -> "热键第8格 · 购买技能";
            case ULTIMATE -> "热键第9格 · 终极技能";
            default -> "技能";
        };
    }

    private String buildSuffix(SkillDefinition skill, SkillKind kind, OperatorLoadout load) {
        if (load == null) {
            return "";
        }
        return switch (kind) {
            case SIGNATURE -> {
                String s = " §8[" + load.getSignatureCharges() + "/" + load.getSignatureMax() + "]";
                if (load.getSignatureCharges() < load.getSignatureMax()
                        && load.getSignatureRechargeLeftSeconds() > 0) {
                    s += " §7" + load.getSignatureRechargeLeftSeconds() + "s";
                }
                yield s;
            }
            case PURCHASABLE -> load.isPurchasableReady()
                    ? " §a×" + load.getPurchasableUsesLeft()
                    : " §7未购买";
            case ULTIMATE -> {
                int cost = skill.getUltimateCost();
                yield " §8[" + load.getUltimatePoints() + "/" + cost + "]";
            }
            default -> "";
        };
    }

    private Material materialFor(SkillDefinition skill, SkillKind kind) {
        String h = skill.getHandlerId() == null ? "" : skill.getHandlerId().toLowerCase();
        return switch (kind) {
            case SIGNATURE -> switch (h) {
                case "splash_harming", "splash_regen" -> Material.SPLASH_POTION;
                case "recon_arrow" -> Material.SPECTRAL_ARROW;
                case "dash", "ender_pearl" -> {
                    Material spear = Material.matchMaterial("DIAMOND_SPEAR");
                    if (spear == null) {
                        spear = Material.matchMaterial("TRIDENT");
                    }
                    yield spear != null ? spear : Material.DIAMOND_SWORD;
                }
                default -> Material.DIAMOND_SWORD;
            };
            case PURCHASABLE -> switch (h) {
                case "lingering_slowness" -> Material.LINGERING_POTION;
                case "round_smoke" -> Material.FIREWORK_STAR;
                case "wind_charge" -> {
                    Material wc = Material.matchMaterial("WIND_CHARGE");
                    yield wc != null ? wc : Material.BLAZE_ROD;
                }
                case "emergency_beacon" -> Material.END_CRYSTAL;
                default -> Material.BLAZE_ROD;
            };
            case ULTIMATE -> switch (h) {
                case "primed_tnt" -> Material.TNT;
                case "summon_vex" -> Material.SOUL_LANTERN;
                case "global_silence" -> Material.BELL;
                default -> Material.NETHER_STAR;
            };
            default -> Material.PAPER;
        };
    }

    public boolean isSkillStack(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        String type = stack.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.type(), PersistentDataType.STRING);
        if ("skill".equalsIgnoreCase(type)) {
            return true;
        }
        return stack.getItemMeta().getPersistentDataContainer()
                .has(OperatorKeys.skillKind(), PersistentDataType.STRING);
    }

    public SkillKind getSkillKind(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        String k = stack.getItemMeta().getPersistentDataContainer()
                .get(OperatorKeys.skillKind(), PersistentDataType.STRING);
        if (k == null) {
            return null;
        }
        try {
            return SkillKind.valueOf(k);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // =====================================================================
    // 使用技能 —— 必须手持对应技能物品
    // =====================================================================
    public boolean tryUseSkill(Player player, SkillKind kind) {
        Match match = plugin.getMatchManager().getMatch();
        if (match == null) {
            player.sendMessage("§c不在对局中");
            return false;
        }
        OperatorLoadout load = getLoadout(player.getUniqueId());
        if (load == null || load.getDefinition() == null) {
            player.sendMessage("§c未选择干员");
            return false;
        }
        if (kind == null || kind == SkillKind.PASSIVE) {
            return false;
        }

        long now = System.currentTimeMillis();
        Long cdUntil = skillClickCooldownUntil.get(player.getUniqueId());
        if (cdUntil != null && cdUntil > now) {
            return true;
        }

        if (load.isSilenced()) {
            player.sendMessage("§c技能已被禁用！");
            return true;
        }

        PlayerSession session = match.getSession(player.getUniqueId());
        if (session == null || !session.isAlive()) {
            return true;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!isSkillStack(hand)) {
            player.sendMessage("§c请手持技能物品再使用。");
            return true;
        }
        SkillKind handKind = getSkillKind(hand);
        if (handKind != null) {
            kind = handKind;
        }

        int held = player.getInventory().getHeldItemSlot();
        int expectedSlot = skillSlot(kind);
        if (expectedSlot < 0 || held != expectedSlot) {
            player.sendMessage("§c请在正确技能槽使用（7招牌/8购买/9大招）。");
            return true;
        }

        if (kind == SkillKind.PURCHASABLE && load.isBeaconDeployGrace()) {
            player.sendMessage("§e[DFS] 装置部署中，稍后再传送。");
            skillClickCooldownUntil.put(player.getUniqueId(), now + 400);
            return true;
        }

        SkillDefinition skill = skillOf(load, kind);
        if (skill == null) {
            return true;
        }

        switch (kind) {
            case SIGNATURE -> {
                if (load.getSignatureCharges() <= 0) {
                    player.sendMessage("§c招牌充能不足（" + load.getSignatureRechargeLeftSeconds() + "s）");
                    return true;
                }
            }
            case PURCHASABLE -> {
                if (!load.isPurchasableReady() || load.getPurchasableUsesLeft() <= 0) {
                    player.sendMessage("§c请先在商店购买「技能充能」。");
                    return true;
                }
            }
            case ULTIMATE -> {
                if (load.getUltimatePoints() < skill.getUltimateCost()) {
                    player.sendMessage("§c大招充能不足 §8("
                            + load.getUltimatePoints() + "/" + skill.getUltimateCost() + ")");
                    return true;
                }
            }
            default -> {
                return true;
            }
        }

        skillClickCooldownUntil.put(player.getUniqueId(), now + 400);

        SkillHandler handler = handlers.get(skill.getHandlerId());
        SkillResult result = handler.execute(new SkillContext(plugin, player, match, load, skill, kind));

        if (!result.success()) {
            if (load.isBeaconDeployGrace()) {
                skillClickCooldownUntil.put(player.getUniqueId(),
                        now + org.starset.deltaforcestrike.operator.skill.impl.EmergencyBeaconHandler.DEPLOY_GRACE_MS);
            } else {
                skillClickCooldownUntil.put(player.getUniqueId(), now + 200);
            }
            if (result.message() != null) {
                player.sendMessage("§c" + result.message());
            }
            return true;
        }

        switch (kind) {
            case SIGNATURE -> {
                load.setSignatureCharges(Math.max(0, load.getSignatureCharges() - 1));
                if (load.getSignatureCharges() < load.getSignatureMax()) {
                    load.startSignatureRecharge(skill.getRechargeSeconds());
                }
                writeSignatureSlot(player, load);
            }
            case PURCHASABLE -> {
                String hid = skill.getHandlerId() == null
                        ? "" : skill.getHandlerId().toLowerCase(Locale.ROOT);
                // 妮可：部署不取消充能；传送成功才取消
                if ("emergency_beacon".equals(hid)) {
                    if (load.isBeaconTeleportConsumed()) {
                        consumePurchasableFully(player, load);
                        load.setBeaconTeleportConsumed(false);
                    } else if (load.isBeaconArmed()) {
                        skillClickCooldownUntil.put(player.getUniqueId(),
                                System.currentTimeMillis()
                                        + org.starset.deltaforcestrike.operator.skill.impl.EmergencyBeaconHandler.DEPLOY_GRACE_MS);
                        writePurchasableSlot(player, load);
                    } else {
                        consumePurchasableFully(player, load);
                    }
                } else {
                    // 强效烟幕 / 断后 / 风弹等：释放成功 → 立刻取消充能
                    consumePurchasableFully(player, load);
                    skillClickCooldownUntil.put(player.getUniqueId(), System.currentTimeMillis() + 500L);
                }
            }
            case ULTIMATE -> {
                load.setUltimatePoints(Math.max(0, load.getUltimatePoints() - skill.getUltimateCost()));
                writeUltimateSlot(player, load);
            }
            default -> {
                // PASSIVE 等不应进入
            }
        }

        if (result.message() != null) {
            player.sendMessage("§d[技能] §f" + result.message());
        }
        return true;
    }

    private static int skillSlot(SkillKind kind) {
        if (kind == null) {
            return -1;
        }
        return switch (kind) {
            case SIGNATURE -> InventorySlots.SIGNATURE;
            case PURCHASABLE -> InventorySlots.PURCHASABLE;
            case ULTIMATE -> InventorySlots.ULTIMATE;
            case PASSIVE -> -1;
        };
    }

    private static SkillDefinition skillOf(OperatorLoadout load, SkillKind kind) {
        if (load == null || load.getDefinition() == null || kind == null) {
            return null;
        }
        return switch (kind) {
            case SIGNATURE -> load.getDefinition().getSignature();
            case PURCHASABLE -> load.getDefinition().getPurchasable();
            case ULTIMATE -> load.getDefinition().getUltimate();
            case PASSIVE -> load.getDefinition().getPassive();
        };
    }

    /** 购买技能彻底消耗：不再出现在第 8 格，本回合不能再放 */
    private void consumePurchasableFully(Player player, OperatorLoadout load) {
        if (load != null) {
            load.setPurchasableReady(false);
            load.setPurchasableUsesLeft(0);
        }
        if (player != null && player.isOnline()) {
            player.getInventory().setItem(InventorySlots.PURCHASABLE, null);
            player.updateInventory();
        }
    }


    // =====================================================================
    // Tick：充能 + CD 归零通知
    // =====================================================================

    public void tick() {
        Match match = plugin.getMatchManager().getMatch();
        if (match == null) {
            return;
        }

        boolean inProgress = match.getState()
                == org.starset.deltaforcestrike.match.MatchState.IN_PROGRESS;

        // 强效烟幕：按 loadout 到期时间刷粒子（与在线玩家循环解耦，避免误停）
        for (OperatorLoadout smokeLoad : loadouts.values()) {
            if (smokeLoad == null) {
                continue;
            }
            smokeLoad.tickSmokeExpiry();
            if (inProgress
                    && smokeLoad.isRoundSmokeActive()
                    && smokeLoad.getRoundSmokeCenter() != null) {
                double r = smokeLoad.getRoundSmokeRadius() > 0
                        ? smokeLoad.getRoundSmokeRadius() : 5.0;
                tickRoundSmoke(smokeLoad.getRoundSmokeCenter(), r);
            }
        }

        for (Player p : match.onlinePlayers()) {
            OperatorLoadout load = getLoadout(p.getUniqueId());
            if (load == null || load.getDefinition() == null) {
                continue;
            }

            int before = load.getSignatureCharges();
            if (load.tickRecharge() || load.getSignatureCharges() > before) {
                notifySignatureReady(p, load);
            }

            applyPassive(p, load);

            if (p.getTicksLived() % 20 == 0) {
                ensureSkillSlotsPresent(p, load);
            }
        }

        tickAierPassive(match);
    }

    /**
     * 若 7/9 格空了或不是技能，重新写入；8 仅在已购买时写入。
     */
    private void ensureSkillSlotsPresent(Player p, OperatorLoadout load) {
        if (p == null || load == null || load.getDefinition() == null) {
            return;
        }

        ItemStack s6 = p.getInventory().getItem(InventorySlots.SIGNATURE);
        ItemStack s7 = p.getInventory().getItem(InventorySlots.PURCHASABLE);
        ItemStack s8 = p.getInventory().getItem(InventorySlots.ULTIMATE);

        // 第7：招牌必须在
        if (!isSkillStack(s6) || getSkillKind(s6) != SkillKind.SIGNATURE) {
            writeSignatureSlot(p, load);
        }

        // 第9：大招必须在
        if (!isSkillStack(s8) || getSkillKind(s8) != SkillKind.ULTIMATE) {
            writeUltimateSlot(p, load);
        }

        // 第8：仅当仍 ready 且 uses>0 才允许存在；否则强制清空
        if (load.isPurchasableReady() && load.getPurchasableUsesLeft() > 0) {
            if (!isSkillStack(s7) || getSkillKind(s7) != SkillKind.PURCHASABLE) {
                writePurchasableSlot(p, load);
            }
        } else {
            // ★ 用完/未买：绝不写回，并清掉残留
            if (s7 != null && !s7.getType().isAir()) {
                p.getInventory().setItem(InventorySlots.PURCHASABLE, null);
            }
        }
    }


    private void notifySignatureReady(Player p, OperatorLoadout load) {
        String name = load.getDefinition() != null && load.getDefinition().getSignature() != null
                ? load.getDefinition().getSignature().getName()
                : "招牌技能";
        p.sendMessage("§a§l[DFS] 招牌技能已就绪: §f" + name
                + " §8[" + load.getSignatureCharges() + "/" + load.getSignatureMax() + "]");
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.4f);
        p.sendActionBar(LEGACY.deserialize("§a✔ " + name + " 充能完成"));
        writeSignatureSlot(p, load);
    }

    public void applyPassive(Player p, OperatorLoadout load) {
        OperatorDefinition def = load.getDefinition();
        if (def == null || def.getPassive() == null) {
            return;
        }
        String handler = def.getPassive().getHandlerId();
        if ("sneak_speed".equalsIgnoreCase(handler) || "wulong".equals(def.getId())) {
            if (p.isSneaking()) {
                int amp = def.getPassive().getInt("amplifier", 1);
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, amp, false, false, true));
            }
            return;
        }
        for (PotionSpec spec : def.getPassive().getEffects()) {
            if (spec == null || spec.type() == null) {
                continue;
            }
            p.addPotionEffect(new PotionEffect(spec.type(), 80, spec.amplifier(), false, false, true));
        }
    }

    /**
     * 技能投掷物落地：部署强效烟幕（与 GrenadeService 无关）。
     */
    public void deployRoundSmoke(Player owner, OperatorLoadout load,
                                 org.bukkit.Location center, double radius, int durationSeconds) {
        if (load == null || center == null || center.getWorld() == null) {
            return;
        }
        double r = radius > 0 ? radius : 5.0;
        int sec = durationSeconds > 0 ? durationSeconds : 90;
        load.startRoundSmoke(center, r, sec);

        var world = center.getWorld();
        world.playSound(center, Sound.BLOCK_FIRE_EXTINGUISH, 1.2f, 0.55f);
        // 落地瞬间爆发粒子
        tickRoundSmoke(center, r);
        if (owner != null && owner.isOnline()) {
            owner.sendMessage("§d[技能] §f强效烟幕已落地（持续 " + sec + " 秒）");
        }
    }

    /**
     * 强效烟幕持续粒子（独立于普通烟雾弹 BukkitRunnable）。
     * 粒子倍率仅复用 config 数值，不调用 GrenadeService。
     */
    private void tickRoundSmoke(org.bukkit.Location c, double r) {
        if (c == null || c.getWorld() == null) {
            return;
        }
        double mul = Math.max(1.0, plugin.getConfig().getDouble("grenade.particle-multiplier", 2.0));
        // 技能烟可在 operators.yml particle-multiplier 再乘（经 loadout 时已用半径；此处用全局倍率）
        int samples = Math.max(1, (int) Math.round(40 * mul));
        int cosy = Math.max(1, (int) Math.round(2 * mul));
        int signal = Math.max(1, (int) Math.round(1 * mul));
        int cloud = Math.max(1, (int) Math.round(15 * mul));

        var world = c.getWorld();
        for (int i = 0; i < samples; i++) {
            double ox = (Math.random() * 2 - 1) * r;
            double oy = Math.random() * 2.8;
            double oz = (Math.random() * 2 - 1) * r;
            if (ox * ox + oz * oz > r * r) {
                continue;
            }
            org.bukkit.Location pt = c.clone().add(ox, oy, oz);
            try {
                world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, pt, cosy, 0.15, 0.2, 0.15, 0.01);
                world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, pt, signal, 0.2, 0.25, 0.2, 0.01);
            } catch (Throwable ex) {
                world.spawnParticle(Particle.SMOKE, pt, cosy * 2, 0.2, 0.3, 0.2, 0.02);
                world.spawnParticle(Particle.LARGE_SMOKE, pt, signal * 2, 0.25, 0.35, 0.25, 0.01);
            }
        }
        world.spawnParticle(Particle.CLOUD, c.clone().add(0, 1, 0), cloud, r * 0.4, 1.0, r * 0.4, 0.02);
    }

    private void tickAierPassive(Match match) {
        for (Player aier : match.onlinePlayers()) {
            OperatorLoadout load = getLoadout(aier.getUniqueId());
            if (load == null || load.getDefinition() == null) {
                continue;
            }
            if (!"aier".equals(load.getDefinition().getId())) {
                continue;
            }
            PlayerSession as = match.getSession(aier.getUniqueId());
            if (as == null || !as.isAlive()) {
                continue;
            }
            for (Player mate : match.onlinePlayers()) {
                PlayerSession ms = match.getSession(mate.getUniqueId());
                if (ms == null || ms.getTeam() != as.getTeam() || !ms.isAlive()) {
                    continue;
                }
                for (PotionEffect pe : mate.getActivePotionEffects()) {
                    if (isNegative(pe.getType())) {
                        mate.removePotionEffect(pe.getType());
                    }
                }
            }
        }
    }

    private boolean isNegative(PotionEffectType t) {
        if (t == null) {
            return false;
        }
        return t.equals(PotionEffectType.POISON)
                || t.equals(PotionEffectType.WITHER)
                || t.equals(PotionEffectType.SLOWNESS)
                || t.equals(PotionEffectType.MINING_FATIGUE)
                || t.equals(PotionEffectType.BLINDNESS)
                || t.equals(PotionEffectType.HUNGER)
                || t.equals(PotionEffectType.WEAKNESS)
                || t.equals(PotionEffectType.LEVITATION)
                || t.equals(PotionEffectType.UNLUCK)
                || t.equals(PotionEffectType.DARKNESS)
                || t.equals(PotionEffectType.INSTANT_DAMAGE);
    }

    /**
     * 清除大招带来的临时增益（妮可狂暴等）。
     * 随后 onRoundStart 会 applyPassive 续上被动。
     */
    public void clearUltimateBuffs(Player p) {
        if (p == null || !p.isOnline()) {
            return;
        }
        // 大招常见增益：力量/抗性/跳跃/速度(狂暴II)
        p.removePotionEffect(PotionEffectType.STRENGTH);
        p.removePotionEffect(PotionEffectType.RESISTANCE);
        p.removePotionEffect(PotionEffectType.SPEED);
        try {
            p.removePotionEffect(PotionEffectType.JUMP_BOOST);
        } catch (Throwable ignored) {
        }
    }
}
