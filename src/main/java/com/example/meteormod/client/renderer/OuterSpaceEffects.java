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
 *  – Pure black background (fog color = Vec3.ZERO)
 *  – 1500 dark-purple stars rendered as small quads
 *  – No sun, moon, clouds, or weather
 */
@OnlyIn(Dist.CLIENT)
public class OuterSpaceEffects extends DimensionSpecialEffects {

    // Dark purple star colour: R=0.35, G=0.0, B=0.6
    private static final float STAR_R = 0.35f;
    private static final float STAR_G = 0.00f;
    private static final float STAR_B = 0.60f;

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

    // ── Custom sky: purple stars, return true to skip vanilla rendering ───

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick,
                             Matrix4f modelViewMatrix, Camera camera,
                             Matrix4f projectionMatrix, boolean isFoggy,
                             Runnable setupFog) {

        // Build the star geometry once on the GL thread
        if (starBuffer == null) {
            starBuffer = buildStarBuffer();
        }

        RenderSystem.depthMask(false);

        // Set dark-purple tint and draw the pre-built star quads
        RenderSystem.setShaderColor(STAR_R, STAR_G, STAR_B, 1.0f);
        RenderSystem.setShader(GameRenderer::getPositionShader);
        starBuffer.bind();
        starBuffer.drawWithShader(modelViewMatrix, projectionMatrix,
                GameRenderer.getPositionShader());
        VertexBuffer.unbind();

        // Reset colour so nothing downstream gets tinted
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.depthMask(true);

        return true; // true = we handled the sky, skip vanilla rendering
    }

    // ── Star geometry builder ─────────────────────────────────────────────

    /**
     * Generates 1500 stars as small {@code POSITION} quads on a sphere of
     * radius 100, using the same algorithm as vanilla's {@code LevelRenderer}
     * star buffer. The vertex colour is controlled entirely via
     * {@link RenderSystem#setShaderColor} at draw time.
     */
    private static VertexBuffer buildStarBuffer() {
        VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);

        Random rng = new Random(10842L); // fixed seed → deterministic star field

        for (int i = 0; i < 1500; i++) {
            double d0 = rng.nextFloat() * 2.0 - 1.0;
            double d1 = rng.nextFloat() * 2.0 - 1.0;
            double d2 = rng.nextFloat() * 2.0 - 1.0;
            double d3 = 0.15 + rng.nextFloat() * 0.1;       // half-size
            double d4 = d0 * d0 + d1 * d1 + d2 * d2;

            if (d4 < 1.0 && d4 > 0.01) {
                d4 = 1.0 / Math.sqrt(d4);
                d0 *= d4; d1 *= d4; d2 *= d4;

                // Centre of star on 100-unit sphere
                double cx = d0 * 100.0;
                double cy = d1 * 100.0;
                double cz = d2 * 100.0;

                // Azimuth and elevation angles
                double azimuth   = Math.atan2(d0, d2);
                double sinAz     = Math.sin(azimuth);
                double cosAz     = Math.cos(azimuth);
                double elevation = Math.atan2(Math.sqrt(d0 * d0 + d2 * d2), d1);
                double sinEl     = Math.sin(elevation);
                double cosEl     = Math.cos(elevation);

                // Random spin of the quad
                double spin    = rng.nextDouble() * Math.PI * 2.0;
                double sinSpin = Math.sin(spin);
                double cosSpin = Math.cos(spin);

                // Emit four corners (vanilla drawStars algorithm)
                for (int j = 0; j < 4; j++) {
                    double u = (j & 2) == 0 ?  d3 : -d3;
                    double v = (j & 1) == 0 ?  d3 : -d3;

                    // Rotate corner by spin
                    double ru = u * cosSpin - v * sinSpin;
                    double rv = v * cosSpin + u * sinSpin;

                    // Project onto sphere surface
                    double re = ru * sinEl;
                    double qx = re * cosAz - (ru * cosEl) * sinAz;
                    double qy = ru * cosEl;
                    double qz = re * sinAz + (ru * cosEl) * cosAz;

                    // Offset perpendicular component (rv)
                    qx -= rv * sinAz;
                    qz += rv * cosAz;

                    builder.addVertex((float)(cx + qx), (float)(cy + qy), (float)(cz + qz));
                }
            }
        }

        MeshData mesh = builder.buildOrThrow();
        buffer.bind();
        buffer.upload(mesh);
        VertexBuffer.unbind();
        return buffer;
    }
}
