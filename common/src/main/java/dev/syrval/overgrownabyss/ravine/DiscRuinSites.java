package dev.syrval.overgrownabyss.ravine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.ToDoubleFunction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Where the ruins of a cell's discs stand (see {@link DiscRuins}). Each is a pure function of the cell's hash, its disc's
 * place in the layout and the pieces the level has (see {@link RuinPieces}), like the discs themselves, so the sites are found
 * once for a cell and then only read.
 *
 * <p>A piece asks for a round of ground, the air over it and, if some of its layers lie in the ground, the rock under it. A
 * place has that room when the round lies inside the disc's rim, its ground steps by no more than a block, little of it is
 * water, no other ruin is near (of its own disc, or of another whose platform runs into this one at the same height), no stem
 * or root passes through it, the air over it is open (under the roof of the dome where the disc is in the rock, and under
 * whatever disc is above) and there is rock where the piece lies in the ground.
 * Each ruin has a few places drawn for it and the kinds put in an order drawn by weight, and each kind's pieces in an order
 * drawn by theirs; the ruin is the first piece in that order with room at one of the places, and is left out if none has any.
 * So a tall piece stands where there is the height for it, and a lower one takes its place where there is not.
 *
 * <p>Ruins are more or less frequent with a disc's height in its hole, and so is each kind: see {@link DiscRuins#byHeight}.
 * A ruin of one of the theme's own kinds is also given the loot table of its disc: see {@link DiscRuins.Loot}.
 *
 * <p>A theme's troves are stood the same way and by the same rules, from a list and draws of their own (see
 * {@link DiscSites}), once every ruin of the cell stands: so a theme given troves keeps each of its ruins where it was, and a
 * trove keeps clear of them. A trove also keeps clear of the hole's roots, which a ruin does not: no piece replaces a root's
 * wood, so a ruin is built round one that passes through it, and of a trove's few blocks nothing would be left.
 */
final class DiscRuinSites {
    static final int MAX_PER_DISC = 6;
    // Blocks kept between a ruin's round and the disc's rim, another ruin's round, or a stem.
    static final double MARGIN = 2;
    // The most the ground may step under a ruin. A piece stands on the lower ground, so it is at most this deep in the higher.
    static final int MAX_STEP = 1;
    // The most of a ruin's round that may be water.
    static final double MAX_WET = 0.2;
    // Hash indices kept apart for each disc, from the first of its list's (see DiscSites): one for whether it has ruins, then
    // one for each of its ruins, which all of that ruin's own draws are made from.
    private static final int PER_DISC = 256;
    private static final int TRIES = 8;
    // A ruin's own draws: two for each place tried, one for the seed, then one for each kind's place in the order. The draws
    // that order a kind's pieces are made from one more each, kept apart below zero.
    private static final int SEED_DRAW = 2 * TRIES;
    private static final int FIRST_KIND_DRAW = SEED_DRAW + 1;
    // The draw that rolls a site's loot table from a mix, far from the kinds' draws so that none of the others is moved.
    private static final int LOOT_DRAW = 1 << 20;
    // Air is only counted on where it is this far from any rock, so that a piece touches neither the roof nor a wall beside it.
    private static final double CLEAR = 1;
    // The air and the ground are tested at the middle of a round and at this many points round its edge and round half way out.
    private static final int SPOKES = 8;
    // The ground may be a block higher than where a piece stands, so the air is tested from the third block up.
    private static final int FIRST_AIR = 3;

    private DiscRuinSites() {}

    /** A ruin or a trove, which disc of the layout it stands on, and the radius of the round of ground kept for it. */
    record Placed(int disc, RuinSite site, double radius) {}

    /** What stands in a cell: its ruins and its troves, each with the disc it stands on, in the order of those discs. */
    record Stood(List<Placed> ruins, List<Placed> troves) {
        Stood {
            ruins = List.copyOf(ruins);
            troves = List.copyOf(troves);
        }

        List<Placed> all() {
            var all = new ArrayList<>(ruins);
            all.addAll(troves);
            return all;
        }
    }

    /** Every ruin and every trove in a cell whose hole has these {@code roots}. */
    static Stood onDiscs(
            RavineSettings settings, RavineBounds bounds, RavineCell cell, DiscLayout layout, List<Optional<DiscTheme>> themes,
            List<DiscTraits> traits, RuinPieces pieces, RootLayout roots) {
        var hole = new Hole(settings, bounds, cell, layout, roots);
        // Discs of one layer run into one another, so a ruin keeps clear of those on every disc and not only of its own.
        var standing = new ArrayList<Standing>();
        List<Placed> ruins = hole.stand(DiscSites.RUINS, themes, traits, pieces, standing);
        return new Stood(ruins, hole.stand(DiscSites.TROVES, themes, traits, pieces, standing));
    }

    private static int base(DiscSites which, int disc) {
        return which.hashBase() + disc * PER_DISC;
    }

    /**
     * Things in an order drawn by weight, given a uniform draw in [0, 1) for each: one of twice the weight is twice as likely to
     * come first, and again to come next among those that are left. Each draw is stretched by its thing's weight and the least
     * goes first, which is that order without drawing over again for each place in it.
     */
    static <T> List<T> inOrder(List<T> things, ToDoubleFunction<T> weight, ToDoubleFunction<T> unit) {
        record Drawn<E>(E thing, double key) {}
        var drawn = new ArrayList<Drawn<T>>(things.size());
        for (T thing : things) {
            drawn.add(new Drawn<>(thing, -Math.log(1 - unit.applyAsDouble(thing)) / weight.applyAsDouble(thing)));
        }
        drawn.sort(Comparator.comparingDouble(Drawn::key));
        return drawn.stream().map(Drawn::thing).toList();
    }

    /**
     * A disc that holds ruins: which of its theme's two lists they are of, its place in the layout, its theme, the loot
     * of the theme's own ruins in that list, and the traits of the disc, which the table of each site is chosen by.
     */
    private record Host(DiscSites which, int index, Disc disc, DiscTheme theme, DiscRuins.Loot loot, DiscTraits traits) {}

    /**
     * A kind as one disc has it: its place among the level's kinds, its weight at the disc's height, its pieces the disc is wide
     * enough for, and the loot table a ruin of it is given there, which a borrowed kind has none of.
     */
    private record Offered(int index, double weight, List<RuinPieces.Piece> pieces, boolean ownLoot) {}

    /** The room a piece needs. Pieces of one size have the same room, so a size that found none is not tried again for a ruin. */
    private record Size(double radius, int height, int sink) {
        static Size of(RuinPieces.Piece piece) {
            return new Size(piece.radius(), piece.height(), piece.sink());
        }
    }

    /** A ruin that has been stood: its disc, its round, and the heights its piece lies between. */
    private record Standing(int disc, Round round, int bottom, int top) {

        // Two ruins of one disc are kept apart whatever their heights, as its ground between them is one slope.
        boolean isNear(int otherDisc, Round other, int otherBottom, int otherTop) {
            return (disc == otherDisc || bottom <= otherTop && top >= otherBottom)
                    && round.distanceTo(other.centreX(), other.centreZ()) < round.radius() + other.radius() + MARGIN;
        }
    }

    /** A test of one column of a round. */
    @FunctionalInterface
    private interface Column {
        boolean holds(int x, int z);
    }

    /** The ground kept for one ruin: the columns within {@code radius} of the middle of the block at {@code (x, z)}. */
    private record Round(int x, int z, double radius) {
        double centreX() {
            return x + 0.5;
        }

        double centreZ() {
            return z + 0.5;
        }

        double distanceTo(double px, double pz) {
            return Math.hypot(px - centreX(), pz - centreZ());
        }
    }

    private record Hole(RavineSettings settings, RavineBounds bounds, RavineCell cell, DiscLayout layout, RootLayout roots) {

        /** One of the two lists on every disc whose theme has it, in the order of the discs; each piece stood is added to {@code standing}. */
        List<Placed> stand(DiscSites which, List<Optional<DiscTheme>> themes, List<DiscTraits> traits, RuinPieces pieces, List<Standing> standing) {
            var sites = new ArrayList<Placed>();
            for (int i = 0; i < layout.discs().size(); i++) {
                Optional<DiscTheme> theme = themes.get(i);
                Optional<DiscRuins> ruins = theme.flatMap(which::of);
                if (ruins.isEmpty()) {
                    continue;
                }
                double height = traits.get(i).height();
                List<RuinPieces.Kind> kinds = pieces.of(ruins.get());
                if (!kinds.isEmpty() && RavineCells.unit(cell.hash(), base(which, i)) < ruins.get().chance() * ruins.get().byHeight().at(height)) {
                    var host = new Host(which, i, layout.discs().get(i), theme.get(), ruins.get().loot(), traits.get(i));
                    ruinsOn(host, ruins.get(), kinds, height, standing, sites);
                }
            }
            return sites;
        }

        private void ruinsOn(Host host, DiscRuins ruins, List<RuinPieces.Kind> kinds, double height, List<Standing> standing, List<Placed> sites) {
            Disc disc = host.disc();
            var offered = new ArrayList<Offered>(kinds.size());
            for (int i = 0; i < kinds.size(); i++) {
                RuinPieces.Kind kind = kinds.get(i);
                double weight = kind.weight() * kind.byHeight().at(height);
                List<RuinPieces.Piece> fitting = kind.pieces().stream().filter(piece -> piece.radius() + MARGIN <= disc.radius()).toList();
                if (weight > 0 && !fitting.isEmpty()) {
                    offered.add(new Offered(i, weight, fitting, !kind.borrowed()));
                }
            }
            if (offered.isEmpty()) {
                return;
            }
            double area = Math.PI * disc.radius() * disc.radius();
            int wanted = Math.clamp(Math.round(area / ruins.every() * ruins.byHeight().at(height)), 1, MAX_PER_DISC);
            for (int ruin = 0; ruin < wanted; ruin++) {
                long draws = RavineCells.bits(cell.hash(), base(host.which(), host.index()) + 1 + ruin);
                List<Offered> order = inOrder(offered, Offered::weight, kind -> RavineCells.unit(draws, FIRST_KIND_DRAW + kind.index()));
                standOne(host, order, draws, standing).ifPresent(sites::add);
            }
        }

        // One ruin: the first piece with room, of the kinds in the order given and of each kind's pieces in an order drawn by
        // their weights in its pool.
        private Optional<Placed> standOne(Host host, List<Offered> kinds, long draws, List<Standing> standing) {
            var full = new HashSet<Size>();
            for (Offered kind : kinds) {
                long order = RavineCells.bits(draws, -1 - kind.index());
                for (RuinPieces.Piece piece : inOrder(kind.pieces(), RuinPieces.Piece::weight, each -> RavineCells.unit(order, each.element()))) {
                    if (full.contains(Size.of(piece))) {
                        continue;
                    }
                    Optional<Placed> site = place(host, piece, kind.ownLoot(), draws, standing);
                    if (site.isPresent()) {
                        return site;
                    }
                    full.add(Size.of(piece));
                }
            }
            return Optional.empty();
        }

        // A piece at the first of the places drawn for its ruin where it has room.
        private Optional<Placed> place(
                Host host, RuinPieces.Piece piece, boolean ownLoot, long draws, List<Standing> standing) {
            for (int attempt = 0; attempt < TRIES; attempt++) {
                Round round = roundAt(host.disc(), piece, draws, attempt);
                OptionalInt ground = groundFor(host, piece, round, standing);
                if (ground.isPresent()) {
                    standing.add(new Standing(host.index(), round, ground.getAsInt() - piece.sink(), topOver(ground.getAsInt(), piece)));
                    long seed = RavineCells.bits(draws, SEED_DRAW);
                    Optional<ResourceKey<LootTable>> loot = ownLoot
                            ? host.loot().tableFor(host.traits(), RavineCells.unit(draws, LOOT_DRAW)) : Optional.empty();
                    var site = new RuinSite(round.x(), ground.getAsInt() + 1 - piece.sink(), round.z(), piece.pool(), piece.element(), seed, loot);
                    return Optional.of(new Placed(host.index(), site, round.radius()));
                }
            }
            return Optional.empty();
        }

        private Round roundAt(Disc disc, RuinPieces.Piece piece, long draws, int attempt) {
            // A piece that lies deeper than the platform is thick only has rock under it over the flare of the disc's stem or
            // where the disc runs into the wall, and places drawn evenly rarely fall on the flare: so it is tried there first.
            if (attempt == 0 && piece.sink() + 1 >= settings.discs().floorThickness()) {
                return new Round((int) Math.floor(disc.x()), (int) Math.floor(disc.z()), piece.radius());
            }
            double angle = 2 * Math.PI * RavineCells.unit(draws, 2 * attempt);
            // The root of the draw spreads the places evenly over the ground instead of gathering them in the middle.
            double out = Math.sqrt(RavineCells.unit(draws, 2 * attempt + 1)) * (disc.radius() - piece.radius() - MARGIN);
            return new Round((int) Math.floor(disc.x() + out * Math.cos(angle)), (int) Math.floor(disc.z() + out * Math.sin(angle)), piece.radius());
        }

        /** The height of the lowest ground in a round, which the piece stands on, or empty if the round has no room for it. */
        private OptionalInt groundFor(Host host, RuinPieces.Piece piece, Round round, List<Standing> standing) {
            Disc disc = host.disc();
            double fromAxis = round.distanceTo(disc.x(), disc.z());
            // Putting the middle on a whole block may have moved the round out past the rim's margin.
            if (fromAxis + round.radius() + MARGIN > disc.radius()) {
                return OptionalInt.empty();
            }
            int lowest = disc.topBlockAt(Math.max(0, fromAxis - round.radius()));
            if (disc.topBlockAt(fromAxis + round.radius()) - lowest > MAX_STEP) {
                return OptionalInt.empty();
            }
            int top = topOver(lowest, piece);
            if (standing.stream().anyMatch(other -> other.isNear(host.index(), round, lowest - piece.sink(), top))) {
                return OptionalInt.empty();
            }
            if (isWet(host, round) || isCrossed(disc, round, lowest, top)
                    || !isRockUnder(round, lowest, piece.sink()) || !isOpenOver(round, lowest, top)) {
                return OptionalInt.empty();
            }
            if (host.which() == DiscSites.TROVES && isRooted(round, lowest + 1 - piece.sink(), top)) {
                return OptionalInt.empty();
            }
            return OptionalInt.of(lowest);
        }

        // Whether a root of the hole's comes within the margin of a round between two heights. A root's knots are about a
        // block apart along it, so each is asked.
        private boolean isRooted(Round round, int bottom, int top) {
            for (Root root : roots.roots()) {
                for (Root.Knot knot : root.knots()) {
                    if (knot.y() + knot.radius() >= bottom && knot.y() - knot.radius() <= top
                            && round.distanceTo(knot.x(), knot.z()) < round.radius() + knot.radius() + MARGIN) {
                        return true;
                    }
                }
            }
            return false;
        }

        // The highest block kept clear for a piece that stands on ground at this height.
        private static int topOver(int ground, RuinPieces.Piece piece) {
            return ground + Math.max(piece.height(), FIRST_AIR);
        }

        // A stream may run under a ruin, whose floor then bridges it; a pond may not lie under one. Every other column is asked.
        private boolean isWet(Host host, Round round) {
            if (host.theme().water().isEmpty()) {
                return false;
            }
            DiscWater water = host.theme().water().get();
            int reach = (int) Math.ceil(round.radius());
            int columns = 0;
            int wet = 0;
            for (int x = round.x() - reach; x <= round.x() + reach; x += 2) {
                for (int z = round.z() - reach; z <= round.z() + reach; z += 2) {
                    if (round.distanceTo(x + 0.5, z + 0.5) <= round.radius()) {
                        columns++;
                        wet += water.depthAt(host.disc(), cell.hash(), host.index(), x, z) > 0 ? 1 : 0;
                    }
                }
            }
            return wet > MAX_WET * columns;
        }

        // A stem is thinner than the gaps between the columns where the air is tested, so stems and roots are looked for by name.
        private boolean isCrossed(Disc disc, Round round, int lowest, int top) {
            DiscShape shape = settings.discs();
            for (Disc other : layout.discs()) {
                double apart = round.distanceTo(other.x(), other.z());
                boolean crosses = switch (other.support()) {
                    case Disc.Support.Standing stem -> other != disc && other.undersideAt(shape, 0) > lowest && stem.bottom() <= top
                            && apart < round.radius() + shape.stemRadiusFor(other.radius()) + MARGIN;
                    case Disc.Support.Hanging root -> root.top() > lowest && other.floor() <= top
                            && apart < round.radius() + shape.rootRadiusFor(other.radius()) + MARGIN;
                };
                if (crosses) {
                    return true;
                }
            }
            return false;
        }

        // The middle of a round, and points round its edge and round half way out.
        private boolean everyColumn(Round round, Column test) {
            if (!test.holds(round.x(), round.z())) {
                return false;
            }
            for (int spoke = 0; spoke < SPOKES; spoke++) {
                double angle = 2 * Math.PI * spoke / SPOKES;
                for (double out = round.radius(); out > round.radius() / 4; out /= 2) {
                    int x = (int) Math.floor(round.centreX() + out * Math.cos(angle));
                    int z = (int) Math.floor(round.centreZ() + out * Math.sin(angle));
                    if (!test.holds(x, z)) {
                        return false;
                    }
                }
            }
            return true;
        }

        // The layers of a piece that lie in the ground must lie in rock, so that nothing of it shows under the disc. A
        // platform is a few blocks thick: a piece with more layers than that in the ground only has the rock where the stem
        // flares out under the platform, or where the disc runs into the wall of the hole and the ground there was never opened.
        private boolean isRockUnder(Round round, int lowest, int sink) {
            return sink == 0 || everyColumn(round, (x, z) -> {
                for (int y = lowest - sink; y < lowest; y++) {
                    if (!isRock(x, y, z)) {
                        return false;
                    }
                }
                return true;
            });
        }

        // Every other block up, and the highest block a piece may reach.
        private boolean isOpenOver(Round round, int lowest, int top) {
            return everyColumn(round, (x, z) -> {
                for (int y = lowest + FIRST_AIR; y < top; y += 2) {
                    if (!isOpen(x, y, z)) {
                        return false;
                    }
                }
                return isOpen(x, top, z);
            });
        }

        private boolean isOpen(int x, int y, int z) {
            return RavineShape.signedDistance(settings, bounds, cell, layout, x, y, z) <= -CLEAR
                    && RavineShape.rockDistance(settings, bounds, cell, layout, x, y, z) >= CLEAR;
        }

        // What the level builds there: the rock put back for a disc, or ground the carve left alone. The carve and the rock
        // are worked out for each block, so this needs no margin; the ground left alone may still hold a cave of the terrain's.
        private boolean isRock(int x, int y, int z) {
            return RavineShape.rockDistance(settings, bounds, cell, layout, x, y, z) < 0
                    || RavineShape.signedDistance(settings, bounds, cell, layout, x, y, z) > 0;
        }
    }
}
