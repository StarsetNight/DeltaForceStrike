package org.starset.deltaforcestrike.bomb;

/** Distance calculation independent of worlds, players and the scheduler. */
public final class BombDamageRules {
    private BombDamageRules() {}

    /** 0-20% 100% | 20-40% 75% | 40-60% 50% | 60-80% 30% | 80-100% 15% */
    public static double damageByDistance(double dist, double radius, double maxDamage, double minDamage) {
        if (radius <= 0 || dist > radius) return 0;
        double ratio = dist / radius;
        double mult;
        if (ratio <= 0.20) mult = 1.00;
        else if (ratio <= 0.40) mult = 0.75;
        else if (ratio <= 0.60) mult = 0.50;
        else if (ratio <= 0.80) mult = 0.30;
        else mult = 0.15;
        return Math.max(minDamage, maxDamage * mult);
    }
}
