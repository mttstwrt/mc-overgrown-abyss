package dev.syrval.verdantdeep.basilisk.entity;

/**
 * Pure math of the basilisk's size: every number that depends on how big this individual is.
 * No Minecraft imports, so the rules sit in one place and are the same on both sides.
 */
public final class BasiliskSize {
    public static final float MIN_SCALE = 2.0F;
    public static final float MAX_SCALE = 3.5F;
    /** Segment count at MIN_SCALE and the added segments per scale unit; capped at MAX_SEGMENTS. */
    public static final int BASE_SEGMENTS = 10;
    public static final double SEGMENTS_PER_SCALE = 4.0D;
    public static final int MAX_SEGMENTS = 16;

    public static final double MIN_HEALTH = 120.0D;
    public static final double MAX_HEALTH = 200.0D;
    public static final double BASE_DAMAGE = 6.0D;
    /** Attack damage multiplier from the smallest to the largest basilisk. */
    public static final double MAX_DAMAGE_MULTIPLIER = 1.30D;
    /** Lunge/bite reach multiplier from the smallest to the largest basilisk. */
    public static final double MAX_REACH_MULTIPLIER = 1.25D;
    public static final int MIN_XP = 30;
    public static final int MAX_XP = 60;
    /** Step height per scale unit: about 5 blocks at the smallest, 8.75 at the largest. No blocks are broken. */
    public static final double STEP_HEIGHT_PER_SCALE = 2.5D;

    /** Roll exponent: u^exponent with u uniform 0..1; above 1 skews toward small. */
    public static final double ROLL_SKEW = 1.8D;
    /** How much the exponent drops at the chasm floor (depth share 1): slightly bigger deep down. */
    public static final double ROLL_DEPTH_BIAS = 0.6D;

    private BasiliskSize() {}

    /** @param u uniform 0..1 @param depth chasm depth share 0 (mouth) .. 1 (floor), 0 if unknown */
    public static float roll(double u, double depth) {
        double d = Math.min(1.0D, Math.max(0.0D, depth));
        double t = Math.pow(Math.min(1.0D, Math.max(0.0D, u)), ROLL_SKEW - ROLL_DEPTH_BIAS * d);
        return (float) (MIN_SCALE + t * (MAX_SCALE - MIN_SCALE));
    }

    /** 0 at MIN_SCALE, 1 at MAX_SCALE. */
    public static double t(double scale) {
        return Math.min(1.0D, Math.max(0.0D, (scale - MIN_SCALE) / (MAX_SCALE - MIN_SCALE)));
    }

    public static double maxHealth(double scale) { return MIN_HEALTH + (MAX_HEALTH - MIN_HEALTH) * t(scale); }

    public static double attackDamage(double scale) { return BASE_DAMAGE * (1.0D + (MAX_DAMAGE_MULTIPLIER - 1.0D) * t(scale)); }

    public static double reachMultiplier(double scale) { return 1.0D + (MAX_REACH_MULTIPLIER - 1.0D) * t(scale); }

    public static int xpReward(double scale) { return (int) Math.round(MIN_XP + (MAX_XP - MIN_XP) * t(scale)); }

    public static double stepHeight(double scale) { return STEP_HEIGHT_PER_SCALE * scale; }

    public static int segmentCount(double scale) {
        int n = (int) Math.round(BASE_SEGMENTS + (scale - MIN_SCALE) * SEGMENTS_PER_SCALE);
        return Math.max(BASE_SEGMENTS, Math.min(MAX_SEGMENTS, n));
    }
}
