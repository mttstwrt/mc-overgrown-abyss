package dev.syrval.overgrownabyss.ravine;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What each cell holds in one level: whether it has a hole at all, between which heights, and the hole's discs. A cell has no
 * hole where one of its sample columns is ocean or river, or, for a cone whose top follows the ground (see
 * {@link RimSettings}), where the ground round its mouth is too near sea level. Both questions need things that only exist
 * once the level's noise is wired (its biome source and climate sampler, its terrain), so the answers to them are installed
 * afterwards by the density hook, before any chunk is generated.
 */
final class RavineSites {
    // Columns round a cone's mouth where the ground is read for its lip and its edge.
    static final int RIM_PROBES = 24;

    private final RavineSettings settings;
    // Null only for the unbound carve the codec produces, which is never asked.
    private final RavineBounds level;
    // Cells are few and each answer costs biome lookups and columns of terrain; the maps are safe to share between worker threads.
    private final ConcurrentHashMap<RavineCell, Optional<RavineBounds>> bounds = new ConcurrentHashMap<>();
    // The discs of each cell, built once: they never change, and each sample would otherwise rebuild them from hashes.
    private final ConcurrentHashMap<RavineCell, CellDiscs> discs = new ConcurrentHashMap<>();
    private volatile LandCheck land = LandCheck.EVERYWHERE;
    private volatile Ground ground = new Ground(SurfaceProbe.SOLID, 0);

    /** A level's terrain and the height of its sea, which are installed together. */
    private record Ground(SurfaceProbe probe, int seaLevel) {}

    RavineSites(RavineSettings settings, RavineBounds level) {
        this.settings = settings;
        this.level = level;
    }

    void restrictToLand(LandCheck land) {
        this.land = land;
        forget();
    }

    void followGround(SurfaceProbe probe, int seaLevel) {
        this.ground = new Ground(probe, seaLevel);
        forget();
    }

    private void forget() {
        bounds.clear();
        discs.clear();
    }

    /**
     * The heights a cell's hole lies between: the level's, or for a cone whose top follows the ground the level's floor, the
     * cell's own lip and the edge of its mouth. Empty if the cell holds no hole.
     */
    Optional<RavineBounds> boundsOf(RavineCell cell) {
        return bounds.computeIfAbsent(cell, this::survey);
    }

    /** The discs of a cell that holds a hole between {@code bounds}, which is what {@link #boundsOf} gave for it. */
    CellDiscs discsOf(RavineCell cell, RavineBounds bounds) {
        return discs.computeIfAbsent(cell, c -> CellDiscs.of(settings, bounds, c, ground.probe()));
    }

    private Optional<RavineBounds> survey(RavineCell cell) {
        LandCheck current = land;
        if (!cell.samplePoints().stream().allMatch(column -> current.isLand(column.x(), column.z()))) {
            return Optional.empty();
        }
        Optional<RimSettings> rim = settings.cone().flatMap(ConeSettings::rim);
        return rim.isEmpty() ? Optional.of(level) : mouthOf(settings.cone().get(), rim.get(), cell, ground);
    }

    /**
     * A cone's lip and the edge of its mouth, or empty where the ground round the mouth is too near sea level. The lip is
     * {@code dip} under the ground that the rim's {@code low_share} of the columns read lie below, and never above the level's
     * top. The edge is {@code dip} under the ground at each column, and nowhere under the lip.
     *
     * <p>Each column's ground is taken as the middle one of its own and its two neighbours', so that a single pothole or cave
     * mouth does not count.
     */
    private Optional<RavineBounds> mouthOf(ConeSettings cone, RimSettings rim, RavineCell cell, Ground current) {
        int least = current.seaLevel() + rim.minAboveSea();
        // Ground higher than this gives the same lip and edge, so it is not measured.
        int from = level.topY() + (int) Math.ceil(rim.dip());
        int[] heights = new int[RIM_PROBES];
        for (int i = 0; i < RIM_PROBES; i++) {
            double angle = 2 * Math.PI * i / RIM_PROBES;
            heights[i] = current.probe().heightAt(
                    (int) Math.floor(cell.centreX() + cone.topRadius() * Math.cos(angle)),
                    (int) Math.floor(cell.centreZ() + cone.topRadius() * Math.sin(angle)), from);
            // Two low columns this close make the one between them low as well, whatever the rest are, so most cells on low
            // ground are given up after two columns.
            if (heights[i] < least && (i > 0 && heights[i - 1] < least || i > 1 && heights[i - 2] < least)) {
                return Optional.empty();
            }
        }
        int[] ground = new int[RIM_PROBES];
        for (int i = 0; i < RIM_PROBES; i++) {
            ground[i] = middle(heights[(i + RIM_PROBES - 1) % RIM_PROBES], heights[i], heights[(i + 1) % RIM_PROBES]);
        }
        int[] sorted = ground.clone();
        Arrays.sort(sorted);
        if (sorted[0] < least) {
            return Optional.empty();
        }
        int low = sorted[Math.min((int) (rim.lowShare() * RIM_PROBES), RIM_PROBES - 1)];
        int lip = (int) Math.floor(Math.min(low - rim.dip(), level.topY()));
        List<Integer> edge = Arrays.stream(ground).map(height -> Math.max(lip, (int) Math.floor(height - rim.dip()))).boxed().toList();
        return RavineBounds.of(level.floorY(), lip, edge);
    }

    private static int middle(int a, int b, int c) {
        return Math.max(Math.min(a, b), Math.min(Math.max(a, b), c));
    }
}
