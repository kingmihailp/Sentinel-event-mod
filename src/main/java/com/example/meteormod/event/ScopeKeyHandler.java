package com.example.meteormod.event;

import com.example.meteormod.MeteorMod;
import com.example.meteormod.client.ModKeyBindings;
import com.example.meteormod.client.ScopeOverlay;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Client-only GAME-bus handlers for the Sentinel Cannon scope mode. */
@EventBusSubscriber(modid = MeteorMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ScopeKeyHandler {

    // ── Toggle scope on keybind press ──────────────────────────────────────

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (ModKeyBindings.SCOPE_KEY.consumeClick()) {
            ScopeOverlay.toggle();
        }
    }

    // ── Mouse scroll: zoom in/out while scope is active ────────────────────

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (!ScopeOverlay.active) return;

        double delta = event.getScrollDeltaY();
        if (delta > 0) {
            ScopeOverlay.zoomLevel = Math.min(ScopeOverlay.zoomLevel + 1, ScopeOverlay.MAX_ZOOM);
        } else if (delta < 0) {
            ScopeOverlay.zoomLevel = Math.max(ScopeOverlay.zoomLevel - 1, 0);
        }

        // Cancel so the hotbar doesn't scroll while in scope mode
        event.setCanceled(true);
    }

    // ── FOV: apply zoom level via ComputeFovModifierEvent ──────────────────

    @SubscribeEvent
    public static void onComputeFov(ComputeFovModifierEvent event) {
        if (!ScopeOverlay.active) return;

        // Each zoom step reduces FOV by 25% (0.75^n):
        // zoom 0 = 1.0×, zoom 1 = 1.33×, zoom 2 = 1.78×, zoom 3 = 2.37×, zoom 4 = 3.16×
        float zoomFactor = (float) Math.pow(0.75, ScopeOverlay.zoomLevel);
        event.setNewFovModifier(event.getNewFovModifier() * zoomFactor);
    }

    // ── Hide held item / arm while scope is active ─────────────────────────

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (ScopeOverlay.active) {
            event.setCanceled(true);
        }
    }

    // ── Red wireframe outline around all entities while scope is active ─────

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        if (!ScopeOverlay.active) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer lines = bufferSource.getBuffer(RenderType.lines());

        for (Entity entity : mc.level.entitiesForRendering()) {
            AABB box = entity.getBoundingBox();
            LevelRenderer.renderLineBox(poseStack, lines,
                    box.minX - camPos.x, box.minY - camPos.y, box.minZ - camPos.z,
                    box.maxX - camPos.x, box.maxY - camPos.y, box.maxZ - camPos.z,
                    1.0f, 0.0f, 0.0f, 1.0f);
        }

        bufferSource.endBatch(RenderType.lines());
    }
}
