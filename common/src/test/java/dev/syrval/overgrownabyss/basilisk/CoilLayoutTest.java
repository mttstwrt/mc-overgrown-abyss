package dev.syrval.overgrownabyss.basilisk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.syrval.overgrownabyss.basilisk.entity.BasiliskSegmentChain;
import org.junit.jupiter.api.Test;

class CoilLayoutTest {
    @Test
    void spiralKeepsSpacingAndDoesNotOverlapItself() {
        for (float yaw : new float[] {0.0F, 77.0F, -200.0F}) {
            double[][] p = BasiliskSegmentChain.coilLayout(10, yaw);
            for (int i = 0; i < p.length; i++) {
                assertTrue(Math.hypot(p[i][0], p[i][1]) >= 0.8D, "segment " + i + " too close to the head");
                for (int j = 0; j < i; j++) {
                    double d = Math.hypot(p[i][0] - p[j][0], p[i][1] - p[j][1]);
                    if (i - j == 1) assertEquals(BasiliskSegmentChain.SPACING, d, 1.0E-6D);
                    else assertTrue(d >= 0.8D, "segments " + j + " and " + i + " overlap: " + d);
                }
            }
        }
    }
}
