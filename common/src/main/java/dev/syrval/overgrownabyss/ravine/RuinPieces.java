package dev.syrval.overgrownabyss.ravine;

import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

/**
 * What the ruins of a level's disc themes are made of: for each theme's {@link DiscRuins}, the kinds a ruin can be there, each
 * with the pieces of its pool and the room every piece needs. The room is measured from the templates the level really has
 * when it loads, which a pack may have replaced and another mod may have added, so none of it is written in a theme. Plain
 * data, built once for a level and then only read.
 */
public record RuinPieces(Map<DiscRuins, List<Kind>> byRuins) {
    public static final RuinPieces NONE = new RuinPieces(Map.of());

    /**
     * One kind of ruin as a level has it: a kind of the theme's own, or one structure of a tag the theme names.
     *
     * @param weight   how often a ruin is of this kind where it has room, against the weights of the others
     * @param byHeight how that weight changes from the hole's lowest disc to its highest
     * @param borrowed whether it is another structure's, which keeps the loot of its own templates
     * @param pieces   the pieces of the kind's pool that could be measured
     */
    public record Kind(float weight, DiscTheme.Ramp byHeight, boolean borrowed, List<Piece> pieces) {
        public Kind {
            pieces = List.copyOf(pieces);
        }
    }

    /**
     * One piece a ruin can be: an element of a template pool, stood on a disc and turned any of four ways about its middle.
     *
     * @param pool    the pool the piece is in
     * @param element which of the pool's elements it is, as the level counts them
     * @param weight  how often the pool gives this piece, against its others
     * @param radius  blocks from the middle of the piece to its furthest corner: the round of ground kept for it
     * @param height  blocks the piece stands over the ground
     * @param sink    how many of its lowest layers lie in the ground
     */
    public record Piece(ResourceKey<StructureTemplatePool> pool, int element, int weight, double radius, int height, int sink) {}

    public RuinPieces {
        byRuins = Map.copyOf(byRuins);
    }

    /** The kinds a theme's ruins can be in this level; none if nothing of them could be measured. */
    List<Kind> of(DiscRuins ruins) {
        return byRuins.getOrDefault(ruins, List.of());
    }
}
