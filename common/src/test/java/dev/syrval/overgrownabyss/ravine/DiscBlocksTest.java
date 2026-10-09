package dev.syrval.overgrownabyss.ravine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import org.junit.jupiter.api.Test;

/** Which blocks of a chunk take a disc's material, in a cone whose discs all share one palette. */
class DiscBlocksTest {
    static {
        MinecraftBootstrap.init();
    }

    static final RavineBounds BOUNDS = ConeShapeTest.BOUNDS;
    static final DiscShape SHAPE = ConeShapeTest.SHAPE;
    static final BlockStateProvider TOP = BlockStateProvider.simple(Blocks.MOSS_BLOCK);
    static final BlockStateProvider BODY = BlockStateProvider.simple(Blocks.CALCITE);
    static final BlockStateProvider STEM = BlockStateProvider.simple(Blocks.PACKED_MUD);
    static final DiscPalette PALETTE = new DiscPalette(
            List.of(new DiscPalette.Layer(1, TOP)), List.of(), Optional.of(BODY), new DiscPalette.Stem(List.of(), Optional.of(STEM)));
    static final DiscTheme THEME = themed(PALETTE);
    static final RavineSettings STANDING = painted(ConeShapeTest.CONE, List.of(THEME));
    static final RavineSettings HANGING = painted(ConeShapeTest.HANGING, List.of(THEME));

    /** A theme that is only a palette: no biome, nothing growing, the same weight everywhere. */
    static DiscTheme themed(DiscPalette palette) {
        return new DiscTheme(Optional.empty(), Optional.empty(), 1, DiscTheme.Ramp.EVEN, DiscTheme.Ramp.EVEN, DiscTheme.Ramp.EVEN, DiscTheme.Limits.NONE, palette, Optional.empty(), List.of(), Optional.empty());
    }

    private record Block(int x, int y, int z) {}

    static RavineSettings painted(ConeSettings cone, List<DiscTheme> themes) {
        return painted(cone, themes, SHAPE);
    }

    static RavineSettings painted(ConeSettings cone, List<DiscTheme> themes, DiscShape shape) {
        RavineSettings s = ConeShapeTest.withCone(cone, shape);
        return new RavineSettings(s.salt(), s.cellSize(), s.chance(), s.sizeBias(), s.floor(), s.top(), s.cavernRadius(), s.cavernHeight(), s.edgeFalloff(),
                s.discs(), themes, s.environment(), s.wallNoise(), s.ravine(), s.cone());
    }

    private static RavineCell cell(RavineSettings settings, int cz) {
        return RavineCells.at(7L, settings, 1, cz).orElseThrow();
    }

    /** What the chunks touching a box of columns end up painted with: a later block replaces an earlier one, as in the world. */
    private static Map<Block, BlockStateProvider> paint(RavineSettings settings, RavineCell cell, double fromX, double toX, double fromZ, double toZ) {
        var painted = new HashMap<Block, BlockStateProvider>();
        CellDiscs discs = CellDiscs.of(settings, BOUNDS, cell);
        for (int chunkX = Math.floorDiv((int) Math.floor(fromX), 16); chunkX <= Math.floorDiv((int) Math.floor(toX), 16); chunkX++) {
            for (int chunkZ = Math.floorDiv((int) Math.floor(fromZ), 16); chunkZ <= Math.floorDiv((int) Math.floor(toZ), 16); chunkZ++) {
                int minX = chunkX * 16;
                int minZ = chunkZ * 16;
                DiscBlocks.forEach(settings, BOUNDS, cell, discs, minX, minZ, -128, 320, (x, y, z, block) -> {
                    assertTrue(x >= minX && x < minX + 16 && z >= minZ && z < minZ + 16 && y >= -128 && y < 320, "outside its chunk");
                    painted.put(new Block(x, y, z), block);
                });
            }
        }
        return painted;
    }

    private static Map<Block, BlockStateProvider> paintAround(RavineSettings settings, RavineCell cell, Disc d) {
        return paint(settings, cell, d.x() - d.radius(), d.x() + d.radius(), d.z() - d.radius(), d.z() + d.radius());
    }

    @Test
    void aPlatformTakesItsMaterialOverItsWholeFootprintInTheOpenAndInsideTheWall() {
        int sampled = 0;
        int tops = 0;
        int inTheWall = 0;
        RavineCell c = cell(STANDING, 1);
        for (Disc d : Carved.layout(STANDING, BOUNDS, c).discs()) {
            Map<Block, BlockStateProvider> painted = paintAround(STANDING, c, d);
            int topY = (int) Math.ceil(d.floor()) - 1;
            for (int degrees = 0; degrees < 360; degrees += 45) {
                for (double share : new double[] {0.1, 0.45, 0.8}) {
                    int x = (int) Math.floor(d.x() + share * d.radius() * Math.cos(Math.toRadians(degrees)));
                    int z = (int) Math.floor(d.z() + share * d.radius() * Math.sin(Math.toRadians(degrees)));
                    // Another disc's platform or stem may pass through the same block, so any material will do for "painted".
                    assertNotNull(painted.get(new Block(x, topY, z)), "bare top of " + d + " at " + x + "," + z);
                    assertNotNull(painted.get(new Block(x, topY - 2, z)), "bare body of " + d);
                    sampled++;
                    tops += painted.get(new Block(x, topY, z)) == TOP ? 1 : 0;
                    inTheWall += Carved.distance(STANDING, BOUNDS, c, x, topY, z) > 0 ? 1 : 0;
                }
            }
        }
        assertTrue(sampled > 200, "sampled " + sampled);
        assertTrue(tops > sampled * 0.9, tops + " of " + sampled + " top blocks have the top material");
        assertTrue(inTheWall > 20, inTheWall + " of the samples lie in rock the carve never opened");
    }

    @Test
    void aBowledPlatformIsPaintedUpToItsRaisedRim() {
        RavineSettings bowled = painted(ConeShapeTest.LOW, List.of(THEME), DiscTest.SLIM);
        RavineCell c = cell(bowled, 1);
        int sampled = 0;
        int tops = 0;
        int raised = 0;
        for (Disc d : Carved.layout(bowled, BOUNDS, c).discs()) {
            Map<Block, BlockStateProvider> painted = paintAround(bowled, c, d);
            for (int degrees = 0; degrees < 360; degrees += 45) {
                for (double share : new double[] {0.15, 0.5, 0.9}) {
                    int x = (int) Math.floor(d.x() + share * d.radius() * Math.cos(Math.toRadians(degrees)));
                    int z = (int) Math.floor(d.z() + share * d.radius() * Math.sin(Math.toRadians(degrees)));
                    int topY = (int) Math.ceil(d.topAt(Math.hypot(x - d.x(), z - d.z()))) - 1;
                    assertNotNull(painted.get(new Block(x, topY, z)), "bare top of " + d + " at " + x + "," + z);
                    sampled++;
                    tops += painted.get(new Block(x, topY, z)) == TOP ? 1 : 0;
                    raised += topY > (int) Math.ceil(d.floor()) - 1 ? 1 : 0;
                }
            }
        }
        assertTrue(sampled > 100, "sampled " + sampled);
        assertTrue(tops > sampled * 0.85, tops + " of " + sampled + " top blocks follow the bowl with the top material");
        assertTrue(raised > sampled / 4, raised + " of " + sampled + " samples lie above the middle of their disc's top");
    }

    @Test
    void aStemIsPaintedExactlyWhereTheTerrainMadeIt() {
        RavineCell c = cell(STANDING, 2);
        int stems = 0;
        int onAxes = 0;
        for (Disc d : Carved.layout(STANDING, BOUNDS, c).discs()) {
            Map<Block, BlockStateProvider> painted = paintAround(STANDING, c, d);
            for (var entry : painted.entrySet()) {
                Block b = entry.getKey();
                if (entry.getValue() == STEM) {
                    assertTrue(Carved.rock(STANDING, BOUNDS, c, b.x(), b.y(), b.z()) < 0, "painted a stem the terrain does not have at " + b);
                    stems++;
                }
            }
            // Down the middle of this disc's stem, every block the terrain made of stem is painted.
            int x = (int) Math.round(d.x());
            int z = (int) Math.round(d.z());
            for (int y = BOUNDS.floorY() + 1; y < d.floor() - SHAPE.floorThickness(); y += 3) {
                if (d.supportDistance(SHAPE, x, y, z) < 0 && Carved.rock(STANDING, BOUNDS, c, x, y, z) < 0) {
                    assertNotNull(painted.get(new Block(x, y, z)), "bare stem of " + d + " at y=" + y);
                    onAxes++;
                }
            }
        }
        assertTrue(stems > 1000, stems + " stem blocks");
        assertTrue(onAxes > 50, onAxes + " checked along the stems' axes");
    }

    @Test
    void aRootIsPaintedFromItsPlatformUpToItsCeiling() {
        RavineCell c = cell(HANGING, 1);
        int roots = 0;
        int checked = 0;
        for (Disc d : Carved.layout(HANGING, BOUNDS, c).discs()) {
            if (!(d.support() instanceof Disc.Support.Hanging root)) {
                continue;
            }
            Map<Block, BlockStateProvider> painted = paintAround(HANGING, c, d);
            int x = (int) Math.round(d.x());
            int z = (int) Math.round(d.z());
            for (int y = (int) Math.floor(d.floor()) + 1; y < root.anchor(); y++) {
                if (Carved.rock(HANGING, BOUNDS, c, x, y, z) < 0) {
                    assertNotNull(painted.get(new Block(x, y, z)), "bare root of " + d + " at y=" + y);
                    checked++;
                }
            }
            assertTrue(painted.get(new Block(x, (int) Math.floor(d.floor() - SHAPE.floorThickness()) - 2, z)) != STEM
                    || d.supportDistance(SHAPE, x, d.floor() - SHAPE.floorThickness() - 2, z) >= 0, "nothing of its own under a hanging disc");
            roots++;
        }
        assertTrue(roots > 3, roots + " hanging discs");
        assertTrue(checked > 50, checked + " root blocks checked");
    }

    @Test
    void aChunksWaterIsItsShareOfEachDiscsPondsAndStreams() {
        var water = new DiscWater(0.4F, 12, 2, 40, 2, 3);
        var wet = new DiscTheme(Optional.empty(), Optional.empty(), 1, DiscTheme.Ramp.EVEN, DiscTheme.Ramp.EVEN, DiscTheme.Ramp.EVEN, DiscTheme.Limits.NONE,
                PALETTE, Optional.of(water), List.of(), Optional.empty());
        RavineSettings settings = painted(ConeShapeTest.LOW, List.of(wet), DiscTest.SLIM);
        RavineCell cell = cell(settings, 1);
        CellDiscs discs = CellDiscs.of(settings, BOUNDS, cell);
        Disc d = discs.layout().discs().get(0);
        record Column(int x, int surface, int z, int depth) {}
        var found = new ArrayList<Column>();
        for (int chunkX = Math.floorDiv((int) Math.floor(d.x() - d.radius()), 16); chunkX <= Math.floorDiv((int) Math.floor(d.x() + d.radius()), 16); chunkX++) {
            for (int chunkZ = Math.floorDiv((int) Math.floor(d.z() - d.radius()), 16); chunkZ <= Math.floorDiv((int) Math.floor(d.z() + d.radius()), 16); chunkZ++) {
                int minX = chunkX * 16;
                int minZ = chunkZ * 16;
                DiscBlocks.forEachWater(cell, discs, minX, minZ, (x, surface, z, depth) -> {
                    assertTrue(x >= minX && x < minX + 16 && z >= minZ && z < minZ + 16, "outside its chunk");
                    found.add(new Column(x, surface, z, depth));
                });
            }
        }
        // Other discs reach into these chunks too; the first disc's own columns are the ones at its height.
        var expected = new ArrayList<Column>();
        for (int x = (int) Math.floor(d.x() - d.radius()); x <= d.x() + d.radius(); x++) {
            for (int z = (int) Math.floor(d.z() - d.radius()); z <= d.z() + d.radius(); z++) {
                int depth = water.depthAt(d, cell.hash(), 0, x, z);
                if (depth > 0) {
                    int surface = d.topBlockAt(Math.hypot(x - d.x(), z - d.z()));
                    assertTrue(d.platformDistance(settings.discs(), x, surface, z) < 0 && d.platformDistance(settings.discs(), x, surface + 1, z) >= 0,
                            "the water's highest block is the platform's highest");
                    assertTrue(d.platformDistance(settings.discs(), x, surface - depth, z) < 0, "and the platform is its bed");
                    expected.add(new Column(x, surface, z, depth));
                }
            }
        }
        assertTrue(expected.size() > 100, expected.size() + " columns of water on the disc");
        assertTrue(found.containsAll(expected), "every column of the disc's water is reported by the chunk it is in");
        var none = new ArrayList<Column>();
        DiscBlocks.forEachWater(cell(STANDING, 0), CellDiscs.of(STANDING, BOUNDS, cell(STANDING, 0)), (int) d.x() & ~15, (int) d.z() & ~15, (x, surface, z, depth) -> none.add(new Column(x, surface, z, depth)));
        assertTrue(none.isEmpty(), "a theme without water has none");
    }

    @Test
    void paintingAChunkTwiceGivesTheSameBlocksInTheSameOrder() {
        RavineCell c = cell(STANDING, 3);
        Disc d = Carved.layout(STANDING, BOUNDS, c).discs().get(0);
        int minX = Math.floorDiv((int) Math.floor(d.x()), 16) * 16;
        int minZ = Math.floorDiv((int) Math.floor(d.z()), 16) * 16;
        var first = new ArrayList<Block>();
        var second = new ArrayList<Block>();
        DiscBlocks.forEach(STANDING, BOUNDS, c, CellDiscs.of(STANDING, BOUNDS, c), minX, minZ, -128, 320, (x, y, z, block) -> first.add(new Block(x, y, z)));
        DiscBlocks.forEach(STANDING, BOUNDS, c, CellDiscs.of(STANDING, BOUNDS, c), minX, minZ, -128, 320, (x, y, z, block) -> second.add(new Block(x, y, z)));
        assertTrue(first.size() > 100, first.size() + " blocks");
        assertEquals(first, second);
    }

    @Test
    void aDiscKeepsTheThemeItsCellGivesIt() {
        DiscTheme other = themed(new DiscPalette(List.of(), List.of(), Optional.of(STEM), DiscPalette.Stem.UNPAINTED));
        RavineSettings two = painted(ConeShapeTest.CONE, List.of(THEME, other));
        RavineCell c = cell(two, 1);
        List<Optional<DiscTheme>> themes = CellDiscs.of(two, BOUNDS, c).themes();
        assertEquals(themes, CellDiscs.of(two, BOUNDS, c).themes(), "the same every time the cell's discs are built");
        long firsts = themes.stream().filter(theme -> theme.orElseThrow() == THEME).count();
        assertTrue(firsts > 0 && firsts < themes.size(), firsts + " of " + themes.size() + " discs got the first of two equal themes");
    }

    @Test
    void aCarveWithoutThemesOrWithoutALevelPaintsNothing() {
        RavineCell c = cell(STANDING, 1);
        Disc d = Carved.layout(STANDING, BOUNDS, c).discs().get(0);
        int minX = Math.floorDiv((int) Math.floor(d.x()), 16) * 16;
        int minZ = Math.floorDiv((int) Math.floor(d.z()), 16) * 16;
        assertTrue(count(carve(STANDING).bind(7L, BOUNDS), minX, minZ) > 100, "a bound carve with a theme paints");
        assertEquals(0, count(carve(STANDING), minX, minZ), "not bound to a level yet");
        assertEquals(0, count(carve(painted(ConeShapeTest.CONE, List.of())).bind(7L, BOUNDS), minX, minZ), "no themes");
    }

    // The codec is the only way to make a carve, as in the game.
    private static RavineCarve carve(RavineSettings settings) {
        var json = RavineSettings.MAP_CODEC.codec().encodeStart(JsonOps.INSTANCE, settings).getOrThrow();
        return RavineCarve.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    private static int count(RavineCarve carve, int minX, int minZ) {
        int[] blocks = {0};
        carve.forEachDiscBlock(minX, minZ, -128, 320, (x, y, z, block) -> blocks[0]++);
        return blocks[0];
    }
}
