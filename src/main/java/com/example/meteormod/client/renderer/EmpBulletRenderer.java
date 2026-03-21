package com.example.meteormod.client.renderer;

import com.example.meteormod.entity.EmpBulletEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * No-op renderer for the EMP bullet.
 * Its visual comes entirely from the EmpParticle trail.
 */
public class EmpBulletRenderer extends EntityRenderer<EmpBulletEntity> {

    public EmpBulletRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public void render(EmpBulletEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        // Intentionally empty — visual provided by client-side particles.
    }

    @Override
    public ResourceLocation getTextureLocation(EmpBulletEntity entity) {
        return ResourceLocation.fromNamespaceAndPath("meteormod", "textures/particle/emp_bullet.png");
    }
}
