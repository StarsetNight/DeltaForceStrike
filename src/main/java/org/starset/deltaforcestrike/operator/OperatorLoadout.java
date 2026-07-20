package org.starset.deltaforcestrike.operator;

import org.bukkit.Location;

import java.util.UUID;

/**
 * 单玩家单局干员运行时状态。
 */
public class OperatorLoadout {

    private final UUID playerId;
    private OperatorDefinition definition;

    private int signatureCharges;
    private long signatureReadyAtMs;

    private boolean purchasableReady;
    private int purchasableUsesLeft;

    private int ultimatePoints;
    private long silencedUntilMs;

    // 妮可信标
    private Location beaconLocation;
    private boolean beaconArmed;
    private long beaconDeployGraceUntilMs;
    private boolean beaconTeleportConsumed;

    // 艾尔强效烟幕
    private Location roundSmokeCenter;
    private boolean roundSmokeActive;
    private double roundSmokeRadius = 5.0;
    private long roundSmokeEndsAtMs; // 0 = 无

    private boolean ultimateActiveThisRound;

    public OperatorLoadout(UUID playerId) {
        this.playerId = playerId;
    }

    public void bind(OperatorDefinition def) {
        this.definition = def;
        if (def != null && def.getSignature() != null) {
            signatureCharges = def.getSignature().getInitialCharges();
            if (signatureCharges <= 0) {
                signatureCharges = def.getSignature().getMaxCharges();
            }
        } else {
            signatureCharges = 0;
        }
        signatureReadyAtMs = 0;
        purchasableReady = false;
        purchasableUsesLeft = 0;
        ultimatePoints = 0;
        silencedUntilMs = 0;
        clearBeacon();
        clearRoundSmoke();
        ultimateActiveThisRound = false;
    }

    /**
     * 新回合：招牌回满清 CD；购买技能若未使用则保留；烟幕/信标清掉；大招点保留。
     */
    public void onRoundStart() {
        refreshSignatureChargesFull();
        // 购买技能：仅使用后才清除（不在此强制作废）
        clearBeacon();
        clearRoundSmoke();
        ultimateActiveThisRound = false;
        silencedUntilMs = 0;
    }

    public void onRoundEnd() {
        // 强效烟幕按 duration-seconds 到期；购买技能跨回合保留直到使用
        ultimateActiveThisRound = false;
        clearBeacon();
    }

    /** 半场：购买技能也作废 */
    public void clearPurchasableCharge() {
        purchasableReady = false;
        purchasableUsesLeft = 0;
    }

    /** 招牌充能回满并清除 CD（每回合开始强制刷新） */
    public void refreshSignatureChargesFull() {
        if (definition != null && definition.getSignature() != null) {
            int max = definition.getSignature().getMaxCharges();
            int initial = definition.getSignature().getInitialCharges();
            signatureCharges = max > 0 ? max : Math.max(1, initial);
        } else {
            signatureCharges = 0;
        }
        signatureReadyAtMs = 0;
    }

    /** 半场换边：大招充能清零 */
    public void resetUltimatePoints() {
        ultimatePoints = 0;
        ultimateActiveThisRound = false;
    }

    public void addUltimatePoints(int n, int maxCap) {
        int cap = maxCap > 0 ? maxCap : Integer.MAX_VALUE;
        ultimatePoints = Math.max(0, Math.min(cap, ultimatePoints + n));
    }

    public boolean isSilenced() {
        return System.currentTimeMillis() < silencedUntilMs;
    }

    public void silenceFor(int seconds) {
        silencedUntilMs = System.currentTimeMillis() + seconds * 1000L;
    }

    public void clearSilence() {
        silencedUntilMs = 0;
    }

    public void startSignatureRecharge(int seconds) {
        signatureReadyAtMs = System.currentTimeMillis() + Math.max(0, seconds) * 1000L;
    }

    public boolean isSignatureRecharging() {
        return signatureCharges < getSignatureMax()
                && signatureReadyAtMs > System.currentTimeMillis();
    }

    public int getSignatureRechargeLeftSeconds() {
        if (signatureReadyAtMs <= 0) {
            return 0;
        }
        long left = signatureReadyAtMs - System.currentTimeMillis();
        return (int) Math.max(0, (left + 999) / 1000);
    }

    /**
     * @return true 若本次 tick 使充能 +1（用于通知）
     */
    public boolean tickRecharge() {
        if (definition == null || definition.getSignature() == null) {
            return false;
        }
        int max = definition.getSignature().getMaxCharges();
        if (signatureCharges >= max) {
            signatureReadyAtMs = 0;
            return false;
        }
        if (signatureReadyAtMs > 0 && System.currentTimeMillis() >= signatureReadyAtMs) {
            signatureCharges = Math.min(max, signatureCharges + 1);
            if (signatureCharges < max) {
                startSignatureRecharge(definition.getSignature().getRechargeSeconds());
            } else {
                signatureReadyAtMs = 0;
            }
            return true;
        }
        return false;
    }

    public int getSignatureMax() {
        return definition == null || definition.getSignature() == null
                ? 0 : definition.getSignature().getMaxCharges();
    }

    public void reduceSignatureCooldown(int seconds) {
        if (signatureReadyAtMs <= 0) {
            return;
        }
        signatureReadyAtMs = Math.max(System.currentTimeMillis(),
                signatureReadyAtMs - Math.max(0, seconds) * 1000L);
    }

    public void addUltimatePoints(int n) {
        addUltimatePoints(n, 0);
    }

    public void clearBeacon() {
        beaconLocation = null;
        beaconArmed = false;
        beaconDeployGraceUntilMs = 0;
        beaconTeleportConsumed = false;
    }

    public void markBeaconJustDeployed(long graceMs) {
        beaconDeployGraceUntilMs = System.currentTimeMillis() + Math.max(0, graceMs);
    }

    public boolean isBeaconDeployGrace() {
        return System.currentTimeMillis() < beaconDeployGraceUntilMs;
    }

    public void startRoundSmoke(Location center, double radius, int durationSeconds) {
        this.roundSmokeCenter = center == null ? null : center.clone();
        this.roundSmokeRadius = radius > 0 ? radius : 5.0;
        this.roundSmokeActive = this.roundSmokeCenter != null;
        this.roundSmokeEndsAtMs = System.currentTimeMillis() + Math.max(1, durationSeconds) * 1000L;
    }

    public void clearRoundSmoke() {
        roundSmokeCenter = null;
        roundSmokeActive = false;
        roundSmokeRadius = 5.0;
        roundSmokeEndsAtMs = 0;
    }

    public void tickSmokeExpiry() {
        if (!roundSmokeActive) {
            return;
        }
        if (roundSmokeEndsAtMs > 0 && System.currentTimeMillis() >= roundSmokeEndsAtMs) {
            clearRoundSmoke();
        }
    }



    // ----- getters / setters -----

    public UUID getPlayerId() {
        return playerId;
    }

    public OperatorDefinition getDefinition() {
        return definition;
    }

    public int getSignatureCharges() {
        return signatureCharges;
    }

    public void setSignatureCharges(int signatureCharges) {
        this.signatureCharges = signatureCharges;
    }

    public boolean isPurchasableReady() {
        return purchasableReady;
    }

    public void setPurchasableReady(boolean purchasableReady) {
        this.purchasableReady = purchasableReady;
    }

    public int getPurchasableUsesLeft() {
        return purchasableUsesLeft;
    }

    public void setPurchasableUsesLeft(int purchasableUsesLeft) {
        this.purchasableUsesLeft = purchasableUsesLeft;
    }

    public int getUltimatePoints() {
        return ultimatePoints;
    }

    public void setUltimatePoints(int ultimatePoints) {
        this.ultimatePoints = ultimatePoints;
    }

    public Location getBeaconLocation() {
        return beaconLocation;
    }

    public void setBeaconLocation(Location beaconLocation) {
        this.beaconLocation = beaconLocation;
    }

    public boolean isBeaconArmed() {
        return beaconArmed;
    }

    public void setBeaconArmed(boolean beaconArmed) {
        this.beaconArmed = beaconArmed;
    }

    public boolean isBeaconTeleportConsumed() {
        return beaconTeleportConsumed;
    }

    public void setBeaconTeleportConsumed(boolean beaconTeleportConsumed) {
        this.beaconTeleportConsumed = beaconTeleportConsumed;
    }

    public boolean isRoundSmokeActive() {
        if (!roundSmokeActive) {
            return false;
        }
        if (roundSmokeEndsAtMs > 0 && System.currentTimeMillis() >= roundSmokeEndsAtMs) {
            clearRoundSmoke();
            return false;
        }
        return true;
    }

    public long getRoundSmokeEndsAtMs() {
        return roundSmokeEndsAtMs;
    }

    public void setRoundSmokeCenter(Location roundSmokeCenter) {
        this.roundSmokeCenter = roundSmokeCenter;
    }

    public void setRoundSmokeActive(boolean roundSmokeActive) {
        this.roundSmokeActive = roundSmokeActive;
    }

    public void setRoundSmokeRadius(double roundSmokeRadius) {
        this.roundSmokeRadius = roundSmokeRadius;
    }

    public Location getRoundSmokeCenter() {
        return roundSmokeCenter;
    }

    public double getRoundSmokeRadius() {
        return roundSmokeRadius;
    }

    public boolean isUltimateActiveThisRound() {
        return ultimateActiveThisRound;
    }

    public void setUltimateActiveThisRound(boolean ultimateActiveThisRound) {
        this.ultimateActiveThisRound = ultimateActiveThisRound;
    }
}
