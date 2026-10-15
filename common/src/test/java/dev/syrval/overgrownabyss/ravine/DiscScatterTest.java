package dev.syrval.overgrownabyss.ravine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import org.junit.jupiter.api.Test;

/** The scatter that growth and gas share picks for growth exactly what growth picked before it was shared: worldgen does not move. */
class DiscScatterTest {
    static {
        MinecraftBootstrap.init();
    }

    private static final int GROWTH_HASH_BASE = 300_000;
    private static final int MAX_GROWTHS = 64;
    private static final double EDGE_CLEAR = 1.5;

    private record Pick(int x, int y, int z, ResourceKey<ConfiguredFeature<?, ?>> feature, DiscTheme.Surface on) {}

    @Test
    void growthPicksTheSamePlacesAsTheLoopItReplaced() throws Exception {
        RavineSettings settings = DiscThemeTest.shipped();
        int compared = 0;
        for (int cz = 0; cz < 4; cz++) {
            RavineCell cell = RavineCells.at(11L, settings, 0, cz).orElseThrow();
            CellDiscs discs = CellDiscs.of(settings, DiscThemeTest.LEVEL, cell);
            for (Disc disc : discs.layout().discs()) {
                double reach = disc.radius() + 1;
                for (int chunkX = Math.floorDiv((int) Math.floor(disc.x() - reach), 16); chunkX <= Math.floorDiv((int) Math.floor(disc.x() + reach), 16); chunkX++) {
                    for (int chunkZ = Math.floorDiv((int) Math.floor(disc.z() - reach), 16); chunkZ <= Math.floorDiv((int) Math.floor(disc.z() + reach), 16); chunkZ++) {
                        var now = new ArrayList<Pick>();
                        DiscGrowth.forEach(settings, cell, discs, chunkX * 16, chunkZ * 16, (x, y, z, feature, on) -> now.add(new Pick(x, y, z, feature, on)));
                        List<Pick> before = before(settings, cell, discs, chunkX * 16, chunkZ * 16);
                        assertEquals(before, now, "chunk " + chunkX + " " + chunkZ + " of cell " + cz);
                        compared += before.size();
                    }
                }
            }
        }
        assertTrue(compared > 1000, "too few places to tell anything: " + compared);
    }

    // DiscGrowth.forEach as it was before its draw was shared with gas, kept word for word.
    private static List<Pick> before(RavineSettings settings, RavineCell cell, CellDiscs discs, int minX, int minZ) {
        var picks = new ArrayList<Pick>();
        List<Disc> all = discs.layout().discs();
        DiscShape shape = settings.discs();
        var lava = new DiscLava(discs, minX, minZ);
        for (int i = 0; i < all.size(); i++) {
            Optional<DiscTheme> theme = discs.themes().get(i);
            if (theme.isEmpty() || theme.get().growth().isEmpty()) {
                continue;
            }
            Disc disc = all.get(i);
            List<DiscTheme.Growth> growth = theme.get().growth();
            double wildness = theme.get().wildness().factorFor(cell.hash(), i);
            DiscGround ground = discs.grounds().get(i);
            for (int g = 0; g < Math.min(growth.size(), MAX_GROWTHS); g++) {
                DiscTheme.Growth each = growth.get(g);
                double reach = disc.radius() + (each.on() == DiscTheme.Surface.RIM ? 1 : 0);
                int fromX = Math.max(minX, (int) Math.ceil(disc.x() - reach));
                int toX = Math.min(minX + 15, (int) Math.floor(disc.x() + reach));
                int fromZ = Math.max(minZ, (int) Math.ceil(disc.z() - reach));
                int toZ = Math.min(minZ + 15, (int) Math.floor(disc.z() + reach));
                for (int x = fromX; x <= toX; x++) {
                    for (int z = fromZ; z <= toZ; z++) {
                        double fromAxis = Math.hypot(x - disc.x(), z - disc.z());
                        if (each.on() != DiscTheme.Surface.RIM && fromAxis > disc.radius() - EDGE_CLEAR) {
                            continue;
                        }
                        double draw = RavineCells.unitAt(cell.hash(), GROWTH_HASH_BASE + i * MAX_GROWTHS + g, x, z) * each.every();
                        if (draw >= wildness * each.weightAt(cell.hash(), i, disc, x, z)) {
                            continue;
                        }
                        OptionalInt y = DiscGrowth.placeOn(each.on(), theme.get(), disc, ground, shape, lava, cell.hash(), i, x, z, fromAxis);
                        if (y.isPresent() && !(each.on() != DiscTheme.Surface.UNDERSIDE && discs.falls().isNear(x, y.getAsInt(), z))) {
                            picks.add(new Pick(x, y.getAsInt(), z, each.feature(), each.on()));
                        }
                    }
                }
            }
        }
        return picks;
    }
}
