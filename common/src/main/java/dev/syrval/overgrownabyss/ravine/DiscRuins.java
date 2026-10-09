package dev.syrval.overgrownabyss.ravine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

/**
 * The ruins on one kind of disc. Not every disc of the theme has any: {@code chance} is the share that do. On those there is
 * a ruin for about every {@code every} blocks of the top, at least one, each of a kind drawn by weight. A ruin is only built
 * where its kind has room, and where the kind drawn first has none the next is tried (see {@link DiscRuinSites}); so a disc
 * drawn to have ruins may come out with fewer than that, or none.
 *
 * @param chance the share of the theme's discs that hold ruins
 * @param every  blocks of a disc's top for each ruin on it; without it a disc holds one
 * @param kinds  the kinds of ruin there are
 */
public record DiscRuins(float chance, int every, List<Kind> kinds) {
    /** The most kinds a theme's ruins may have. */
    public static final int MAX_KINDS = 16;
    private static final int ONE_A_DISC = 1_000_000;

    /**
     * One kind of ruin: a template pool, one of whose pieces is stood on the disc, turned any of four ways about its middle.
     *
     * @param pool   the pool a piece is drawn from. An empty element in it leaves the place bare
     * @param weight how often a ruin is of this kind where it has room, against the weights of the others
     * @param radius blocks from the middle of a piece to its furthest corner: the round of dry, nearly level ground kept for
     *               it inside the disc's rim
     * @param height blocks of clear air a piece needs over that ground
     * @param sink   how many of a piece's lowest layers lie in the ground: 0 for a piece whose lowest layer is a floor laid on
     *               the ground, 1 for one whose lowest layer is the ground itself
     */
    public record Kind(ResourceKey<StructureTemplatePool> pool, float weight, float radius, int height, int sink) {
        static final Codec<Kind> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceKey.codec(Registries.TEMPLATE_POOL).fieldOf("pool").forGetter(Kind::pool),
                Codec.floatRange(0, 1000).fieldOf("weight").forGetter(Kind::weight),
                Codec.floatRange(1, 64).fieldOf("radius").forGetter(Kind::radius),
                Codec.intRange(1, 128).fieldOf("height").forGetter(Kind::height),
                Codec.intRange(0, 8).optionalFieldOf("sink", 0).forGetter(Kind::sink)
        ).apply(i, Kind::new));
    }

    static final Codec<DiscRuins> CODEC = RecordCodecBuilder.<DiscRuins>create(i -> i.group(
            Codec.floatRange(0, 1).fieldOf("chance").forGetter(DiscRuins::chance),
            Codec.intRange(64, ONE_A_DISC).optionalFieldOf("every", ONE_A_DISC).forGetter(DiscRuins::every),
            Kind.CODEC.listOf(1, MAX_KINDS).fieldOf("kinds").forGetter(DiscRuins::kinds)
    ).apply(i, DiscRuins::new)).validate(DiscRuins::validate);

    private static DataResult<DiscRuins> validate(DiscRuins ruins) {
        return ruins.kinds.stream().noneMatch(kind -> kind.weight() > 0)
                ? DataResult.error(() -> "ruins need at least one kind with a weight")
                : DataResult.success(ruins);
    }

    public DiscRuins {
        kinds = List.copyOf(kinds);
    }
}
