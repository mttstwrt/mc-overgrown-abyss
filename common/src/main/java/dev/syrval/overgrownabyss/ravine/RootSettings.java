package dev.syrval.overgrownabyss.ravine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.InclusiveRange;

/**
 * The great roots of a cone: winding wooden roots that run down its wall from under the mouth to the floor, touch the rims
 * of discs on the way, branch more and more towards the bottom and join one another there, with a few thinner ones that
 * climb from one disc to the next. They are blocks written over whatever the hole and its discs left, so a root runs into
 * the wall, into a platform and across the floor alike. Each hole grows its own from its hash (see {@link RootLayout}).
 *
 * <p>These are not the root a hanging disc hangs from ({@link Disc.Support.Hanging}), which is rock and part of its disc.
 *
 * @param great     the roots that run from the top of the hole to its floor
 * @param crossing  the few that span the clear air round the axis, which no other root enters
 * @param branches  the roots that leave other roots
 * @param links     the roots a player can walk up from one disc to the next
 * @param floorRun  blocks a great root runs on along the floor, half buried, before it dives
 * @param winding   how far a root strays from the straight way to where it is going: 0 not at all, 1 the most
 * @param minRadius the thinnest a root may be; a branch that would be thinner is not grown
 * @param wood      what a root is made of where no disc near it says otherwise (see {@link DiscTheme#rootWood()})
 * @param woodReach blocks from a disc within which a root takes that disc's wood
 * @param woodBlend blocks over which one disc's wood gives way to another's where a root is between the two
 */
public record RootSettings(
        Great great, Crossing crossing, Branches branches, Links links, float floorRun, float winding, float minRadius,
        RootWood wood, float woodReach, float woodBlend) {

    // Blocks are sampled at whole coordinates, so a root much thinner than this has gaps along it.
    private static final float THINNEST = 1;

    /**
     * @param count       how many a hole has
     * @param radius      a great root's radius where it leaves the wall under the mouth
     * @param endRadius   its radius where it reaches the floor
     * @param fall        blocks it comes down for each block it goes round the hole, about
     * @param touchChance chance that it turns aside to touch a disc it passes, its top level with the disc's rim
     */
    public record Great(InclusiveRange<Integer> count, float radius, float endRadius, float fall, float touchChance) {
        static final Codec<Great> CODEC = RecordCodecBuilder.create(i -> i.group(
                InclusiveRange.codec(Codec.intRange(0, 16)).fieldOf("count").forGetter(Great::count),
                Codec.floatRange(THINNEST, 16).fieldOf("radius").forGetter(Great::radius),
                Codec.floatRange(THINNEST, 16).fieldOf("end_radius").forGetter(Great::endRadius),
                Codec.floatRange(0.1F, 2).fieldOf("fall").forGetter(Great::fall),
                Codec.floatRange(0, 1).fieldOf("touch_chance").forGetter(Great::touchChance)
        ).apply(i, Great::new));
    }

    /**
     * @param count  how many a hole has
     * @param radius a crossing root's radius
     */
    public record Crossing(InclusiveRange<Integer> count, float radius) {
        static final Codec<Crossing> CODEC = RecordCodecBuilder.create(i -> i.group(
                InclusiveRange.codec(Codec.intRange(0, 8)).fieldOf("count").forGetter(Crossing::count),
                Codec.floatRange(THINNEST, 16).fieldOf("radius").forGetter(Crossing::radius)
        ).apply(i, Crossing::new));
    }

    /**
     * @param every    blocks of root for each branch, before {@code byHeight}
     * @param byHeight how many times as often branches come at the hole's floor and at its top
     * @param shrink   a branch's radius as a share of its parent's where it leaves it
     * @param depth    how many times over a root may branch: 1 for branches only, 2 for branches of branches
     * @param reach    blocks a branch may go to find another root, a disc, the floor or the wall to end in
     */
    public record Branches(float every, DiscTheme.Ramp byHeight, float shrink, int depth, float reach) {
        static final Codec<Branches> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(8, 4096).fieldOf("every").forGetter(Branches::every),
                DiscTheme.Ramp.BY_HEIGHT.optionalFieldOf("by_height", DiscTheme.Ramp.EVEN).forGetter(Branches::byHeight),
                Codec.floatRange(0.3F, 0.95F).fieldOf("shrink").forGetter(Branches::shrink),
                Codec.intRange(0, 3).fieldOf("depth").forGetter(Branches::depth),
                Codec.floatRange(8, 256).fieldOf("reach").forGetter(Branches::reach)
        ).apply(i, Branches::new));
    }

    /**
     * @param chance   chance that a disc has a link up to a disc above it that is near enough
     * @param radius   a link's radius
     * @param maxSlope the most a link climbs for each block it goes forward; a link that cannot reach its disc within
     *                 this is not grown
     */
    public record Links(float chance, float radius, float maxSlope) {
        static final Codec<Links> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0, 1).fieldOf("chance").forGetter(Links::chance),
                Codec.floatRange(THINNEST, 8).fieldOf("radius").forGetter(Links::radius),
                Codec.floatRange(0.2F, 1).fieldOf("max_slope").forGetter(Links::maxSlope)
        ).apply(i, Links::new));
    }

    public static final Codec<RootSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Great.CODEC.fieldOf("great").forGetter(RootSettings::great),
            Crossing.CODEC.fieldOf("crossing").forGetter(RootSettings::crossing),
            Branches.CODEC.fieldOf("branches").forGetter(RootSettings::branches),
            Links.CODEC.fieldOf("links").forGetter(RootSettings::links),
            Codec.floatRange(0, 256).fieldOf("floor_run").forGetter(RootSettings::floorRun),
            Codec.floatRange(0, 1).fieldOf("winding").forGetter(RootSettings::winding),
            Codec.floatRange(THINNEST, 8).fieldOf("min_radius").forGetter(RootSettings::minRadius),
            RootWood.CODEC.fieldOf("wood").forGetter(RootSettings::wood),
            Codec.floatRange(4, 256).fieldOf("wood_reach").forGetter(RootSettings::woodReach),
            Codec.floatRange(1, 128).fieldOf("wood_blend").forGetter(RootSettings::woodBlend)
    ).apply(i, RootSettings::new));
}
