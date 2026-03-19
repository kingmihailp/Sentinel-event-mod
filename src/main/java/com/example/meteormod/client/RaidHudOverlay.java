package com.example.meteormod.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Client-side HUD: six red diamond icons in the top-left corner.
 *
 * Inspired by No Man's Sky sentinel alert levels:
 *   - Remaining waves  → solid bright red ◆
 *   - Defeated waves   → dim dark red     ◆ (faded out)
 *
 * Updated via RaidUpdatePayload from the server.
 */
public final class RaidHudOverlay {

    private static final int TOTAL_WAVES = 6;

    // Visual constants
    private static final int DIAMOND_HALF = 5;        // half-width of each diamond in px
    private static final int DIAMOND_SIZE = DIAMOND_HALF * 2 + 1; // 11 px
    private static final int GAP          = 5;         // gap between diamonds
    private static final int MARGIN_X     = 10;
    private static final int MARGIN_Y     = 10;

    // Colors (ARGB)
    private static final int COLOR_ACTIVE   = 0xFFDD1111; // bright red  — waves remaining
    private static final int COLOR_DONE     = 0x66441111; // dim dark red — waves cleared
    private static final int COLOR_BG       = 0x55000000; // semi-transparent backdrop

    // ── State (written from network thread, read from render thread) ──────────
    private static volatile boolean active         = false;
    private static volatile int     completedWaves = 0;

    private RaidHudOverlay() {}

    /** Called by the network payload handler (main/client thread). */
    public static void update(boolean newActive, int newCompletedWaves) {
        active         = newActive;
        completedWaves = newCompletedWaves;
    }

    /**
     * Registered as a {@code LayeredDraw.Layer}; called every frame while active.
     * Signature matches {@code LayeredDraw.Layer}: (GuiGraphics, DeltaTracker).
     */
    public static void render(GuiGraphics g, DeltaTracker delta) {
        if (!active) return;

        int totalWidth = TOTAL_WAVES * DIAMOND_SIZE + (TOTAL_WAVES - 1) * GAP;
        int bgX1 = MARGIN_X - 4;
        int bgY1 = MARGIN_Y - 4;
        int bgX2 = MARGIN_X + totalWidth + 4;
        int bgY2 = MARGIN_Y + DIAMOND_SIZE + 4;

        // Semi-transparent backdrop for readability
        g.fill(bgX1, bgY1, bgX2, bgY2, COLOR_BG);

        for (int i = 0; i < TOTAL_WAVES; i++) {
            int cx = MARGIN_X + DIAMOND_HALF + i * (DIAMOND_SIZE + GAP);
            int cy = MARGIN_Y + DIAMOND_HALF;
            int color = (i < completedWaves) ? COLOR_DONE : COLOR_ACTIVE;
            drawDiamond(g, cx, cy, DIAMOND_HALF, color);
        }
    }

    /** Fills a diamond (rhombus) centred at (cx, cy) with the given half-radius. */
    private static void drawDiamond(GuiGraphics g, int cx, int cy, int half, int color) {
        for (int dy = -half; dy <= half; dy++) {
            int w = half - Math.abs(dy);
            g.fill(cx - w, cy + dy, cx + w + 1, cy + dy + 1, color);
        }
    }
}
