package com.example.meteormod.client.renderer;

import com.example.meteormod.entity.SentinelEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ParticleTypes;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.CoreGeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Client-side render layer for SentinelEntity particles.
 *
 * Reads live GeckoLib bone data (ALL bone pivot Y + rotation X) to accurately
 * track the nozzle and eye positions through the idle/scan animations.
 *
 * Coordinate conventions (sentinel.geo.json):
 *   Model +Z  = entity BACK  (nozzle ≈ z +9 px)
 *   Model -Z  = entity FRONT (eye    ≈ z -5.5 px)
 *   ALL bone default pivot: (-0.00488, 5.31873, 1.29378) px
 *   Idle anim: ALL Y +21 px (bob), ALL rotX +15 deg (tilt)
 */
public class SentinelParticleLayer extends GeoRenderLayer<SentinelEntity> {

    /** Prevent spawning more than once per game tick per entity. */
    private static final Map<Integer, Integer> lastSpawnTick = new HashMap<>();

    // ALL bone default pivot from model definition (pixels)
    private static final float ALL_PIVOT_Y = 5.31873f;
    private static final float ALL_PIVOT_Z = 1.29378f;

    // Nozzle: center of the thruster geometry (absolute model coords, pixels)
    private static final float NOZZLE_Y = 4.0f;
    private static final float NOZZLE_Z = 9.0f;   // positive Z = back

    // Eye: center of the front-face lens geometry (absolute model coords, pixels)
    private static final float EYE_Y = 5.5f;
    private static final float EYE_Z = -5.5f;    // negative Z = front

    public SentinelParticleLayer(GeoRenderer<SentinelEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, SentinelEntity entity, BakedGeoModel bakedModel,
                       RenderType renderType, MultiBufferSource bufferSource,
                       VertexConsumer buffer, float partialTick,
                       int packedLight, int packedOverlay, int colour) {

        int tick = entity.tickCount;
        // Run at most once per game tick per entity (render is called every frame)
        if (lastSpawnTick.getOrDefault(entity.getId(), -1) == tick) return;
        lastSpawnTick.put(entity.getId(), tick);

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        // ── Read live ALL bone state from GeckoLib ────────────────────────────
        // After animation is applied each frame:
        //   getPivotY() = defaultPivot + animationPositionOffset
        //   getRotX()   = current X-axis rotation in radians
        float curPivotY = ALL_PIVOT_Y; // default (no anim)
        float curRotX   = 0f;
        Optional<CoreGeoBone> allBoneOpt = getGeoModel().getBone("ALL");
        if (allBoneOpt.isPresent()) {
            CoreGeoBone bone = allBoneOpt.get();
            curPivotY = bone.getPivotY();  // includes idle animation Y offset
            curRotX   = bone.getRotX();    // idle animation X tilt (radians)
        }

        float cosR = (float) Math.cos(curRotX);
        float sinR = (float) Math.sin(curRotX);

        // ── Compute nozzle position in model space (pixels), accounting for
        //    ALL bone Y translation + X rotation ────────────────────────────────
        // offset of nozzle from ALL pivot (at rest)
        float nd_y = NOZZLE_Y - ALL_PIVOT_Y; // -1.31873
        float nd_z = NOZZLE_Z - ALL_PIVOT_Z; //  7.70622
        // after X rotation:
        float nozzle_px_y = curPivotY + nd_y * cosR - nd_z * sinR;
        float nozzle_px_z = ALL_PIVOT_Z + nd_y * sinR + nd_z * cosR;

        // ── Compute eye position in model space (pixels) ─────────────────────
        float ed_y = EYE_Y - ALL_PIVOT_Y; //  0.18127
        float ed_z = EYE_Z - ALL_PIVOT_Z; // -6.79378
        float eye_px_y = curPivotY + ed_y * cosR - ed_z * sinR;
        float eye_px_z = ALL_PIVOT_Z + ed_y * sinR + ed_z * cosR;

        // ── Convert to world space ────────────────────────────────────────────
        // model +Z = entity back → world offset: (zm*sin(yaw), 0, -zm*cos(yaw))
        // model -Z = entity front → world offset: (-ze*sin(yaw), 0, ze*cos(yaw))
        float yawRad = (float) Math.toRadians(entity.getYRot());
        double ex = entity.getX();
        double ey = entity.getY();
        double ez = entity.getZ();

        double nozzleWorldX = ex + (nozzle_px_z / 16.0) *  Math.sin(yawRad);
        double nozzleWorldY = ey +  nozzle_px_y / 16.0;
        double nozzleWorldZ = ez + (nozzle_px_z / 16.0) * -Math.cos(yawRad);

        double eyeWorldX = ex + (eye_px_z / 16.0) *  Math.sin(yawRad);
        double eyeWorldY = ey +  eye_px_y / 16.0;
        double eyeWorldZ = ez + (eye_px_z / 16.0) * -Math.cos(yawRad);

        // ── Nozzle: small fire trail every 2 ticks ────────────────────────────
        if (tick % 2 == 0) {
            for (int i = 0; i < 2; i++) {
                double vx = (Math.random() - 0.5) * 0.06;
                double vy = (Math.random() - 0.5) * 0.06;
                double vz = (Math.random() - 0.5) * 0.06;
                level.addParticle(ParticleTypes.FLAME,
                        nozzleWorldX, nozzleWorldY, nozzleWorldZ, vx, vy, vz);
            }
        }

        // ── Eye: ELECTRIC_SPARK beam every 5 ticks during scanning ────────────
        if (entity.isScanning() && tick % 5 == 0) {
            // Direction = entity's current head look vector (synced from server)
            float headYaw   = (float) Math.toRadians(entity.getYHeadRot());
            float headPitch = (float) Math.toRadians(entity.getXRot());
            double lookX = -Math.sin(headYaw) * Math.cos(headPitch);
            double lookY = -Math.sin(headPitch);
            double lookZ =  Math.cos(headYaw) * Math.cos(headPitch);

            for (int i = 0; i < 8; i++) {
                double vx = lookX * 0.3 + (Math.random() - 0.5) * 0.1;
                double vy = lookY * 0.3 + (Math.random() - 0.5) * 0.1;
                double vz = lookZ * 0.3 + (Math.random() - 0.5) * 0.1;
                level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                        eyeWorldX, eyeWorldY, eyeWorldZ, vx, vy, vz);
            }
        }
    }
}
