package dev.syrval.verdantdeep.basilisk.entity.ai;

import java.util.EnumSet;

import dev.syrval.verdantdeep.basilisk.BasiliskEntities;
import dev.syrval.verdantdeep.basilisk.entity.Basilisk;
import dev.syrval.verdantdeep.basilisk.entity.BasiliskPhase;
import dev.syrval.verdantdeep.basilisk.entity.BasiliskSize;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * The basilisk's two attacks, a fast lunge-and-bite predator rather than a heavy hitter.
 *
 *   BITE   target within BITE_RANGE: a very short head telegraph (COIL), then one bite for attack_damage
 *          with Poison as a side effect.
 *   LUNGE  target LUNGE_MIN_RANGE..LUNGE_MAX_RANGE away on a clear straight line: telegraph (REAR or COIL),
 *          a ~7 block dash (STRIKE) that hits once for 1.5x attack_damage plus knockback, then a RECOVERY
 *          (RECOVER) where it is slowed and exposed: the punish window.
 *   After either attack it runs (RETREAT) 7-10 blocks from the target for 30-50 ticks, then swings round to a
 *   bearing 60-120 degrees off the last one (FLANK) and opens with a lunge; it bites only when the target closes in. Between attacks BasiliskStalkGoal keeps the pressure on.
 *
 * Counterplay: the aim locks a few ticks before the dash, so changing direction makes it miss; a shield blocks
 * the hit and the poison; a lunge that would land in lava, an abyss or a wall is not started.
 *
 * NAGA: roughly NagaMovementPattern's INTIMIDATE -> CHARGE -> DAZE, with an aimed dash instead of a
 * path-following charge, so the pathfinder losing track cannot dodge it.
 */
public class BasiliskAttackGoal extends Goal {
    // --- bite
    private static final double BITE_RANGE = 3.0D;          // base blocks from the basilisk to the target (at scale 1)
    private static final double BITE_RANGE_PER_SCALE = 0.8D; // added per scale unit above 1: the hitbox is that much wider
    private static final double LUNGE_MIN_GAP = 1.0D;       // lunge starts this far beyond bite range
    private static final double BITE_HIT_LENIENCY = 0.8D;   // extra reach at the moment the bite lands
    private static final int BITE_WINDUP_TICKS = 5;
    private static final int BITE_COOLDOWN_TICKS = 14;
    private static final int BITE_HOLD_TICKS = 4;           // how long the open-jaw phase stays up after the bite
    // --- lunge
    private static final double LUNGE_MAX_RANGE = 9.0D;     // x BasiliskSize.reachMultiplier
    private static final double LUNGE_MAX_Y_DIFF = 1.5D;    // the dash is flat
    private static final int LUNGE_WINDUP_REAR_TICKS = 8;
    private static final int LUNGE_WINDUP_COIL_TICKS = 6;   // under a ceiling
    private static final int LUNGE_AIM_LOCK_TICKS = 3;
    private static final double LUNGE_SPEED = 1.0D;         // blocks per tick during the dash
    private static final double LUNGE_MAX_DISTANCE = 7.0D;  // blocks covered by a full dash, x BasiliskSize.reachMultiplier
    private static final float LUNGE_DAMAGE_MULTIPLIER = 1.5F;
    private static final double LUNGE_KNOCKBACK = 0.8D;
    private static final int LUNGE_RECOVER_TICKS = 20;
    private static final double LUNGE_RECOVER_SPEED_SCALE = 0.25D; // residual momentum kept each tick while recovering
    private static final int LUNGE_COOLDOWN_TICKS = 60;
    private static final int LUNGE_COOLDOWN_RANDOM = 20;    // plus 0..20
    // --- hit and run: after any attack, retreat out of reach, then come back from another side
    private static final double RETREAT_MIN_DISTANCE = 7.0D;   // blocks from the target; x BasiliskSize.reachMultiplier
    private static final double RETREAT_MAX_DISTANCE = 10.0D;
    private static final int RETREAT_MIN_TICKS = 30;
    private static final int RETREAT_RANDOM_TICKS = 20;        // plus 0..20, so 30..50
    private static final double RETREAT_SPEED = 1.0D;
    private static final double FLANK_MIN_DEGREES = 60.0D;     // new bearing around the target, off the last one
    private static final double FLANK_MAX_DEGREES = 120.0D;
    private static final int FLANK_MAX_TICKS = 40;             // give up flanking and let the stalk goal take over
    private static final double FLANK_ARRIVE_DISTANCE = 2.5D;
    private static final int REPATH_TICKS = 6;

    private enum Step { BITE_WINDUP, LUNGE_WINDUP, LUNGE, RECOVER, RETREAT, FLANK, DONE }

    private final Basilisk basilisk;
    private Step step = Step.DONE;
    private int ticks;
    private int windup;
    private int lungeTicks;
    private int retreatTicks;
    private double retreatDistance;
    private double lastBearing;   // radians, from the target to us when the attack ended
    private double flankBearing;
    private int biteCooldownUntil;
    private int lungeCooldownUntil;
    private boolean hitLanded;
    private Vec3 aim;
    private Vec3 lungeDir = Vec3.ZERO;

    public BasiliskAttackGoal(Basilisk basilisk) {
        this.basilisk = basilisk;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        LivingEntity t = basilisk.getTarget();
        if (t == null || !t.isAlive() || basilisk.isGroggy() || basilisk.getPhase() != BasiliskPhase.IDLE) return false;
        double d = basilisk.distanceTo(t);
        if (d <= biteRange()) {
            return basilisk.tickCount >= biteCooldownUntil && Math.abs(t.getY() - basilisk.getY()) <= 2.5D
                && basilisk.hasLineOfSight(t);
        }
        return d >= lungeMinRange() && d <= lungeMaxRange() && basilisk.tickCount >= lungeCooldownUntil
            && Math.abs(t.getY() - basilisk.getY()) <= LUNGE_MAX_Y_DIFF
            && basilisk.hasLineOfSight(t) && lungeLineIsClear(t);
    }

    @Override
    public boolean canContinueToUse() {
        return step != Step.DONE;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true; // our timers count ticks
    }

    @Override
    public void start() {
        LivingEntity t = basilisk.getTarget();
        basilisk.getNavigation().stop();
        ticks = 0;
        aim = null;
        hitLanded = false;
        if (t != null && basilisk.distanceTo(t) <= biteRange()) {
            step = Step.BITE_WINDUP;
            windup = BITE_WINDUP_TICKS;
            basilisk.setPhase(BasiliskPhase.COIL);
        } else {
            boolean room = TerrainProbe.hasHeadroom(basilisk.level(), basilisk, Basilisk.HEAD_RAISE * basilisk.getScale() + 1.0D);
            step = Step.LUNGE_WINDUP;
            windup = room ? LUNGE_WINDUP_REAR_TICKS : LUNGE_WINDUP_COIL_TICKS;
            basilisk.setPhase(room ? BasiliskPhase.REAR : BasiliskPhase.COIL);
            basilisk.playSound(BasiliskEntities.SND_REAR.get(), 1.6F, 1.1F + basilisk.getRandom().nextFloat() * 0.2F);
        }
    }

    @Override
    public void stop() {
        step = Step.DONE;
        basilisk.setPhase(BasiliskPhase.IDLE);
        basilisk.getNavigation().stop();
    }

    @Override
    public void tick() {
        ticks++;
        LivingEntity t = basilisk.getTarget();
        if (t == null || !t.isAlive()) {
            if (step == Step.LUNGE) { beginRecover(); } else if (step != Step.RECOVER) { step = Step.DONE; }
        }
        switch (step) {
            case BITE_WINDUP -> tickBiteWindup(t);
            case LUNGE_WINDUP -> tickLungeWindup(t);
            case LUNGE -> tickLunge(t);
            case RECOVER -> tickRecover();
            case RETREAT -> tickRetreat(t);
            case FLANK -> tickFlank(t);
            default -> { }
        }
    }

    // ---------------------------------------------------------------- bite

    private void tickBiteWindup(LivingEntity t) {
        basilisk.getNavigation().stop();
        face(t);
        if (ticks < windup) return;
        basilisk.setPhase(BasiliskPhase.STRIKE);
        basilisk.swing(InteractionHand.MAIN_HAND);
        basilisk.playSound(BasiliskEntities.SND_STRIKE.get(), 1.2F, 1.1F + basilisk.getRandom().nextFloat() * 0.2F);
        if (basilisk.distanceTo(t) <= biteRange() + BITE_HIT_LENIENCY && basilisk.hasLineOfSight(t)
            && basilisk.doHurtTarget(t)) { // a shield-blocked bite returns false, so blocking also blocks the poison
            basilisk.applyVenom(t);
        }
        biteCooldownUntil = basilisk.tickCount + BITE_COOLDOWN_TICKS;
        lungeCooldownUntil = Math.min(lungeCooldownUntil, basilisk.tickCount); // the lunge opens the next round
        beginRetreat(t);
    }

    // ---------------------------------------------------------------- lunge

    /** Straight, unobstructed and safe all the way to where a full-length dash would end. */
    private boolean lungeLineIsClear(LivingEntity t) {
        Vec3 dir = flatDirection(basilisk.position(), t.position());
        if (dir == null) return false;
        double reach = Math.min(basilisk.distanceTo(t) + 0.5D, lungeMaxDistance());
        int maxDrop = basilisk.getMaxFallDistance();
        for (double k = 1.0D; k <= reach + 0.5D; k += 1.0D) {
            double step = Math.min(k, reach);
            if (!basilisk.level().noCollision(basilisk, basilisk.getBoundingBox().move(dir.x * step, 0.0D, dir.z * step))) return false;
            if (TerrainProbe.isUnsafeLanding(basilisk.level(), basilisk.getX() + dir.x * step, basilisk.getY(),
                basilisk.getZ() + dir.z * step, maxDrop)) return false;
        }
        return true;
    }

    private void tickLungeWindup(LivingEntity t) {
        basilisk.getNavigation().stop();
        if (ticks < windup - LUNGE_AIM_LOCK_TICKS) {
            face(t);
        } else if (aim == null) {
            // Lock on with a little lead. Changing direction after this makes it miss.
            aim = t.position().add(t.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D).scale(2.0D));
        }
        if (ticks >= windup) beginLunge(t);
    }

    private void beginLunge(LivingEntity t) {
        Vec3 from = basilisk.position();
        Vec3 to = aim != null ? aim : t.position();
        Vec3 dir = flatDirection(from, to);
        if (dir == null) dir = Vec3.directionFromRotation(0.0F, basilisk.getYRot());
        double dist = Math.sqrt((to.x - from.x) * (to.x - from.x) + (to.z - from.z) * (to.z - from.z));
        lungeTicks = lungeTicksFor(dist, lungeMaxDistance());

        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        basilisk.setYRot(yaw);
        basilisk.yBodyRot = yaw;
        basilisk.yHeadRot = yaw;

        lungeDir = dir;
        hitLanded = false;
        step = Step.LUNGE;
        ticks = 0;
        basilisk.setPhase(BasiliskPhase.STRIKE);
        basilisk.setDeltaMovement(dir.x * LUNGE_SPEED, 0.1D, dir.z * LUNGE_SPEED);
        basilisk.hasImpulse = true;
        basilisk.playSound(BasiliskEntities.SND_STRIKE.get(), 1.4F, 0.95F + basilisk.getRandom().nextFloat() * 0.1F);
    }

    /** Ticks of dash needed to cover a flat distance (target distance plus a little overshoot), capped at one full dash. */
    static int lungeTicksFor(double flatDistance, double maxDistance) {
        double reach = Math.min(Math.max(flatDistance + 0.5D, 1.0D), maxDistance);
        return (int) Math.ceil(reach / LUNGE_SPEED);
    }

    private void tickLunge(LivingEntity t) {
        Vec3 mv = basilisk.getDeltaMovement();
        double speed = hitLanded ? LUNGE_SPEED * 0.2D : LUNGE_SPEED;
        basilisk.setDeltaMovement(lungeDir.x * speed, mv.y, lungeDir.z * speed);
        basilisk.hasImpulse = true;

        if (!hitLanded && t != null && t.isAlive()
            && basilisk.getBoundingBox().inflate(0.45D, 0.3D, 0.45D).intersects(t.getBoundingBox())) {
            basilisk.swing(InteractionHand.MAIN_HAND);
            basilisk.lungeHit(t, LUNGE_DAMAGE_MULTIPLIER, LUNGE_KNOCKBACK, lungeDir);
            hitLanded = true;
        }
        if (ticks >= lungeTicks || basilisk.horizontalCollision || (hitLanded && ticks >= 2)) beginRecover();
    }

    private void beginRecover() {
        step = Step.RECOVER;
        ticks = 0;
        basilisk.setPhase(BasiliskPhase.RECOVER);
        basilisk.getNavigation().stop();
        lungeCooldownUntil = basilisk.tickCount + LUNGE_RECOVER_TICKS + LUNGE_COOLDOWN_TICKS
            + basilisk.getRandom().nextInt(LUNGE_COOLDOWN_RANDOM + 1);
    }

    private void tickRecover() {
        Vec3 mv = basilisk.getDeltaMovement();
        basilisk.setDeltaMovement(mv.x * LUNGE_RECOVER_SPEED_SCALE, mv.y, mv.z * LUNGE_RECOVER_SPEED_SCALE);
        if (ticks >= LUNGE_RECOVER_TICKS) beginRetreat(basilisk.getTarget());
    }

    // ---------------------------------------------------------------- retreat + flank

    private void beginRetreat(LivingEntity t) {
        step = Step.RETREAT;
        ticks = 0;
        retreatTicks = RETREAT_MIN_TICKS + basilisk.getRandom().nextInt(RETREAT_RANDOM_TICKS + 1);
        double size = BasiliskSize.reachMultiplier(basilisk.getScale());
        retreatDistance = (RETREAT_MIN_DISTANCE + basilisk.getRandom().nextDouble() * (RETREAT_MAX_DISTANCE - RETREAT_MIN_DISTANCE)) * size;
        lastBearing = t != null ? bearing(t) : 0.0D;
        if (basilisk.getPhase() != BasiliskPhase.STRIKE) basilisk.setPhase(BasiliskPhase.IDLE);
    }

    private void tickRetreat(LivingEntity t) {
        if (ticks == BITE_HOLD_TICKS && basilisk.getPhase() == BasiliskPhase.STRIKE) basilisk.setPhase(BasiliskPhase.IDLE);
        if (ticks >= retreatTicks) { beginFlank(t); return; }
        basilisk.getLookControl().setLookAt(t, 40.0F, 40.0F);
        if (ticks % REPATH_TICKS != 1) return;
        // Straight away from the target along the way we came, or a little to either side if that is blocked.
        double b = bearing(t);
        for (double scale : new double[] {1.0D, 0.6D}) {
            for (double off : new double[] {0.0D, 0.6D, -0.6D}) {
                Vec3 p = pointAround(t, b + off, retreatDistance * scale);
                if (TerrainProbe.isNearEdge(basilisk.level(), p.x, p.y, p.z)) continue; // never retreat toward a drop
                if (basilisk.getNavigation().moveTo(p.x, p.y, p.z, RETREAT_SPEED)) return;
            }
        }
    }

    private void beginFlank(LivingEntity t) {
        step = Step.FLANK;
        ticks = 0;
        double off = Math.toRadians(FLANK_MIN_DEGREES + basilisk.getRandom().nextDouble() * (FLANK_MAX_DEGREES - FLANK_MIN_DEGREES));
        flankBearing = lastBearing + (basilisk.getRandom().nextBoolean() ? off : -off);
        basilisk.getNavigation().stop();
    }

    /** Circle to a point on the new bearing at lunge range; the lunge itself starts from canUse once the line is clear. */
    private void tickFlank(LivingEntity t) {
        double radius = 0.5D * (lungeMinRange() + lungeMaxRange());
        Vec3 p = pointAround(t, flankBearing, radius);
        if (ticks >= FLANK_MAX_TICKS || basilisk.position().distanceTo(p) <= FLANK_ARRIVE_DISTANCE) { step = Step.DONE; return; }
        basilisk.getLookControl().setLookAt(t, 40.0F, 40.0F);
        if (ticks % REPATH_TICKS != 1) return;
        if (TerrainProbe.isNearEdge(basilisk.level(), p.x, p.y, p.z)
            || !basilisk.getNavigation().moveTo(p.x, p.y, p.z, RETREAT_SPEED)) {
            flankBearing += Math.PI; // that side is blocked: try the opposite one
        }
    }

    /** Radians, from the target to us. */
    private double bearing(LivingEntity t) {
        return Math.atan2(basilisk.getZ() - t.getZ(), basilisk.getX() - t.getX());
    }

    private Vec3 pointAround(LivingEntity t, double bearing, double radius) {
        return new Vec3(t.getX() + Math.cos(bearing) * radius, Math.min(basilisk.getY(), t.getY()), t.getZ() + Math.sin(bearing) * radius);
    }

    // ---------------------------------------------------------------- helpers

    private double biteRange() {
        return BITE_RANGE + BITE_RANGE_PER_SCALE * (basilisk.getScale() - 1.0D);
    }

    private double lungeMinRange() {
        return biteRange() + LUNGE_MIN_GAP;
    }

    private double lungeMaxRange() {
        return LUNGE_MAX_RANGE * BasiliskSize.reachMultiplier(basilisk.getScale());
    }

    private double lungeMaxDistance() {
        return LUNGE_MAX_DISTANCE * BasiliskSize.reachMultiplier(basilisk.getScale());
    }

    private void face(LivingEntity t) {
        basilisk.lookAt(t, 60.0F, 60.0F);
        basilisk.getLookControl().setLookAt(t, 60.0F, 60.0F);
    }

    /** Unit horizontal vector from a to b, or null if they are practically on top of each other. */
    private static Vec3 flatDirection(Vec3 a, Vec3 b) {
        double dx = b.x - a.x, dz = b.z - a.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        return len < 0.3D ? null : new Vec3(dx / len, 0.0D, dz / len);
    }
}
