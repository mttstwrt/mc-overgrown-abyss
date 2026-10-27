package dev.syrval.verdantdeep.ravine;

import java.util.OptionalDouble;

/**
 * One player's step of the curse of descent, with no Minecraft types so it can be tested alone. The caller keeps the
 * adjusted depth between ticks and passes it back in.
 */
public final class CurseTracker {
    /** No wither this tick. */
    public static final int NONE = -1;

    private CurseTracker() {}

    /**
     * @param adjusted the depth the player was adjusted to last tick, empty if they were outside the hole
     * @param y        the player's height now
     * @param inside   how far down the hole the player is, 0 at its mouth to 1 at its floor, or empty if outside it
     * @return the new adjusted depth (empty outside the hole) and the wither amplifier to give, or {@link #NONE}
     */
    public static Step step(OptionalDouble adjusted, double y, OptionalDouble inside, CurseSettings settings) {
        if (inside.isEmpty()) {
            return new Step(OptionalDouble.empty(), NONE);
        }
        // Coming in sets the depth to where the player is, so walking or caving in starts nothing.
        double from = adjusted.orElse(y);
        double rate = y < from ? settings.rateDown() : settings.rateUp();
        double moved = from + Math.clamp(y - from, -rate, rate);
        double gap = Math.abs(y - moved);
        double scale = 1 - settings.depthStrictness() * inside.getAsDouble();
        int amplifier = NONE;
        for (int level = 0; level < settings.levels().size(); level++) {
            if (gap >= settings.levels().get(level) * scale) {
                amplifier = level;
            }
        }
        return new Step(OptionalDouble.of(moved), amplifier);
    }

    public record Step(OptionalDouble adjusted, int amplifier) {}
}
