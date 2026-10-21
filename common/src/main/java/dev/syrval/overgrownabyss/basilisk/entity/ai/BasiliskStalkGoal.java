package dev.syrval.overgrownabyss.basilisk.entity.ai;

import java.util.EnumSet;

import dev.syrval.overgrownabyss.basilisk.entity.Basilisk;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Approach, then circle the target at striking range while the venom strike comes off cooldown.
 *
 * NAGA: NagaMovementPattern's CIRCLE state does this with findCirclePoint(); the point-on-a-ring maths below
 * is the same idea. We drop its CHARGE/INTIMIDATE/DAZE/CRUMBLE states: the basilisk's single set-piece is the
 * strike goal, which preempts this goal (lower priority number) whenever it is ready.
 */
public class BasiliskStalkGoal extends Goal {
    private static final double RING_RADIUS = 6.5D;
    private static final double RING_ROTATION = 0.9D; // radians advanced per repath, like the naga's 1.0

    private final Basilisk basilisk;
    private boolean clockwise;
    private int repathIn;
    private int snapCooldown;

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
        if (snapCooldown > 0) snapCooldown--;

        // Quick non-venomous snap if something is right on top of us and the strike is not ready.
        double reach = basilisk.getBbWidth() * 2.0D + target.getBbWidth();
        if (snapCooldown <= 0 && basilisk.distanceToSqr(target) <= reach * reach && basilisk.hasLineOfSight(target)) {
            basilisk.swing(InteractionHand.MAIN_HAND);
            basilisk.doHurtTarget(target);
            snapCooldown = 20;
        }

        if (--repathIn > 0) return;
        repathIn = 10 + basilisk.getRandom().nextInt(10);
        if (basilisk.getRandom().nextInt(4) == 0) clockwise = !clockwise;

        double distance = basilisk.distanceTo(target);
        if (distance > 12.0D) {
            basilisk.getNavigation().moveTo(target, 1.0D);
        } else {
            Vec3 p = ringPoint(target, RING_RADIUS, RING_ROTATION);
            if (!basilisk.getNavigation().moveTo(p.x, p.y, p.z, 1.0D)) {
                basilisk.getNavigation().moveTo(target, 1.0D); // ring point unreachable (trees, cliff): just close in
            }
        }
    }

    /** NAGA: findCirclePoint. A point on a ring around the target, rotated a little ahead of where we are. */
    private Vec3 ringPoint(LivingEntity target, double radius, double rotation) {
        double vx = basilisk.getX() - target.getX();
        double vz = basilisk.getZ() - target.getZ();
        double angle = Math.atan2(vz, vx) + (clockwise ? rotation : -rotation);
        double y = Math.min(basilisk.getY(), target.getY());
        return new Vec3(target.getX() + Math.cos(angle) * radius, y, target.getZ() + Math.sin(angle) * radius);
    }
}
