package dev.syrval.overgrownabyss.ravine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RavineBoundsTest {
    @Test
    void acceptsAnyFloorBelowTheTop() {
        assertEquals(new RavineBounds(-104, 80), RavineBounds.of(-104, 80).orElseThrow());
    }

    @Test
    void aMouthWithoutAnEdgeIsLevelAtTheTopAndAnEdgeIsNowhereBelowIt() {
        RavineBounds level = new RavineBounds(-40, 100);
        assertEquals(100, level.edgeAt(1, 0), 1e-9);
        assertEquals(100, level.edgeAt(-3, 2), 1e-9);
        assertEquals(100, level.highestEdge());
        // Four sides: east, south, west and north.
        RavineBounds sloping = new RavineBounds(-40, 100, List.of(120, 110, 100, 110));
        assertEquals(120, sloping.edgeAt(5, 0), 1e-9);
        assertEquals(110, sloping.edgeAt(0, 5), 1e-9, "the second is a quarter turn on, towards +z");
        assertEquals(100, sloping.edgeAt(-5, 0), 1e-9);
        assertEquals(115, sloping.edgeAt(5, 5), 1e-9, "blended half way between two");
        assertEquals(115, sloping.edgeAt(5, -5), 1e-9, "and between the last and the first");
        assertEquals(120, sloping.highestEdge());
        assertThrows(IllegalArgumentException.class, () -> new RavineBounds(-40, 100, List.of(120, 99)));
        assertTrue(RavineBounds.of(80, 80, List.of(90)).isEmpty());
    }

    @Test
    void rejectsATopAtOrBelowTheFloor() {
        assertTrue(RavineBounds.of(80, 80).isEmpty());
        assertTrue(RavineBounds.of(90, 80).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new RavineBounds(80, 80));
    }
}
