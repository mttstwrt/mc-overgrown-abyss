package dev.syrval.overgrownabyss.basilisk.client;

import dev.syrval.overgrownabyss.basilisk.entity.Basilisk;
import dev.syrval.overgrownabyss.basilisk.entity.BasiliskPhase;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Head only (the body is drawn by BasiliskRenderer from the chain). Model space is vanilla's: y grows downward,
 * feet at y = 24, the front faces -Z. Parts: a wedge head, a hinged jaw, and two hood plates that fold
 * back along the neck and flare outward while rearing.
 *
 * VERSION-SENSITIVE: HierarchicalModel / setupAnim signature changed in 1.21.2+ (render states).
 */
public class BasiliskModel extends HierarchicalModel<Basilisk> {
    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart jaw;
    private final ModelPart hoodLeft;
    private final ModelPart hoodRight;

    public BasiliskModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.jaw = head.getChild("jaw");
        this.hoodLeft = head.getChild("hood_left");
        this.hoodRight = head.getChild("hood_right");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Pivot at the neck joint so nodding rotates about the base of the head.
        PartDefinition head = root.addOrReplaceChild("head",
            CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, -6.0F, -12.0F, 10.0F, 8.0F, 12.0F),
            PartPose.offset(0.0F, 16.0F, 3.0F));

        head.addOrReplaceChild("jaw",
            CubeListBuilder.create().texOffs(0, 20).addBox(-4.0F, 0.0F, -8.0F, 8.0F, 2.0F, 10.0F),
            PartPose.offset(0.0F, 2.0F, -4.0F));

        head.addOrReplaceChild("hood_left",
            CubeListBuilder.create().texOffs(32, 0).addBox(-0.5F, -6.0F, 0.0F, 1.0F, 12.0F, 9.0F),
            PartPose.offset(5.0F, -2.0F, -6.0F));
        head.addOrReplaceChild("hood_right",
            CubeListBuilder.create().texOffs(32, 0).mirror().addBox(-0.5F, -6.0F, 0.0F, 1.0F, 12.0F, 9.0F),
            PartPose.offset(-5.0F, -2.0F, -6.0F));

        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(Basilisk e, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float partial = ageInTicks - e.tickCount; // recover the partial tick; setupAnim does not receive it
        float rear = Basilisk.ease(e.getRearAnim(partial));
        float jawOpen = e.getJawAnim(partial);
        BasiliskPhase phase = e.getPhase();

        // Sway like a charmed cobra: side to side and a slow nod, stronger the higher it is.
        head.yRot = Mth.sin(ageInTicks * 0.13F) * 0.25F * rear;
        head.xRot = -0.25F * rear + Mth.sin(ageInTicks * 0.09F) * 0.06F * rear;
        if (phase == BasiliskPhase.RECOVER) head.xRot += 0.35F; // head hangs low

        // Mouth: slightly open, gaping while hissing in the wind-up, wide on the strike.
        float hiss = rear * (0.35F + 0.15F * Mth.sin(ageInTicks * 0.6F));
        jaw.xRot = 0.06F + hiss + jawOpen * 0.8F;

        // Hood: folded flat at rest, flared up to ~63 degrees.
        hoodLeft.yRot = rear * 1.1F;
        hoodRight.yRot = -rear * 1.1F;
    }
}
