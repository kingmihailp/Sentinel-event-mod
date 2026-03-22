package com.example.meteormod.client;

import com.example.meteormod.item.ModItems;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Optional;

@OnlyIn(Dist.CLIENT)
public class ScopeOverlay {

    public static boolean active    = false;
    public static int     zoomLevel = 0;

    public static final int MAX_ZOOM = 4;

    public static void toggle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!holdsCannon(mc.player)) {
            active = false;
            return;
        }
        active = !active;
        if (!active) zoomLevel = 0; // reset zoom on close
    }

    public static void render(GuiGraphics gfx, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (!holdsCannon(mc.player)) {
            active = false;
            zoomLevel = 0;
            return;
        }
        if (!active) return;

        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();

        // ── 1. Dark green base
        gfx.fill(0, 0, w, h, 0xD5001A00);

        // ── 2. Phosphor glow tint
        gfx.fill(0, 0, w, h, 0x4000CC22);

        // ── 3. Vignette
        int vx = w / 5;
        int vy = h / 5;
        gfx.fill(0,      0,      vx,     h,      0x88000000);
        gfx.fill(w - vx, 0,      w,      h,      0x88000000);
        gfx.fill(vx,     0,      w - vx, vy,     0x88000000);
        gfx.fill(vx,     h - vy, w - vx, h,      0x88000000);

        // ── 4. Rounded corner masks (solid black quarter-circles)
        drawRoundedCorners(gfx, w, h, 45);

        // ── 5. Crosshair
        int cx = w / 2;
        int cy = h / 2;
        final int GAP   = 5;
        final int ARM   = 14;
        final int THICK = 2;
        final int COLOR = 0xFF00FF55;

        gfx.fill(cx - ARM - GAP, cy - THICK / 2,     cx - GAP,       cy + THICK / 2 + 1, COLOR);
        gfx.fill(cx + GAP,       cy - THICK / 2,     cx + ARM + GAP, cy + THICK / 2 + 1, COLOR);
        gfx.fill(cx - THICK / 2, cy - ARM - GAP,     cx + THICK / 2 + 1, cy - GAP,       COLOR);
        gfx.fill(cx - THICK / 2, cy + GAP,           cx + THICK / 2 + 1, cy + ARM + GAP, COLOR);
        gfx.fill(cx - 1, cy - 1, cx + 2, cy + 2, COLOR);

        // ── 6. Zoom level indicator (bottom-right, if zoomed)
        if (zoomLevel > 0) {
            String zoomText = (zoomLevel + 1) + "x";
            gfx.drawString(mc.font, zoomText,
                    w - vx / 2 - mc.font.width(zoomText) / 2,
                    h - vy / 2 - mc.font.lineHeight / 2,
                    0xFF00FF55, false);
        }

        // ── 7. Target entity name above crosshair ─────────────────────────
        Entity target = findScopeTarget(mc);
        if (target != null) {
            String name = target.getDisplayName().getString();
            gfx.drawString(mc.font, name,
                    cx - mc.font.width(name) / 2,
                    cy - ARM - GAP - mc.font.lineHeight - 4,
                    0xFFDD1111, false);
        }
    }

    /**
     * Draws solid black quarter-circle masks in all four corners so the
     * scope overlay appears to have rounded edges.
     */
    private static void drawRoundedCorners(GuiGraphics gfx, int w, int h, int radius) {
        for (int dy = 0; dy < radius; dy++) {
            // Width of the black block at this row from the corner edge
            int dx = radius - (int) Math.ceil(
                    Math.sqrt((double) radius * radius - (double) (radius - dy) * (radius - dy)));
            // Top-left
            gfx.fill(0,      dy,         dx, dy + 1,     0xFF000000);
            // Top-right
            gfx.fill(w - dx, dy,         w,  dy + 1,     0xFF000000);
            // Bottom-left
            gfx.fill(0,      h - dy - 1, dx, h - dy,     0xFF000000);
            // Bottom-right
            gfx.fill(w - dx, h - dy - 1, w,  h - dy,     0xFF000000);
        }
    }

    /** Raycasts up to 64 blocks from the player's eye to find the closest entity in the crosshair. */
    private static Entity findScopeTarget(Minecraft mc) {
        if (mc.level == null || mc.player == null) return null;
        Vec3 eye = mc.player.getEyePosition(1.0f);
        Vec3 end = eye.add(mc.player.getViewVector(1.0f).scale(64.0));
        Entity closest = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == mc.player) continue;
            Optional<Vec3> hit = e.getBoundingBox().inflate(0.3).clip(eye, end);
            if (hit.isPresent()) {
                double d = eye.distanceTo(hit.get());
                if (d < bestDist) {
                    bestDist = d;
                    closest = e;
                }
            }
        }
        return closest;
    }

    public static boolean holdsCannon(Player player) {
        return player.getMainHandItem().is(ModItems.SENTINEL_CANNON.get())
                || player.getOffhandItem().is(ModItems.SENTINEL_CANNON.get());
    }
}
