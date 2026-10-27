package dev.syrval.verdantdeep.basilisk.entity.ai;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.phys.Vec3;

/** Patrol stroll that never picks a destination at or near a disc edge. */
public class BasiliskStrollGoal extends WaterAvoidingRandomStrollGoal {
    private static final int TRIES = 6;

    public BasiliskStrollGoal(PathfinderMob mob, double speed) {
        super(mob, speed);
    }

    @Override
    protected Vec3 getPosition() {
        for (int i = 0; i < TRIES; i++) {
            Vec3 p = super.getPosition();
            if (p == null) return null;
            if (!TerrainProbe.isNearEdge(this.mob.level(), p.x, p.y, p.z)) return p;
        }
        return null;
    }
}
