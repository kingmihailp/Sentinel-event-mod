package com.example.meteormod.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/**
 * Raid HUD overlay.
 *
 * Layout:
 * ┌─────────────────────────────┬──────────────────┐
 * │  ◆  ◆  ◆  ◆  ◆  ◆          │  scrolling code   │
 * │  (6 diamonds = 6 waves)     │  lines (decor)    │
 * └─────────────────────────────┴──────────────────┘
 * ╔══════════════════════════════════════════════════╗
 * ║  WAVE:  (galactic alphabet)    3  (wave number) ║
 * ╚══════════════════════════════════════════════════╝
 */
public final class RaidHudOverlay {

    private static final int TOTAL_WAVES = 6;

    // Diamond shape
    private static final int DIAMOND_HALF = 6;
    private static final int DIAMOND_SIZE = DIAMOND_HALF * 2 + 1; // 13 px
    private static final int DIAMOND_GAP  = 9;

    // Layout
    private static final int MARGIN_X    = 12;
    private static final int MARGIN_Y    = 12;
    private static final int PAD         = 5;
    private static final int CODE_W      = 108;  // width of code text panel

    // Colors (ARGB)
    private static final int COLOR_ACTIVE  = 0xFFDD1111; // bright red — waves left
    private static final int COLOR_DONE    = 0x44551111; // dim — waves cleared
    private static final int COLOR_BG      = 0xBB000000; // near-black backdrop
    private static final int COLOR_DIVIDER = 0xFF770000; // dark red line
    private static final int COLOR_CODE    = 0xFF00BB44; // green code text
    private static final int COLOR_BOTTOM  = 0xCC110000; // darker tint for bottom strip

    // Galactic alphabet font (Standard Galactic Alphabet / enchanting table font)
    private static final ResourceLocation GALACTIC_FONT =
            ResourceLocation.fromNamespaceAndPath("minecraft", "alt");

    // Rotating code lines shown in the right panel
    private static final String[] CODE_LINES = {
            "0x4F2A >> 0x1B3E",
            "SYS::RAID [ACTIVE]",
            "SENTINEL_CNT: ++",
            "0xC7D2 ^ 0x8F11",
            "THREAT: CRITICAL",
            "0x2E9A & 0x4C5B",
            "LOCK: PLAYER_TGT",
            "0xFF00 | 0xA10F",
            "SCAN: HOSTILE",
            "0xDEAD >> 0x02",
            "WAVE_SYS: ONLINE",
            "0xBEEF ^ 0xCAFE",
            "ERR 0x03 ALERT",
            "0x1337 << 0x04",
            "ENGAGE: CONFIRMED",
            "SYS::SPAWN_WAVE",
            "0xF00D & 0xBAD1",
            "ALERT_LVL: MAX",
            "TARGET: ACQUIRED",
            "0x7C3F | 0x0DA2",
    };

    // ── State ─────────────────────────────────────────────────────────────────
    private static volatile boolean active         = false;
    private static volatile int     completedWaves = 0;

    // Code scroller state
    private static long lastCodeTick = 0;
    private static int  codeOffset   = 0;

    private RaidHudOverlay() {}

    /** Called from network payload handler (enqueued on the client thread). */
    public static void update(boolean newActive, int newCompletedWaves) {
        active         = newActive;
        completedWaves = newCompletedWaves;
    }

    /** Registered as LayeredDraw.Layer — called every frame. */
    public static void render(GuiGraphics g, DeltaTracker delta) {
        if (!active) return;

        Minecraft mc   = Minecraft.getInstance();
        Font      font = mc.font;
        int       lh   = font.lineHeight + 2; // line height with spacing

        // ── Code scroller tick ────────────────────────────────────────────
        long now = System.currentTimeMillis();
        if (now - lastCodeTick > 750) {
            lastCodeTick = now;
            codeOffset   = (codeOffset + 1) % CODE_LINES.length;
        }

        // ── Main panel geometry ───────────────────────────────────────────
        // Diamond row width: 6 diamonds with gaps
        int dRowW  = TOTAL_WAVES * (DIAMOND_SIZE + DIAMOND_GAP) - DIAMOND_GAP;
        // Full panel content width: diamonds | divider | code
        int totalW = dRowW + PAD + 1 + PAD + CODE_W;
        // Panel height fits diamonds with vertical padding
        int panelH = DIAMOND_SIZE + PAD * 2;

        int px1 = MARGIN_X;
        int py1 = MARGIN_Y;
        int px2 = px1 + totalW;
        int py2 = py1 + panelH;

        int divX  = px1 + dRowW + PAD;      // x of the 1-px divider
        int codeX = divX + 1 + PAD;         // x where code text starts

        // ── Draw main panel ───────────────────────────────────────────────
        g.fill(px1 - PAD, py1 - PAD, px2 + PAD, py2 + PAD, COLOR_BG);

        // Vertical divider between diamonds and code
        g.fill(divX, py1 - PAD, divX + 1, py2 + PAD, COLOR_DIVIDER);

        // ── Diamonds ──────────────────────────────────────────────────────
        int dCY = (py1 + py2) / 2; // vertical center of panel
        for (int i = 0; i < TOTAL_WAVES; i++) {
            int cx    = px1 + DIAMOND_HALF + i * (DIAMOND_SIZE + DIAMOND_GAP);
            int color = (i < completedWaves) ? COLOR_DONE : COLOR_ACTIVE;
            drawDiamond(g, cx, dCY, DIAMOND_HALF, color);
        }

        // ── Scrolling code lines (3 visible at a time) ────────────────────
        int codeBlockH = 3 * lh;
        int codeY      = (py1 + py2 - codeBlockH) / 2;
        for (int i = 0; i < 3; i++) {
            String line = CODE_LINES[(codeOffset + i) % CODE_LINES.length];
            g.drawString(font, line, codeX, codeY + i * lh, COLOR_CODE, false);
        }

        // ── Bottom strip: galactic "WAVE:" + Arabic wave number ───────────
        int bY1 = py2 + PAD + 3;
        int bY2 = bY1 + font.lineHeight + PAD * 2;

        g.fill(px1 - PAD, bY1, px2 + PAD, bY2, COLOR_BOTTOM);

        // "WAVE: " rendered in Standard Galactic Alphabet
        Component galacticLabel = Component.literal("WAVE: ")
                .withStyle(Style.EMPTY.withFont(GALACTIC_FONT));

        int textY    = bY1 + PAD;
        int textX    = px1;
        int labelW   = font.width("WAVE: "); // SGA chars share metrics with regular font

        g.drawString(font, galacticLabel, textX, textY, COLOR_ACTIVE, false);

        // Wave number (Arabic numeral) right after the label
        int currentWave = Math.min(completedWaves + 1, TOTAL_WAVES);
        g.drawString(font, String.valueOf(currentWave), textX + labelW, textY, COLOR_ACTIVE, false);
    }

    /** Fills a diamond (rhombus) centred at (cx, cy) with the given half-radius. */
    private static void drawDiamond(GuiGraphics g, int cx, int cy, int half, int color) {
        for (int dy = -half; dy <= half; dy++) {
            int w = half - Math.abs(dy);
            g.fill(cx - w, cy + dy, cx + w + 1, cy + dy + 1, color);
        }
    }
}
