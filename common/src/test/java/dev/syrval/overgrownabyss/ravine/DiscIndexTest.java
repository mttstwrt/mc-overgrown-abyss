package dev.syrval.overgrownabyss.ravine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** The discs near each column of a hole: none that matters left out, and the carve the same as over every disc. */
class DiscIndexTest {
    private static final RavineSettings SETTINGS = ConeRimTest.SETTINGS;
    private static final RavineBounds BOUNDS = new RavineBounds(-40, 200);
    private static final double MARGIN = SETTINGS.edgeFalloff();

    private static RavineCell cell(int cz) {
        return RavineCells.at(7L, SETTINGS, 5, cz).orElseThrow();
    }

    private static ConeDiscLayout layout(RavineCell c) {
        return new ConeDiscLayout(SETTINGS, ConeRimTest.CONE, BOUNDS, c);
    }

    @Test
    void aColumnIsGivenEveryDiscWithinTheMarginOfItInTheLayoutsOrder() {
        int fewer = 0;
        int columns = 0;
        for (int cz = 0; cz < 3; cz++) {
            RavineCell c = cell(cz);
            List<Disc> discs = layout(c).discs();
            assertTrue(discs.size() > 60, discs.size() + " discs");
            DiscIndex index = new DiscIndex(discs, MARGIN);
            for (double x = c.centreX() - 230; x <= c.centreX() + 230; x += 3.7) {
                for (double z = c.centreZ() - 230; z <= c.centreZ() + 230; z += 3.7) {
                    List<Disc> near = index.near(x, z);
                    int next = 0;
                    for (Disc disc : discs) {
                        double apart = Math.hypot(x - disc.x(), z - disc.z());
                        boolean listed = next < near.size() && near.get(next).equals(disc);
                        next += listed ? 1 : 0;
                        assertTrue(listed || apart > disc.radius() + MARGIN, disc + " is left out " + apart + " from its axis");
                        // A square of columns is 16 blocks along, so a listed disc is no further off than its corner.
                        assertTrue(!listed || apart <= disc.radius() + MARGIN + 16 * Math.sqrt(2), disc + " is listed " + apart + " from its axis");
                    }
                    assertEquals(near.size(), next, "listed in the layout's order, each once");
                    fewer += near.size() < discs.size() / 4 ? 1 : 0;
                    columns++;
                }
            }
            assertEquals(List.of(), index.near(c.centreX() + 5000, c.centreZ()));
            assertEquals(List.of(), index.near(c.centreX(), c.centreZ() - 5000));
        }
        assertTrue(fewer > columns * 0.9, fewer + " of " + columns + " columns look at under a quarter of the discs");
        assertEquals(List.of(), new DiscIndex(List.of(), MARGIN).near(0, 0), "no discs, none near");
    }

    @Test
    void theCarveIsTheSameAsOverEveryDisc() {
        Random random = new Random(11);
        int inDomes = 0;
        int inRock = 0;
        for (int cz = 0; cz < 3; cz++) {
            RavineCell c = cell(cz);
            ConeDiscLayout indexed = layout(c);
            DiscLayout every = indexed::discs;
            for (int i = 0; i < 60_000; i++) {
                double x = Math.floor(c.centreX() + (random.nextDouble() * 2 - 1) * SETTINGS.maxReach());
                double z = Math.floor(c.centreZ() + (random.nextDouble() * 2 - 1) * SETTINGS.maxReach());
                double y = Math.floor(BOUNDS.floorY() + random.nextDouble() * (BOUNDS.topY() - BOUNDS.floorY()));
                // Further off than the falloff the carve is the same whatever the distance, and that far the index may leave a disc out.
                double dome = Math.min(MARGIN, Discs.domeDistance(every, x, y, z));
                double rock = Math.min(MARGIN, Discs.rockDistance(every, SETTINGS.discs(), x, y, z));
                assertEquals(dome, Math.min(MARGIN, Discs.domeDistance(indexed, x, y, z)), 0, "the domes at " + x + ", " + y + ", " + z);
                assertEquals(rock, Math.min(MARGIN, Discs.rockDistance(indexed, SETTINGS.discs(), x, y, z)), 0, "the rock at " + x + ", " + y + ", " + z);
                inDomes += dome < 0 ? 1 : 0;
                inRock += rock < 0 ? 1 : 0;
            }
        }
        assertTrue(inDomes > 10_000 && inRock > 1_000, inDomes + " points in domes and " + inRock + " in rock");
    }
}
