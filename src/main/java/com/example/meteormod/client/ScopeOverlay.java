package com.example.meteormod.client;

import com.example.meteormod.item.ModItems;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ScopeOverlay {

    public static boolean active = false;

    public static void toggle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        // Only activate if player holds the cannon
        if (!holdsCannon(mc.player)) {
            active = false;
            return;
        }
        active = !active;
    }

    public static void render(GuiGraphics gfx, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Auto-close if cannon is no longer in hand
        if (!holdsCannon(mc.player)) {
            active = false;
            return;
        }
        if (!active) return;

        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();

        // ── 1. Dark green base (most opaque layer — sets the green "screen" tone)
        gfx.fill(0, 0, w, h, 0xD5001A00);

        // ── 2. Lighter green tint to give the phosphor-glow
        gfx.fill(0, 0, w, h, 0x4000CC22);

        // ── 3. Horizontal scanlines every 3px — CRT/screen effect (slight pixelation)
        for (int y = 0; y < h; y += 3) {
            gfx.fill(0, y, w, y + 1, 0x44000000);
        }

        // ── 4. Vertical pixel-grid lines every 4px — reinforces "low-res screen"
        for (int x = 0; x < w; x += 4) {
            gfx.fill(x, 0, x + 1, h, 0x33000000);
        }

        // ── 5. Vignette — corners/edges darker to focus on center
        int vx = w / 5;
        int vy = h / 5;
        gfx.fill(0,      0,      vx,     h,      0x88000000); // left
        gfx.fill(w - vx, 0,      w,      h,      0x88000000); // right
        gfx.fill(vx,     0,      w - vx, vy,     0x88000000); // top
        gfx.fill(vx,     h - vy, w - vx, h,      0x88000000); // bottom

        // ── 6. Crosshair — two arms + center dot (bright green)
        int cx = w / 2;
        int cy = h / 2;
        final int GAP   = 5;   // gap around center
        final int ARM   = 14;  // arm length
        final int THICK = 2;   // arm thickness
        final int COLOR = 0xFF00FF55;

        // Horizontal arms
        gfx.fill(cx - ARM - GAP, cy - THICK / 2, cx - GAP, cy + THICK / 2 + 1, COLOR);
        gfx.fill(cx + GAP,       cy - THICK / 2, cx + ARM + GAP, cy + THICK / 2 + 1, COLOR);

        // Vertical arms
        gfx.fill(cx - THICK / 2, cy - ARM - GAP, cx + THICK / 2 + 1, cy - GAP, COLOR);
        gfx.fill(cx - THICK / 2, cy + GAP,       cx + THICK / 2 + 1, cy + ARM + GAP, COLOR);

        // Center dot (2×2)
        gfx.fill(cx - 1, cy - 1, cx + 2, cy + 2, COLOR);
    }

    private static boolean holdsCannon(Player player) {
        return player.getMainHandItem().is(ModItems.SENTINEL_CANNON.get())
                || player.getOffhandItem().is(ModItems.SENTINEL_CANNON.get());
    }
}
