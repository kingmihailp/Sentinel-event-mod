package com.example.meteormod.client.renderer;

import com.example.meteormod.entity.TurretBulletEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Renders the turret bullet as a glowing cyan laser beam.
 *
 * Two quads form a cross (+) oriented along the velocity direction.
 * RenderType.lightning() gives additive blending → natural glow effect.
 * The beam is bright at the base and fades to transparent at the tip.
 */
public class TurretBulletRenderer extends EntityRenderer<TurretBulletEntity> {

    /** Beam visual length as a multiplier of the per-tick velocity. */
    private static final float LEN_MULT = 5.0f;
    /** Half-width of each quad in blocks. */
    private static final float HALF_W   = 0.05f;
    /** Core colour: R=0, G=200, B=255 (cyan-blue). */
    private static final int R = 0, G = 200, B = 255;

    public TurretBulletRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public void render(TurretBulletEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        Vec3 vel = entity.getDeltaMovement();
        if (vel.lengthSqr() < 1e-6) return;

        poseStack.pushPose();

        // ── Rotate so the local +Z axis points along the velocity ──────────
        float yawDeg   = (float) Math.toDegrees(Math.atan2(vel.x, vel.z));
        float pitchDeg = (float) Math.toDegrees(Math.atan2(-vel.y,
                Math.sqrt(vel.x * vel.x + vel.z * vel.z)));
        poseStack.mulPose(Axis.YP.rotationDegrees(yawDeg));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitchDeg));

        float len = (float)(vel.length() * LEN_MULT);
        VertexConsumer vc = buffer.getBuffer(RenderType.lightning());
        Matrix4f m = poseStack.last().pose();

        // Outer glow (wide, semi-transparent)
        beam(vc, m, HALF_W * 3, len, 80);
        // Core beam (narrow, opaque)
        beam(vc, m, HALF_W, len, 220);

        poseStack.popPose();
    }

    /** Draws an X-cross (2 quads at 90°) beam fading from {@code alpha} at the base to 0 at tip. */
    private static void beam(VertexConsumer vc, Matrix4f m, float hw, float len, int alpha) {
        // Horizontal slab
        quad(vc, m, -hw, 0,  hw, 0,  len, alpha);
        // Vertical slab
        quad(vc, m,  0, -hw, 0,  hw, len, alpha);
    }

    private static void quad(VertexConsumer vc, Matrix4f m,
                              float x0, float y0, float x1, float y1,
                              float len, int alpha) {
        vc.addVertex(m, x0, y0, 0  ).setColor(R, G, B, alpha);
        vc.addVertex(m, x1, y1, 0  ).setColor(R, G, B, alpha);
        vc.addVertex(m, x1, y1, len).setColor(R, G, B, 0);
        vc.addVertex(m, x0, y0, len).setColor(R, G, B, 0);
    }

    @Override
    public ResourceLocation getTextureLocation(TurretBulletEntity entity) {
        // Lightning render type is untextured; this is just required by the base class.
        return ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/white.png");
    }
}
