package dev.syrval.verdantdeep.basilisk.client;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.Entity;

/** One body block with a dorsal ridge. Drawn repeatedly, tapered, by BasiliskRenderer. */
public class BasiliskSegmentModel extends HierarchicalModel<Entity> {
    private final ModelPart root;

    public BasiliskSegmentModel(ModelPart root) {
        this.root = root;
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body",
            CubeListBuilder.create().texOffs(0, 0).addBox(-6.0F, 10.0F, -7.0F, 12.0F, 14.0F, 14.0F),
            PartPose.ZERO);
        root.addOrReplaceChild("ridge",
            CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, 7.0F, -5.0F, 2.0F, 3.0F, 10.0F),
            PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(Entity e, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        // static
    }
}
