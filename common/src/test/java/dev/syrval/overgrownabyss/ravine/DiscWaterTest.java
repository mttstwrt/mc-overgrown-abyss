package dev.syrval.overgrownabyss.ravine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Ponds and streams in a disc's top: how much, how deep, and that the water cannot run off. */
class DiscWaterTest {
    static final Disc FLAT = new Disc(0.5, 0.5, 20, 60, 27);
    static final Disc BOWL = new Disc(0.5, 0.5, 20, 60, 27, 4, Disc.Support.Standing.TO_THE_FLOOR);

    static DiscWater water(float ponds, float streamWidth) {
        return new DiscWater(ponds, 12, streamWidth, 40, 2, 3);
    }

    /** Depths over the whole of a disc inside its bank, for twenty holes, so that a share is not one hole's luck. */
    private static List<Integer> depths(DiscWater water, Disc disc) {
        var depths = new ArrayList<Integer>();
        for (long hash = 1; hash <= 20; hash++) {
            for (int x = -60; x <= 60; x++) {
                for (int z = -60; z <= 60; z++) {
                    if (Math.hypot(x - disc.x(), z - disc.z()) <= disc.radius() - water.bank()) {
                        depths.add(water.depthAt(disc, hash * 0x9E3779B97F4A7C15L, 3, x, z));
                    }
                }
            }
        }
        return depths;
    }

    private static double share(List<Integer> depths, int ofDepth) {
        return depths.stream().filter(depth -> ofDepth == 0 ? depth > 0 : depth == ofDepth).count() / (double) depths.size();
    }

    @Test
    void aShareOfAFlatTopIsPondAndHalfOfThatIsDeep() {
        List<Integer> depths = depths(water(0.3F, 0), FLAT);
        assertEquals(0.3, share(depths, 0), 0.03, "three tenths of the top asked for");
        assertEquals(0.15, share(depths, 2), 0.03, "the middle half of the ponds is at full depth");
        assertEquals(0, share(depths(water(0, 0), FLAT), 0), 1e-9, "no ponds and no streams is no water");
    }

    @Test
    void aStreamIsOneDeepAndAboutAsWideAsAskedAllAlong() {
        DiscWater streams = water(0, 2);
        List<Integer> depths = depths(streams, FLAT);
        assertTrue(share(depths, 0) > 0.03 && share(depths, 0) < 0.12, "streams two blocks wide, forty apart, take " + share(depths, 0) + " of the top");
        assertEquals(0, share(depths, 2), 1e-9, "a stream is never deeper than one block");
        assertTrue(share(depths(water(0.3F, 2), FLAT), 0) > 0.32, "streams run where the ponds are not");
        // A stream is a ribbon: from any of its columns dry ground is a few blocks away, east to west or north to south.
        int wet = 0;
        int widest = 0;
        for (int x = -40; x <= 40; x++) {
            for (int z = -40; z <= 40; z++) {
                if (streams.depthAt(FLAT, 77L, 0, x, z) == 0) {
                    continue;
                }
                wet++;
                int alongX = 1;
                int alongZ = 1;
                for (int step = 1; streams.depthAt(FLAT, 77L, 0, x + step, z) > 0; step++) {
                    alongX++;
                }
                for (int step = 1; streams.depthAt(FLAT, 77L, 0, x - step, z) > 0; step++) {
                    alongX++;
                }
                for (int step = 1; streams.depthAt(FLAT, 77L, 0, x, z + step) > 0; step++) {
                    alongZ++;
                }
                for (int step = 1; streams.depthAt(FLAT, 77L, 0, x, z - step) > 0; step++) {
                    alongZ++;
                }
                widest = Math.max(widest, Math.min(alongX, alongZ));
            }
        }
        assertTrue(wet > 100, wet + " columns of stream on the disc");
        assertTrue(widest <= 5, "a stream asked to be 2 wide is " + widest + " across at its widest");
    }

    @Test
    void waterIsNoDeeperThanAskedAndKeepsOffTheRim() {
        var shallow = new DiscWater(0.5F, 12, 3, 40, 1, 6);
        for (int x = -62; x <= 62; x++) {
            for (int z = -62; z <= 62; z++) {
                int depth = shallow.depthAt(FLAT, 77L, 0, x, z);
                assertTrue(depth >= 0 && depth <= 1, "depth " + depth);
                assertTrue(depth == 0 || Math.hypot(x - FLAT.x(), z - FLAT.z()) <= FLAT.radius() - 6, "water " + x + "," + z + " in the bank");
            }
        }
    }

    @Test
    void waterLiesOnlyWhereNoGroundBesideItIsLower() {
        DiscWater water = water(0.5F, 3);
        int wet = 0;
        for (int x = -60; x <= 60; x++) {
            for (int z = -60; z <= 60; z++) {
                if (water.depthAt(BOWL, 77L, 0, x, z) == 0) {
                    continue;
                }
                wet++;
                int top = BOWL.topBlockAt(Math.hypot(x - BOWL.x(), z - BOWL.z()));
                for (int[] beside : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int other = BOWL.topBlockAt(Math.hypot(x + beside[0] - BOWL.x(), z + beside[1] - BOWL.z()));
                    assertTrue(other >= top, "water at " + x + "," + z + " would run off to " + (x + beside[0]) + "," + (z + beside[1]));
                }
            }
        }
        long onFlat = depths(water, FLAT).stream().filter(depth -> depth > 0).count() / 20;
        assertTrue(wet > 1000 && wet < onFlat, wet + " wet columns in a bowl, " + onFlat + " on a flat disc: the steps of a bowl stay dry");
    }

    @Test
    void eachDiscOfAHoleHasItsOwnWaterAndItIsTheSameEveryTime() {
        DiscWater water = water(0.3F, 2);
        var first = new ArrayList<Integer>();
        var again = new ArrayList<Integer>();
        var other = new ArrayList<Integer>();
        for (int x = -40; x <= 40; x++) {
            for (int z = -40; z <= 40; z++) {
                first.add(water.depthAt(FLAT, 77L, 0, x, z));
                again.add(water.depthAt(FLAT, 77L, 0, x, z));
                other.add(water.depthAt(FLAT, 77L, 1, x, z));
            }
        }
        assertEquals(first, again);
        assertNotEquals(first, other, "the disc above has the same ponds in the same places");
    }

    @Test
    void waterIsWrittenAsHowMuchAndTheRestHasDefaults() {
        DiscWater plain = DiscWater.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"ponds\": 0.25}")).getOrThrow();
        assertEquals(new DiscWater(0.25F, 12, 0, 40, 2, 3), plain);
        DiscWater full = DiscWater.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"ponds\": 0.4, \"pond_size\": 18, \"stream_width\": 2.5, \"stream_spacing\": 30, \"depth\": 1, \"bank\": 5}")).getOrThrow();
        assertEquals(new DiscWater(0.4F, 18, 2.5F, 30, 1, 5), full);
        assertEquals(full, DiscWater.CODEC.parse(JsonOps.INSTANCE, DiscWater.CODEC.encodeStart(JsonOps.INSTANCE, full).getOrThrow()).getOrThrow());
        assertTrue(DiscWater.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"ponds\": 0.2, \"depth\": 3}")).isError(), "deeper than a platform can hold");
        assertTrue(DiscWater.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"ponds\": 0.2, \"bank\": 0}")).isError(), "water at the very rim would pour off it");
    }
}
