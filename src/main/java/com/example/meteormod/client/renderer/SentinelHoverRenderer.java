package com.example.meteormod.client.renderer;

import com.example.meteormod.client.model.SentinelHoverModel;
import com.example.meteormod.entity.SentinelHoverEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class SentinelHoverRenderer extends GeoEntityRenderer<SentinelHoverEntity> {

    public SentinelHoverRenderer(EntityRendererProvider.Context context) {
        super(context, new SentinelHoverModel());
        this.shadowRadius = 0.6f;
    }

    /**
     * GeckoLib uses LivingEntity.yBodyRot for rotation, returning 0 for plain Entity subclasses.
     * Override to use the entity's own yRot so the hover rotates with the rider's look direction.
     */
    @Override
    protected void applyRotations(SentinelHoverEntity animatable, PoseStack poseStack,
                                   float ageInTicks, float rotationYaw, float partialTick, float nativeScale) {
        float yaw = Mth.lerp(partialTick, animatable.yRotO, animatable.getYRot());
        poseStack.mulPose(Axis.YP.rotationDegrees(180f - yaw));
    }
}
