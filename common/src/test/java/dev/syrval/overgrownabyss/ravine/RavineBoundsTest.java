package dev.syrval.overgrownabyss.ravine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RavineBoundsTest {
    @Test
    void acceptsAnyFloorBelowTheTop() {
        assertEquals(new RavineBounds(-104, 80), RavineBounds.of(-104, 80).orElseThrow());
    }

    @Test
    void rejectsATopAtOrBelowTheFloor() {
        assertTrue(RavineBounds.of(80, 80).isEmpty());
        assertTrue(RavineBounds.of(90, 80).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new RavineBounds(80, 80));
    }
}
