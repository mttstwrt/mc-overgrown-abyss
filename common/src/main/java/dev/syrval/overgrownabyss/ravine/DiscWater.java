package dev.syrval.overgrownabyss.ravine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

/**
 * Shallow water lying in the top of a disc: ponds, and streams that wind across it. Water takes the place of the top blocks
 * of the platform, so it is never deeper than {@code depth} and has the platform's own material under it. It is water that
 * cannot run off: none lies within {@code bank} blocks of the rim, nor in a column with lower ground beside it, which leaves
 * a low dam wherever the bowl steps down.
 *
 * @param ponds         the share of the top that is pond, before the rim and the dams take their part
 * @param pondSize      blocks from one pond to the next, roughly; larger gives fewer, wider ponds
 * @param streamWidth   how wide a stream is, in blocks; 0 for no streams
 * @param streamSpacing blocks from one stream, or one bend of a stream, to the next, roughly
 * @param depth         how deep the middle of a pond is, in blocks; its edge and every stream are 1 deep
 * @param bank          blocks of dry ground kept inside the rim
 */
public record DiscWater(float ponds, float pondSize, float streamWidth, float streamSpacing, int depth, int bank) {
    /** The deepest water a disc may hold; a platform must be thicker than this. */
    public static final int MAX_DEPTH = 2;

    static final Codec<DiscWater> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.floatRange(0, 0.9F).optionalFieldOf("ponds", 0F).forGetter(DiscWater::ponds),
            Codec.floatRange(4, 128).optionalFieldOf("pond_size", 12F).forGetter(DiscWater::pondSize),
            Codec.floatRange(0, 8).optionalFieldOf("stream_width", 0F).forGetter(DiscWater::streamWidth),
            Codec.floatRange(8, 256).optionalFieldOf("stream_spacing", 40F).forGetter(DiscWater::streamSpacing),
            Codec.intRange(1, MAX_DEPTH).optionalFieldOf("depth", MAX_DEPTH).forGetter(DiscWater::depth),
            Codec.intRange(2, 32).optionalFieldOf("bank", 3).forGetter(DiscWater::bank)
    ).apply(i, DiscWater::new));

    private static final int HASH_BASE = 400_000;
    // Where a stream's value changes by less than this over the spacing of the streams, it marks no line to follow.
    private static final double LEVEL_GROUND = 0.25;
    // The values that a twentieth, two twentieths and so on of RavineCells.smoothAt lie below, measured over three million
    // draws. Blended values gather around a half, so a share of the top is turned into a level through these.
    private static final List<Double> LEVELS = List.of(
            0.000, 0.148, 0.209, 0.258, 0.299, 0.337, 0.372, 0.405, 0.438, 0.469, 0.500,
            0.531, 0.562, 0.595, 0.628, 0.663, 0.701, 0.742, 0.791, 0.852, 1.000);

    /** The value that {@code share} of all smooth values lie below. */
    static double levelBelow(double share) {
        double at = Math.clamp(share, 0, 1) * (LEVELS.size() - 1);
        int below = Math.min((int) at, LEVELS.size() - 2);
        return LEVELS.get(below) + (LEVELS.get(below + 1) - LEVELS.get(below)) * (at - below);
    }

    /**
     * How deep the water is in one column of a disc's top, or 0 where there is none. {@code index} is the disc's place in its
     * layout, which gives each disc of a hole its own ponds and streams.
     */
    int depthAt(Disc disc, long hash, int index, int x, int z) {
        double fromAxis = Math.hypot(x - disc.x(), z - disc.z());
        if (fromAxis > disc.radius() - bank) {
            return 0;
        }
        int wanted = wantedAt(hash, index, x, z);
        return wanted > 0 && isHeldIn(disc, x, z, fromAxis) ? wanted : 0;
    }

    private int wantedAt(long hash, int index, int x, int z) {
        double pond = smooth(hash, HASH_BASE + 2 * index, x, z, pondSize);
        // The half of a pond where the value is highest is its middle.
        if (pond >= levelBelow(1 - ponds / 2.0)) {
            return depth;
        }
        if (pond >= levelBelow(1 - ponds)) {
            return 1;
        }
        return streamWidth > 0 && fromStream(hash, HASH_BASE + 2 * index + 1, x, z) < streamWidth / 2 ? 1 : 0;
    }

    /**
     * How far a column is from the nearest stream, about. A stream follows the line along which a smooth value is a half, and
     * a column's distance from that line is how far its value is from a half, over how fast the value changes there. That
     * keeps a stream the same width all along, where a plain band of values would spread into a lake wherever they level out.
     */
    private double fromStream(long hash, int index, int x, int z) {
        double slope = Math.hypot(
                smooth(hash, index, x + 1, z, streamSpacing) - smooth(hash, index, x - 1, z, streamSpacing),
                smooth(hash, index, x, z + 1, streamSpacing) - smooth(hash, index, x, z - 1, streamSpacing)) / 2;
        if (slope * streamSpacing < LEVEL_GROUND) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.abs(smooth(hash, index, x, z, streamSpacing) - 0.5) / slope;
    }

    // The smooth values lie on a square grid, which shows as water running north and south or east and west. Each set of
    // values is turned by an angle of its own, so the grid lines up with nothing.
    private static double smooth(long hash, int index, int x, int z, double size) {
        double angle = RavineCells.unit(hash, index) * 2 * Math.PI;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return RavineCells.smoothAt(hash, index, (x * cos - z * sin) / size, (x * sin + z * cos) / size);
    }

    // Water would run off into lower ground beside it, and the top steps down wherever the bowl does.
    private static boolean isHeldIn(Disc disc, int x, int z, double fromAxis) {
        int top = disc.topBlockAt(fromAxis);
        return topBlockAt(disc, x + 1, z) >= top && topBlockAt(disc, x - 1, z) >= top
                && topBlockAt(disc, x, z + 1) >= top && topBlockAt(disc, x, z - 1) >= top;
    }

    private static int topBlockAt(Disc disc, int x, int z) {
        return disc.topBlockAt(Math.hypot(x - disc.x(), z - disc.z()));
    }
}
