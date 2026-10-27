package dev.syrval.verdantdeep.basilisk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.syrval.verdantdeep.basilisk.entity.BasiliskSegmentChain;

import org.junit.jupiter.api.Test;

class ChainDroopTest {
    @Test
    void tailPastLedgeHangsDownWithSpacingKept() {
        BasiliskSegmentChain c = new BasiliskSegmentChain(10);
        // Head on a ledge (ground y=0 for x <= 0.5), void beyond; the body trails off the edge toward +x (yaw 90).
        BasiliskSegmentChain.GroundSampler s = (x, y, z) -> x <= 0.5D ? 0.0D : Double.NaN;
        c.reset(0.0D, 0.0D, 0.0D, 90.0F);
        for (int t = 0; t < 100; t++) c.update(0.0D, 0.0D, 0.0D, 90.0F, 0.0F, s);
        int last = c.count - 1;
        assertTrue(c.y[last] < -3.0D, "tail should hang below the ledge, was " + c.y[last]);
        for (int i = 1; i < c.count; i++) {
            double d = Math.sqrt(Math.pow(c.x[i] - c.x[i - 1], 2) + Math.pow(c.y[i] - c.y[i - 1], 2) + Math.pow(c.z[i] - c.z[i - 1], 2));
            assertEquals(c.spacing(), d, 1.0E-6D);
        }
    }
}
