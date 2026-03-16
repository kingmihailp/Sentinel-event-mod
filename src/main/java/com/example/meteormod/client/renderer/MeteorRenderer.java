package com.example.meteormod.client.renderer;

import com.example.meteormod.entity.MeteorEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;

public class MeteorRenderer extends EntityRenderer<MeteorEntity> {

    private final BlockRenderDispatcher blockRenderer;

    public MeteorRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blockRenderer = context.getBlockRenderDispatcher();
        this.shadowRadius = 0.5f;
    }

    @Override
    public void render(MeteorEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {

        poseStack.pushPose();

        // Center the block on the entity origin
        poseStack.translate(-0.5, 0.0, -0.5);

        // Slow rotation for a tumbling effect
        float angle = (entity.tickCount + partialTick) * 4.0f;
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(angle));
        poseStack.mulPose(Axis.XP.rotationDegrees(angle * 0.7f));
        poseStack.translate(-0.5, -0.5, -0.5);

        // Render as an iron block
        blockRenderer.renderSingleBlock(
                Blocks.IRON_BLOCK.defaultBlockState(),
                poseStack,
                buffer,
                packedLight,
                OverlayTexture.NO_OVERLAY
        );

        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(MeteorEntity entity) {
        return ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/iron_block.png");
    }
}
