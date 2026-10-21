package dev.syrval.overgrownabyss.basilisk.client;

import dev.syrval.overgrownabyss.OvergrownAbyss;

import net.minecraft.client.model.geom.ModelLayerLocation;

/** Model layer ids, shared by both loaders' client entrypoints. */
public final class BasiliskModels {
    public static final ModelLayerLocation HEAD = new ModelLayerLocation(OvergrownAbyss.id("basilisk"), "main");
    public static final ModelLayerLocation SEGMENT = new ModelLayerLocation(OvergrownAbyss.id("basilisk_segment"), "main");

    private BasiliskModels() {}
}
