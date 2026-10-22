package dev.syrval.overgrownabyss.basilisk.entity.ai;

import dev.syrval.overgrownabyss.basilisk.entity.Basilisk;
import dev.syrval.overgrownabyss.basilisk.entity.BasiliskPhase;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.control.MoveControl;

/**
 * NAGA: NagaMoveControl adds a sine-wave sideways wobble (xxa) so it slithers. Same idea here, with two additions:
 *  - the wobble is gentler and off while a strike is being prepared or recovered from;
 *  - a lava whisker: before moving, look one and two blocks ahead; if the column there ends in lava (or no
 *    floor within fall range) cancel the step. This protects the path-less "steer straight at the target"
 *    fallback and the sideways wobble, neither of which the pathfinder checks.
 */
public class BasiliskMoveControl extends MoveControl {
    /** Stuck fallback: this many ticks with less than STUCK_MIN_MOVE blocks of progress while pathing to a target. */
    private static final int STUCK_TICKS = 20;
    private static final double STUCK_MIN_MOVE = 0.5D;
    private static final double STUCK_MIN_TARGET_DISTANCE = 3.0D;
    private static final double STUCK_HOP = 0.9D;   // upward speed of the hop, blocks per tick
    private static final double STUCK_PUSH = 0.4D;  // horizontal push toward the target during the hop
    private static final double STUCK_SPEED = 1.0D;

    private final Basilisk basilisk;
    private int stuckTicks;
    private double stuckX, stuckZ;

    public BasiliskMoveControl(Basilisk basilisk) {
        super(basilisk);
        this.basilisk = basilisk;
    }

    @Override
    public void tick() {
        if (!basilisk.level().isClientSide) tickStuckFallback();

        if (this.operation == Operation.MOVE_TO && basiliskFrontIsUnsafe()) {
            this.operation = Operation.WAIT;
            this.basilisk.setZza(0.0F);
            this.basilisk.getNavigation().stop();
            return;
        }

        if (basilisk.getPhase() == BasiliskPhase.IDLE) {
            this.basilisk.xxa = Mth.cos(this.basilisk.tickCount * 0.3F) * 0.35F;
        } else {
            this.basilisk.xxa = 0.0F;
        }
        super.tick();
    }

    /**
     * Pathing toward something but hardly moving for STUCK_TICKS: hop with a push toward the destination, and
     * steer straight at the target for a moment (the move control bypasses the path), instead of waiting for a repath.
     */
    private void tickStuckFallback() {
        LivingEntity target = basilisk.getTarget();
        boolean heading = target != null && basilisk.getPhase() == BasiliskPhase.IDLE && !basilisk.isAsleep()
            && !basilisk.isGroggy() && (this.operation == Operation.MOVE_TO || !basilisk.getNavigation().isDone());
        if (!heading || basilisk.distanceTo(target) < STUCK_MIN_TARGET_DISTANCE) {
            stuckTicks = 0;
            return;
        }
        if (stuckTicks == 0) {
            stuckX = basilisk.getX();
            stuckZ = basilisk.getZ();
        }
        if (++stuckTicks < STUCK_TICKS) return;
        stuckTicks = 0;
        if (Math.hypot(basilisk.getX() - stuckX, basilisk.getZ() - stuckZ) >= STUCK_MIN_MOVE) return;

        double dx = target.getX() - basilisk.getX(), dz = target.getZ() - basilisk.getZ();
        double len = Math.max(1.0E-3D, Math.sqrt(dx * dx + dz * dz));
        if (basilisk.onGround() || basilisk.horizontalCollision) {
            basilisk.setDeltaMovement(dx / len * STUCK_PUSH, STUCK_HOP, dz / len * STUCK_PUSH);
            basilisk.hasImpulse = true;
        }
        setWantedPosition(target.getX(), target.getY(), target.getZ(), STUCK_SPEED);
    }

    private boolean basiliskFrontIsUnsafe() {
        double dx = this.wantedX - basilisk.getX();
        double dz = this.wantedZ - basilisk.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.2D) return false;
        if (this.wantedY > basilisk.getY() + 0.5D) return false; // heading up: a step or a climb, not a fall
        dx /= len;
        dz /= len;
        int maxDrop = basilisk.getMaxFallDistance();
        // Probe from the edge of the body, not from its centre: the first blocks ahead are under the head itself.
        double edge = basilisk.getBbWidth() * 0.5D;
        for (double reach = edge + 1.0D; reach <= edge + 2.0D; reach += 1.0D) {
            double px = basilisk.getX() + dx * reach;
            double pz = basilisk.getZ() + dz * reach;
            if (TerrainProbe.isUnsafeLanding(basilisk.level(), px, basilisk.getY() + 0.2D, pz, maxDrop)) return true;
        }
        return false;
    }
}
