package dev.syrval.overgrownabyss.ravine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.IntStream;

/**
 * Where the ruins of a cell's discs stand (see {@link DiscRuins}). Each is a pure function of the cell's hash and its disc's
 * place in the layout, like the discs themselves, so the sites are found once for a cell and then only read.
 *
 * <p>A ruin's kind asks for a round of ground and the air over it. A place has that room when the round lies inside the disc's
 * rim, its ground steps by no more than a block, little of it is water, no other ruin of the disc is near, no stem or root
 * passes through it, and the air over it is open: under the roof of the dome where the disc is in the rock, and under
 * whatever disc is above. Each ruin has a few places drawn for it and the kinds put in an order drawn by weight; it is of the
 * first kind in that order with room at one of the places, and is left out if none has any. So a tall kind stands where there
 * is the height for it, and a lower one takes its place where there is not.
 */
final class DiscRuinSites {
    static final int MAX_PER_DISC = 6;
    // Blocks kept between a ruin's round and the disc's rim, another ruin's round, or a stem.
    static final double MARGIN = 2;
    // The most the ground may step under a ruin. A piece stands on the lower ground, so it is at most this deep in the higher.
    static final int MAX_STEP = 1;
    // The most of a ruin's round that may be water.
    static final double MAX_WET = 0.2;
    private static final int HASH_BASE = 800_000;
    // Hash indices kept apart for each disc, and within those for each of its ruins: one for each kind's place in the
    // order, two for each place tried, and one for the seed.
    private static final int PER_DISC = 256;
    private static final int PER_RUIN = 40;
    private static final int TRIES = 8;
    // The carve becomes blocks a noise cell at a time, so air is only counted on where it is this far from any rock.
    private static final double CLEAR = 1;
    // The air is tested over the middle of a round and over this many points round its edge and round half way out.
    private static final int SPOKES = 8;
    // The ground may be a block higher than where a piece stands, so the air is tested from the third block up.
    private static final int FIRST_AIR = 3;

    private DiscRuinSites() {}

    /** The sites of every ruin in a cell, in the order of the discs they stand on. */
    static List<RuinSite> of(RavineSettings settings, RavineBounds bounds, RavineCell cell, DiscLayout layout, List<Optional<DiscTheme>> themes) {
        var hole = new Hole(settings, bounds, cell, layout);
        var sites = new ArrayList<RuinSite>();
        for (int i = 0; i < layout.discs().size(); i++) {
            Optional<DiscTheme> theme = themes.get(i);
            Optional<DiscRuins> ruins = theme.flatMap(DiscTheme::ruins);
            if (ruins.isPresent() && RavineCells.unit(cell.hash(), base(i)) < ruins.get().chance()) {
                hole.ruinsOn(i, theme.get(), ruins.get(), sites);
            }
        }
        return List.copyOf(sites);
    }

    private static int base(int disc) {
        return HASH_BASE + disc * PER_DISC;
    }

    /**
     * The kinds in an order drawn by weight, given a uniform draw in [0, 1) for each: a kind of twice the weight is twice as
     * likely to come first, and again to come next among those that are left. Each draw is stretched by its kind's weight and
     * the least goes first, which is that order without drawing over again for each place in it.
     */
    static List<DiscRuins.Kind> inOrder(List<DiscRuins.Kind> kinds, List<Double> units) {
        record Drawn(DiscRuins.Kind kind, double key) {}
        var drawn = new ArrayList<Drawn>(kinds.size());
        for (int i = 0; i < kinds.size(); i++) {
            drawn.add(new Drawn(kinds.get(i), -Math.log(1 - units.get(i)) / kinds.get(i).weight()));
        }
        drawn.sort(Comparator.comparingDouble(Drawn::key));
        return drawn.stream().map(Drawn::kind).toList();
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

    private record Hole(RavineSettings settings, RavineBounds bounds, RavineCell cell, DiscLayout layout) {

        void ruinsOn(int index, DiscTheme theme, DiscRuins ruins, List<RuinSite> sites) {
            Disc disc = layout.discs().get(index);
            List<DiscRuins.Kind> fitting = ruins.kinds().stream()
                    .filter(kind -> kind.weight() > 0 && kind.radius() + MARGIN <= disc.radius())
                    .toList();
            if (fitting.isEmpty()) {
                return;
            }
            int wanted = Math.clamp(Math.round(Math.PI * disc.radius() * disc.radius() / ruins.every()), 1, MAX_PER_DISC);
            var standing = new ArrayList<Round>();
            for (int ruin = 0; ruin < wanted; ruin++) {
                int slot = base(index) + 1 + ruin * PER_RUIN;
                List<Double> units = IntStream.range(0, fitting.size()).mapToObj(place -> RavineCells.unit(cell.hash(), slot + place)).toList();
                stand(index, disc, theme, inOrder(fitting, units), slot + DiscRuins.MAX_KINDS, standing).ifPresent(sites::add);
            }
        }

        // One ruin: the first kind in the order given that has room at one of the places drawn from {@code slot} on.
        private Optional<RuinSite> stand(int index, Disc disc, DiscTheme theme, List<DiscRuins.Kind> kinds, int slot, List<Round> standing) {
            for (DiscRuins.Kind kind : kinds) {
                for (int attempt = 0; attempt < TRIES; attempt++) {
                    double angle = 2 * Math.PI * RavineCells.unit(cell.hash(), slot + 2 * attempt);
                    // The root of the draw spreads the places evenly over the ground instead of gathering them in the middle.
                    double out = Math.sqrt(RavineCells.unit(cell.hash(), slot + 1 + 2 * attempt)) * (disc.radius() - kind.radius() - MARGIN);
                    var round = new Round(
                            (int) Math.floor(disc.x() + out * Math.cos(angle)), (int) Math.floor(disc.z() + out * Math.sin(angle)), kind.radius());
                    OptionalInt ground = groundFor(index, disc, theme, kind, round, standing);
                    if (ground.isPresent()) {
                        standing.add(round);
                        long seed = RavineCells.bits(cell.hash(), slot + 2 * TRIES);
                        return Optional.of(new RuinSite(round.x(), ground.getAsInt() + 1 - kind.sink(), round.z(), kind.pool(), seed));
                    }
                }
            }
            return Optional.empty();
        }

        /** The height of the lowest ground in a round, which a piece of this kind stands on, or empty if the round has no room for one. */
        private OptionalInt groundFor(int index, Disc disc, DiscTheme theme, DiscRuins.Kind kind, Round round, List<Round> standing) {
            double fromAxis = round.distanceTo(disc.x(), disc.z());
            // Putting the middle on a whole block may have moved the round out past the rim's margin.
            if (fromAxis + round.radius() + MARGIN > disc.radius()) {
                return OptionalInt.empty();
            }
            int lowest = disc.topBlockAt(Math.max(0, fromAxis - round.radius()));
            if (disc.topBlockAt(fromAxis + round.radius()) - lowest > MAX_STEP) {
                return OptionalInt.empty();
            }
            if (standing.stream().anyMatch(other -> other.distanceTo(round.centreX(), round.centreZ()) < other.radius() + round.radius() + MARGIN)) {
                return OptionalInt.empty();
            }
            int top = lowest + Math.max(kind.height(), FIRST_AIR);
            if (isWet(index, disc, theme, round) || isCrossed(disc, round, lowest, top) || !isOpenOver(round, lowest, top)) {
                return OptionalInt.empty();
            }
            return OptionalInt.of(lowest);
        }

        // A stream may run under a ruin, whose floor then bridges it; a pond may not lie under one. Every other column is asked.
        private boolean isWet(int index, Disc disc, DiscTheme theme, Round round) {
            if (theme.water().isEmpty()) {
                return false;
            }
            DiscWater water = theme.water().get();
            int reach = (int) Math.ceil(round.radius());
            int columns = 0;
            int wet = 0;
            for (int x = round.x() - reach; x <= round.x() + reach; x += 2) {
                for (int z = round.z() - reach; z <= round.z() + reach; z += 2) {
                    if (round.distanceTo(x + 0.5, z + 0.5) <= round.radius()) {
                        columns++;
                        wet += water.depthAt(disc, cell.hash(), index, x, z) > 0 ? 1 : 0;
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

        private boolean isOpenOver(Round round, int lowest, int top) {
            if (!isOpenOver(round.x(), round.z(), lowest, top)) {
                return false;
            }
            for (int spoke = 0; spoke < SPOKES; spoke++) {
                double angle = 2 * Math.PI * spoke / SPOKES;
                for (double out = round.radius(); out > round.radius() / 4; out /= 2) {
                    int x = (int) Math.floor(round.centreX() + out * Math.cos(angle));
                    int z = (int) Math.floor(round.centreZ() + out * Math.sin(angle));
                    if (!isOpenOver(x, z, lowest, top)) {
                        return false;
                    }
                }
            }
            return true;
        }

        // Every other block up, and the highest block a piece may reach.
        private boolean isOpenOver(int x, int z, int lowest, int top) {
            for (int y = lowest + FIRST_AIR; y < top; y += 2) {
                if (!isOpen(x, y, z)) {
                    return false;
                }
            }
            return isOpen(x, top, z);
        }

        private boolean isOpen(int x, int y, int z) {
            return RavineShape.signedDistance(settings, bounds, cell, layout, x, y, z) <= -CLEAR
                    && RavineShape.rockDistance(settings, bounds, cell, layout, x, y, z) >= CLEAR;
        }
    }
}
