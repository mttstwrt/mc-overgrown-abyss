package dev.syrval.verdantdeep.ravine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
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
    // Named because the owner's dials hold what they turn to the same limit (see Dials).
    static final int LEAST_EVERY = 64;

    /**
     * The treasure of a theme's own ruins: loot tables from the poorest to the richest, each with the rank a disc must have
     * for its ruins to hold it. A disc's rank runs from 0 to 1 among the discs of its hole and rises with its depth and with
     * its distance from the hole's centre. All the chests of a ruin hold the last table whose {@code from} its disc's rank
     * reaches. A ruin borrowed from another structure keeps the loot its own templates name.
     *
     * <p>Instead of tiers a theme may give a {@code mix}: tables with weights that change with the disc's height, with no
     * boundary between them. Each site rolls its table once from those weights (see {@link DiscRuinSites}). Exactly one of
     * {@code tables} and {@code mix} is given.
     *
     * @param tables   the tables, poorest first; with none, or on a disc whose rank reaches none, a ruin's chests keep what
     *                 its pool's processors gave them
     * @param mix      the weighted tables of the other form; {@code depth} and {@code distance} do nothing with it
     * @param depth    how much a disc's depth in its hole counts toward its rank
     * @param distance how much its distance from the hole's centre counts
     */
    public record Loot(List<Tier> tables, float depth, float distance, List<Weighted> mix) {
        static final Loot NONE = new Loot(List.of(), 1, 1);

        public Loot(List<Tier> tables, float depth, float distance) {
            this(tables, depth, distance, List.of());
        }

        /**
         * One table of a mix.
         *
         * @param table    the loot table
         * @param weight   how often a site holds it, against the others
         * @param byHeight how that weight changes from the hole's lowest disc to its highest
         */
        public record Weighted(ResourceKey<LootTable> table, float weight, DiscTheme.Ramp byHeight) {
            static final Codec<Weighted> CODEC = RecordCodecBuilder.create(i -> i.group(
                    ResourceKey.codec(Registries.LOOT_TABLE).fieldOf("table").forGetter(Weighted::table),
                    Codec.floatRange(0, 1000).fieldOf("weight").forGetter(Weighted::weight),
                    DiscTheme.Ramp.BY_HEIGHT.optionalFieldOf("by_height", DiscTheme.Ramp.EVEN).forGetter(Weighted::byHeight)
            ).apply(i, Weighted::new));

            double at(double height) {
                return weight * byHeight.at(height);
            }
        }

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
                Tier.CODEC.listOf().optionalFieldOf("tables", List.of()).forGetter(Loot::tables),
                Codec.floatRange(0, 100).optionalFieldOf("depth", 1F).forGetter(Loot::depth),
                Codec.floatRange(0, 100).optionalFieldOf("distance", 1F).forGetter(Loot::distance),
                Weighted.CODEC.listOf().optionalFieldOf("mix", List.of()).forGetter(Loot::mix)
        ).apply(i, Loot::new)).validate(Loot::validate);

        private static DataResult<Loot> validate(Loot loot) {
            if (loot.tables.isEmpty() == loot.mix.isEmpty()) {
                return DataResult.error(() -> "loot needs exactly one of tables and mix, and not an empty one");
            }
            // The weights are straight lines of the height, so they add up to something over zero everywhere if they do at both ends.
            if (!loot.mix.isEmpty() && (loot.mixTotal(0) <= 0 || loot.mixTotal(1) <= 0)) {
                return DataResult.error(() -> "each table of a loot mix needs a weight, and the mix some weight at every height");
            }
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
            mix = List.copyOf(mix);
        }

        private double mixTotal(double height) {
            return mix.stream().mapToDouble(entry -> entry.at(height)).sum();
        }

        /**
         * The table for one site of a disc: by its rank if the loot is in tiers, else one of the mix by its weights at the
         * disc's height, chosen by {@code roll}, a uniform draw in [0, 1) made for the site.
         */
        Optional<ResourceKey<LootTable>> tableFor(DiscTraits traits, double roll) {
            return mix.isEmpty() ? tableFor(traits) : Optional.of(pick(mix, traits.height(), roll));
        }

        /** The entry of a mix at {@code roll}, a uniform draw in [0, 1), by the weights at {@code height}. */
        static ResourceKey<LootTable> pick(List<Weighted> mix, double height, double roll) {
            double total = mix.stream().mapToDouble(entry -> entry.at(height)).sum();
            double left = roll * total;
            ResourceKey<LootTable> last = mix.getFirst().table();
            for (Weighted entry : mix) {
                double weight = entry.at(height);
                if (weight <= 0) {
                    continue;
                }
                last = entry.table();
                if (left < weight) {
                    return last;
                }
                left -= weight;
            }
            return last;
        }

        /** How deep and far out a disc is among its hole's discs, by these weights: 0 for the highest at the centre, 1 for the lowest at the edge. */
        double rank(DiscTraits traits) {
            return (depth * (1 - traits.height()) + distance * traits.distance()) / (depth + distance);
        }

        /** The table for the ruins of a disc by its rank, for loot in tiers; none if its rank reaches no table. */
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
     * @param groups   the groups of kinds this is in, whose dials turn its weight: {@code hoard} and {@code nest}
     */
    public record Kind(ResourceKey<StructureTemplatePool> pool, float weight, DiscTheme.Ramp byHeight, int sink, List<String> groups) {
        static final Codec<Kind> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceKey.codec(Registries.TEMPLATE_POOL).fieldOf("pool").forGetter(Kind::pool),
                Codec.floatRange(0, 1000).fieldOf("weight").forGetter(Kind::weight),
                DiscTheme.Ramp.BY_HEIGHT.optionalFieldOf("by_height", DiscTheme.Ramp.EVEN).forGetter(Kind::byHeight),
                Codec.intRange(0, 64).optionalFieldOf("sink", 0).forGetter(Kind::sink),
                Codec.STRING.listOf().optionalFieldOf("groups", List.of()).forGetter(Kind::groups)
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
            Codec.intRange(LEAST_EVERY, ONE_A_DISC).optionalFieldOf("every", ONE_A_DISC).forGetter(DiscRuins::every),
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
