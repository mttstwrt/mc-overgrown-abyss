package dev.syrval.overgrownabyss.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.syrval.overgrownabyss.ravine.SurfaceProbe;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseSettings;
import org.junit.jupiter.api.Test;

/** The ground read from a level's density. */
class TerrainSurfaceTest {
    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // The overworld's: from -64 up 384 blocks, in noise cells 8 high.
    private static final NoiseSettings NOISE = NoiseSettings.create(-64, 384, 1, 2);

    /** Rock from the bottom up to a height, less a cave. */
    private record Terrain(int surface, int caveFloor, int caveRoof, AtomicInteger sampled) implements DensityFunction.SimpleFunction {
        Terrain(int surface) {
            this(surface, 0, 0, new AtomicInteger());
        }

        @Override
        public double compute(FunctionContext context) {
            sampled.incrementAndGet();
            int y = context.blockY();
            return y <= surface && !(y > caveFloor && y < caveRoof) ? 0.4 : -0.2;
        }

        @Override
        public double minValue() {
            return -1;
        }

        @Override
        public double maxValue() {
            return 1;
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            throw new UnsupportedOperationException("never written");
        }
    }

    @Test
    void theGroundIsTheHighestRockOfTheColumn() {
        for (int surface = 60; surface <= 90; surface++) {
            assertEquals(surface, TerrainSurface.of(NOISE, new Terrain(surface)).heightAt(5, -7, 400), "ground at " + surface);
        }
        assertEquals(319, TerrainSurface.of(NOISE, new Terrain(1000)).heightAt(0, 0, 400), "rock to the top of the level");
        assertEquals(-65, TerrainSurface.of(NOISE, new Terrain(-1000)).heightAt(0, 0, 400), "no rock at all is under the bottom");
    }

    @Test
    void groundAboveWhatWasAskedIsNotMeasured() {
        Terrain mountain = new Terrain(200);
        assertEquals(150, TerrainSurface.of(NOISE, mountain).heightAt(0, 0, 150));
        assertEquals(1, mountain.sampled().get(), "one sample says there is rock that high");
        assertEquals(71, TerrainSurface.of(NOISE, new Terrain(71)).heightAt(0, 0, 71));
        assertEquals(70, TerrainSurface.of(NOISE, new Terrain(71)).heightAt(0, 0, 70));
        Terrain plain = new Terrain(100);
        assertEquals(100, TerrainSurface.of(NOISE, plain).heightAt(0, 0, 163));
        assertTrue(plain.sampled().get() <= (163 - 100) / 8 + 9, plain.sampled().get() + " samples for 63 blocks of air");
    }

    @Test
    void aCaveUnderTheGroundDoesNotCountAsTheGround() {
        SurfaceProbe caved = TerrainSurface.of(NOISE, new Terrain(80, 40, 60, new AtomicInteger()));
        assertEquals(80, caved.heightAt(0, 0, 400));
        assertEquals(40, caved.heightAt(0, 0, 55), "asked from inside the cave, the ground is the cave's floor");
    }
}
