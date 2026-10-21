package dev.syrval.overgrownabyss.basilisk.entity.ai;

import java.util.EnumSet;

import dev.syrval.overgrownabyss.basilisk.BasiliskEntities;
import dev.syrval.overgrownabyss.basilisk.entity.Basilisk;
import dev.syrval.overgrownabyss.basilisk.entity.BasiliskPhase;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * The signature attack: rear up like a cobra, hold, lunge, then drop its head and recover.
 *
 *   WINDUP  (REAR or COIL phase)  neck rises, hood flares, hiss. Tracks the target until AIM_LOCK_TICKS before
 *                                 the end, then commits to a fixed point: a player who keeps moving dodges it.
 *   LUNGE   (STRIKE phase)        fast flat dash along the locked line; the first overlap bites once, with venom.
 *   RECOVER (RECOVER phase)       head down, cannot move or attack, takes extra damage.
 *
 * Counterplay built in: enough damage during WINDUP staggers it (skips straight to RECOVER); a shield blocks
 * the bite and therefore the venom; a lunge that would land in lava or an abyss is aborted.
 *
 * NAGA: roughly NagaMovementPattern's INTIMIDATE (rattle) -> CHARGE -> DAZE sequence, with an aimed dash
 * instead of a path-following charge, so it cannot be dodged by the pathfinder losing track.
 */
public class BasiliskVenomStrikeGoal extends Goal {
    private static final int REAR_TICKS = 26;
    private static final int COIL_TICKS = 12;
    private static final int AIM_LOCK_TICKS = 8;
    private static final int RECOVER_TICKS = 30;
    private static final int STAGGER_TICKS = 40;
    private static final float INTERRUPT_DAMAGE = 10.0F;

    private static final double MIN_RANGE = 2.0D;
    private static final double MAX_RANGE = 7.0D;
    private static final double MAX_LUNGE = 6.5D;
    private static final double LUNGE_SPEED = 0.7D;
    private static final int COOLDOWN_MIN = 60;
    private static final int COOLDOWN_RANGE = 50;

    private enum Step { WINDUP, LUNGE, RECOVER, DONE }

    private final Basilisk basilisk;
    private Step step = Step.DONE;
    private int ticks;
    private int windup;
    private int recoverTicks;
    private int lungeTicks;
    private int cooldownUntil;
    private Vec3 aim;
    private Vec3 lungeDir = Vec3.ZERO;
    private boolean bitten;

    public BasiliskVenomStrikeGoal(Basilisk basilisk) {
        this.basilisk = basilisk;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        LivingEntity t = basilisk.getTarget();
        if (t == null || !t.isAlive() || basilisk.tickCount < cooldownUntil) return false;
        if (basilisk.getPhase() != BasiliskPhase.IDLE) return false;
        double d = basilisk.distanceTo(t);
        if (d < MIN_RANGE || d > MAX_RANGE) return false;
        if (Math.abs(t.getY() - basilisk.getY()) > 2.5D) return false; // the lunge is flat; do not rear at someone on a ledge
        return basilisk.hasLineOfSight(t);
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
        basilisk.getNavigation().stop();
        boolean room = TerrainProbe.hasHeadroom(basilisk.level(), basilisk, Basilisk.HEAD_RAISE + 1.0D);
        windup = room ? REAR_TICKS : COIL_TICKS;
        basilisk.setPhase(room ? BasiliskPhase.REAR : BasiliskPhase.COIL);
        step = Step.WINDUP;
        ticks = 0;
        aim = null;
        bitten = false;
        basilisk.playSound(BasiliskEntities.SND_REAR.get(), 1.6F, 0.9F + basilisk.getRandom().nextFloat() * 0.2F);
    }

    @Override
    public void stop() {
        step = Step.DONE;
        basilisk.setPhase(BasiliskPhase.IDLE);
        cooldownUntil = basilisk.tickCount + COOLDOWN_MIN + basilisk.getRandom().nextInt(COOLDOWN_RANGE);
    }

    @Override
    public void tick() {
        ticks++;
        switch (step) {
            case WINDUP -> tickWindup();
            case LUNGE -> tickLunge();
            case RECOVER -> { if (ticks >= recoverTicks) step = Step.DONE; }
            default -> { }
        }
    }

    private void tickWindup() {
        LivingEntity t = basilisk.getTarget();
        if (t == null || !t.isAlive()) { beginRecover(8); return; }
        if (basilisk.consumeInterrupt(INTERRUPT_DAMAGE)) {
            basilisk.playSound(BasiliskEntities.SND_HURT.get(), 1.2F, 0.7F);
            beginRecover(STAGGER_TICKS);
            return;
        }
        basilisk.getNavigation().stop();
        if (ticks < windup - AIM_LOCK_TICKS) {
            basilisk.lookAt(t, 45.0F, 45.0F);
            basilisk.getLookControl().setLookAt(t, 45.0F, 45.0F);
        } else if (aim == null) {
            // Lock on, leading the target slightly. Changing direction after this makes it miss.
            aim = t.position().add(t.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D).scale(3.0D));
        }
        if (ticks >= windup) beginLunge(t);
    }

    private void beginLunge(LivingEntity t) {
        Vec3 from = basilisk.position();
        Vec3 to = aim != null ? aim : t.position();
        double fx = to.x - from.x, fz = to.z - from.z;
        double dist = Math.sqrt(fx * fx + fz * fz);
        Vec3 dir = dist < 0.3D ? Vec3.directionFromRotation(0.0F, basilisk.getYRot()) : new Vec3(fx / dist, 0.0D, fz / dist);

        double reach = Math.min(Math.max(dist + 1.0D, 2.5D), MAX_LUNGE);
        Vec3 end = from.add(dir.scale(reach));
        if (TerrainProbe.isUnsafeLanding(basilisk.level(), end.x, from.y, end.z, basilisk.getMaxFallDistance())) {
            beginRecover(10); // refuse to lunge into lava or an abyss
            return;
        }

        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        basilisk.setYRot(yaw);
        basilisk.yBodyRot = yaw;
        basilisk.yHeadRot = yaw;

        lungeDir = dir;
        lungeTicks = (int) Math.ceil(reach / LUNGE_SPEED);
        bitten = false;
        step = Step.LUNGE;
        ticks = 0;
        basilisk.setPhase(BasiliskPhase.STRIKE);
        basilisk.setDeltaMovement(dir.x * LUNGE_SPEED, 0.1D, dir.z * LUNGE_SPEED);
        basilisk.hasImpulse = true;
        basilisk.playSound(BasiliskEntities.SND_STRIKE.get(), 1.4F, 0.95F + basilisk.getRandom().nextFloat() * 0.1F);
    }

    private void tickLunge() {
        Vec3 mv = basilisk.getDeltaMovement();
        double speed = LUNGE_SPEED * (1.0D - 0.5D * ticks / Math.max(1, lungeTicks));
        if (bitten) speed *= 0.2D;
        basilisk.setDeltaMovement(lungeDir.x * speed, mv.y, lungeDir.z * speed);
        basilisk.hasImpulse = true;

        LivingEntity t = basilisk.getTarget();
        if (!bitten && t != null && t.isAlive()
            && basilisk.getBoundingBox().inflate(0.45D, 0.3D, 0.45D).intersects(t.getBoundingBox())) {
            basilisk.swing(InteractionHand.MAIN_HAND);
            basilisk.doHurtTarget(t); // phase is STRIKE, so a landed bite applies venom
            bitten = true;
        }
        if (ticks >= lungeTicks || (bitten && ticks >= 3)) beginRecover(RECOVER_TICKS);
    }

    private void beginRecover(int length) {
        step = Step.RECOVER;
        ticks = 0;
        recoverTicks = length;
        basilisk.setPhase(BasiliskPhase.RECOVER);
        basilisk.getNavigation().stop();
        Vec3 mv = basilisk.getDeltaMovement();
        basilisk.setDeltaMovement(0.0D, mv.y, 0.0D);
    }
}
