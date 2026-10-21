package dev.syrval.overgrownabyss.basilisk.client;

import dev.syrval.overgrownabyss.OvergrownAbyss;
import dev.syrval.overgrownabyss.basilisk.entity.Basilisk;
import dev.syrval.overgrownabyss.basilisk.entity.BasiliskSegmentChain;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * NAGA: NagaRenderer draws only the head; each NagaSegment has its own renderer registered through TF's
 * custom part-renderer system. Here the segments are hitboxes only, so this one renderer draws head AND
 * body from the chain, interpolated by partial tick. No second renderer, no part-render hooks.
 *
 * VERSION-SENSITIVE: the whole class. 1.21.2+ moved to render states (createRenderState/extractRenderState,
 * submit-style drawing), as TF's own 1.21.x NagaRenderer shows. The chain, goals and entity do not change.
 */
public class BasiliskRenderer extends MobRenderer<Basilisk, BasiliskModel> {
    private static final ResourceLocation TEXTURE = OvergrownAbyss.id("textures/entity/basilisk.png");
    private static final ResourceLocation ALERT_TEXTURE = OvergrownAbyss.id("textures/entity/basilisk_alert.png");
    private static final ResourceLocation SEGMENT_TEXTURE = OvergrownAbyss.id("textures/entity/basilisk_segment.png");

    private final BasiliskSegmentModel segmentModel;

    public BasiliskRenderer(EntityRendererProvider.Context context) {
        super(context, new BasiliskModel(context.bakeLayer(BasiliskModels.HEAD)), 0.6F);
        this.segmentModel = new BasiliskSegmentModel(context.bakeLayer(BasiliskModels.SEGMENT));
    }

    @Override
    public ResourceLocation getTextureLocation(Basilisk entity) {
        return entity.getPhase().isAlert() ? ALERT_TEXTURE : TEXTURE;
    }

    @Override
    protected float getFlipDegrees(Basilisk entity) {
        return 0.0F; // do not keel over on death; the body is a chain, not a rigid box
    }

    @Override
    protected void scale(Basilisk entity, PoseStack stack, float partialTick) {
        super.scale(entity, stack, partialTick);
        // This runs in the flipped model space (y points down), so a negative translate lifts the head.
        float raise = Basilisk.HEAD_RAISE * Basilisk.ease(entity.getRearAnim(partialTick));
        stack.translate(0.0F, -raise, 0.0F);
    }

    @Override
    public void render(Basilisk entity, float entityYaw, float partialTick, PoseStack stack, MultiBufferSource buffers, int light) {
        super.render(entity, entityYaw, partialTick, stack, buffers, light);
        renderBody(entity, partialTick, stack, buffers, light);
    }

    private void renderBody(Basilisk entity, float partialTick, PoseStack stack, MultiBufferSource buffers, int light) {
        BasiliskSegmentChain c = entity.getChain();
        double hx = Mth.lerp(partialTick, entity.xo, entity.getX());
        double hy = Mth.lerp(partialTick, entity.yo, entity.getY());
        double hz = Mth.lerp(partialTick, entity.zo, entity.getZ());

        VertexConsumer consumer = buffers.getBuffer(segmentModel.renderType(SEGMENT_TEXTURE));
        int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0.0F);

        for (int i = 0; i < c.count; i++) {
            double sx = Mth.lerp(partialTick, c.px[i], c.x[i]) - hx;
            double sy = Mth.lerp(partialTick, c.py[i], c.y[i]) - hy;
            double sz = Mth.lerp(partialTick, c.pz[i], c.z[i]) - hz;
            float yaw = Mth.rotLerp(partialTick, c.pyaw[i], c.yaw[i]);
            float pitch = Mth.lerp(partialTick, c.ppitch[i], c.pitch[i]);
            float taper = 1.0F - 0.55F * i / c.count;

            stack.pushPose();
            stack.translate(sx, sy, sz);
            stack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
            stack.mulPose(Axis.XP.rotationDegrees(-pitch)); // chain pitch: positive = nose down; model front is -Z
            stack.scale(taper, taper, taper);
            stack.scale(-1.0F, -1.0F, 1.0F);
            stack.translate(0.0F, -1.501F, 0.0F);
            segmentModel.renderToBuffer(stack, consumer, light, overlay, -1);
            stack.popPose();
        }
    }
}
