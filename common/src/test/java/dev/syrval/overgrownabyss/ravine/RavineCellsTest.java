package dev.syrval.overgrownabyss.ravine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.InclusiveRange;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import org.junit.jupiter.api.Test;

class RavineCellsTest {
    static {
        MinecraftBootstrap.init();
    }

    static final RavineEnvironment ENVIRONMENT = new RavineEnvironment(
            TagKey.create(Registries.BIOME, ResourceLocation.parse("overgrown_abyss:ravine_forbidden")),
            Optional.empty());
    static final RavineWalls WALLS = new RavineWalls(12, 0.85F, 8, 0.03F, 10, 0.012F, 24, 0.22F, 6);
    static final RavineCurvature CURVATURE = new RavineCurvature(70, 40, 45, 25);
    static final RavineBridges BRIDGES = new RavineBridges(3, 0.45F, 7, 6, 0.1F, 0.75F);
    static final RavineSettings SETTINGS = settings(42L, 0.5F);

    static RavineSettings settings(long salt, float chance) {
        return new RavineSettings(
                salt, 2048, chance, new InclusiveRange<>(0, 400), new InclusiveRange<>(24, 110),
                VerticalAnchor.absolute(-40), VerticalAnchor.absolute(80), 0.35F, 128, 48,
                WALLS, CURVATURE, BRIDGES, ENVIRONMENT);
    }

    @Test
    void sameInputsGiveSameCell() {
        assertEquals(RavineCells.at(1234L, SETTINGS, 3, -7), RavineCells.at(1234L, SETTINGS, 3, -7));
    }

    @Test
    void seedAndSaltChangeTheLayout() {
        RavineSettings otherSalt = settings(43L, 1F);
        RavineSettings always = settings(42L, 1F);
        assertNotEquals(RavineCells.at(1L, always, 0, 0), RavineCells.at(2L, always, 0, 0));
        assertNotEquals(RavineCells.at(1L, always, 0, 0), RavineCells.at(1L, otherSalt, 0, 0));
    }

    @Test
    void chanceControlsHowManyCellsHoldARavine() {
        int found = 0;
        for (int x = 0; x < 100; x++) {
            for (int z = 0; z < 100; z++) {
                found += RavineCells.at(99L, SETTINGS, x, z).isPresent() ? 1 : 0;
            }
        }
        assertTrue(found > 4500 && found < 5500, "found " + found);
    }

    @Test
    void everyRavineFitsInsideItsCell() {
        double reach = SETTINGS.maxReach();
        for (int x = -20; x < 20; x++) {
            for (int z = -20; z < 20; z++) {
                Optional<RavineCell> cell = RavineCells.at(7L, SETTINGS, x, z);
                if (cell.isEmpty()) {
                    continue;
                }
                RavineCell c = cell.get();
                double minX = (double) x * SETTINGS.cellSize();
                double minZ = (double) z * SETTINGS.cellSize();
                assertTrue(c.centreX() - reach >= minX && c.centreX() + reach <= minX + SETTINGS.cellSize());
                assertTrue(c.centreZ() - reach >= minZ && c.centreZ() + reach <= minZ + SETTINGS.cellSize());
            }
        }
    }

    @Test
    void sizeRunsFromARoundHoleToTheConfiguredLargest() {
        RavineSettings always = settings(42L, 1F);
        double shortest = Double.MAX_VALUE;
        double longest = 0;
        double narrowest = Double.MAX_VALUE;
        double widest = 0;
        for (int x = -30; x < 30; x++) {
            for (int z = -30; z < 30; z++) {
                RavineCell c = RavineCells.at(3L, always, x, z).orElseThrow();
                shortest = Math.min(shortest, c.halfLength() * 2);
                longest = Math.max(longest, c.halfLength() * 2);
                narrowest = Math.min(narrowest, c.halfWidth() * 2);
                widest = Math.max(widest, c.halfWidth() * 2);
                // One draw sets both, so a long ravine is always a wide one.
                assertEquals(c.halfLength() * 2 / 400, (c.halfWidth() * 2 - 24) / (110 - 24), 1e-9);
            }
        }
        assertTrue(shortest < 12 && narrowest < 28, "smallest: length " + shortest + " width " + narrowest);
        assertTrue(longest > 388 && longest <= 400 && widest > 107 && widest <= 110, "largest: length " + longest + " width " + widest);
    }

    @Test
    void bridgesAndCurvesStayWithinTheirLimitsAndSmallRavinesStayOpen() {
        RavineSettings always = settings(42L, 1F);
        boolean sawBridge = false;
        boolean sawBend = false;
        for (int x = -30; x < 30; x++) {
            for (int z = -30; z < 30; z++) {
                RavineCell c = RavineCells.at(5L, always, x, z).orElseThrow();
                double scale = c.halfLength() * 2 / 400;
                if (scale < BRIDGES.minSize()) {
                    assertTrue(c.bridges().isEmpty(), "a small ravine must not be bridged");
                }
                assertTrue(c.bridges().size() <= BRIDGES.maxCount());
                for (RavineCell.Bridge bridge : c.bridges()) {
                    assertTrue(Math.abs(bridge.along()) <= c.halfLength() * 0.7 + 1e-9);
                    assertTrue(bridge.height() >= BRIDGES.minHeight() && bridge.height() <= BRIDGES.maxHeight());
                    sawBridge = true;
                }
                assertTrue(Math.abs(c.bend().bend()) <= CURVATURE.maxBend() * scale + 1e-9);
                assertTrue(Math.abs(c.bend().wiggle()) <= CURVATURE.maxWiggle() * scale + 1e-9);
                assertTrue(c.lean().lean() >= 0 && c.lean().lean() <= CURVATURE.maxLean());
                assertTrue(Math.abs(c.lean().bow()) <= CURVATURE.maxBow());
                sawBend |= Math.abs(c.bend().bend()) > 1;
            }
        }
        assertTrue(sawBridge && sawBend);
    }

    @Test
    void containingMapsNegativeCoordinatesToTheRightCell() {
        assertEquals(RavineCells.at(5L, SETTINGS, -1, -1), RavineCells.containing(5L, SETTINGS, -0.5, -2047));
        assertEquals(RavineCells.at(5L, SETTINGS, -2, 0), RavineCells.containing(5L, SETTINGS, -2049, 0));
    }

    @Test
    void centreChunkContainsTheRavineCentreForEveryChunkOfTheCell() {
        RavineSettings always = settings(42L, 1F);
        RavineCell cell = RavineCells.at(5L, always, -1, 2).orElseThrow();
        ChunkPos expected = new ChunkPos(Math.floorDiv((int) Math.floor(cell.centreX()), 16), Math.floorDiv((int) Math.floor(cell.centreZ()), 16));
        // Cell (-1, 2) spans chunks x -128..-1 and z 256..383.
        for (int chunkX : new int[] {-128, -64, -1}) {
            for (int chunkZ : new int[] {256, 300, 383}) {
                assertEquals(expected, RavineCells.centreChunk(5L, always, chunkX, chunkZ));
            }
        }
    }

    @Test
    void centreChunkFallsBackToTheCellMiddleWhenThereIsNoRavine() {
        RavineSettings never = settings(42L, 0F);
        assertEquals(new ChunkPos(64, 64), RavineCells.centreChunk(5L, never, 3, 100));
        assertEquals(new ChunkPos(-64, 64), RavineCells.centreChunk(5L, never, -1, 0));
    }
}
