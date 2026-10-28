package dev.syrval.verdantdeep.ravine;

import net.minecraft.resources.ResourceLocation;

/**
 * A region in which gas of one medium drifts toward its hole's axis: an upright disc standing on the anchor (the middle of the
 * bottom), one for each gas a disc's theme gives off. It makes none itself; the hole's {@link GasSource}s do.
 */
public record GasFlow(ResourceLocation medium, double x, double y, double z, float radius, float height) {}
