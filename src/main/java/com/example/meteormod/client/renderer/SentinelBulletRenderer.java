package com.example.meteormod.client.renderer;

import com.example.meteormod.entity.SentinelBulletEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * No-op renderer for the Sentinel bullet.
 * The bullet is fully invisible as a geometry — its appearance comes entirely
 * from the SOUL_FIRE_FLAME particle trail spawned in SentinelBulletEntity.tick().
 */
public class SentinelBulletRenderer extends EntityRenderer<SentinelBulletEntity> {

    public SentinelBulletRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public void render(SentinelBulletEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        // Intentionally empty — visual is provided by client-side particles only.
    }

    @Override
    public ResourceLocation getTextureLocation(SentinelBulletEntity entity) {
        return ResourceLocation.fromNamespaceAndPath("minecraft",
                "textures/particle/soul_fire_particle_0.png");
    }
}
