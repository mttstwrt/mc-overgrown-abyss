package dev.syrval.overgrownabyss.ravine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;

/**
 * Shallow water lying in the top of a disc: ponds, and streams that wind across it. Water takes the place of the top blocks
 * of the platform, so it is never deeper than {@code depth} and has the platform's own material under it. It is water that
 * cannot run off: none lies within {@code bank} blocks of the rim, nor in a column with lower ground beside it, which leaves
 * a low dam wherever the bowl steps down. A theme may have lava lie there in its place, by the same rules.
 *
 * @param ponds         the share of the top that is pond, before the rim and the dams take their part
 * @param pondSize      blocks from one pond to the next, roughly; larger gives fewer, wider ponds
 * @param streamWidth   how wide a stream is, in blocks; 0 for no streams
 * @param streamSpacing blocks from one stream, or one bend of a stream, to the next, roughly
 * @param depth         how deep the middle of a pond is, in blocks; its edge and every stream are 1 deep
 * @param bank          blocks of dry ground kept inside the rim
 * @param fluid         what the ponds and streams hold
 * @param clear         blocks lava keeps from what the game would have it set alight: the ground of other discs about level
 *                      with it, and the hole's roots (see {@link DiscLava}); 0 for lava that lies wherever a pond does
 */
public record DiscWater(float ponds, float pondSize, float streamWidth, float streamSpacing, int depth, int bank, Fluid fluid, int clear) {
    /** The deepest water a disc may hold; a platform must be thicker than this. */
    public static final int MAX_DEPTH = 2;

    /** What lies in a disc's ponds and streams. Only water takes part in the hole's falls and springs (see {@link DiscFalls}). */
    public enum Fluid implements StringRepresentable {
        WATER("water"),
        LAVA("lava");

        static final Codec<Fluid> CODEC = StringRepresentable.fromEnum(Fluid::values);
        private final String name;

        Fluid(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    static final Codec<DiscWater> CODEC = RecordCodecBuilder.<DiscWater>create(i -> i.group(
            Codec.floatRange(0, 0.9F).optionalFieldOf("ponds", 0F).forGetter(DiscWater::ponds),
            Codec.floatRange(4, 128).optionalFieldOf("pond_size", 12F).forGetter(DiscWater::pondSize),
            Codec.floatRange(0, 8).optionalFieldOf("stream_width", 0F).forGetter(DiscWater::streamWidth),
            Codec.floatRange(8, 256).optionalFieldOf("stream_spacing", 40F).forGetter(DiscWater::streamSpacing),
            Codec.intRange(1, MAX_DEPTH).optionalFieldOf("depth", MAX_DEPTH).forGetter(DiscWater::depth),
            Codec.intRange(2, 32).optionalFieldOf("bank", 3).forGetter(DiscWater::bank),
            Fluid.CODEC.optionalFieldOf("fluid", Fluid.WATER).forGetter(DiscWater::fluid),
            Codec.intRange(0, DiscLava.MAX_CLEAR).optionalFieldOf("clear", 0).forGetter(DiscWater::clear)
    ).apply(i, DiscWater::new)).validate(DiscWater::validate);

    // Water sets nothing alight, and a fall is led from a pond wherever the pond lies.
    private static DataResult<DiscWater> validate(DiscWater water) {
        return water.fluid == Fluid.WATER && water.clear > 0
                ? DataResult.error(() -> "only lava keeps clear of what burns")
                : DataResult.success(water);
    }

    private static final int HASH_BASE = 400_000;
    // Where a stream's value changes by less than this over the spacing of the streams, it marks no line to follow.
    private static final double LEVEL_GROUND = 0.25;
    // A smooth value changes by at most this over one of its sizes (2.13 at its steepest).
    private static final double STEEPEST = 2.2;
    // Ground right beside water stays on the rock: a bank to stand on, and no wall over the water.
    private static final double SHORE = 1;
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

    /**
     * How many blocks of ground may stand over the platform in a column without burying water or walling it in (see
     * {@link DiscGround}), up to {@code most}: none where the disc holds water or would but for the rim or a step of the bowl,
     * none right beside that, and a block more for each block further from it.
     */
    double dryRoomAt(long hash, int index, int x, int z, double most) {
        if (farFromWater(hash, index, x, z) - SHORE >= most) {
            return most;
        }
        // Water may be near, so look: the nearest column that would hold some, out as far as it could matter.
        int reach = (int) Math.ceil(most + SHORE);
        double nearest = Double.POSITIVE_INFINITY;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                double away = Math.hypot(dx, dz);
                if (away < nearest && wantedAt(hash, index, x + dx, z + dz) > 0) {
                    nearest = away;
                }
            }
        }
        return Math.clamp(nearest - SHORE, 0, most);
    }

    // Blocks that no water is nearer than, reckoned from the two smooth values alone: one that is some way from the level that
    // marks a pond's edge, or a stream's middle, is at least that many blocks from it. A cheap answer, and a low one.
    private double farFromWater(long hash, int index, int x, int z) {
        double fromPond = ponds > 0
                ? (RavineCells.levelBelow(1 - ponds) - RavineCells.smoothOver(hash, HASH_BASE + 2 * index, x, z, pondSize)) * pondSize / STEEPEST
                : Double.POSITIVE_INFINITY;
        double fromStream = streamWidth > 0
                ? Math.abs(RavineCells.smoothOver(hash, HASH_BASE + 2 * index + 1, x, z, streamSpacing) - 0.5) * streamSpacing / STEEPEST - streamWidth / 2
                : Double.POSITIVE_INFINITY;
        return Math.min(fromPond, fromStream);
    }

    private int wantedAt(long hash, int index, int x, int z) {
        double pond = RavineCells.smoothOver(hash, HASH_BASE + 2 * index, x, z, pondSize);
        // The half of a pond where the value is highest is its middle.
        if (pond >= RavineCells.levelBelow(1 - ponds / 2.0)) {
            return depth;
        }
        if (pond >= RavineCells.levelBelow(1 - ponds)) {
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
                RavineCells.smoothOver(hash, index, x + 1, z, streamSpacing) - RavineCells.smoothOver(hash, index, x - 1, z, streamSpacing),
                RavineCells.smoothOver(hash, index, x, z + 1, streamSpacing) - RavineCells.smoothOver(hash, index, x, z - 1, streamSpacing)) / 2;
        if (slope * streamSpacing < LEVEL_GROUND) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.abs(RavineCells.smoothOver(hash, index, x, z, streamSpacing) - 0.5) / slope;
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
