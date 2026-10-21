package dev.syrval.overgrownabyss.basilisk.entity.ai;

import dev.syrval.overgrownabyss.basilisk.entity.Basilisk;
import dev.syrval.overgrownabyss.basilisk.entity.BasiliskPhase;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.control.MoveControl;

/**
 * NAGA: NagaMoveControl adds a sine-wave sideways wobble (xxa) so it slithers. Same idea here, with two additions:
 *  - the wobble is gentler and off while a strike is being prepared or recovered from;
 *  - a lava whisker: before moving, look one and two blocks ahead; if the column there ends in lava (or no
 *    floor within fall range) cancel the step. This protects the path-less "steer straight at the target"
 *    fallback and the sideways wobble, neither of which the pathfinder checks.
 */
public class BasiliskMoveControl extends MoveControl {
    private final Basilisk basilisk;

    public BasiliskMoveControl(Basilisk basilisk) {
        super(basilisk);
        this.basilisk = basilisk;
    }

    @Override
    public void tick() {
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

    private boolean basiliskFrontIsUnsafe() {
        double dx = this.wantedX - basilisk.getX();
        double dz = this.wantedZ - basilisk.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.2D) return false;
        dx /= len;
        dz /= len;
        int maxDrop = basilisk.getMaxFallDistance();
        for (double reach = 1.0D; reach <= 2.0D; reach += 1.0D) {
            double px = basilisk.getX() + dx * reach;
            double pz = basilisk.getZ() + dz * reach;
            if (TerrainProbe.isUnsafeLanding(basilisk.level(), px, basilisk.getY() + 0.2D, pz, maxDrop)) return true;
        }
        return false;
    }
}
