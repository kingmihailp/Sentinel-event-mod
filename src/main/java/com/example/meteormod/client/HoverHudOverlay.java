package com.example.meteormod.client;

import com.example.meteormod.MeteorMod;
import com.example.meteormod.entity.SentinelHoverEntity;
import com.example.meteormod.network.ShootHoverPacket;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client-side HUD drawn while the player is riding the Sentinel Hover:
 *  • Replaces the vanilla crosshair with a blue targeting reticle.
 *  • Dims the hotbar to indicate it is locked.
 *
 * Input blocking (attack → shoot, use/scroll cancelled) is handled by
 * {@link HoverInputHandler} which lives on the GAME event bus.
 */
@OnlyIn(Dist.CLIENT)
public class HoverHudOverlay {

    /** Shoot cooldown tracker (client-side, mirrors server cooldown). */
    private static int clientShootCooldown = 0;

    public static void tickCooldown() {
        if (clientShootCooldown > 0) clientShootCooldown--;
    }

    /**
     * Called by HoverInputHandler when the player clicks LMB while riding.
     * Sends the packet and starts the client-side cooldown display.
     */
    public static void onShootPressed() {
        if (clientShootCooldown > 0) return;
        PacketDistributor.sendToServer(new ShootHoverPacket());
        clientShootCooldown = SentinelHoverEntity.SHOOT_COOLDOWN_TICKS;
    }

    // ── GUI layer render ─────────────────────────────────────────────────

    public static void render(GuiGraphics gfx, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.getVehicle() instanceof SentinelHoverEntity)) return;

        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();

        // ── Hotbar dim overlay ────────────────────────────────────────────
        int hotbarLeft  = w / 2 - 91;
        int hotbarRight = w / 2 + 91;
        int hotbarTop   = h - 22;
        gfx.fill(hotbarLeft, hotbarTop, hotbarRight, h, 0xAA000000);
        // Lock icon hint – small "LOCKED" text above hotbar
        String locked = "[ LOCKED ]";
        gfx.drawString(mc.font, locked,
                w / 2 - mc.font.width(locked) / 2,
                h - 26 - mc.font.lineHeight,
                0xFF00BBFF, false);

        // ── Blue targeting reticle ─────────────────────────────────────────
        int cx = w / 2;
        int cy = h / 2;

        final int COLOR  = 0xFF00BBFF;  // cyan-blue
        final int ARM    = 10;          // arm length
        final int GAP    = 4;           // gap around centre
        final int THICK  = 1;

        // Horizontal arms
        gfx.fill(cx - ARM - GAP, cy - THICK, cx - GAP, cy + THICK + 1, COLOR);
        gfx.fill(cx + GAP,       cy - THICK, cx + ARM + GAP, cy + THICK + 1, COLOR);
        // Vertical arms
        gfx.fill(cx - THICK, cy - ARM - GAP, cx + THICK + 1, cy - GAP, COLOR);
        gfx.fill(cx - THICK, cy + GAP,       cx + THICK + 1, cy + ARM + GAP, COLOR);
        // Centre dot
        gfx.fill(cx - 1, cy - 1, cx + 2, cy + 2, COLOR);

        // Cooldown arc indicator (simple rectangle fill shrinks as cooldown runs out)
        if (clientShootCooldown > 0) {
            float pct = (float) clientShootCooldown / SentinelHoverEntity.SHOOT_COOLDOWN_TICKS;
            int arcW = (int)((ARM * 2) * pct);
            gfx.fill(cx - ARM, cy + GAP + 4, cx - ARM + arcW, cy + GAP + 6, 0xFF0066FF);
        }
    }
}
