package dev.syrval.overgrownabyss.ravine;

import net.minecraft.resources.ResourceLocation;

/**
 * One fog in the world, as the gas library draws it (see {@link GasSettings.Fog}): the look of a medium, by its id, filling an
 * upright round shape that stands on its anchor, {@code bottomRadius} across at its base and {@code topRadius} at its top,
 * thinning from {@code bottomDensity} to {@code topDensity} on the way up and to nothing over its outermost {@code edge} blocks.
 */
public record GasFog(ResourceLocation medium, double x, double y, double z, float bottomRadius, float topRadius, float height,
                     float bottomDensity, float topDensity, float edge) {}
