package dev.syrval.verdantdeep.basilisk.client;

import dev.syrval.verdantdeep.VerdantDeep;

import net.minecraft.client.model.geom.ModelLayerLocation;

/** Model layer ids, shared by both loaders' client entrypoints. */
public final class BasiliskModels {
    public static final ModelLayerLocation HEAD = new ModelLayerLocation(VerdantDeep.id("basilisk"), "main");
    public static final ModelLayerLocation SEGMENT = new ModelLayerLocation(VerdantDeep.id("basilisk_segment"), "main");

    private BasiliskModels() {}
}
