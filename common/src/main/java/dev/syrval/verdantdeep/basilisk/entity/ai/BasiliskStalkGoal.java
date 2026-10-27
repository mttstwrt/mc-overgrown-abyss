package dev.syrval.verdantdeep.basilisk.entity.ai;

import java.util.EnumSet;

import dev.syrval.verdantdeep.basilisk.entity.Basilisk;
import dev.syrval.verdantdeep.basilisk.entity.BasiliskSize;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Approach, then circle or dart in on the target while the attacks in BasiliskAttackGoal come off cooldown.
 *
 * NAGA: NagaMovementPattern's CIRCLE state does this with findCirclePoint(); the point-on-a-ring maths below
 * is the same idea. We drop its CHARGE/INTIMIDATE/DAZE/CRUMBLE states: the basilisk's attacks live in the
 * attack goal, which preempts this goal (lower priority number) whenever it is ready.
 */
public class BasiliskStalkGoal extends Goal {
    private static final double RING_RADIUS = 7.0D;           // x BasiliskSize.reachMultiplier: inside lunge range, outside bite range
    private static final double RING_ROTATION = 0.9D; // radians advanced per repath, like the naga's 1.0
    private static final double CHASE_DISTANCE = 12.0D;   // beyond this, run straight at the target
    private static final double CLOSE_IN_CHANCE = 0.1D;   // per repath inside CHASE_DISTANCE: dart in to bite range instead of circling (hit and run: rare)
    private static final double MOVE_SPEED = 1.0D;        // multiplier on movement_speed, which already carries the engaged bonus
    private static final int REPATH_MIN = 6;              // ticks between repaths; short, so it re-positions quickly
    private static final int REPATH_RANGE = 6;

    private final Basilisk basilisk;
    private boolean clockwise;
    private int repathIn;

    public BasiliskStalkGoal(Basilisk basilisk) {
        this.basilisk = basilisk;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = basilisk.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        clockwise = basilisk.getRandom().nextBoolean();
        repathIn = 0;
    }

    @Override
    public void stop() {
        basilisk.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = basilisk.getTarget();
        if (target == null) return;
        basilisk.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (--repathIn > 0) return;
        repathIn = REPATH_MIN + basilisk.getRandom().nextInt(REPATH_RANGE);
        if (basilisk.getRandom().nextInt(4) == 0) clockwise = !clockwise;

        double distance = basilisk.distanceTo(target);
        if (distance > CHASE_DISTANCE || basilisk.getRandom().nextDouble() < CLOSE_IN_CHANCE) {
            basilisk.getNavigation().moveTo(target, MOVE_SPEED);
        } else {
            double radius = RING_RADIUS * BasiliskSize.reachMultiplier(basilisk.getScale());
            Vec3 p = ringPoint(basilisk, target, radius, RING_ROTATION, clockwise);
            if (TerrainProbe.isNearEdge(basilisk.level(), p.x, p.y, p.z)) {
                clockwise = !clockwise; // that side is an edge: try the other way round
                p = ringPoint(basilisk, target, radius, RING_ROTATION, clockwise);
            }
            if (TerrainProbe.isNearEdge(basilisk.level(), p.x, p.y, p.z)
                || !basilisk.getNavigation().moveTo(p.x, p.y, p.z, MOVE_SPEED)) {
                basilisk.getNavigation().moveTo(target, MOVE_SPEED); // ring point unreachable (trees, cliff): just close in
            }
        }
    }

    /** NAGA: findCirclePoint. A point on a ring around the target, rotated a little ahead of where we are. */
    static Vec3 ringPoint(Basilisk basilisk, LivingEntity target, double radius, double rotation, boolean clockwise) {
        double vx = basilisk.getX() - target.getX();
        double vz = basilisk.getZ() - target.getZ();
        double angle = Math.atan2(vz, vx) + (clockwise ? rotation : -rotation);
        double y = Math.min(basilisk.getY(), target.getY());
        return new Vec3(target.getX() + Math.cos(angle) * radius, y, target.getZ() + Math.sin(angle) * radius);
    }
}
