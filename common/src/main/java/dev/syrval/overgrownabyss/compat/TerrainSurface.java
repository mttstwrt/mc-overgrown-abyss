package dev.syrval.overgrownabyss.compat;

import dev.syrval.overgrownabyss.ravine.SurfaceProbe;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseSettings;

/**
 * The ground a level's terrain makes before any ravine is carved into it, read from the level's own final density: a block is
 * rock where that is above 0, as in the noise fill. The game blends the density between the corners of its noise cells and this
 * takes it at the column itself, so the two can differ by a few blocks (see SCOPE.md for the measured difference).
 *
 * <p>A column costs several samples of the whole density with none of the noise fill's caches, far more than a block of noise
 * fill does, so this is for the few columns that decide where a hole's top is and how high its domes may be.
 */
final class TerrainSurface implements SurfaceProbe {
    private final DensityFunction density;
    private final int bottom;
    private final int top;
    private final int step;

    private TerrainSurface(DensityFunction density, int bottom, int top, int step) {
        this.density = density;
        this.bottom = bottom;
        this.top = top;
        this.step = step;
    }

    /** The ground of {@code density}, a final density wired for one level, over the heights {@code noise} covers. */
    static SurfaceProbe of(NoiseSettings noise, DensityFunction density) {
        return new TerrainSurface(density, noise.minY(), noise.minY() + noise.height() - 1, noise.getCellHeight());
    }

    @Override
    public int heightAt(int blockX, int blockZ, int from) {
        int start = Math.min(from, top);
        // Down a noise cell at a time to the first rock, then up block by block to the air over it.
        for (int y = start; y >= bottom; y -= step) {
            if (isRock(blockX, y, blockZ)) {
                int surface = y;
                while (surface < Math.min(start, y + step - 1) && isRock(blockX, surface + 1, blockZ)) {
                    surface++;
                }
                return surface;
            }
        }
        return bottom - 1;
    }

    private boolean isRock(int x, int y, int z) {
        return density.compute(new DensityFunction.SinglePointContext(x, y, z)) > 0;
    }
}
