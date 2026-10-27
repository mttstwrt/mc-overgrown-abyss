package dev.syrval.verdantdeep.basilisk.entity;

/**
 * Attack state, synced to the client so animation, textures and particles can follow it.
 * Naga equivalent: NagaMovementPattern.MovementState (INTIMIDATE / CHARGE / DAZE ...).
 */
public enum BasiliskPhase {
    /** Moving or stalking; no attack in progress. */
    IDLE(0.0F),
    /** Telegraph with full headroom: neck rises, hood flares, head sways. */
    REAR(1.0F),
    /** Telegraph without headroom (under canopy/ceiling): low coil, shorter wind-up. */
    COIL(0.3F),
    /** The lunge itself. Only bites landed in this phase carry venom. */
    STRIKE(0.0F),
    /** Head down after a lunge or a stagger: the punish window. */
    RECOVER(0.0F);

    private static final BasiliskPhase[] VALUES = values();
    private final float rearTarget;

    BasiliskPhase(float rearTarget) {
        this.rearTarget = rearTarget;
    }

    /** 0..1: how far the neck should be raised in this phase. */
    public float rearTarget() {
        return rearTarget;
    }

    public boolean isTelegraph() {
        return this == REAR || this == COIL;
    }

    public boolean isAlert() {
        return this != IDLE;
    }

    public static BasiliskPhase byId(int id) {
        return VALUES[Math.max(0, Math.min(VALUES.length - 1, id))];
    }
}
