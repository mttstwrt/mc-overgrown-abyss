package dev.syrval.overgrownabyss.ravine;

/** Receives each disc in a chunk whose theme inherits a biome's features, with what it inherits. */
@FunctionalInterface
public interface DiscPlotSink {
    void accept(DiscTheme.Inherits inherits, DiscPlot plot);
}
