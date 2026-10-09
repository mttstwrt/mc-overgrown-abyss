package dev.syrval.overgrownabyss.ravine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The ruins on one kind of disc. Not every disc of the theme has any: {@code chance} is the share that do. On those there is
 * a ruin for about every {@code every} blocks of the top, at least one, each of a kind drawn by weight. A ruin is only built
 * where a piece of its kind has room, and where the kind drawn first has none the next is tried (see {@link DiscRuinSites}); so
 * a disc drawn to have ruins may come out with fewer than that, or none.
 *
 * <p>How much room a piece needs is not written here: it is measured from the piece's template when a level loads (see
 * {@link RuinPieces}).
 *
 * @param chance     the share of the theme's discs that hold ruins
 * @param every      blocks of a disc's top for each ruin on it; without it a disc holds one
 * @param byHeight   how many times as frequent the ruins are from the hole's lowest disc to its highest: both the share of
 *                   discs that hold any and how many a disc holds are multiplied by it
 * @param loot       what the chests of the theme's own kinds hold
 * @param kinds      the kinds of ruin the theme has of its own
 * @param structures other structures whose pieces stand as ruins here, by tag
 */
public record DiscRuins(float chance, int every, DiscTheme.Ramp byHeight, Loot loot, List<Kind> kinds, List<Borrowed> structures) {
    private static final int ONE_A_DISC = 1_000_000;

    /**
     * The treasure of a theme's own ruins: loot tables from the poorest to the richest, each with the rank a disc must have
     * for its ruins to hold it. A disc's rank runs from 0 to 1 among the discs of its hole and rises with its depth and with
     * its distance from the hole's centre. All the chests of a ruin hold the last table whose {@code from} its disc's rank
     * reaches. A ruin borrowed from another structure keeps the loot its own templates name.
     *
     * @param tables   the tables, poorest first; with none, or on a disc whose rank reaches none, a ruin's chests keep what
     *                 its pool's processors gave them
     * @param depth    how much a disc's depth in its hole counts toward its rank
     * @param distance how much its distance from the hole's centre counts
     */
    public record Loot(List<Tier> tables, float depth, float distance) {
        static final Loot NONE = new Loot(List.of(), 1, 1);

        /**
         * One table and the discs that have it.
         *
         * @param table the loot table
         * @param from  the least rank of a disc whose ruins hold it; they hold the next table instead from that one's
         */
        public record Tier(ResourceKey<LootTable> table, float from) {
            static final Codec<Tier> CODEC = RecordCodecBuilder.create(i -> i.group(
                    ResourceKey.codec(Registries.LOOT_TABLE).fieldOf("table").forGetter(Tier::table),
                    Codec.floatRange(0, 1).optionalFieldOf("from", 0F).forGetter(Tier::from)
            ).apply(i, Tier::new));
        }

        static final Codec<Loot> CODEC = RecordCodecBuilder.<Loot>create(i -> i.group(
                ExtraCodecs.nonEmptyList(Tier.CODEC.listOf()).fieldOf("tables").forGetter(Loot::tables),
                Codec.floatRange(0, 100).optionalFieldOf("depth", 1F).forGetter(Loot::depth),
                Codec.floatRange(0, 100).optionalFieldOf("distance", 1F).forGetter(Loot::distance)
        ).apply(i, Loot::new)).validate(Loot::validate);

        private static DataResult<Loot> validate(Loot loot) {
            if (loot.depth + loot.distance <= 0) {
                return DataResult.error(() -> "loot needs depth or distance to count for something");
            }
            for (int i = 1; i < loot.tables.size(); i++) {
                if (loot.tables.get(i).from() <= loot.tables.get(i - 1).from()) {
                    return DataResult.error(() -> "each loot table after the first must be from a higher rank than the one before it");
                }
            }
            return DataResult.success(loot);
        }

        public Loot {
            tables = List.copyOf(tables);
        }

        /** How deep and far out a disc is among its hole's discs, by these weights: 0 for the highest at the centre, 1 for the lowest at the edge. */
        double rank(DiscTraits traits) {
            return (depth * (1 - traits.height()) + distance * traits.distance()) / (depth + distance);
        }

        /** The table for the ruins of a disc; none if its rank reaches no table. */
        Optional<ResourceKey<LootTable>> tableFor(DiscTraits traits) {
            double rank = rank(traits);
            Optional<ResourceKey<LootTable>> reached = Optional.empty();
            for (Tier tier : tables) {
                if (rank >= tier.from()) {
                    reached = Optional.of(tier.table());
                }
            }
            return reached;
        }
    }

    /**
     * One kind of ruin: a template pool, one of whose pieces is stood on the disc, turned any of four ways about its middle.
     *
     * @param pool     the pool a piece is drawn from, by its weight there among the pieces that have room
     * @param weight   how often a ruin is of this kind where it has room, against the weights of the others
     * @param byHeight how that weight changes from the hole's lowest disc to its highest
     * @param sink     how many of a piece's lowest layers lie in the ground: 0 for a piece whose lowest layer is a floor laid on
     *                 the ground, 1 for one whose lowest layer is the ground itself, more for one with a cellar
     */
    public record Kind(ResourceKey<StructureTemplatePool> pool, float weight, DiscTheme.Ramp byHeight, int sink) {
        static final Codec<Kind> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceKey.codec(Registries.TEMPLATE_POOL).fieldOf("pool").forGetter(Kind::pool),
                Codec.floatRange(0, 1000).fieldOf("weight").forGetter(Kind::weight),
                DiscTheme.Ramp.BY_HEIGHT.optionalFieldOf("by_height", DiscTheme.Ramp.EVEN).forGetter(Kind::byHeight),
                Codec.intRange(0, 64).optionalFieldOf("sink", 0).forGetter(Kind::sink)
        ).apply(i, Kind::new));
    }

    /**
     * Ruins borrowed from other structures, which other mods and packs may add: every structure in {@code tag} that starts on
     * the surface from a template pool is a kind of ruin of this weight. The first piece of such a structure stands alone, as
     * deep in the ground as its own file starts it, and nothing is joined on to it. A structure that is not in the level is
     * simply not there, so a tag may name those of mods that are not installed.
     *
     * @param tag      the structures
     * @param weight   the weight of each of them as a kind
     * @param byHeight how that weight changes from the hole's lowest disc to its highest
     */
    public record Borrowed(TagKey<Structure> tag, float weight, DiscTheme.Ramp byHeight) {
        static final Codec<Borrowed> CODEC = RecordCodecBuilder.create(i -> i.group(
                TagKey.hashedCodec(Registries.STRUCTURE).fieldOf("tag").forGetter(Borrowed::tag),
                Codec.floatRange(0, 1000).fieldOf("weight").forGetter(Borrowed::weight),
                DiscTheme.Ramp.BY_HEIGHT.optionalFieldOf("by_height", DiscTheme.Ramp.EVEN).forGetter(Borrowed::byHeight)
        ).apply(i, Borrowed::new));
    }

    static final Codec<DiscRuins> CODEC = RecordCodecBuilder.<DiscRuins>create(i -> i.group(
            Codec.floatRange(0, 1).fieldOf("chance").forGetter(DiscRuins::chance),
            Codec.intRange(64, ONE_A_DISC).optionalFieldOf("every", ONE_A_DISC).forGetter(DiscRuins::every),
            DiscTheme.Ramp.BY_HEIGHT.optionalFieldOf("by_height", DiscTheme.Ramp.EVEN).forGetter(DiscRuins::byHeight),
            Loot.CODEC.optionalFieldOf("loot", Loot.NONE).forGetter(DiscRuins::loot),
            Kind.CODEC.listOf().optionalFieldOf("kinds", List.of()).forGetter(DiscRuins::kinds),
            Borrowed.CODEC.listOf().optionalFieldOf("structures", List.of()).forGetter(DiscRuins::structures)
    ).apply(i, DiscRuins::new)).validate(DiscRuins::validate);

    private static DataResult<DiscRuins> validate(DiscRuins ruins) {
        boolean weighted = ruins.kinds.stream().anyMatch(kind -> kind.weight() > 0)
                || ruins.structures.stream().anyMatch(borrowed -> borrowed.weight() > 0);
        return weighted
                ? DataResult.success(ruins)
                : DataResult.error(() -> "ruins need at least one kind or tag of structures with a weight");
    }

    public DiscRuins {
        kinds = List.copyOf(kinds);
        structures = List.copyOf(structures);
    }
}
