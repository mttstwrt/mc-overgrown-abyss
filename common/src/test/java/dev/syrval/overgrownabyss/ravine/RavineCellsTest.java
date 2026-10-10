package dev.syrval.overgrownabyss.ravine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
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
    static final RavineCurvature CURVATURE = new RavineCurvature(70, 40, 45, 25);
    static final DiscShape SHAPE = new DiscShape(22, 55, 0.45F, 14, 4, 0.2F, 2.5F, 32F, 1.5F, 3F, 4F, 0F, 0F, 0F);
    static final RavinePlacement PLACEMENT = new RavinePlacement(40, 12, 12, 0.4F, 0.75F, 0.8F, 0F, 4F, 0.5F);
    static final RavineGeometry GEOMETRY = new RavineGeometry(new InclusiveRange<>(0, 400), new InclusiveRange<>(24, 110), 0.35F, CURVATURE, PLACEMENT);
    static final RavineSettings SETTINGS = settings(42L, 0.5F);

    static RavineSettings settings(long salt, float chance) {
        return settings(salt, chance, 1F);
    }

    static RavineSettings settings(long salt, float chance, float sizeBias) {
        return new RavineSettings(
                salt, 2048, chance, sizeBias, VerticalAnchor.absolute(-40), VerticalAnchor.absolute(80), 128, 48, 8F,
                SHAPE, List.of(), ENVIRONMENT, WallNoise.NONE, Optional.of(GEOMETRY), Optional.empty(), Optional.empty());
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
    void curvesStayWithinTheirLimits() {
        RavineSettings always = settings(42L, 1F);
        boolean sawBend = false;
        for (int x = -30; x < 30; x++) {
            for (int z = -30; z < 30; z++) {
                RavineCell c = RavineCells.at(5L, always, x, z).orElseThrow();
                double scale = c.halfLength() * 2 / 400;
                assertTrue(Math.abs(c.bend().bend()) <= CURVATURE.maxBend() * scale + 1e-9);
                assertTrue(Math.abs(c.bend().wiggle()) <= CURVATURE.maxWiggle() * scale + 1e-9);
                assertTrue(c.lean().lean() >= 0 && c.lean().lean() <= CURVATURE.maxLean());
                assertTrue(Math.abs(c.lean().bow()) <= CURVATURE.maxBow());
                sawBend |= Math.abs(c.bend().bend()) > 1;
            }
        }
        assertTrue(sawBend);
    }

    @Test
    void sizeBiasBelowOneFavoursLargeRavines() {
        RavineSettings large = settings(42L, 1F, 0.5F);
        int total = 0;
        int longCount = 0;
        int small = 0;
        for (int x = -50; x < 50; x++) {
            for (int z = -50; z < 50; z++) {
                double length = RavineCells.at(7L, large, x, z).orElseThrow().halfLength() * 2;
                total++;
                longCount += length > 250 ? 1 : 0;
                small += length < 120 ? 1 : 0;
            }
        }
        // A uniform draw would give 37% over 250 and 30% under 120; the square root gives about 61% and 9%.
        assertTrue(longCount > total * 0.5, longCount + " of " + total + " are over 250 long");
        assertTrue(small < total * 0.15, small + " of " + total + " are under 120 long");
    }

    @Test
    void mostLeaningRavinesCurveBackAndEveryRavineWiggles() {
        RavineSettings always = settings(42L, 1F);
        int s = 0;
        int total = 0;
        for (int x = -50; x < 50; x++) {
            for (int z = -50; z < 50; z++) {
                RavineCell c = RavineCells.at(8L, always, x, z).orElseThrow();
                total++;
                s += c.lean().bow() <= 0 ? 1 : 0;
                double scale = c.halfLength() * 2 / 400;
                assertTrue(Math.abs(c.bend().wiggle()) >= CURVATURE.maxWiggle() * scale * 0.5 - 1e-9);
            }
        }
        assertTrue(s > total * 0.68 && s < total * 0.82, s + " of " + total + " bow against the lean");
    }

    @Test
    void smoothValuesAreTheDrawnOnesAtWholeCoordinatesAndChangeGraduallyBetween() {
        long hash = 0x1234_5678_9ABCL;
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                assertEquals(RavineCells.unitAt(hash, 4, x, z), RavineCells.smoothAt(hash, 4, x, z), 1e-12);
            }
        }
        for (double x = -5; x < 5; x += 0.013) {
            for (double z = -5; z < 5; z += 0.37) {
                double here = RavineCells.smoothAt(hash, 4, x, z);
                assertTrue(here >= 0 && here < 1, "out of range: " + here);
                // Between two whole coordinates a value moves by at most the difference of the drawn ones, at most 1.5 times as fast as evenly.
                assertTrue(Math.abs(RavineCells.smoothAt(hash, 4, x + 0.013, z) - here) <= 1.5 * 0.013 + 1e-9, "a jump at " + x + ", " + z);
            }
        }
        assertTrue(RavineCells.smoothAt(hash, 4, 0.4, 0.4) != RavineCells.smoothAt(hash, 5, 0.4, 0.4), "each index is a pattern of its own");
    }

    @Test
    void smoothValuesRoundAClosedSurfaceMeetWhereItClosesAndChangeGradually() {
        long hash = 0x5EEDL;
        int around = 9;
        for (double along = -6; along < 6; along += 0.37) {
            assertEquals(RavineCells.smoothRound(hash, 20, 0, around, along), RavineCells.smoothRound(hash, 20, around, around, along), 1e-12, "the same after one turn");
            assertEquals(RavineCells.smoothRound(hash, 20, 2.3, around, along), RavineCells.smoothRound(hash, 20, 2.3 - around, around, along), 1e-9, "and one turn back");
            for (double round = 0; round < around; round += 0.29) {
                double here = RavineCells.smoothRound(hash, 20, round, around, along);
                assertTrue(here >= 0 && here < 1, "out of range: " + here);
                assertTrue(Math.abs(RavineCells.smoothRound(hash, 20, round + 0.013, around, along) - here) <= 1.5 * 0.013 + 1e-9, "a jump round at " + round);
                assertTrue(Math.abs(RavineCells.smoothRound(hash, 20, round, around, along + 0.013) - here) <= 1.5 * 0.013 + 1e-9, "a jump along at " + along);
            }
        }
        assertTrue(RavineCells.smoothRound(hash, 20, 0.4, around, 0.4) != RavineCells.smoothRound(hash, 22, 0.4, around, 0.4), "each index is a pattern of its own");
    }

    @Test
    void aShareIsTurnedIntoALevelOfTheSmoothValuesThatHoldsThatShare() {
        assertEquals(0, RavineCells.levelBelow(0), 1e-9);
        assertEquals(0.5, RavineCells.levelBelow(0.5), 1e-9);
        assertEquals(1, RavineCells.levelBelow(1), 1e-9);
        for (double share : new double[] {0.05, 0.1, 0.3, 0.5, 0.7, 0.92}) {
            double level = RavineCells.levelBelow(share);
            assertEquals(1 - level, RavineCells.levelBelow(1 - share), 1e-9, "the values lie evenly about a half");
            int below = 0;
            int all = 0;
            for (int x = 0; x < 600; x++) {
                for (int z = 0; z < 600; z++) {
                    below += RavineCells.smoothAt(0xABCDEFL, 9, x / 7.3, z / 7.3) < level ? 1 : 0;
                    all++;
                }
            }
            assertEquals(share, below / (double) all, 0.015, "values under the level for a share of " + share);
            assertEquals(share, RavineCells.shareBelow(level), 1e-9, "and the level turns back into the share");
        }
        assertEquals(0, RavineCells.shareBelow(0), 1e-9);
        assertEquals(1, RavineCells.shareBelow(1), 1e-9);
        double previous = -1;
        for (double level = 0; level <= 1; level += 0.01) {
            assertTrue(RavineCells.shareBelow(level) > previous, "a higher level holds a larger share");
            previous = RavineCells.shareBelow(level);
        }
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
