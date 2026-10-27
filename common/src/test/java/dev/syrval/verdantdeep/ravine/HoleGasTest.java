package dev.syrval.verdantdeep.ravine;

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

    private static final ResourceLocation MIST = ResourceLocation.fromNamespaceAndPath("verdant_deep", "mist");
    private static final ResourceLocation EMBER = ResourceLocation.fromNamespaceAndPath("verdant_deep", "ember");
    private static final ResourceLocation SPRAY = ResourceLocation.fromNamespaceAndPath("verdant_deep", "spray");

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
    void aHoleSendsNoMoreThanItsCap() throws Exception {
        HoleGas gas = gasOf(gassing(mist(DiscTheme.Surface.TOP, 1, new GasShape.Cylinder(1, 1))), DiscThemeTest.VANILLA, 0);
        assertEquals(HoleGas.MAX_SOURCES, gas.sources().size());
        assertTrue(gas.dropped() > 0, "nothing dropped");
    }

}
