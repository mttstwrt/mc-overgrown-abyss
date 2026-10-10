package dev.syrval.overgrownabyss.ravine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import org.junit.jupiter.api.Test;

/** The ground that stands over a disc's platform: where it rises, where it must not, and what it is made of. */
class DiscGroundTest {
    static {
        MinecraftBootstrap.init();
    }

    static final BlockStateProvider TURF = BlockStateProvider.simple(Blocks.GRASS_BLOCK);
    static final BlockStateProvider SOIL = BlockStateProvider.simple(Blocks.DIRT);
    static final BlockStateProvider ROCK = BlockStateProvider.simple(Blocks.ANDESITE);
    static final DiscPalette PALETTE = new DiscPalette(List.of(new DiscPalette.Layer(1, TURF)), List.of(), Optional.of(SOIL), DiscPalette.Stem.UNPAINTED);
    static final DiscRelief.Mounds MOUNDS = new DiscRelief.Mounds(3, 22, 0.35F);
    static final DiscRelief.Outcrops OUTCROPS = new DiscRelief.Outcrops(5, 7, 0.05F, ROCK);
    static final DiscRelief BOTH = new DiscRelief(Optional.of(MOUNDS), Optional.of(OUTCROPS));
    static final DiscRelief MOUNDS_ONLY = new DiscRelief(Optional.of(MOUNDS), Optional.empty());

    static DiscTheme theme(DiscRelief relief, Optional<DiscWater> water, DiscTheme.Growth... growth) {
        return new DiscTheme(
                Optional.empty(), Optional.empty(), 1, DiscTheme.Ramp.EVEN, DiscTheme.Ramp.EVEN, DiscTheme.Ramp.EVEN, DiscTheme.Limits.NONE,
                PALETTE, water, List.of(growth), DiscTheme.Wildness.EVEN, relief, Optional.empty(), Optional.empty());
    }

    /** The mod's own hole with one theme on every disc. */
    static RavineSettings with(DiscTheme theme) throws Exception {
        RavineSettings base = DiscThemeTest.shipped();
        return new RavineSettings(base.salt(), base.cellSize(), 1F, base.sizeBias(), VerticalAnchor.absolute(-40), VerticalAnchor.absolute(80),
                base.cavernRadius(), base.cavernHeight(), base.edgeFalloff(), base.discs(), List.of(theme), base.environment(), base.wallNoise(), base.ravine(), base.cone(), base.roots());
    }

    private record Block(int x, int y, int z) {}

    @Test
    void aMoundIsWalkedUpAndAnOutcropStandsInTheWay() {
        int raised = 0;
        int rocky = 0;
        int all = 0;
        double highestMound = 0;
        double highestOutcrop = 0;
        double steepestOutcrop = 0;
        boolean others = false;
        for (int x = 0; x < 500; x++) {
            for (int z = 0; z < 500; z++) {
                double mound = MOUNDS.riseAt(77L, 3, x, z);
                assertTrue(mound >= 0 && mound <= 3, "a mound of " + mound);
                // A block at its edge and under a block's rise for a block's run from there, so two columns side by side never
                // differ by more than one whole block.
                assertTrue(Math.abs(Math.floor(mound) - Math.floor(MOUNDS.riseAt(77L, 3, x + 1, z))) <= 1
                        && Math.abs(Math.floor(mound) - Math.floor(MOUNDS.riseAt(77L, 3, x, z + 1))) <= 1, "a step in a mound at " + x + ", " + z);
                assertTrue(mound == 0 || mound >= 1, "a mound of less than a block: " + mound);
                double outcrop = OUTCROPS.riseAt(77L, 3, x, z);
                assertTrue(outcrop == 0 || outcrop >= 1 && outcrop <= 5, "an outcrop of " + outcrop);
                raised += mound > 0 ? 1 : 0;
                rocky += outcrop > 0 ? 1 : 0;
                highestMound = Math.max(highestMound, mound);
                highestOutcrop = Math.max(highestOutcrop, outcrop);
                steepestOutcrop = Math.max(steepestOutcrop, Math.abs(outcrop - OUTCROPS.riseAt(77L, 3, x + 1, z)));
                others |= mound > 1 && MOUNDS.riseAt(77L, 4, x, z) == 0;
                all++;
            }
        }
        assertEquals(0.35, raised / (double) all, 0.05, "mounds a block high or more on about the share of the top asked for");
        assertEquals(0.05, rocky / (double) all, 0.03, "and outcrops on theirs");
        assertEquals(3, highestMound, 1e-9, "a wide mound reaches the height it is allowed");
        assertEquals(5, highestOutcrop, 1e-9);
        assertTrue(steepestOutcrop > 1, "an outcrop's side is a step, not a slope: " + steepestOutcrop);
        assertTrue(others, "each disc has mounds of its own");
    }

    @Test
    void theGroundLiesOnTheRockAtTheRimAndRoundWhatStandsOnThePlatform() {
        var disc = new Disc(0, 0, 60, 60, 27, 3, Disc.Support.Standing.TO_THE_FLOOR);
        var ruin = new DiscGround.Flat(12.5, -20.5, 9);
        var ground = new DiscGround(disc, 77L, 3, BOTH, Optional.empty(), List.of(ruin));
        int highest = 0;
        int raised = 0;
        for (int x = -66; x <= 66; x++) {
            for (int z = -66; z <= 66; z++) {
                int rise = ground.riseAt(x, z);
                double fromAxis = Math.hypot(x, z);
                assertTrue(rise >= 0 && rise <= 5, "a rise of " + rise);
                assertEquals(disc.topBlockAt(fromAxis) + rise, ground.topBlockAt(x, z), "the ground is the rock and what stands over it");
                // Level for four blocks inside the rim, and from there no steeper than a block for a block.
                assertTrue(rise <= Math.max(0, 60 - DiscGround.RIM_FLAT - fromAxis), rise + " blocks of ground " + (60 - fromAxis) + " from the rim");
                double fromRuin = Math.hypot(x - ruin.x(), z - ruin.z()) - ruin.radius();
                assertTrue(rise <= Math.max(0, fromRuin), rise + " blocks of ground " + fromRuin + " from a ruin's round");
                assertEquals(ground.riseAt(x, z), rise, "the same every time");
                highest = Math.max(highest, rise);
                raised += rise > 0 ? 1 : 0;
            }
        }
        assertEquals(5, highest, "an outcrop stands its full height somewhere on a disc this size");
        assertTrue(raised > 0.15 * Math.PI * 56 * 56, raised + " columns raised");
        var level = new DiscGround(disc, 77L, 3, DiscRelief.FLAT, Optional.empty(), List.of());
        assertEquals(0, level.riseAt(5, 5), "a theme with no relief leaves the platform as its ground");
        assertEquals(disc.topBlockAt(Math.hypot(5, 5)), level.topBlockAt(5, 5));
    }

    @Test
    void noGroundStandsOverADiscsWaterNorRightBesideIt() throws Exception {
        var water = new DiscWater(0.3F, 14, 2, 30, 2, 3);
        RavineSettings settings = with(theme(BOTH, Optional.of(water)));
        RavineCell cell = RavineCells.at(11L, settings, 0, 0).orElseThrow();
        CellDiscs cellDiscs = CellDiscs.of(settings, DiscThemeTest.VANILLA, cell);
        Disc d = DiscThemeTest.largest(cellDiscs.layout().discs());
        int index = cellDiscs.layout().discs().indexOf(d);
        DiscGround ground = cellDiscs.grounds().get(index);
        int wet = 0;
        int shore = 0;
        int steepShore = 0;
        int raised = 0;
        for (int x = (int) Math.floor(d.x() - d.radius()); x <= d.x() + d.radius(); x++) {
            for (int z = (int) Math.floor(d.z() - d.radius()); z <= d.z() + d.radius(); z++) {
                raised += ground.riseAt(x, z) > 0 ? 1 : 0;
                if (water.depthAt(d, cell.hash(), index, x, z) == 0) {
                    continue;
                }
                wet++;
                assertEquals(0, ground.riseAt(x, z), "ground over water at " + x + ", " + z);
                for (int[] step : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    if (water.depthAt(d, cell.hash(), index, x + step[0], z + step[1]) == 0) {
                        shore++;
                        steepShore += ground.riseAt(x + step[0], z + step[1]) > 0 ? 1 : 0;
                    }
                }
            }
        }
        assertTrue(wet > 200 && raised > 200, wet + " columns of water and " + raised + " of raised ground");
        assertEquals(0, steepShore, "of the " + shore + " columns right beside water, those with ground over the rock");
    }

    @Test
    void aMoundIsTheThemesOwnGroundWithItsTopLayerOnTopAndAnOutcropIsItsOwnBlock() throws Exception {
        RavineSettings settings = with(theme(BOTH, Optional.empty()));
        RavineCell cell = RavineCells.at(11L, settings, 0, 0).orElseThrow();
        CellDiscs cellDiscs = CellDiscs.of(settings, DiscThemeTest.VANILLA, cell);
        Disc d = DiscThemeTest.largest(cellDiscs.layout().discs());
        DiscGround ground = cellDiscs.grounds().get(cellDiscs.layout().discs().indexOf(d));
        Map<Block, BlockStateProvider> platforms = new HashMap<>();
        Map<Block, BlockStateProvider> mounds = new HashMap<>();
        for (int chunkX = Math.floorDiv((int) Math.floor(d.x() - d.radius()), 16); chunkX <= Math.floorDiv((int) Math.floor(d.x() + d.radius()), 16); chunkX++) {
            for (int chunkZ = Math.floorDiv((int) Math.floor(d.z() - d.radius()), 16); chunkZ <= Math.floorDiv((int) Math.floor(d.z() + d.radius()), 16); chunkZ++) {
                int minX = chunkX * 16;
                int minZ = chunkZ * 16;
                DiscBlocks.forEach(settings, DiscThemeTest.VANILLA, cell, cellDiscs, minX, minZ, -64, 320, (x, y, z, block) -> platforms.put(new Block(x, y, z), block));
                DiscBlocks.forEachMound(cellDiscs, minX, minZ, -64, 320, (x, y, z, block) -> {
                    assertTrue(x >= minX && x < minX + 16 && z >= minZ && z < minZ + 16, "outside its chunk");
                    mounds.put(new Block(x, y, z), block);
                });
            }
        }
        int soil = 0;
        int rock = 0;
        int level = 0;
        for (int x = (int) Math.ceil(d.x() - d.radius()); x <= d.x() + d.radius(); x++) {
            for (int z = (int) Math.ceil(d.z() - d.radius()); z <= d.z() + d.radius(); z++) {
                double fromAxis = Math.hypot(x - d.x(), z - d.z());
                if (fromAxis >= d.radius() - 1 || isUnderAnother(cellDiscs.layout().discs(), d, x, z)) {
                    continue;
                }
                int top = d.topBlockAt(fromAxis);
                int rise = ground.riseAt(x, z);
                assertTrue(!mounds.containsKey(new Block(x, top + rise + 1, z)), "ground over the ground's own top at " + x + ", " + z);
                if (rise == 0) {
                    assertEquals(TURF, platforms.get(new Block(x, top, z)), "level ground keeps the platform's top layer");
                    assertTrue(!mounds.containsKey(new Block(x, top + 1, z)));
                    level++;
                    continue;
                }
                assertEquals(SOIL, platforms.get(new Block(x, top, z)), "under a mound the platform's top block is no longer the top");
                boolean outcrop = ground.isOutcropAt(x, z);
                for (int up = 1; up <= rise; up++) {
                    BlockStateProvider expected = outcrop ? ROCK : up == rise ? TURF : SOIL;
                    assertEquals(expected, mounds.get(new Block(x, top + up, z)), up + " of " + rise + " over the rock at " + x + ", " + z);
                }
                soil += outcrop ? 0 : 1;
                rock += outcrop ? 1 : 0;
            }
        }
        assertTrue(soil > 100 && rock > 5 && level > 100, soil + " columns of mound, " + rock + " of outcrop, " + level + " level");
    }

    // A disc higher up whose platform reaches this column paints it too, and what it paints is not this disc's.
    private static boolean isUnderAnother(List<Disc> discs, Disc d, int x, int z) {
        return discs.stream().anyMatch(other -> other != d && Math.hypot(x - other.x(), z - other.z()) <= other.radius() + 1);
    }

    @Test
    void whatGrowsOnATopStartsOnTheGroundAndNotInIt() throws Exception {
        var everywhere = new DiscTheme.Growth(DiscThemeTest.feature("minecraft:jungle_tree"), 4, DiscTheme.Surface.TOP, Optional.empty(), Optional.empty());
        RavineSettings settings = with(theme(BOTH, Optional.empty(), everywhere));
        RavineCell cell = RavineCells.at(11L, settings, 0, 0).orElseThrow();
        CellDiscs cellDiscs = CellDiscs.of(settings, DiscThemeTest.VANILLA, cell);
        List<Disc> discs = cellDiscs.layout().discs();
        Disc d = DiscThemeTest.largest(discs);
        DiscGround ground = cellDiscs.grounds().get(discs.indexOf(d));
        int onMounds = 0;
        int onTheLevel = 0;
        for (DiscThemeTest.Place place : DiscThemeTest.placesAround(settings, cell, cellDiscs, d)) {
            double fromAxis = Math.hypot(place.x() - d.x(), place.z() - d.z());
            if (fromAxis > d.radius() || isUnderAnother(discs, d, place.x(), place.z())) {
                continue;
            }
            assertEquals(ground.topBlockAt(place.x(), place.z()) + 1, place.y(), "a place at " + place);
            onMounds += ground.riseAt(place.x(), place.z()) > 0 ? 1 : 0;
            onTheLevel += ground.riseAt(place.x(), place.z()) == 0 ? 1 : 0;
        }
        assertTrue(onMounds > 30 && onTheLevel > 30, onMounds + " places on raised ground and " + onTheLevel + " on the level");
    }
}
