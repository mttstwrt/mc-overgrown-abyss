package dev.syrval.verdantdeep.ravine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Where one hole gives off gas: from its discs, as each theme lists (see {@link DiscTheme.Gas}), at its falls and springs, and
 * over its cavern's floor, and the fog that fills its air (see {@link GasSettings}). It is worked out from the same hashes and
 * shapes as the rest of the hole, not from the world's blocks, so a source may stand where a player has since built; the gas
 * library only feeds the open cells it finds.
 *
 * @param sources the hole's sources, at most {@link #MAX_SOURCES}
 * @param fogs    the hole's fogs, at most {@link #MAX_FOGS}
 * @param dropped how many more sources there were, left out so that what a player is sent stays bounded
 */
public record HoleGas(List<GasSource> sources, List<GasFog> fogs, int dropped) {
    public static final HoleGas NONE = new HoleGas(List.of(), List.of(), 0);
    /** The most sources one hole sends. */
    public static final int MAX_SOURCES = 4096;
    /** The most fogs one hole sends. */
    public static final int MAX_FOGS = 4;
    private static final int GAS_HASH_BASE = 2_000_000;
    // Hash indices kept apart for this many gas entries per theme.
    private static final int MAX_GASES = 64;
    private static final int CAVERN_HASH_BASE = 2_100_000;
    private static final int CHUNK_SIZE = 16;
    // Sources on the cavern's floor keep this share of its radius in, clear of the wall's bumps.
    private static final double CAVERN_INSIDE = 0.95;

    public HoleGas {
        sources = List.copyOf(sources);
        fogs = List.copyOf(fogs);
        if (fogs.size() > MAX_FOGS) {
            throw new IllegalArgumentException("A hole has at most " + MAX_FOGS + " fogs, got " + fogs.size());
        }
    }

    static HoleGas of(RavineSettings settings, RavineBounds bounds, RavineCell cell, CellDiscs discs) {
        List<GasSource> found = new ArrayList<>();
        List<GasFog> fogs = new ArrayList<>();
        // The hole's own first: there are few of them, and a cap that bites takes the last of the themes' many.
        settings.dressing().gas().ifPresent(gas -> {
            gas.fog().ifPresent(fog -> fogs.add(fromFog(settings, fog, bounds, cell)));
            gas.falls().ifPresent(falls -> fromFalls(falls, discs.falls(), found));
            gas.springs().ifPresent(springs -> fromSprings(springs, discs.falls(), found));
            fromCavern(settings, gas.cavern(), bounds, cell, found);
        });
        fromThemes(settings, cell, discs, found);
        int dropped = Math.max(0, found.size() - MAX_SOURCES);
        return new HoleGas(found.subList(0, found.size() - dropped), fogs, dropped);
    }

    // Chunk by chunk, as growth is, because what keeps lava clear of what burns is worked out for a chunk at a time.
    private static void fromThemes(RavineSettings settings, RavineCell cell, CellDiscs discs, List<GasSource> found) {
        List<Disc> all = discs.layout().discs();
        int minChunkX = Integer.MAX_VALUE;
        int maxChunkX = Integer.MIN_VALUE;
        int minChunkZ = Integer.MAX_VALUE;
        int maxChunkZ = Integer.MIN_VALUE;
        for (int i = 0; i < all.size(); i++) {
            if (discs.themes().get(i).map(theme -> theme.gas().isEmpty()).orElse(true)) {
                continue;
            }
            Disc disc = all.get(i);
            double reach = disc.radius() + 1;
            minChunkX = Math.min(minChunkX, Math.floorDiv((int) Math.floor(disc.x() - reach), CHUNK_SIZE));
            maxChunkX = Math.max(maxChunkX, Math.floorDiv((int) Math.floor(disc.x() + reach), CHUNK_SIZE));
            minChunkZ = Math.min(minChunkZ, Math.floorDiv((int) Math.floor(disc.z() - reach), CHUNK_SIZE));
            maxChunkZ = Math.max(maxChunkZ, Math.floorDiv((int) Math.floor(disc.z() + reach), CHUNK_SIZE));
        }
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                fromThemesIn(settings, cell, discs, chunkX * CHUNK_SIZE, chunkZ * CHUNK_SIZE, found);
            }
        }
    }

    private static void fromThemesIn(RavineSettings settings, RavineCell cell, CellDiscs discs, int minX, int minZ, List<GasSource> found) {
        List<Disc> all = discs.layout().discs();
        DiscShape shape = settings.discs();
        var lava = new DiscLava(discs, minX, minZ);
        for (int i = 0; i < all.size(); i++) {
            Optional<DiscTheme> theme = discs.themes().get(i);
            if (theme.isEmpty() || theme.get().gas().isEmpty()) {
                continue;
            }
            Disc disc = all.get(i);
            List<DiscTheme.Gas> gas = theme.get().gas();
            double wildness = theme.get().wildness().factorFor(cell.hash(), i);
            DiscGround ground = discs.grounds().get(i);
            int index = i;
            for (int g = 0; g < Math.min(gas.size(), MAX_GASES); g++) {
                DiscTheme.Gas each = gas.get(g);
                DiscScatter.forEachPick(cell.hash(), GAS_HASH_BASE + i * MAX_GASES + g, disc, each.on(), each.every(), wildness,
                        (x, z) -> each.weightAt(cell.hash(), index, disc, x, z), minX, minZ, (x, z, fromAxis) -> {
                            OptionalDouble y = anchorOf(each, theme.get(), disc, ground, shape, lava, cell.hash(), index, x, z, fromAxis);
                            if (y.isPresent()) {
                                found.add(new GasSource(each.medium(), each.shape(), x + 0.5, y.getAsDouble(), z + 0.5, each.rate()));
                            }
                        });
            }
        }
    }

    // The height of a source's anchor in a picked column, or empty where its surface is not there. Over water the gas rests on
    // the water, where a growth starts on the bed; under the disc it hangs from the lowest rock, whose underside is the top of
    // the open block a growth starts in; everywhere else it rests where a growth would start.
    private static OptionalDouble anchorOf(
            DiscTheme.Gas gas, DiscTheme theme, Disc disc, DiscGround ground, DiscShape shape, DiscLava lava, long hash, int index, int x, int z,
            double fromAxis) {
        return switch (gas.on()) {
            case WATER -> {
                int depth = theme.water().filter(water -> water.fluid() == DiscWater.Fluid.WATER).map(water -> water.depthAt(disc, hash, index, x, z)).orElse(0);
                yield depth > 0 ? OptionalDouble.of(gas.shape().standingOn(disc.topBlockAt(fromAxis) + 1)) : OptionalDouble.empty();
            }
            case UNDERSIDE -> {
                OptionalInt open = disc.hangBlockAt(shape, fromAxis);
                yield open.isPresent() ? OptionalDouble.of(gas.shape().hangingFrom(open.getAsInt() + 1)) : OptionalDouble.empty();
            }
            default -> {
                OptionalInt open = DiscGrowth.placeOn(gas.on(), theme, disc, ground, shape, lava, hash, index, x, z, fromAxis);
                yield open.isPresent() ? OptionalDouble.of(gas.shape().standingOn(open.getAsInt())) : OptionalDouble.empty();
            }
        };
    }

    // Of a cone, from round its base and the cavern at the floor to round its mouth at the top; of a ravine, the cavern. It
    // reaches an edge past the walls, and as far again as their unevenness moves them, and an edge below the floor, so that it
    // is at its full thickness wherever they are.
    private static GasFog fromFog(RavineSettings settings, GasSettings.Fog fog, RavineBounds bounds, RavineCell cell) {
        float edge = fog.edge();
        double base = bounds.floorY() - edge;
        double reach = edge + settings.wallNoise().maxDisplacement();
        if (settings.cone().isEmpty()) {
            float radius = (float) (settings.cavernRadius() + reach);
            return new GasFog(fog.medium(), cell.centreX(), base, cell.centreZ(), radius, radius, settings.cavernHeight() + 2 * edge,
                    fog.bottomDensity(), fog.topDensity(), edge);
        }
        ConeSettings cone = settings.cone().get();
        double height = bounds.topY() - base;
        double top = cone.topRadius() + reach;
        // The narrowest that, with that top, holds the wall that far in at every height of the hole.
        double bottom = top;
        for (int y = bounds.floorY(); y < bounds.topY(); y++) {
            double up = (y - base) / height;
            bottom = Math.max(bottom, (wallAt(settings, cone, bounds, y) + reach - top * up) / (1 - up));
        }
        return new GasFog(fog.medium(), cell.centreX(), base, cell.centreZ(), (float) bottom, (float) top, (float) height,
                fog.bottomDensity(), fog.topDensity(), edge);
    }

    // How far the hole's wall is from its axis before its unevenness: the cone's, or the cavern's dome where that is wider.
    private static double wallAt(RavineSettings settings, ConeSettings cone, RavineBounds bounds, int y) {
        double t = (y - bounds.floorY()) / (double) settings.cavernHeight();
        double cavern = t >= 0 && t < 1 ? settings.cavernRadius() * Math.sqrt(1 - t * t) : 0;
        return Math.max(ConeShape.radiusAt(settings, cone, bounds, y), cavern);
    }

    // A fall's water comes down through `drop` open blocks under its level onto the water it lands in, whose top is the floor of
    // the lowest of them.
    private static void fromFalls(GasSettings.Falls falls, DiscFalls all, List<GasSource> found) {
        for (DiscFalls.Fall fall : all.falls()) {
            double x = fall.x() + 0.5;
            double z = fall.z() + 0.5;
            int landing = fall.level() - fall.drop();
            falls.spray().ifPresent(spray -> found.add(new GasSource(spray.medium(), spray.shape(), x, spray.shape().standingOn(landing), z, spray.rate())));
            falls.curtain().ifPresent(curtain -> found.add(new GasSource(
                    curtain.medium(), new GasShape.Cylinder(fall.width() / 2F + curtain.margin(), fall.drop()), x, landing, z, curtain.rate())));
        }
    }

    // A spring's level is the top of the pool its water comes down in.
    private static void fromSprings(GasSettings.Source springs, DiscFalls all, List<GasSource> found) {
        for (DiscFalls.Spring spring : all.springs()) {
            found.add(new GasSource(springs.medium(), springs.shape(), spring.x() + 0.5, springs.shape().standingOn(spring.level() + 1), spring.z() + 0.5, springs.rate()));
        }
    }

    // The cavern's floor is level at the hole's floor, round the cell's centre.
    private static void fromCavern(RavineSettings settings, List<GasSettings.Spread> cavern, RavineBounds bounds, RavineCell cell, List<GasSource> found) {
        double radius = settings.cavernRadius() * CAVERN_INSIDE;
        int fromX = (int) Math.floor(cell.centreX() - radius);
        int toX = (int) Math.ceil(cell.centreX() + radius);
        int fromZ = (int) Math.floor(cell.centreZ() - radius);
        int toZ = (int) Math.ceil(cell.centreZ() + radius);
        for (int e = 0; e < cavern.size(); e++) {
            GasSettings.Spread spread = cavern.get(e);
            double y = spread.shape().standingOn(bounds.floorY());
            for (int x = fromX; x <= toX; x++) {
                for (int z = fromZ; z <= toZ; z++) {
                    if (Math.hypot(x + 0.5 - cell.centreX(), z + 0.5 - cell.centreZ()) <= radius
                            && RavineCells.unitAt(cell.hash(), CAVERN_HASH_BASE + e, x, z) * spread.every() < 1) {
                        found.add(new GasSource(spread.medium(), spread.shape(), x + 0.5, y, z + 0.5, spread.rate()));
                    }
                }
            }
        }
    }
}
