package dev.syrval.overgrownabyss.ravine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import org.junit.jupiter.api.Test;

/** Where a hole gives off gas: on the surfaces its themes name, at its falls and springs, and over its cavern's floor. */
class HoleGasTest {
    static {
        MinecraftBootstrap.init();
    }

    private static final ResourceLocation MIST = ResourceLocation.fromNamespaceAndPath("overgrown_abyss", "mist");
    private static final ResourceLocation EMBER = ResourceLocation.fromNamespaceAndPath("overgrown_abyss", "ember");

    private record Column(int x, int y, int z) {}

    /** The mod's own hole with one theme on every disc, which gives off the gas given and grows nothing; the hole itself has none. */
    private static RavineSettings gassing(DiscTheme.Gas... gas) throws Exception {
        RavineSettings base = DiscThemeTest.shipped();
        var theme = new DiscTheme(Optional.empty(), Optional.empty(), 1, DiscThemeTest.EVEN, DiscThemeTest.EVEN, DiscThemeTest.EVEN, DiscTheme.Limits.NONE,
                DiscPalette.UNPAINTED, Optional.empty(), List.of(), DiscTheme.Wildness.EVEN, DiscRelief.FLAT, Optional.empty(), Optional.empty(), Optional.empty(),
                List.of(gas));
        ConeDressing dressing = base.dressing();
        return new RavineSettings(base.salt(), base.cellSize(), 1F, base.sizeBias(), VerticalAnchor.absolute(-40), VerticalAnchor.absolute(80),
                base.cavernRadius(), base.cavernHeight(), base.edgeFalloff(), base.discs(), List.of(theme), base.environment(), base.wallNoise(), base.ravine(),
                base.cone(), new ConeDressing(dressing.roots(), dressing.falls(), dressing.curse(), Optional.empty()));
    }

    private static DiscTheme.Gas mist(DiscTheme.Surface on, int every, GasShape shape) {
        return new DiscTheme.Gas(MIST, every, on, Optional.empty(), Optional.empty(), shape, 0.5F);
    }

    private static HoleGas gasOf(RavineSettings settings, RavineBounds bounds, int cellZ) {
        RavineCell cell = RavineCells.at(11L, settings, 0, cellZ).orElseThrow();
        return HoleGas.of(settings, bounds, cell, CellDiscs.of(settings, bounds, cell));
    }

    @Test
    void aHoleGivesOffTheSameGasEveryTime() throws Exception {
        RavineSettings settings = DiscThemeTest.shipped();
        HoleGas first = gasOf(settings, DiscThemeTest.LEVEL, 0);
        assertTrue(first.sources().size() > 100, "a hole with " + first.sources().size() + " sources");
        assertEquals(first, gasOf(settings, DiscThemeTest.LEVEL, 0));
    }

    @Test
    void gasOnTopRestsOnTheGroundOfADisc() throws Exception {
        RavineSettings settings = gassing(mist(DiscTheme.Surface.TOP, 40, new GasShape.Cylinder(2, 1)));
        RavineCell cell = RavineCells.at(11L, settings, 0, 0).orElseThrow();
        CellDiscs discs = CellDiscs.of(settings, DiscThemeTest.VANILLA, cell);
        List<GasSource> sources = HoleGas.of(settings, DiscThemeTest.VANILLA, cell, discs).sources();
        assertTrue(sources.size() > 50, "only " + sources.size() + " sources on top");
        for (GasSource source : sources) {
            int x = (int) Math.floor(source.x());
            int z = (int) Math.floor(source.z());
            boolean onAGround = false;
            for (int i = 0; i < discs.layout().discs().size(); i++) {
                Disc disc = discs.layout().discs().get(i);
                onAGround |= Math.hypot(x - disc.x(), z - disc.z()) <= disc.radius() && source.y() == discs.grounds().get(i).topBlockAt(x, z) + 1;
            }
            assertTrue(onAGround, () -> "gas at " + source + " is on no disc's ground");
        }
    }

    @Test
    void gasUnderADiscHangsFromItsRock() throws Exception {
        RavineSettings settings = gassing(mist(DiscTheme.Surface.UNDERSIDE, 40, new GasShape.Cylinder(2, 3)));
        RavineCell cell = RavineCells.at(11L, settings, 0, 0).orElseThrow();
        CellDiscs discs = CellDiscs.of(settings, DiscThemeTest.VANILLA, cell);
        List<GasSource> sources = HoleGas.of(settings, DiscThemeTest.VANILLA, cell, discs).sources();
        assertTrue(sources.size() > 20, "only " + sources.size() + " sources under the discs");
        for (GasSource source : sources) {
            int x = (int) Math.floor(source.x());
            int z = (int) Math.floor(source.z());
            boolean hanging = false;
            for (Disc disc : discs.layout().discs()) {
                OptionalInt open = disc.hangBlockAt(settings.discs(), Math.hypot(x - disc.x(), z - disc.z()));
                // The cylinder's top is the underside of the rock, the top of the open block under it.
                hanging |= open.isPresent() && source.y() + 3 == open.getAsInt() + 1;
            }
            assertTrue(hanging, () -> "gas at " + source + " hangs from no disc");
        }
    }

    @Test
    void gasOnTheRimIsJustOutsideThePlatform() throws Exception {
        RavineSettings settings = gassing(mist(DiscTheme.Surface.RIM, 4, new GasShape.Sphere(1)));
        RavineCell cell = RavineCells.at(11L, settings, 0, 0).orElseThrow();
        CellDiscs discs = CellDiscs.of(settings, DiscThemeTest.VANILLA, cell);
        List<GasSource> sources = HoleGas.of(settings, DiscThemeTest.VANILLA, cell, discs).sources();
        assertTrue(sources.size() > 20, "only " + sources.size() + " sources on the rims");
        for (GasSource source : sources) {
            int x = (int) Math.floor(source.x());
            int z = (int) Math.floor(source.z());
            boolean atARim = false;
            for (Disc disc : discs.layout().discs()) {
                double fromAxis = Math.hypot(x - disc.x(), z - disc.z());
                atARim |= fromAxis >= disc.radius() && fromAxis <= disc.radius() + 1.5;
            }
            assertTrue(atARim, () -> "gas at " + source + " is at no disc's rim");
        }
    }

    @Test
    void emberRisesOnlyOverTheLavaOfTheModsOwnLavaDiscs() throws Exception {
        RavineSettings settings = DiscThemeTest.shipped();
        int checked = 0;
        for (int cz = 0; cz < 6; cz++) {
            RavineCell cell = RavineCells.at(11L, settings, 0, cz).orElseThrow();
            CellDiscs discs = CellDiscs.of(settings, DiscThemeTest.LEVEL, cell);
            Set<Column> lava = new HashSet<>();
            Set<Column> chunks = new HashSet<>();
            for (Disc disc : discs.layout().discs()) {
                for (int x = Math.floorDiv((int) Math.floor(disc.x() - disc.radius()), 16); x <= Math.floorDiv((int) Math.floor(disc.x() + disc.radius()), 16); x++) {
                    for (int z = Math.floorDiv((int) Math.floor(disc.z() - disc.radius()), 16); z <= Math.floorDiv((int) Math.floor(disc.z() + disc.radius()), 16); z++) {
                        chunks.add(new Column(x, 0, z));
                    }
                }
            }
            for (Column chunk : chunks) {
                DiscBlocks.forEachWater(cell, discs, chunk.x() * 16, chunk.z() * 16, (x, surface, z, depth, fluid) -> {
                    if (fluid == DiscWater.Fluid.LAVA) {
                        lava.add(new Column(x, surface, z));
                    }
                });
            }
            for (GasSource source : HoleGas.of(settings, DiscThemeTest.LEVEL, cell, discs).sources()) {
                if (source.medium().equals(EMBER) && source.shape() instanceof GasShape.Cylinder) {
                    var under = new Column((int) Math.floor(source.x()), (int) source.y() - 1, (int) Math.floor(source.z()));
                    assertTrue(lava.contains(under), () -> "ember at " + source + " with no lava under it");
                    checked++;
                }
            }
        }
        assertTrue(checked > 0, "no lava disc gave off ember");
    }

    @Test
    void theModsOwnFallsSprayWhereTheirWaterLandsAndAlongTheirDrop() throws Exception {
        RavineSettings settings = DiscThemeTest.shipped();
        int falls = 0;
        for (int cz = 0; cz < 6; cz++) {
            RavineCell cell = RavineCells.at(11L, settings, 0, cz).orElseThrow();
            CellDiscs discs = CellDiscs.of(settings, DiscThemeTest.LEVEL, cell);
            List<GasSource> sources = HoleGas.of(settings, DiscThemeTest.LEVEL, cell, discs).sources();
            for (DiscFalls.Fall fall : discs.falls().falls()) {
                falls++;
                double landing = fall.level() - fall.drop();
                assertTrue(sources.contains(new GasSource(MIST, new GasShape.Sphere(3), fall.x() + 0.5, landing + 3, fall.z() + 0.5, 1.5F)),
                        () -> "no spray where " + fall + " lands");
                assertTrue(sources.contains(new GasSource(MIST, new GasShape.Cylinder(fall.width() / 2F + 1.5F, fall.drop()), fall.x() + 0.5, landing, fall.z() + 0.5, 0.25F)),
                        () -> "no curtain along " + fall);
            }
        }
        assertTrue(falls > 0, "no falls to look at");
    }

    @Test
    void theModsOwnCavernGasLiesOnItsFloorInsideIt() throws Exception {
        RavineSettings settings = DiscThemeTest.shipped();
        RavineCell cell = RavineCells.at(11L, settings, 0, 0).orElseThrow();
        GasShape floorMist = new GasShape.Cylinder(4, 1);
        int found = 0;
        for (GasSource source : gasOf(settings, DiscThemeTest.LEVEL, 0).sources()) {
            if (source.shape().equals(floorMist)) {
                found++;
                assertEquals(DiscThemeTest.LEVEL.floorY(), source.y(), () -> "cavern gas off the floor: " + source);
                assertTrue(Math.hypot(source.x() - cell.centreX(), source.z() - cell.centreZ()) <= settings.cavernRadius(), () -> "cavern gas outside it: " + source);
            }
        }
        // One for every 300 blocks of a floor 136 blocks across, kept a little in from its wall.
        assertTrue(found > 120 && found < 260, "cavern gas: " + found);
    }

    @Test
    void hazeHangsInTheHolesOpenAirFromItsFloorToItsTop() throws Exception {
        RavineSettings settings = DiscThemeTest.shipped();
        GasSettings.Haze haze = settings.dressing().gas().flatMap(GasSettings::haze).orElseThrow();
        RavineBounds bounds = DiscThemeTest.LEVEL;
        RavineCell cell = RavineCells.at(11L, settings, 0, 0).orElseThrow();
        CellDiscs discs = CellDiscs.of(settings, bounds, cell);
        List<GasSource> sources = HoleGas.of(settings, bounds, cell, discs).sources().stream()
                .filter(source -> source.shape().equals(haze.shape()) && source.rate() == haze.rate()).toList();
        double lowest = Double.MAX_VALUE;
        double highest = -Double.MAX_VALUE;
        for (GasSource source : sources) {
            assertTrue(RavineShape.signedDistance(settings, bounds, cell, discs.layout(), source.x(), source.y(), source.z()) < 0,
                    () -> "haze in the ground at " + source);
            assertTrue(RavineShape.rockDistance(settings, bounds, cell, discs.layout(), source.x(), source.y(), source.z()) > 0,
                    () -> "haze in a disc at " + source);
            lowest = Math.min(lowest, source.y());
            highest = Math.max(highest, source.y());
        }
        assertTrue(lowest < bounds.floorY() + haze.layerSpacing(), "lowest haze at " + lowest);
        assertTrue(highest > bounds.topY() - 2 * haze.layerSpacing() && highest < bounds.topY(), "highest haze at " + highest);
        // About one in each box of the lattice the hole's air fills: a cone 45 blocks across at its top and 150 at its floor.
        double boxes = 0;
        for (double y = bounds.floorY(); y < bounds.topY(); y++) {
            double radius = ConeShape.radiusAt(settings, settings.cone().orElseThrow(), bounds, y);
            boxes += Math.PI * radius * radius / (haze.spacing() * haze.spacing() * haze.layerSpacing());
        }
        assertTrue(sources.size() > boxes * 0.8 && sources.size() < boxes * 1.6, sources.size() + " haze sources for " + Math.round(boxes) + " boxes of the cone");
    }

    @Test
    void aHoleSendsNoMoreThanItsCap() throws Exception {
        HoleGas gas = gasOf(gassing(mist(DiscTheme.Surface.TOP, 1, new GasShape.Cylinder(1, 1))), DiscThemeTest.VANILLA, 0);
        assertEquals(HoleGas.MAX_SOURCES, gas.sources().size());
        assertTrue(gas.dropped() > 0, "nothing dropped");
    }

    @Test
    void aHoleWithoutGasHasNone() throws Exception {
        assertEquals(HoleGas.NONE, gasOf(gassing(), DiscThemeTest.VANILLA, 0));
    }
}
