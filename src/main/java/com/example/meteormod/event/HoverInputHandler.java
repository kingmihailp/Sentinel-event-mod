package com.example.meteormod.event;

import com.example.meteormod.MeteorMod;
import com.example.meteormod.client.HoverHudOverlay;
import com.example.meteormod.entity.SentinelHoverEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * GAME-bus client-side event subscriber.
 *
 *  • Cancels the vanilla crosshair when riding the hover (replaced by HoverHudOverlay).
 *  • Blocks use-item / pick-block inputs while riding.
 *  • Redirects LMB (attack) to turret fire.
 *  • Blocks hotbar scroll while riding.
 */
@EventBusSubscriber(modid = MeteorMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class HoverInputHandler {

    // ── Tick cooldown ─────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        HoverHudOverlay.tickCooldown();
    }

    // ── Cancel vanilla crosshair ──────────────────────────────────────────

    @SubscribeEvent
    public static void onCrosshairPre(RenderGuiLayerEvent.Pre event) {
        if (!VanillaGuiLayers.CROSSHAIR.equals(event.getName())) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getVehicle() instanceof SentinelHoverEntity) {
            event.setCanceled(true); // our overlay draws its own crosshair
        }
    }

    // ── Block use / pick-block / attack → redirect attack to shoot ────────

    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.getVehicle() instanceof SentinelHoverEntity)) return;

        if (event.isAttack()) {
            event.setCanceled(true);
            event.setSwingHand(false);
            HoverHudOverlay.onShootPressed();
        } else if (event.isUseItem() || event.isPickBlock()) {
            event.setCanceled(true);
        }
    }

    // ── Block hotbar scroll ───────────────────────────────────────────────

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getVehicle() instanceof SentinelHoverEntity) {
            event.setCanceled(true);
        }
    }
}
