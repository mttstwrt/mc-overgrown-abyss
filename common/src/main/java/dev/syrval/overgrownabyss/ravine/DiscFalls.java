package dev.syrval.overgrownabyss.ravine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The falls of one hole (see {@link FallSettings}), found once for its cell and then only read.
 *
 * <p>A fall starts where a disc's own stream or pond comes up against the bank inside its rim. Still water is led on from there
 * through the bank, at the same level, to the platform's last column, the lip, which the game runs when the chunk comes
 * alive. Where it lands on a lower disc it is given a pool, and from the pool it may be led on to that disc's rim and over
 * again. A fall is only made where it comes down on a disc's top with room for a pool, or on the hole's floor: never on a
 * ruin, a root, a stem or the wall.
 *
 * <p>All of a fall's water but what the game runs is water that cannot leave: every column of it has ground, or water of its
 * own level, on each side but a lip's open one, and a block of its platform under it. Ground that stands higher than the
 * water is cut down to it, by no more than leaves that block.
 */
final class DiscFalls {
    static final DiscFalls NONE = new DiscFalls(List.of(), List.of());
    // Level ground kept round a fall's water, for a bank to stand on, and clear of what a disc grows.
    static final double CLEAR = 2.5;
    // Round the column a fall comes down a disc grows nothing this far out, so that no crown is spread under the water.
    static final double CLEAR_UNDER = 7;
    // Blocks up or down from a fall's water within which a place on a disc counts as beside it.
    private static final int BESIDE_IN_HEIGHT = 4;
    private static final int CHUNK_BITS = 4;
    private static final int HASH_BASE = 1_400_000;
    private static final int COUNT_DRAW = HASH_BASE;
    private static final int ORDER_DRAWS = HASH_BASE + 10;
    private static final int CURTAIN_DRAWS = HASH_BASE + 100_000;
    private static final int WIDTH_DRAWS = HASH_BASE + 200_000;
    private static final int CHAIN_DRAWS = HASH_BASE + 300_000;
    private static final int[][] BESIDE = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    // A start is looked for every so many blocks along the inside of a bank.
    private static final double BETWEEN_STARTS = 2;
    // Dry ground kept inside the rim of a disc whose theme has no water of its own to say how much.
    private static final int BANK = 3;
    // A fall keeps this far from a root, so that it is not run down one.
    private static final double FROM_A_ROOT = 1.5;
    // The side of the cubes roots' knots are sorted into: over the thickest a root may be with that margin.
    private static final int ROOT_GRID = 18;
    // The ways a pool's water is tried towards the rim: straight for the middle of the hole, then more and more to each side.
    private static final double[] TURNS = {0, 0.45, -0.45, 0.9, -0.9, 1.4, -1.4};

    /** One column of one disc. */
    private record Key(int disc, int x, int z) {}

    /**
     * One column of a fall's still water: the height of its top block, how deep it is, how many blocks of ground over it are
     * cleared away, and whether it is a lip, where the water leaves its disc.
     */
    record Column(int disc, int x, int z, int level, int depth, int cut, boolean lip) {}

    /**
     * One fall: the disc it leaves, the column it falls down and from what height, how wide its lip is, how many blocks of
     * open air it falls through, the disc it lands on if it does not go down to the floor, and how many falls came before it
     * in its chain.
     */
    record Fall(int from, int x, int z, int level, int width, int drop, OptionalInt onto, int step) {}

    private final List<Column> columns;
    private final List<Fall> falls;
    private final Map<Long, List<Column>> byChunk = new HashMap<>();
    private final Set<Key> held = new HashSet<>();
    // The heights of the falls' water near each column, by the column's place (see columnOf).
    private final Map<Long, List<Integer>> near = new HashMap<>();
    private final Map<Integer, List<DiscGround.Flat>> clear = new HashMap<>();

    private DiscFalls(List<Column> columns, List<Fall> falls) {
        this.columns = List.copyOf(columns);
        this.falls = List.copyOf(falls);
        for (Column column : this.columns) {
            byChunk.computeIfAbsent(chunkOf(column.x(), column.z()), chunk -> new ArrayList<>()).add(column);
            held.add(new Key(column.disc(), column.x(), column.z()));
            clear.computeIfAbsent(column.disc(), disc -> new ArrayList<>()).add(new DiscGround.Flat(column.x(), column.z(), CLEAR));
            keepClear(column.x(), column.z(), column.level(), CLEAR);
        }
        for (Fall fall : this.falls) {
            if (fall.onto().isPresent()) {
                keepClear(fall.x(), fall.z(), fall.level() - fall.drop(), CLEAR_UNDER);
            }
        }
    }

    private void keepClear(int x, int z, int level, double radius) {
        int reach = (int) Math.ceil(radius);
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                if (Math.hypot(dx, dz) <= radius) {
                    near.computeIfAbsent(columnOf(x + dx, z + dz), column -> new ArrayList<>()).add(level);
                }
            }
        }
    }

    /**
     * The falls of a cell. {@code ruins} are the rounds of ground each disc's ruins stand on, in the layout's order, which a
     * fall's water keeps out of.
     */
    static DiscFalls of(
            RavineSettings settings, RavineBounds bounds, RavineCell cell, DiscLayout layout, List<Optional<DiscTheme>> themes,
            List<DiscTraits> traits, List<List<DiscGround.Flat>> ruins, RootLayout roots) {
        if (settings.falls().isEmpty() || settings.cone().isEmpty()) {
            return NONE;
        }
        var grower = new Grower(settings, settings.cone().get(), settings.falls().get(), bounds, cell, layout, themes, traits, ruins, roots);
        grower.grow();
        return new DiscFalls(List.copyOf(grower.taken.values()), grower.grown);
    }

    List<Fall> falls() {
        return falls;
    }

    List<Column> columns() {
        return columns;
    }

    /** Calls {@code sink} with each column of a fall's water in the chunk whose lowest corner is {@code (minX, minZ)}. */
    void forEachColumn(int minX, int minZ, Consumer<Column> sink) {
        byChunk.getOrDefault(chunkOf(minX, minZ), List.of()).forEach(sink);
    }

    /** Whether a column of a disc is a fall's water, which then takes the place of any the disc's own theme would put there. */
    boolean holds(int disc, int x, int z) {
        return held.contains(new Key(disc, x, z));
    }

    /**
     * Whether a block is in a fall's still water or by it, or near where a fall comes down, where a disc grows nothing that
     * might stand in the water's way. Discs of one layer run into one another, so this goes by the place and not by the disc.
     */
    boolean isNear(int x, int y, int z) {
        for (int level : near.getOrDefault(columnOf(x, z), List.of())) {
            if (Math.abs(y - level) <= BESIDE_IN_HEIGHT) {
                return true;
            }
        }
        return false;
    }

    /** The rounds of a disc's top that a fall's water needs level: each of its columns and the ground by it. */
    List<DiscGround.Flat> clearOf(int disc) {
        return clear.getOrDefault(disc, List.of());
    }

    private static long chunkOf(int x, int z) {
        return columnOf(x >> CHUNK_BITS, z >> CHUNK_BITS);
    }

    private static long columnOf(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    /** Finds a hole's falls. It is used once and thrown away; what it found is what is kept. */
    private static final class Grower {
        private final RavineSettings settings;
        private final ConeSettings cone;
        private final FallSettings falls;
        private final RavineBounds bounds;
        private final RavineCell cell;
        private final DiscLayout layout;
        private final List<Disc> discs;
        private final DiscShape shape;
        private final List<Optional<DiscTheme>> themes;
        private final List<DiscTraits> traits;
        private final List<List<DiscGround.Flat>> ruins;
        private final RootLayout roots;
        // Every knot of every root, by the cube of ROOT_GRID blocks it lies in. No root is thicker than a cube is wide.
        private final Map<Long, List<Root.Knot>> knots = new HashMap<>();
        private final Map<Key, Column> taken = new LinkedHashMap<>();
        private final List<Fall> grown = new ArrayList<>();
        private final int[] startedOn;

        /** A place where a disc's own water meets its bank, and the way straight out from the disc's middle through it. */
        private record Start(int id, int disc, int x, int z, double angle) {}

        /** What a fall comes down on: the top of a disc at a height, or the floor. */
        private record Landing(OptionalInt onto, int y) {}

        Grower(
                RavineSettings settings, ConeSettings cone, FallSettings falls, RavineBounds bounds, RavineCell cell, DiscLayout layout,
                List<Optional<DiscTheme>> themes, List<DiscTraits> traits, List<List<DiscGround.Flat>> ruins, RootLayout roots) {
            this.settings = settings;
            this.cone = cone;
            this.falls = falls;
            this.bounds = bounds;
            this.cell = cell;
            this.layout = layout;
            this.discs = layout.discs();
            this.shape = settings.discs();
            this.themes = themes;
            this.traits = traits;
            this.ruins = ruins;
            this.roots = roots;
            this.startedOn = new int[discs.size()];
            for (Root root : roots.roots()) {
                for (Root.Knot knot : root.knots()) {
                    knots.computeIfAbsent(cubeOf(Math.floorDiv((int) Math.floor(knot.x()), ROOT_GRID), Math.floorDiv((int) Math.floor(knot.y()), ROOT_GRID),
                            Math.floorDiv((int) Math.floor(knot.z()), ROOT_GRID)), cube -> new ArrayList<>()).add(knot);
                }
            }
        }

        private static long cubeOf(int x, int y, int z) {
            return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
        }

        void grow() {
            int wanted = RootRoutes.countFor(falls.count(), RavineCells.unit(cell.hash(), COUNT_DRAW));
            List<Start> order = DiscRuinSites.inOrder(
                    starts().stream().filter(start -> weightOf(start) > 0).toList(), this::weightOf, start -> RavineCells.unit(cell.hash(), ORDER_DRAWS + start.id()));
            // Most places a fall could start have nothing under them but the floor. Left to the draw alone a hole would have
            // few pools and fewer cascades, so half its falls, rounded up, are first looked for among those that land on a disc.
            int started = start(order, (wanted + 1) / 2, true);
            start(order, wanted - started, false);
        }

        // Starts falls at the first places in the order that can have one, up to so many, and says how many it started.
        private int start(List<Start> order, int most, boolean ontoADiscOnly) {
            int started = 0;
            for (Start start : order) {
                if (started >= most) {
                    break;
                }
                if (startedOn[start.disc()] < falls.maxPerDisc() && start(start, ontoADiscOnly)) {
                    startedOn[start.disc()]++;
                    started++;
                }
            }
            return started;
        }

        private double weightOf(Start start) {
            return falls.byHeight().at(traits.get(start.disc()).height());
        }

        // Every place a fall might start: a column just inside a disc's bank that holds the disc's own water.
        private List<Start> starts() {
            var found = new ArrayList<Start>();
            for (int i = 0; i < discs.size(); i++) {
                Optional<DiscWater> water = themes.get(i).flatMap(DiscTheme::water);
                if (water.isEmpty()) {
                    continue;
                }
                Disc disc = discs.get(i);
                double ring = disc.radius() - water.get().bank() - 0.5;
                int around = Math.max(8, (int) (2 * Math.PI * ring / BETWEEN_STARTS));
                for (int step = 0; step < around; step++) {
                    double angle = 2 * Math.PI * step / around;
                    int x = (int) Math.round(disc.x() + ring * Math.cos(angle));
                    int z = (int) Math.round(disc.z() + ring * Math.sin(angle));
                    if (water.get().depthAt(disc, cell.hash(), i, x, z) > 0) {
                        found.add(new Start(found.size(), i, x, z, angle));
                    }
                }
            }
            return found;
        }

        private boolean start(Start start, boolean ontoADiscOnly) {
            Disc disc = discs.get(start.disc());
            int level = disc.topBlockAt(fromAxis(disc, start.x(), start.z()));
            int width = widthFor(start, disc);
            return run(start.disc(), start.x(), start.z(), start.angle(), level, width, 0, ontoADiscOnly)
                    || width > falls.width() && run(start.disc(), start.x(), start.z(), start.angle(), level, falls.width(), 0, ontoADiscOnly);
        }

        private int widthFor(Start start, Disc disc) {
            return falls.curtain()
                    .filter(curtain -> disc.radius() >= curtain.minRadius() && RavineCells.unit(cell.hash(), CURTAIN_DRAWS + start.id()) < curtain.chance())
                    .map(curtain -> RootRoutes.countFor(curtain.width(), RavineCells.unit(cell.hash(), WIDTH_DRAWS + start.id())))
                    .orElse(falls.width());
        }

        /**
         * Makes one fall from still water at a point of a disc, led one way to the rim, with the pool it lands in and whatever
         * goes on from that. False if there is no making it, or if it would reach the floor when only one that lands on a disc
         * is wanted, and then nothing of it is kept.
         *
         * <p>The fall's own column is the one beside its lip, or the middle one of those beside a wide lip.
         */
        private boolean run(int i, double fromX, double fromZ, double angle, int level, int width, int step, boolean ontoADiscOnly) {
            Disc disc = discs.get(i);
            Optional<List<Column>> channel = channel(i, fromX, fromZ, Math.cos(angle), Math.sin(angle), level, width);
            if (channel.isEmpty()) {
                return false;
            }
            // The game runs a lip's water into each open block beside it, and down from there. All of it has to come down on
            // the one thing, and the fall is reckoned from the column nearest the middle of them.
            List<int[]> drops = dropsFrom(disc, channel.get());
            Optional<Landing> landing = Optional.empty();
            for (int[] drop : drops) {
                Optional<Landing> under = landing(i, drop[0], drop[1], level);
                if (under.isEmpty() || landing.isPresent() && !landing.get().onto().equals(under.get().onto())
                        || level - under.get().y() - 1 < falls.minDrop() || hasARootInItsWay(drop[0], drop[1], under.get().y(), level)) {
                    return false;
                }
                landing = landing.filter(lowest -> lowest.y() <= under.get().y()).or(() -> under);
            }
            if (landing.isEmpty() || ontoADiscOnly && landing.get().onto().isEmpty()) {
                return false;
            }
            int[] middle = drops.get(drops.size() / 2);
            int x = middle[0];
            int z = middle[1];
            List<Column> pool = List.of();
            if (landing.get().onto().isPresent()) {
                Optional<List<Column>> made = pool(landing.get().onto().getAsInt(), x, z, width);
                if (made.isEmpty() || !drops.stream().allMatch(drop -> made.get().stream().anyMatch(column -> column.x() == drop[0] && column.z() == drop[1]))) {
                    return false;
                }
                pool = made.get();
            }
            keep(channel.get());
            keep(pool);
            grown.add(new Fall(i, x, z, level, width, level - landing.get().y() - 1, landing.get().onto(), step));
            if (!pool.isEmpty() && step + 1 < falls.maxSteps() && RavineCells.unit(cell.hash(), CHAIN_DRAWS + grown.size()) < falls.chain()) {
                goOn(landing.get().onto().getAsInt(), x, z, pool.getFirst().level(), width, step + 1);
            }
            return true;
        }

        // From a pool on to its disc's rim and over, by the first way that can be made. A disc's rim is in open air on the side
        // of the hole's middle and in the wall on the other, so that is the side to make for.
        private void goOn(int i, int poolX, int poolZ, int level, int width, int step) {
            double inward = Math.atan2(cell.centreZ() - poolZ, cell.centreX() - poolX);
            for (double turn : TURNS) {
                if (run(i, poolX, poolZ, inward + turn, level, width, step, false)) {
                    return;
                }
            }
        }

        // The open columns beside a channel's lips, outside the platform, in the order they lie along the rim.
        private static List<int[]> dropsFrom(Disc disc, List<Column> channel) {
            var drops = new ArrayList<int[]>();
            for (Column column : channel) {
                for (int[] step : BESIDE) {
                    int x = column.x() + step[0];
                    int z = column.z() + step[1];
                    if (column.lip() && fromAxis(disc, x, z) >= disc.radius() && drops.stream().noneMatch(drop -> drop[0] == x && drop[1] == z)) {
                        drops.add(new int[] {x, z});
                    }
                }
            }
            drops.sort(Comparator.comparingDouble(drop -> Math.atan2(drop[1] - disc.z(), drop[0] - disc.x())));
            return drops;
        }

        private void keep(List<Column> more) {
            for (Column column : more) {
                taken.putIfAbsent(new Key(column.disc(), column.x(), column.z()), column);
            }
        }

        /**
         * The water of a channel {@code width} wide, from a point of a disc straight on to its rim at one level; empty if any of
         * it would not stay where it is put, or if it comes to no lip.
         */
        private Optional<List<Column>> channel(int i, double fromX, double fromZ, double ux, double uz, int level, int width) {
            Disc disc = discs.get(i);
            var strip = new LinkedHashSet<Key>();
            for (double along = 0; fromAxis(disc, fromX + ux * along, fromZ + uz * along) < disc.radius(); along += 0.5) {
                for (int lane = 0; lane < width; lane++) {
                    double aside = lane - (width - 1) / 2.0;
                    int x = (int) Math.round(fromX + ux * along - uz * aside);
                    int z = (int) Math.round(fromZ + uz * along + ux * aside);
                    if (fromAxis(disc, x, z) < disc.radius()) {
                        strip.add(new Key(i, x, z));
                    }
                }
            }
            var columns = new ArrayList<Column>();
            boolean reachesTheRim = false;
            for (Key key : strip) {
                Column already = taken.get(key);
                if (already != null && already.level() == level) {
                    // The pool this channel leaves, or a channel it runs into: the same water.
                    continue;
                }
                Optional<Column> column = already == null ? column(disc, key, level, 1, strip) : Optional.empty();
                if (column.isEmpty()) {
                    return Optional.empty();
                }
                columns.add(column.get());
                reachesTheRim |= column.get().lip();
            }
            return reachesTheRim ? Optional.of(columns) : Optional.empty();
        }

        /**
         * A pool round the column a fall comes down on; empty where the disc has no room for one or nothing to hold it. A wide
         * fall has a wider pool.
         */
        private Optional<List<Column>> pool(int i, int middleX, int middleZ, int width) {
            Disc disc = discs.get(i);
            Optional<DiscTheme> theme = themes.get(i);
            FallSettings.Pool pool = falls.pool();
            // Left as the terrain's own rock, the ground round the water could be a cave, and the water would drain into it.
            if (theme.isEmpty() || !theme.get().palette().coversTop(pool.depth() + 1)) {
                return Optional.empty();
            }
            double radius = Math.max(pool.radius(), width / 2.0 + 2);
            if (fromAxis(disc, middleX, middleZ) + radius > disc.radius() - theme.get().water().map(DiscWater::bank).orElse(BANK)) {
                return Optional.empty();
            }
            int reach = (int) Math.ceil(radius);
            var round = new LinkedHashSet<Key>();
            int lowest = Integer.MAX_VALUE;
            for (int x = middleX - reach; x <= middleX + reach; x++) {
                for (int z = middleZ - reach; z <= middleZ + reach; z++) {
                    if (Math.hypot(x - middleX, z - middleZ) <= radius) {
                        round.add(new Key(i, x, z));
                        lowest = Math.min(lowest, disc.topBlockAt(fromAxis(disc, x, z)));
                    }
                }
            }
            // The pool's level is the lowest ground in its round. Where the bowl steps down just outside it, the column on the
            // step stays ground, as a dam.
            int level = lowest;
            var members = new LinkedHashSet<>(round);
            members.removeIf(key -> hasLowerGroundBeside(disc, key, level, round));
            if (!members.contains(new Key(i, middleX, middleZ))) {
                return Optional.empty();
            }
            var columns = new ArrayList<Column>();
            for (Key key : members) {
                boolean inTheMiddle = Math.hypot(key.x() - middleX, key.z() - middleZ) <= radius - 1.5;
                Optional<Column> deep = inTheMiddle ? column(disc, key, level, pool.depth(), members) : Optional.empty();
                Optional<Column> made = deep.or(() -> column(disc, key, level, 1, members));
                if (made.isEmpty()) {
                    return Optional.empty();
                }
                columns.add(made.get());
            }
            return Optional.of(columns);
        }

        private boolean hasLowerGroundBeside(Disc disc, Key key, int level, Set<Key> round) {
            for (int[] step : BESIDE) {
                int x = key.x() + step[0];
                int z = key.z() + step[1];
                if (!round.contains(new Key(key.disc(), x, z)) && disc.topBlockAt(fromAxis(disc, x, z)) < level) {
                    return true;
                }
            }
            return false;
        }

        /**
         * One column of still water with its top block at {@code level}, as one of {@code members}; empty if water put there
         * would not stay, or has no business there.
         */
        private Optional<Column> column(Disc disc, Key key, int level, int depth, Set<Key> members) {
            int x = key.x();
            int z = key.z();
            int ground = disc.topBlockAt(fromAxis(disc, x, z));
            int cut = ground - level;
            // A block of the platform is left under the water, however much ground is cut away over it.
            if (cut < 0 || cut > (int) Math.floor(shape.floorThickness()) - 1 - depth || disc.platformDistance(shape, x, level - depth, z) >= 0) {
                return Optional.empty();
            }
            if (air(x, ground + 1, z) <= 0 || ruins.get(key.disc()).stream().anyMatch(ruin -> Math.hypot(x - ruin.x(), z - ruin.z()) <= ruin.radius())) {
                return Optional.empty();
            }
            Optional<DiscWater> own = themes.get(key.disc()).flatMap(DiscTheme::water);
            boolean lip = false;
            for (int[] step : BESIDE) {
                int besideX = x + step[0];
                int besideZ = z + step[1];
                Key beside = new Key(key.disc(), besideX, besideZ);
                if (members.contains(beside)) {
                    continue;
                }
                if (fromAxis(disc, besideX, besideZ) >= disc.radius()) {
                    lip = true;
                    continue;
                }
                int besideGround = disc.topBlockAt(fromAxis(disc, besideX, besideZ));
                Column other = taken.get(beside);
                // Water beside this at another level would run into it or out of it, whichever of the two is the higher.
                boolean otherWater = other != null
                        ? other.level() != level
                        : besideGround != level && own.filter(water -> water.depthAt(disc, cell.hash(), key.disc(), besideX, besideZ) > 0).isPresent();
                if (besideGround < level || otherWater) {
                    return Optional.empty();
                }
            }
            // Asked last, as every knot of every root has to be looked at.
            return isByARoot(x, level, z) ? Optional.empty() : Optional.of(new Column(key.disc(), x, z, level, depth, cut, lip));
        }

        /** What water let go at a height comes down on, straight under a column; empty if that is no place for a fall to end. */
        private Optional<Landing> landing(int from, int x, int z, int level) {
            // The floor's own height is the lowest open block: open air is reckoned from it, and is none there.
            for (int y = level; y > bounds.floorY(); y--) {
                if (air(x, y, z) > 0) {
                    continue;
                }
                for (int i = 0; i < discs.size(); i++) {
                    Disc disc = discs.get(i);
                    double fromAxis = fromAxis(disc, x, z);
                    if (i != from && fromAxis < disc.radius() && disc.topBlockAt(fromAxis) == y) {
                        return Optional.of(new Landing(OptionalInt.of(i), y));
                    }
                }
                // A stem, the wall, or the side of a platform.
                return Optional.empty();
            }
            return Optional.of(new Landing(OptionalInt.empty(), bounds.floorY() - 1));
        }

        // Whether a root crosses a column anywhere between two heights, which water let go at the upper one would come down on.
        private boolean hasARootInItsWay(int x, int z, int bottom, int top) {
            int cubeX = Math.floorDiv(x, ROOT_GRID);
            int cubeZ = Math.floorDiv(z, ROOT_GRID);
            for (int cubeY = Math.floorDiv(bottom, ROOT_GRID) - 1; cubeY <= Math.floorDiv(top, ROOT_GRID) + 1; cubeY++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        for (Root.Knot knot : knots.getOrDefault(cubeOf(cubeX + dx, cubeY, cubeZ + dz), List.of())) {
                            if (knot.y() > bottom - knot.radius() && knot.y() < top + knot.radius()
                                    && Math.hypot(knot.x() - x, knot.z() - z) < knot.radius() + FROM_A_ROOT) {
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        }

        // A root is written over whatever is there, a fall's still water included.
        private boolean isByARoot(int x, int y, int z) {
            int cubeX = Math.floorDiv(x, ROOT_GRID);
            int cubeY = Math.floorDiv(y, ROOT_GRID);
            int cubeZ = Math.floorDiv(z, ROOT_GRID);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        for (Root.Knot knot : knots.getOrDefault(cubeOf(cubeX + dx, cubeY + dy, cubeZ + dz), List.of())) {
                            if (knot.distanceTo(x, y, z) < knot.radius() + FROM_A_ROOT) {
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        }

        private double air(int x, int y, int z) {
            return ConeShape.airDistance(settings, cone, bounds, cell, layout, x, y, z);
        }

        private static double fromAxis(Disc disc, double x, double z) {
            return Math.hypot(x - disc.x(), z - disc.z());
        }
    }
}
