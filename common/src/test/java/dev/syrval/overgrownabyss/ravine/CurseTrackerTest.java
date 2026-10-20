package dev.syrval.overgrownabyss.ravine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class CurseTrackerTest {
    private static final CurseSettings SETTINGS = new CurseSettings(0.5, 0.25, List.of(10.0, 20.0), 100, 4, 0);

    private static CurseTracker.Step step(OptionalDouble adjusted, double y) {
        return CurseTracker.step(adjusted, y, OptionalDouble.of(0), SETTINGS);
    }

    @Test
    void outsideTheHoleHasNothingAndKeepsNoDepth() {
        var step = CurseTracker.step(OptionalDouble.of(50), 10, OptionalDouble.empty(), SETTINGS);
        assertTrue(step.adjusted().isEmpty());
        assertEquals(CurseTracker.NONE, step.amplifier());
    }

    @Test
    void comingInIsAdjustedToWhereThePlayerStands() {
        var step = step(OptionalDouble.empty(), 40);
        assertEquals(40, step.adjusted().getAsDouble());
        assertEquals(CurseTracker.NONE, step.amplifier());
    }

    @Test
    void travelAtTheRateNeverOpensAGap() {
        double adjusted = 100;
        for (int tick = 0; tick < 200; tick++) {
            double y = 100 - 0.5 * (tick + 1);
            var step = step(OptionalDouble.of(adjusted), y);
            assertEquals(CurseTracker.NONE, step.amplifier());
            adjusted = step.adjusted().getAsDouble();
        }
        assertEquals(0, adjusted, 1e-9);
    }

    @Test
    void goingUpAndDownUseTheirOwnRates() {
        assertEquals(99.5, step(OptionalDouble.of(100), 0).adjusted().getAsDouble());
        assertEquals(100.25, step(OptionalDouble.of(100), 200).adjusted().getAsDouble());
    }

    @Test
    void aGapPassesThroughTheLevels() {
        assertEquals(CurseTracker.NONE, step(OptionalDouble.of(100), 91).amplifier());
        assertEquals(0, step(OptionalDouble.of(100), 89).amplifier());
        assertEquals(1, step(OptionalDouble.of(100), 79).amplifier());
    }

    @Test
    void aTeleportOpensAGapAndWaitingClosesIt() {
        var step = step(OptionalDouble.of(100), 40);
        assertEquals(1, step.amplifier());
        double adjusted = step.adjusted().getAsDouble();
        int ticks = 0;
        while (step(OptionalDouble.of(adjusted), 40).amplifier() != CurseTracker.NONE) {
            adjusted = step(OptionalDouble.of(adjusted), 40).adjusted().getAsDouble();
            ticks++;
        }
        assertTrue(ticks > 0 && ticks < 200);
        assertTrue(Math.abs(adjusted - 40) < 11);
    }

    @Test
    void strictnessNarrowsTheGapsTowardTheFloor() {
        var strict = new CurseSettings(0.5, 0.5, List.of(10.0), 100, 4, 0.5);
        assertEquals(CurseTracker.NONE, CurseTracker.step(OptionalDouble.of(100), 90.5, OptionalDouble.of(0), strict).amplifier());
        assertEquals(0, CurseTracker.step(OptionalDouble.of(100), 94.5, OptionalDouble.of(1), strict).amplifier());
    }

}
