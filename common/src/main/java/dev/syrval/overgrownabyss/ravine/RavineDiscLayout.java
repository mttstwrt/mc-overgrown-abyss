package dev.syrval.overgrownabyss.ravine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The discs of a ravine, cut into its walls: see {@link RavineDomes} for where they go. A grid position is turned into a
 * {@link Disc} at its floor height, in world coordinates, so the disc is the same as a cone's.
 */
final class RavineDiscLayout implements DiscLayout {
    private final RavineSettings settings;
    private final RavineBounds bounds;
    private final RavineCell cell;
    private final List<Disc> discs;

    RavineDiscLayout(RavineSettings settings, RavineBounds bounds, RavineCell cell) {
        this.settings = settings;
        this.bounds = bounds;
        this.cell = cell;
        var placed = new ArrayList<Disc>();
        int rows = RavineDomes.rows(settings, bounds);
        int slots = RavineDomes.slots(settings, cell);
        for (int side = 1; side >= -1; side -= 2) {
            for (int row = 0; row < rows; row++) {
                for (int slot = 0; slot < slots; slot++) {
                    at(side, row, slot).ifPresent(placed::add);
                }
            }
        }
        this.discs = Discs.standing(placed, settings.discs());
    }

    /** The disc at a grid position as placed, before its stem is ended on the discs below it; {@link #discs()} has those. */
    Optional<Disc> at(int side, int row, int slot) {
        return RavineDomes.at(settings, bounds, cell, side, row, slot).map(this::disc);
    }

    // The disc's axis is vertical in the world, so its centre is placed using the shaft's lean at its floor.
    private Disc disc(RavineDomes.Dome dome) {
        double[] centre = RavineShape.worldOf(bounds, cell, dome.along(), RavineShape.centreSideways(settings, bounds, cell, dome), dome.floor());
        // A ravine's discs do not vary their bowls yet: each has the full depth for its size.
        return Disc.placed(centre[0], centre[1], dome.floor(), dome.radius(), dome.height(), settings.discs().bowlDepthFor(dome.radius(), 0));
    }

    @Override
    public List<Disc> discs() {
        return discs;
    }
}
