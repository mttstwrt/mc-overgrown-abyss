package dev.syrval.verdantdeep.ravine;

import net.minecraft.resources.ResourceLocation;

/**
 * One source of gas in the world: the gas by its id among the gas library's media, the room it fills, its anchor (which a
 * cylinder stands on and a sphere is centred on) and the density each cell of that room gains per second.
 */
public record GasSource(ResourceLocation medium, GasShape shape, double x, double y, double z, float rate) {}
