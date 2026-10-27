package dev.syrval.verdantdeep.basilisk.client;

import dev.architectury.registry.client.level.entity.EntityModelLayerRegistry;
import dev.architectury.registry.client.level.entity.EntityRendererRegistry;
import dev.syrval.verdantdeep.basilisk.BasiliskEntities;

import net.minecraft.client.renderer.entity.NoopRenderer;

/** Client-only. Call only when running on the client, never from a server code path. */
public final class BasiliskClient {
    private BasiliskClient() {}

    public static void register() {
        EntityModelLayerRegistry.register(BasiliskModels.HEAD, BasiliskModel::createLayer);
        EntityModelLayerRegistry.register(BasiliskModels.SEGMENT, BasiliskSegmentModel::createLayer);
        EntityRendererRegistry.register(BasiliskEntities.BASILISK, BasiliskRenderer::new);
        EntityRendererRegistry.register(BasiliskEntities.SEGMENT, NoopRenderer::new); // hitbox only; body is drawn by the parent
    }
}
