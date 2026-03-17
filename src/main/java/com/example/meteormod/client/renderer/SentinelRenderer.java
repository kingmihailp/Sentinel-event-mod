package com.example.meteormod.client.renderer;

import com.example.meteormod.client.model.SentinelModel;
import com.example.meteormod.entity.SentinelEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Client-side renderer for SentinelEntity.
 *
 * To add render layers (e.g. glowing eyes, armour overlay):
 *   this.addRenderLayer(new MyLayer(this));
 *
 * GeckoLib render layers extend GeoRenderLayer<SentinelEntity>.
 */
public class SentinelRenderer extends GeoEntityRenderer<SentinelEntity> {

    public SentinelRenderer(EntityRendererProvider.Context context) {
        super(context, new SentinelModel());
        this.shadowRadius = 0.4f;
        this.addRenderLayer(new SentinelParticleLayer(this));
    }

    // Override render() if you need to apply a global scale or offset:
    //
    // @Override
    // public void render(SentinelEntity entity, float yaw, float partialTick,
    //                    PoseStack poseStack, MultiBufferSource buffer, int light) {
    //     poseStack.pushPose();
    //     poseStack.scale(1.2f, 1.2f, 1.2f);
    //     super.render(entity, yaw, partialTick, poseStack, buffer, light);
    //     poseStack.popPose();
    // }
}
