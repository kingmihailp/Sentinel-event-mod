package com.example.meteormod.client;

import com.example.meteormod.entity.ModEntities;
import com.example.meteormod.entity.SentinelEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Renders a live SentinelEntity 3-D model as the creative-tab icon.
 *
 * The entity's head yaw / pitch follow the current mouse cursor position so
 * the sentinel "looks at" the player just like Alex's Mobs does.
 *
 * WHY no extra Y rotation in the PoseStack:
 *   LivingEntityRenderer.render() already applies (180° − yBodyRot) to the
 *   PoseStack before drawing the model. At yBodyRot = 0 that is exactly 180°,
 *   which makes the entity face the viewer. Adding another 180° here would
 *   produce a total of 360° (= 0°) — showing the entity's back instead.
 */
public class SentinelIconBEWLR extends BlockEntityWithoutLevelRenderer {

    private SentinelEntity dummy;

    public SentinelIconBEWLR(BlockEntityRenderDispatcher blockEntityRenderDispatcher,
                              EntityModelSet entityModelSet) {
        super(blockEntityRenderDispatcher, entityModelSet);
    }

    @Override
    public void renderByItem(ItemStack stack,
                             ItemDisplayContext ctx,
                             PoseStack pose,
                             MultiBufferSource buffers,
                             int light,
                             int overlay) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        // Re-create dummy when level changes (e.g. world reload)
        if (dummy == null || dummy.level() != mc.level) {
            dummy = new SentinelEntity(ModEntities.SENTINEL.get(), mc.level);
            dummy.setNoGravity(true);
        }

        // Drive GeckoLib idle animation forward using wall-clock time
        dummy.tickCount = (int)(System.currentTimeMillis() / 50L);

        // ── Mouse → head direction ──────────────────────────────────────────
        // Normalise raw screen coords to [-0.5, +0.5]
        double screenW = mc.getWindow().getScreenWidth();
        double screenH = mc.getWindow().getScreenHeight();
        float mx = (float)(mc.mouseHandler.xpos() / screenW) - 0.5f;
        float my = (float)(mc.mouseHandler.ypos() / screenH) - 0.5f;

        // LivingEntityRenderer applies (180 − yBodyRot), so yBodyRot = 0 → faces viewer.
        // Negative mx makes the sentinel look right when the cursor moves right.
        float yaw   = -mx * 120.0f;   // ±60° left/right
        float pitch = -my *  60.0f;   // ±30° up/down

        dummy.setYRot(yaw);
        dummy.yBodyRot  = yaw;
        dummy.yHeadRot  = yaw;
        dummy.yHeadRotO = yaw;
        dummy.setXRot(pitch);

        // ── Render ──────────────────────────────────────────────────────────
        pose.pushPose();

        // Centre in the icon slot; feet near the bottom edge.
        // No extra Y rotation — LivingEntityRenderer already applies 180° internally.
        pose.translate(0.5, 0.05, 0.5);
        pose.scale(0.5f, 0.5f, 0.5f);  // sentinel 1.8 bl → ~0.9 bl, fits the icon

        mc.getEntityRenderDispatcher().setRenderShadow(false);
        mc.getEntityRenderDispatcher().render(
                dummy, 0.0, 0.0, 0.0, 0.0f, 1.0f, pose, buffers, light);
        mc.getEntityRenderDispatcher().setRenderShadow(true);

        pose.popPose();
    }
}
