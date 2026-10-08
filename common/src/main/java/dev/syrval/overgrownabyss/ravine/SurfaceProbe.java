package dev.syrval.overgrownabyss.ravine;

/** The ground at a block column as the level's own terrain makes it, before any hole is carved into it. */
@FunctionalInterface
public interface SurfaceProbe {
    /** Rock wherever it is asked, which is what a carve reads until its level installs the real ground. */
    SurfaceProbe SOLID = (x, z, from) -> from;

    /**
     * The height of the highest solid block of a column at or below {@code from}. Whoever asks names the highest ground it
     * cares about, so that no time goes into measuring a mountain above that.
     */
    int heightAt(int blockX, int blockZ, int from);
}
