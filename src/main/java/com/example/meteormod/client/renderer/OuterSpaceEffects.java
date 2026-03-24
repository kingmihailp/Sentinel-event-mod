package com.example.meteormod.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

import java.util.Random;

/**
 * Custom sky for the Outer Space dimension:
 *  – Pure black background
 *  – 1500 stars with per-star colours: purple (main), blue-white, rare yellow & red
 *  – Varied sizes; correct square-quad geometry
 *  – No sun, moon, clouds, or weather
 */
@OnlyIn(Dist.CLIENT)
public class OuterSpaceEffects extends DimensionSpecialEffects {

    /** Lazily built on the first render call (must be on the GL thread). */
    private VertexBuffer starBuffer;

    public OuterSpaceEffects() {
        super(Float.NaN,    // cloudLevel – NaN = no clouds
              false,        // hasGround
              SkyType.NONE, // no vanilla sky rendering
              false,        // forceBrightLightmap
              false);       // constantAmbientLight
    }

    // ── Fog colour: pure black void ───────────────────────────────────────

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 biomeColor, float brightness) {
        return Vec3.ZERO;
    }

    @Override
    public boolean isFoggyAt(int x, int z) {
        return false;
    }

    // ── Custom sky: coloured stars, return true to skip vanilla rendering ─

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick,
                             Matrix4f modelViewMatrix, Camera camera,
                             Matrix4f projectionMatrix, boolean isFoggy,
                             Runnable setupFog) {

        if (starBuffer == null) {
            starBuffer = buildStarBuffer();
        }

        RenderSystem.depthMask(false);
        // Vertex colours are baked in; reset shader colour to white so they show as-is
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        starBuffer.bind();
        starBuffer.drawWithShader(modelViewMatrix, projectionMatrix,
                GameRenderer.getPositionColorShader());
        VertexBuffer.unbind();

        RenderSystem.depthMask(true);

        return true; // skip vanilla rendering
    }

    // ── Star geometry builder ─────────────────────────────────────────────

    /**
     * Generates 1500 stars as {@code POSITION_COLOR} quads on a sphere of radius 100.
     *
     * <p>Quad winding follows vanilla's correct counterclockwise order so each star
     * renders as a true square (not a bowtie/triangle).
     *
     * <p>Colour distribution per star:
     * <ul>
     *   <li>70 % – bright purple  (0.75, 0.05, 1.0)</li>
     *   <li>20 % – blue-white     (0.75, 0.85, 1.0)</li>
     *   <li> 7 % – yellow         (1.0,  0.92, 0.25)</li>
     *   <li> 3 % – red            (1.0,  0.25, 0.12)</li>
     * </ul>
     */
    private static VertexBuffer buildStarBuffer() {
        VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        Random rng = new Random(10842L);

        for (int i = 0; i < 1500; i++) {
            double d0 = rng.nextFloat() * 2.0 - 1.0;
            double d1 = rng.nextFloat() * 2.0 - 1.0;
            double d2 = rng.nextFloat() * 2.0 - 1.0;

            // Varied star sizes: 60% tiny, 30% medium, 10% large
            double sizeRoll = rng.nextDouble();
            double d3 = sizeRoll < 0.60
                    ? 0.06 + rng.nextDouble() * 0.08   // tiny:   0.06 – 0.14
                    : sizeRoll < 0.90
                    ? 0.16 + rng.nextDouble() * 0.12   // medium: 0.16 – 0.28
                    : 0.32 + rng.nextDouble() * 0.22;  // large:  0.32 – 0.54

            double d4 = d0 * d0 + d1 * d1 + d2 * d2;
            if (d4 >= 1.0 || d4 <= 0.01) continue;

            d4 = 1.0 / Math.sqrt(d4);
            d0 *= d4; d1 *= d4; d2 *= d4;

            double cx = d0 * 100.0;
            double cy = d1 * 100.0;
            double cz = d2 * 100.0;

            double azimuth   = Math.atan2(d0, d2);
            double sinAz     = Math.sin(azimuth);
            double cosAz     = Math.cos(azimuth);
            double elevation = Math.atan2(Math.sqrt(d0 * d0 + d2 * d2), d1);
            double sinEl     = Math.sin(elevation);
            double cosEl     = Math.cos(elevation);

            double spin    = rng.nextDouble() * Math.PI * 2.0;
            double sinSpin = Math.sin(spin);
            double cosSpin = Math.cos(spin);

            // Per-star colour (using a fresh roll so geometry is unaffected)
            float[] col = pickStarColor(rng);
            float cr = col[0], cg = col[1], cb = col[2];

            // Emit four corners in correct CCW winding (vanilla algorithm)
            for (int j = 0; j < 4; j++) {
                double u = (double)((j & 2) - 1) * d3;       // -,-,+,+
                double v = (double)((j + 1 & 2) - 1) * d3;   // -,+,+,-

                double ru = u * cosSpin - v * sinSpin;
                double rv = v * cosSpin + u * sinSpin;

                double re = ru * sinEl;
                double qx = re * cosAz - (ru * cosEl) * sinAz;
                double qy = ru * cosEl;
                double qz = re * sinAz + (ru * cosEl) * cosAz;

                qx -= rv * sinAz;
                qz += rv * cosAz;

                builder.addVertex((float)(cx + qx), (float)(cy + qy), (float)(cz + qz))
                       .setColor(cr, cg, cb, 1.0f);
            }
        }

        MeshData mesh = builder.buildOrThrow();
        buffer.bind();
        buffer.upload(mesh);
        VertexBuffer.unbind();
        return buffer;
    }

    /** Returns a star colour based on realistic rarity distribution. */
    private static float[] pickStarColor(Random rng) {
        double roll = rng.nextDouble();
        if (roll < 0.70) {
            return new float[]{ 0.75f, 0.05f, 1.00f }; // purple (main)
        } else if (roll < 0.90) {
            return new float[]{ 0.75f, 0.85f, 1.00f }; // blue-white
        } else if (roll < 0.97) {
            return new float[]{ 1.00f, 0.92f, 0.25f }; // yellow (rare)
        } else {
            return new float[]{ 1.00f, 0.25f, 0.12f }; // red (very rare)
        }
    }
}
