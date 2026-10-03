package dev.syrval.overgrownabyss.ravine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.minecraft.util.InclusiveRange;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import org.junit.jupiter.api.Test;

class RavineCellsTest {
    static {
        MinecraftBootstrap.init();
    }

    static final RavineSettings SETTINGS = new RavineSettings(
            42L, 2048, 0.5F, new InclusiveRange<>(240, 400), new InclusiveRange<>(70, 110),
            VerticalAnchor.absolute(-40), VerticalAnchor.absolute(80), 0.35F, 128, 48, 12, 0.85F, 0.03F, 10, 6);

    @Test
    void sameInputsGiveSameCell() {
        assertEquals(RavineCells.at(1234L, SETTINGS, 3, -7), RavineCells.at(1234L, SETTINGS, 3, -7));
    }

    @Test
    void seedAndSaltChangeTheLayout() {
        RavineSettings otherSalt = new RavineSettings(
                43L, 2048, 1F, SETTINGS.length(), SETTINGS.width(), VerticalAnchor.absolute(-40), VerticalAnchor.absolute(80), 0.35F, 128, 48, 12, 0.85F, 0.03F, 10, 6);
        RavineSettings always = new RavineSettings(
                42L, 2048, 1F, SETTINGS.length(), SETTINGS.width(), VerticalAnchor.absolute(-40), VerticalAnchor.absolute(80), 0.35F, 128, 48, 12, 0.85F, 0.03F, 10, 6);
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
                assertTrue(c.halfLength() * 2 >= 240 && c.halfLength() * 2 <= 400);
                assertTrue(c.halfWidth() * 2 >= 70 && c.halfWidth() * 2 <= 110);
            }
        }
    }

    @Test
    void containingMapsNegativeCoordinatesToTheRightCell() {
        assertEquals(RavineCells.at(5L, SETTINGS, -1, -1), RavineCells.containing(5L, SETTINGS, -0.5, -2047));
        assertEquals(RavineCells.at(5L, SETTINGS, -2, 0), RavineCells.containing(5L, SETTINGS, -2049, 0));
    }
}
