package dev.syrval.verdantdeep.basilisk;

import java.util.OptionalDouble;

import dev.syrval.verdantdeep.basilisk.entity.Basilisk;
import dev.syrval.verdantdeep.compat.RavineFootprintHolder;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;

/** Natural spawn rule of the basilisk: rare, only inside a chasm, more likely toward the floor, never near another. */
public final class BasiliskSpawning {
    /** Depth share (0 mouth, 1 floor) below which it never spawns. */
    public static final double DEPTH_START = 0.35;
    /** Chance of accepting a spawn attempt at the floor. */
    public static final double BASE_CHANCE = 0.35;
    /** Blocks around a spawn that must hold no other basilisk. */
    public static final double NO_SPAWN_RADIUS = 48.0;
    /** Blocks outside the chasm wall that still count as inside it. */
    private static final double MARGIN = 4.0;

    private BasiliskSpawning() {}

    public static boolean canSpawn(EntityType<Basilisk> type, ServerLevelAccessor accessor, MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        if (accessor.getDifficulty() == Difficulty.PEACEFUL) {
            return false;
        }
        if (!Mob.checkMobSpawnRules(type, accessor, spawnType, pos, random)) {
            return false;
        }
        if (spawnType != MobSpawnType.NATURAL && spawnType != MobSpawnType.CHUNK_GENERATION) {
            return true;
        }
        ServerLevel level = accessor.getLevel();
        OptionalDouble depth = RavineFootprintHolder.of(level).depthShareIn(pos.getX(), pos.getY(), pos.getZ(), MARGIN);
        if (depth.isEmpty() || random.nextDouble() >= chance(depth.getAsDouble())) {
            return false;
        }
        return level.getEntitiesOfClass(Basilisk.class, new AABB(pos).inflate(NO_SPAWN_RADIUS)).isEmpty();
    }

    /** Chance of accepting a spawn attempt at a depth share: 0 up to {@link #DEPTH_START}, smoothly up to {@link #BASE_CHANCE} at 1. */
    public static double chance(double depth) {
        double t = Math.min(1.0, Math.max(0.0, (depth - DEPTH_START) / (1.0 - DEPTH_START)));
        return t * t * (3.0 - 2.0 * t) * BASE_CHANCE;
    }
}
