package dev.syrval.overgrownabyss.ravine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LandGateTest {
    // Along the x axis: ends at x = -150 and x = 150.
    private static final RavineCell CELL = new RavineCell(0, 0, 1, 0, 150, 50);

    @Test
    void samplePointsRunFromEndToEndAlongTheCentreLine() {
        assertEquals(
                java.util.List.of(new RavineCell.Column(-150, 0), new RavineCell.Column(-75, 0), new RavineCell.Column(0, 0),
                        new RavineCell.Column(75, 0), new RavineCell.Column(150, 0)),
                CELL.samplePoints());
    }

    @Test
    void allowsEverywhereUntilACheckIsInstalled() {
        assertTrue(new LandGate().allows(CELL));
    }

    @Test
    void oneWetSamplePointRejectsTheWholeRavine() {
        LandGate gate = new LandGate();
        gate.use((x, z) -> x < 140);
        assertFalse(gate.allows(CELL));
        gate.use((x, z) -> true);
        assertTrue(gate.allows(CELL));
    }

    @Test
    void asksOncePerCell() {
        AtomicInteger asked = new AtomicInteger();
        LandGate gate = new LandGate();
        gate.use((x, z) -> {
            asked.incrementAndGet();
            return true;
        });
        gate.allows(CELL);
        gate.allows(CELL);
        assertEquals(5, asked.get());
    }
}
